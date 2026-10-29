use std::{
    cmp::Ordering,
    fs,
    path::PathBuf,
};

use anyhow::{anyhow, Context, Result};
use chrono::{DateTime, Duration, Local, Utc};
use csv::Writer;
use rusqlite::{params, Connection, OptionalExtension, Transaction};
use rust_xlsxwriter::{Workbook, Worksheet};

use crate::models::{
    AppBootstrapData, AppSettingsRecord, CreateTagInput, DashboardData, DashboardStats,
    DuplicateMatch, DuplicateQuery, ExportProfilesInput, ExportSingleProfileInput,
    InteractionInput, InteractionLogRecord, ProfileDetailRecord, ProfileInput, ReminderEventRecord,
    SavedFilterInput, SavedFilterRecord, SearchFilters, StudentProfileRecord, TagRecord,
    UpdateReminderInput, UpdateSettingsInput,
};

const DEFAULT_REMINDER_TIME: &str = "09:00";

#[derive(Clone)]
pub struct Database {
    db_path: PathBuf,
}

impl Database {
    pub fn new(db_path: PathBuf) -> Result<Self> {
        if let Some(parent) = db_path.parent() {
            fs::create_dir_all(parent).with_context(|| {
                format!("failed to create app data directory {}", parent.display())
            })?;
        }

        let database = Self { db_path };
        database.initialize()?;
        Ok(database)
    }

    pub fn bootstrap(&self) -> Result<AppBootstrapData> {
        Ok(AppBootstrapData {
            tags: self.list_tags()?,
            saved_filters: self.list_saved_filters()?,
            settings: self.get_settings()?,
            dashboard: self.dashboard()?,
        })
    }

    pub fn search_profiles(&self, filters: SearchFilters) -> Result<Vec<StudentProfileRecord>> {
        let mut profiles = self.load_profiles(filters.include_archived)?;
        profiles.retain(|profile| profile_matches(profile, &filters));
        sort_profiles(&mut profiles, filters.sort_by.as_deref());
        Ok(profiles)
    }

    pub fn get_profile_detail(&self, student_profile_id: i64) -> Result<ProfileDetailRecord> {
        let conn = self.open_connection()?;
        let profile = self
            .load_profile_by_id_conn(&conn, student_profile_id)?
            .ok_or_else(|| anyhow!("未找到对应档案"))?;

        Ok(ProfileDetailRecord {
            profile,
            interactions: self.list_interactions_by_profile_conn(&conn, student_profile_id)?,
            reminders: self.list_reminders_for_profile_conn(&conn, student_profile_id)?,
        })
    }

    pub fn create_profile(&self, input: ProfileInput) -> Result<StudentProfileRecord> {
        validate_profile_input(&input)?;
        let mut conn = self.open_connection()?;
        let tx = conn.transaction()?;
        let now = now_iso();
        let full_name = normalize_required(&input.full_name);

        tx.execute(
            "INSERT INTO student_profiles (
                full_name, nickname, phone, wechat_id, school, major, grade, city,
                identity_type, focus_directions, demand_summary, intention_level,
                urgency_level, verification_status, source_channel, current_status,
                owner_notes, created_at, updated_at, last_contact_at, next_follow_up_at, is_archived
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?18, NULL, ?19, 0)",
            params![
                full_name,
                normalize_optional(&input.nickname),
                normalize_optional(&input.phone),
                normalize_optional(&input.wechat_id),
                normalize_optional(&input.school),
                normalize_optional(&input.major),
                normalize_optional(&input.grade),
                normalize_optional(&input.city),
                normalize_required(&input.identity_type),
                serialize_string_array(&input.focus_directions)?,
                normalize_optional(&input.demand_summary),
                normalize_required(&input.intention_level),
                normalize_required(&input.urgency_level),
                normalize_required(&input.verification_status),
                normalize_optional(&input.source_channel),
                normalize_required(&input.current_status),
                normalize_optional(&input.owner_notes),
                now,
                normalize_optional(&input.next_follow_up_at),
            ],
        )?;

        let profile_id = tx.last_insert_rowid();
        self.sync_profile_tags(&tx, profile_id, &input.tag_ids)?;
        self.replace_pending_reminders(
            &tx,
            profile_id,
            None,
            input.next_follow_up_at.as_deref(),
            format!("跟进 {}", normalize_required(&input.full_name)),
        )?;
        tx.commit()?;

        let conn = self.open_connection()?;
        self.load_profile_by_id_conn(&conn, profile_id)?
            .ok_or_else(|| anyhow!("新建档案后读取失败"))
    }

    pub fn update_profile(&self, student_profile_id: i64, input: ProfileInput) -> Result<StudentProfileRecord> {
        validate_profile_input(&input)?;
        let mut conn = self.open_connection()?;
        let tx = conn.transaction()?;

        let existing = self
            .load_profile_by_id_tx(&tx, student_profile_id)?
            .ok_or_else(|| anyhow!("未找到对应档案"))?;
        let now = now_iso();

        tx.execute(
            "UPDATE student_profiles SET
                full_name = ?1,
                nickname = ?2,
                phone = ?3,
                wechat_id = ?4,
                school = ?5,
                major = ?6,
                grade = ?7,
                city = ?8,
                identity_type = ?9,
                focus_directions = ?10,
                demand_summary = ?11,
                intention_level = ?12,
                urgency_level = ?13,
                verification_status = ?14,
                source_channel = ?15,
                current_status = ?16,
                owner_notes = ?17,
                updated_at = ?18,
                next_follow_up_at = ?19
             WHERE id = ?20",
            params![
                normalize_required(&input.full_name),
                normalize_optional(&input.nickname),
                normalize_optional(&input.phone),
                normalize_optional(&input.wechat_id),
                normalize_optional(&input.school),
                normalize_optional(&input.major),
                normalize_optional(&input.grade),
                normalize_optional(&input.city),
                normalize_required(&input.identity_type),
                serialize_string_array(&input.focus_directions)?,
                normalize_optional(&input.demand_summary),
                normalize_required(&input.intention_level),
                normalize_required(&input.urgency_level),
                normalize_required(&input.verification_status),
                normalize_optional(&input.source_channel),
                normalize_required(&input.current_status),
                normalize_optional(&input.owner_notes),
                now,
                normalize_optional(&input.next_follow_up_at),
                student_profile_id,
            ],
        )?;

        self.sync_profile_tags(&tx, student_profile_id, &input.tag_ids)?;
        self.replace_pending_reminders(
            &tx,
            student_profile_id,
            None,
            input.next_follow_up_at.as_deref(),
            format!("跟进 {}", normalize_required(&input.full_name)),
        )?;
        tx.commit()?;

        let conn = self.open_connection()?;
        self.load_profile_by_id_conn(&conn, existing.id)?
            .ok_or_else(|| anyhow!("更新档案后读取失败"))
    }

    pub fn archive_profile(&self, student_profile_id: i64) -> Result<()> {
        let mut conn = self.open_connection()?;
        let tx = conn.transaction()?;
        let now = now_iso();

        tx.execute(
            "UPDATE student_profiles
             SET is_archived = 1,
                 current_status = '归档',
                 next_follow_up_at = NULL,
                 updated_at = ?1
             WHERE id = ?2",
            params![now, student_profile_id],
        )?;
        tx.execute(
            "UPDATE reminder_events
             SET status = 'cancelled', updated_at = ?1
             WHERE student_profile_id = ?2 AND status IN ('pending', 'sent', 'overdue')",
            params![now_iso(), student_profile_id],
        )?;
        tx.commit()?;
        Ok(())
    }

    pub fn find_potential_duplicates(&self, query: DuplicateQuery) -> Result<Vec<DuplicateMatch>> {
        let profiles = self.load_profiles(true)?;
        let normalized_name = normalize_required(&query.full_name).to_lowercase();
        let normalized_phone = query.phone.as_deref().map(normalize_required);
        let normalized_wechat = query.wechat_id.as_deref().map(normalize_required);
        let mut matches = Vec::new();

        for profile in profiles {
            let mut reasons = Vec::new();

            if let Some(phone) = normalized_phone.as_deref() {
                if profile.phone.as_deref() == Some(phone) {
                    reasons.push("手机号一致".to_string());
                }
            }

            if let Some(wechat) = normalized_wechat.as_deref() {
                if profile.wechat_id.as_deref() == Some(wechat) {
                    reasons.push("微信号一致".to_string());
                }
            }

            if !normalized_name.is_empty() {
                let current_name = profile.full_name.to_lowercase();
                if current_name == normalized_name {
                    reasons.push("姓名一致".to_string());
                } else if current_name.contains(&normalized_name) || normalized_name.contains(&current_name) {
                    reasons.push("姓名相近".to_string());
                }
            }

            if !reasons.is_empty() {
                matches.push(DuplicateMatch { profile, reasons });
            }
        }

        Ok(matches)
    }

    pub fn add_interaction_and_refresh_profile(&self, input: InteractionInput) -> Result<ProfileDetailRecord> {
        validate_interaction_input(&input)?;
        let mut conn = self.open_connection()?;
        let tx = conn.transaction()?;
        let now = now_iso();

        let current_profile = self
            .load_profile_by_id_tx(&tx, input.student_profile_id)?
            .ok_or_else(|| anyhow!("未找到对应档案"))?;

        tx.execute(
            "INSERT INTO interaction_logs (
                student_profile_id, contacted_at, channel, summary, details, outcome,
                verification_todo, next_follow_up_at, created_at
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                input.student_profile_id,
                normalize_required(&input.contacted_at),
                normalize_required(&input.channel),
                normalize_required(&input.summary),
                normalize_optional(&input.details),
                normalize_optional(&input.outcome),
                normalize_optional(&input.verification_todo),
                normalize_optional(&input.next_follow_up_at),
                now,
            ],
        )?;
        let interaction_id = tx.last_insert_rowid();

        tx.execute(
            "UPDATE student_profiles SET
                last_contact_at = ?1,
                next_follow_up_at = ?2,
                current_status = COALESCE(?3, current_status),
                intention_level = COALESCE(?4, intention_level),
                verification_status = COALESCE(?5, verification_status),
                updated_at = ?6
             WHERE id = ?7",
            params![
                normalize_required(&input.contacted_at),
                normalize_optional(&input.next_follow_up_at),
                normalize_optional_ref(input.current_status.as_deref()),
                normalize_optional_ref(input.intention_level.as_deref()),
                normalize_optional_ref(input.verification_status.as_deref()),
                now_iso(),
                input.student_profile_id,
            ],
        )?;

        if let Some(tag_ids) = input.tag_ids.as_ref() {
            self.sync_profile_tags(&tx, input.student_profile_id, tag_ids)?;
        }

        self.replace_pending_reminders(
            &tx,
            input.student_profile_id,
            Some(interaction_id),
            input.next_follow_up_at.as_deref(),
            format!("跟进 {}", current_profile.full_name),
        )?;
        tx.commit()?;

        self.get_profile_detail(input.student_profile_id)
    }

    pub fn list_interactions_by_profile(&self, student_profile_id: i64) -> Result<Vec<InteractionLogRecord>> {
        let conn = self.open_connection()?;
        self.list_interactions_by_profile_conn(&conn, student_profile_id)
    }

    pub fn update_next_follow_up(&self, input: UpdateReminderInput) -> Result<ProfileDetailRecord> {
        let mut conn = self.open_connection()?;
        let tx = conn.transaction()?;
        let current_profile = self
            .load_profile_by_id_tx(&tx, input.student_profile_id)?
            .ok_or_else(|| anyhow!("未找到对应档案"))?;

        tx.execute(
            "UPDATE student_profiles
             SET next_follow_up_at = ?1, updated_at = ?2
             WHERE id = ?3",
            params![
                normalize_optional(&input.next_follow_up_at),
                now_iso(),
                input.student_profile_id
            ],
        )?;

        self.replace_pending_reminders(
            &tx,
            input.student_profile_id,
            None,
            input.next_follow_up_at.as_deref(),
            format!("跟进 {}", current_profile.full_name),
        )?;
        tx.commit()?;

        self.get_profile_detail(input.student_profile_id)
    }

    pub fn save_filter(&self, input: SavedFilterInput) -> Result<SavedFilterRecord> {
        let conn = self.open_connection()?;
        let name = normalize_required(&input.name);
        conn.execute(
            "INSERT INTO saved_filters (name, filters_json, sort_by, show_on_homepage, created_at)
             VALUES (?1, ?2, ?3, ?4, ?5)
             ON CONFLICT(name) DO UPDATE SET
               filters_json = excluded.filters_json,
               sort_by = excluded.sort_by,
               show_on_homepage = excluded.show_on_homepage",
            params![
                name,
                serde_json::to_string(&input.filters)?,
                normalize_required(&input.sort_by),
                bool_to_int(input.show_on_homepage),
                now_iso(),
            ],
        )?;
        self.get_saved_filter_by_name(&conn, &name)?
            .ok_or_else(|| anyhow!("保存分组后读取失败"))
    }

    pub fn delete_saved_filter(&self, filter_id: i64) -> Result<()> {
        let conn = self.open_connection()?;
        conn.execute("DELETE FROM saved_filters WHERE id = ?1", params![filter_id])?;
        Ok(())
    }

    pub fn list_saved_filters(&self) -> Result<Vec<SavedFilterRecord>> {
        let conn = self.open_connection()?;
        self.list_saved_filters_conn(&conn)
    }

    pub fn run_saved_filter(&self, filter_id: i64) -> Result<Vec<StudentProfileRecord>> {
        let conn = self.open_connection()?;
        let saved_filter = self
            .get_saved_filter_by_id(&conn, filter_id)?
            .ok_or_else(|| anyhow!("未找到保存分组"))?;
        let mut filters = saved_filter.filters;
        filters.sort_by = Some(saved_filter.sort_by);
        self.search_profiles(filters)
    }

    pub fn list_homepage_filters(&self) -> Result<Vec<SavedFilterRecord>> {
        Ok(self
            .list_saved_filters()?
            .into_iter()
            .filter(|filter| filter.show_on_homepage)
            .collect())
    }

    pub fn list_tags(&self) -> Result<Vec<TagRecord>> {
        let conn = self.open_connection()?;
        let mut stmt = conn.prepare(
            "SELECT id, name, category, color, is_system
             FROM tags
             ORDER BY is_system DESC, category ASC, name ASC",
        )?;
        let tags = stmt
            .query_map([], |row| {
                Ok(TagRecord {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    category: row.get(2)?,
                    color: row.get(3)?,
                    is_system: row.get::<_, i64>(4)? == 1,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(tags)
    }

    pub fn create_tag(&self, input: CreateTagInput) -> Result<TagRecord> {
        let conn = self.open_connection()?;
        conn.execute(
            "INSERT INTO tags (name, category, color, is_system)
             VALUES (?1, ?2, ?3, 0)",
            params![
                normalize_required(&input.name),
                normalize_required(&input.category),
                normalize_required(&input.color),
            ],
        )?;
        let tag_id = conn.last_insert_rowid();
        self.get_tag_by_id(&conn, tag_id)?
            .ok_or_else(|| anyhow!("标签创建后读取失败"))
    }

    pub fn dashboard(&self) -> Result<DashboardData> {
        let active_profiles = self.load_profiles(false)?;
        let all_profiles = self.load_profiles(true)?;

        let now = Local::now();
        let today = now.date_naive();
        let seven_days_ago = now - Duration::days(7);
        let three_days_ago = now - Duration::days(3);

        let mut due_today = Vec::new();
        let mut overdue = Vec::new();
        let mut recent_created = Vec::new();
        let mut recent_updated = Vec::new();
        let mut stale_high_intention = Vec::new();
        let mut high_intention_count = 0usize;

        for profile in &active_profiles {
            if profile.intention_level == "高" {
                high_intention_count += 1;
            }

            if let Some(next_follow_up_at) = profile.next_follow_up_at.as_deref() {
                if let Ok(value) = parse_local_datetime(next_follow_up_at) {
                    if value.date_naive() == today {
                        due_today.push(profile.clone());
                    }
                    if value < now {
                        overdue.push(profile.clone());
                    }
                }
            }

            if parse_local_datetime(&profile.created_at)
                .map(|value| value >= seven_days_ago)
                .unwrap_or(false)
            {
                recent_created.push(profile.clone());
            }

            if parse_local_datetime(&profile.updated_at)
                .map(|value| value >= seven_days_ago)
                .unwrap_or(false)
            {
                recent_updated.push(profile.clone());
            }

            if profile.intention_level == "高" {
                let stale = profile
                    .last_contact_at
                    .as_deref()
                    .and_then(|value| parse_local_datetime(value).ok())
                    .map(|value| value <= three_days_ago)
                    .unwrap_or(true);
                if stale {
                    stale_high_intention.push(profile.clone());
                }
            }
        }

        sort_profiles(&mut due_today, Some("nextFollowUpAsc"));
        sort_profiles(&mut overdue, Some("nextFollowUpAsc"));
        sort_profiles(&mut recent_created, Some("createdDesc"));
        sort_profiles(&mut recent_updated, Some("updatedDesc"));
        sort_profiles(&mut stale_high_intention, Some("lastContactDesc"));

        Ok(DashboardData {
            stats: DashboardStats {
                total_active: active_profiles.len(),
                archived_count: all_profiles.iter().filter(|profile| profile.is_archived).count(),
                today_due_count: due_today.len(),
                overdue_count: overdue.len(),
                high_intention_count,
            },
            due_today: take_limit(due_today, 8),
            overdue: take_limit(overdue, 8),
            recent_created: take_limit(recent_created, 8),
            recent_updated: take_limit(recent_updated, 8),
            stale_high_intention: take_limit(stale_high_intention, 8),
            homepage_filters: self.list_homepage_filters()?,
        })
    }

    pub fn list_due_reminders(&self) -> Result<Vec<ReminderEventRecord>> {
        let conn = self.open_connection()?;
        let mut stmt = conn.prepare(
            "SELECT id, student_profile_id, interaction_log_id, remind_at, title, status, notification_sent, created_at, updated_at
             FROM reminder_events
             WHERE status IN ('pending', 'sent', 'overdue')
             ORDER BY remind_at ASC",
        )?;
        let now = Local::now();

        let reminders = stmt
            .query_map([], |row| {
                let remind_at: String = row.get(3)?;
                let status = row.get::<_, String>(5)?;
                let normalized_status = if status == "pending"
                    && parse_local_datetime(&remind_at)
                        .map(|value| value < now)
                        .unwrap_or(false)
                {
                    "overdue".to_string()
                } else {
                    status
                };

                Ok(ReminderEventRecord {
                    id: row.get(0)?,
                    student_profile_id: row.get(1)?,
                    interaction_log_id: row.get(2)?,
                    remind_at,
                    title: row.get(4)?,
                    status: normalized_status,
                    notification_sent: row.get::<_, i64>(6)? == 1,
                    created_at: row.get(7)?,
                    updated_at: row.get(8)?,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;

        Ok(reminders)
    }

    pub fn mark_reminder_sent(&self, reminder_id: i64) -> Result<()> {
        let conn = self.open_connection()?;
        conn.execute(
            "UPDATE reminder_events
             SET notification_sent = 1, status = 'sent', updated_at = ?1
             WHERE id = ?2",
            params![now_iso(), reminder_id],
        )?;
        Ok(())
    }

    pub fn get_settings(&self) -> Result<AppSettingsRecord> {
        let conn = self.open_connection()?;
        self.load_settings_from_conn(&conn)
    }

    pub fn update_settings(&self, input: UpdateSettingsInput) -> Result<AppSettingsRecord> {
        let conn = self.open_connection()?;
        conn.execute(
            "INSERT INTO app_settings (key, value) VALUES ('defaultReminderTime', ?1)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![normalize_required(&input.default_reminder_time)],
        )?;
        self.load_settings_from_conn(&conn)
    }

    pub fn export_profiles_to_xlsx(&self, input: ExportProfilesInput) -> Result<String> {
        let profiles = self.search_profiles(input.filters)?;
        let output_path = PathBuf::from(input.file_path);
        let mut workbook = Workbook::new();

        {
            let worksheet = workbook.add_worksheet();
            worksheet.set_name("档案")?;
            write_profile_sheet(worksheet, &profiles)?;
        }

        {
            let worksheet = workbook.add_worksheet();
            worksheet.set_name("跟进记录")?;
            write_interaction_sheet(worksheet, self, &profiles)?;
        }

        workbook.save(&output_path)?;
        Ok(output_path.display().to_string())
    }

    pub fn export_profiles_to_csv(&self, input: ExportProfilesInput) -> Result<String> {
        let profiles = self.search_profiles(input.filters)?;
        let output_path = PathBuf::from(input.file_path);
        let mut writer = Writer::from_path(&output_path)?;
        write_profile_csv(&mut writer, &profiles)?;
        writer.flush()?;
        Ok(output_path.display().to_string())
    }

    pub fn export_single_profile_timeline(&self, input: ExportSingleProfileInput) -> Result<String> {
        let detail = self.get_profile_detail(input.student_profile_id)?;
        let output_path = PathBuf::from(input.file_path);
        let mut workbook = Workbook::new();

        {
            let worksheet = workbook.add_worksheet();
            worksheet.set_name("档案")?;
            write_profile_sheet(worksheet, &[detail.profile.clone()])?;
        }

        {
            let worksheet = workbook.add_worksheet();
            worksheet.set_name("时间线")?;
            write_single_profile_timeline_sheet(worksheet, &detail)?;
        }

        workbook.save(&output_path)?;
        Ok(output_path.display().to_string())
    }

    pub fn backup_database(&self, file_path: String) -> Result<String> {
        let target = PathBuf::from(file_path);
        if let Some(parent) = target.parent() {
            fs::create_dir_all(parent)?;
        }

        fs::copy(&self.db_path, &target)?;
        let conn = self.open_connection()?;
        conn.execute(
            "INSERT INTO app_settings (key, value) VALUES ('lastBackupAt', ?1)
             ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![now_iso()],
        )?;
        Ok(target.display().to_string())
    }

    fn initialize(&self) -> Result<()> {
        let conn = self.open_connection()?;
        conn.execute_batch(
            "
            CREATE TABLE IF NOT EXISTS student_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL,
                nickname TEXT,
                phone TEXT,
                wechat_id TEXT,
                school TEXT,
                major TEXT,
                grade TEXT,
                city TEXT,
                identity_type TEXT NOT NULL,
                focus_directions TEXT NOT NULL,
                demand_summary TEXT,
                intention_level TEXT NOT NULL,
                urgency_level TEXT NOT NULL,
                verification_status TEXT NOT NULL,
                source_channel TEXT,
                current_status TEXT NOT NULL,
                owner_notes TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                last_contact_at TEXT,
                next_follow_up_at TEXT,
                is_archived INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS interaction_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_profile_id INTEGER NOT NULL,
                contacted_at TEXT NOT NULL,
                channel TEXT NOT NULL,
                summary TEXT NOT NULL,
                details TEXT,
                outcome TEXT,
                verification_todo TEXT,
                next_follow_up_at TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                category TEXT NOT NULL,
                color TEXT NOT NULL,
                is_system INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS profile_tags (
                student_profile_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY (student_profile_id, tag_id),
                FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS saved_filters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                filters_json TEXT NOT NULL,
                sort_by TEXT NOT NULL,
                show_on_homepage INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS reminder_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_profile_id INTEGER NOT NULL,
                interaction_log_id INTEGER,
                remind_at TEXT NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                notification_sent INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY(student_profile_id) REFERENCES student_profiles(id) ON DELETE CASCADE,
                FOREIGN KEY(interaction_log_id) REFERENCES interaction_logs(id) ON DELETE SET NULL
            );

            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            ",
        )?;

        self.seed_tags(&conn)?;
        self.seed_settings(&conn)?;
        Ok(())
    }

    fn open_connection(&self) -> Result<Connection> {
        let conn = Connection::open(&self.db_path)?;
        conn.execute("PRAGMA foreign_keys = ON", [])?;
        Ok(conn)
    }

    fn seed_tags(&self, conn: &Connection) -> Result<()> {
        for (name, category, color, is_system) in default_tags() {
            conn.execute(
                "INSERT OR IGNORE INTO tags (name, category, color, is_system)
                 VALUES (?1, ?2, ?3, ?4)",
                params![name, category, color, bool_to_int(is_system)],
            )?;
        }
        Ok(())
    }

    fn seed_settings(&self, conn: &Connection) -> Result<()> {
        conn.execute(
            "INSERT OR IGNORE INTO app_settings (key, value) VALUES ('defaultReminderTime', ?1)",
            params![DEFAULT_REMINDER_TIME],
        )?;
        conn.execute(
            "INSERT OR IGNORE INTO app_settings (key, value) VALUES ('lastBackupAt', '')",
            [],
        )?;
        Ok(())
    }

    fn load_profiles(&self, include_archived: bool) -> Result<Vec<StudentProfileRecord>> {
        let conn = self.open_connection()?;
        let sql = if include_archived {
            "SELECT id, full_name, nickname, phone, wechat_id, school, major, grade, city,
                    identity_type, focus_directions, demand_summary, intention_level, urgency_level,
                    verification_status, source_channel, current_status, owner_notes, created_at,
                    updated_at, last_contact_at, next_follow_up_at, is_archived
             FROM student_profiles"
        } else {
            "SELECT id, full_name, nickname, phone, wechat_id, school, major, grade, city,
                    identity_type, focus_directions, demand_summary, intention_level, urgency_level,
                    verification_status, source_channel, current_status, owner_notes, created_at,
                    updated_at, last_contact_at, next_follow_up_at, is_archived
             FROM student_profiles
             WHERE is_archived = 0"
        };

        let mut stmt = conn.prepare(sql)?;
        let mut profiles = stmt
            .query_map([], map_profile_row)?
            .collect::<rusqlite::Result<Vec<_>>>()?;

        for profile in &mut profiles {
            profile.tags = self.list_tags_for_profile_conn(&conn, profile.id)?;
            profile.tag_ids = profile.tags.iter().map(|tag| tag.id).collect();
        }

        Ok(profiles)
    }

    fn load_profile_by_id_conn(
        &self,
        conn: &Connection,
        student_profile_id: i64,
    ) -> Result<Option<StudentProfileRecord>> {
        let profile = conn
            .query_row(
                "SELECT id, full_name, nickname, phone, wechat_id, school, major, grade, city,
                        identity_type, focus_directions, demand_summary, intention_level, urgency_level,
                        verification_status, source_channel, current_status, owner_notes, created_at,
                        updated_at, last_contact_at, next_follow_up_at, is_archived
                 FROM student_profiles
                 WHERE id = ?1",
                params![student_profile_id],
                map_profile_row,
            )
            .optional()?;

        Ok(profile.map(|mut profile| {
            profile.tags = self
                .list_tags_for_profile_conn(conn, profile.id)
                .unwrap_or_default();
            profile.tag_ids = profile.tags.iter().map(|tag| tag.id).collect();
            profile
        }))
    }

    fn load_profile_by_id_tx(
        &self,
        tx: &Transaction<'_>,
        student_profile_id: i64,
    ) -> Result<Option<StudentProfileRecord>> {
        let profile = tx
            .query_row(
                "SELECT id, full_name, nickname, phone, wechat_id, school, major, grade, city,
                        identity_type, focus_directions, demand_summary, intention_level, urgency_level,
                        verification_status, source_channel, current_status, owner_notes, created_at,
                        updated_at, last_contact_at, next_follow_up_at, is_archived
                 FROM student_profiles
                 WHERE id = ?1",
                params![student_profile_id],
                map_profile_row,
            )
            .optional()?;

        Ok(profile.map(|mut profile| {
            profile.tags = self.list_tags_for_profile_tx(tx, profile.id).unwrap_or_default();
            profile.tag_ids = profile.tags.iter().map(|tag| tag.id).collect();
            profile
        }))
    }

    fn list_tags_for_profile_conn(
        &self,
        conn: &Connection,
        student_profile_id: i64,
    ) -> Result<Vec<TagRecord>> {
        let mut stmt = conn.prepare(
            "SELECT t.id, t.name, t.category, t.color, t.is_system
             FROM tags t
             INNER JOIN profile_tags pt ON pt.tag_id = t.id
             WHERE pt.student_profile_id = ?1
             ORDER BY t.category ASC, t.name ASC",
        )?;
        let tags = stmt
            .query_map(params![student_profile_id], |row| {
                Ok(TagRecord {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    category: row.get(2)?,
                    color: row.get(3)?,
                    is_system: row.get::<_, i64>(4)? == 1,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(tags)
    }

    fn list_tags_for_profile_tx(
        &self,
        tx: &Transaction<'_>,
        student_profile_id: i64,
    ) -> Result<Vec<TagRecord>> {
        let mut stmt = tx.prepare(
            "SELECT t.id, t.name, t.category, t.color, t.is_system
             FROM tags t
             INNER JOIN profile_tags pt ON pt.tag_id = t.id
             WHERE pt.student_profile_id = ?1
             ORDER BY t.category ASC, t.name ASC",
        )?;
        let tags = stmt
            .query_map(params![student_profile_id], |row| {
                Ok(TagRecord {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    category: row.get(2)?,
                    color: row.get(3)?,
                    is_system: row.get::<_, i64>(4)? == 1,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(tags)
    }

    fn list_interactions_by_profile_conn(
        &self,
        conn: &Connection,
        student_profile_id: i64,
    ) -> Result<Vec<InteractionLogRecord>> {
        let mut stmt = conn.prepare(
            "SELECT id, student_profile_id, contacted_at, channel, summary, details, outcome, verification_todo, next_follow_up_at, created_at
             FROM interaction_logs
             WHERE student_profile_id = ?1
             ORDER BY contacted_at DESC, created_at DESC",
        )?;
        let interactions = stmt
            .query_map(params![student_profile_id], |row| {
                Ok(InteractionLogRecord {
                    id: row.get(0)?,
                    student_profile_id: row.get(1)?,
                    contacted_at: row.get(2)?,
                    channel: row.get(3)?,
                    summary: row.get(4)?,
                    details: row.get(5)?,
                    outcome: row.get(6)?,
                    verification_todo: row.get(7)?,
                    next_follow_up_at: row.get(8)?,
                    created_at: row.get(9)?,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(interactions)
    }

    fn list_reminders_for_profile_conn(
        &self,
        conn: &Connection,
        student_profile_id: i64,
    ) -> Result<Vec<ReminderEventRecord>> {
        let mut stmt = conn.prepare(
            "SELECT id, student_profile_id, interaction_log_id, remind_at, title, status, notification_sent, created_at, updated_at
             FROM reminder_events
             WHERE student_profile_id = ?1
             ORDER BY remind_at DESC",
        )?;
        let reminders = stmt
            .query_map(params![student_profile_id], |row| {
                Ok(ReminderEventRecord {
                    id: row.get(0)?,
                    student_profile_id: row.get(1)?,
                    interaction_log_id: row.get(2)?,
                    remind_at: row.get(3)?,
                    title: row.get(4)?,
                    status: row.get(5)?,
                    notification_sent: row.get::<_, i64>(6)? == 1,
                    created_at: row.get(7)?,
                    updated_at: row.get(8)?,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(reminders)
    }

    fn sync_profile_tags(
        &self,
        tx: &Transaction<'_>,
        student_profile_id: i64,
        tag_ids: &[i64],
    ) -> Result<()> {
        tx.execute(
            "DELETE FROM profile_tags WHERE student_profile_id = ?1",
            params![student_profile_id],
        )?;

        for tag_id in tag_ids {
            tx.execute(
                "INSERT INTO profile_tags (student_profile_id, tag_id) VALUES (?1, ?2)",
                params![student_profile_id, tag_id],
            )?;
        }

        Ok(())
    }

    fn replace_pending_reminders(
        &self,
        tx: &Transaction<'_>,
        student_profile_id: i64,
        interaction_log_id: Option<i64>,
        next_follow_up_at: Option<&str>,
        title: String,
    ) -> Result<()> {
        tx.execute(
            "UPDATE reminder_events
             SET status = 'completed', updated_at = ?1
             WHERE student_profile_id = ?2 AND status IN ('pending', 'sent', 'overdue')",
            params![now_iso(), student_profile_id],
        )?;

        if let Some(remind_at) = normalize_optional_ref(next_follow_up_at) {
            let created_at = now_iso();
            tx.execute(
                "INSERT INTO reminder_events (
                    student_profile_id, interaction_log_id, remind_at, title, status, notification_sent, created_at, updated_at
                 ) VALUES (?1, ?2, ?3, ?4, 'pending', 0, ?5, ?5)",
                params![student_profile_id, interaction_log_id, remind_at, title, created_at],
            )?;
        }

        Ok(())
    }

    fn list_saved_filters_conn(&self, conn: &Connection) -> Result<Vec<SavedFilterRecord>> {
        let mut stmt = conn.prepare(
            "SELECT id, name, filters_json, sort_by, show_on_homepage, created_at
             FROM saved_filters
             ORDER BY show_on_homepage DESC, created_at DESC",
        )?;
        let filters = stmt
            .query_map([], |row| {
                let filters_json: String = row.get(2)?;
                Ok(SavedFilterRecord {
                    id: row.get(0)?,
                    name: row.get(1)?,
                    filters: serde_json::from_str::<SearchFilters>(&filters_json).unwrap_or_default(),
                    sort_by: row.get(3)?,
                    show_on_homepage: row.get::<_, i64>(4)? == 1,
                    created_at: row.get(5)?,
                })
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        Ok(filters)
    }

    fn get_saved_filter_by_id(
        &self,
        conn: &Connection,
        filter_id: i64,
    ) -> Result<Option<SavedFilterRecord>> {
        let filter = conn
            .query_row(
                "SELECT id, name, filters_json, sort_by, show_on_homepage, created_at
                 FROM saved_filters
                 WHERE id = ?1",
                params![filter_id],
                |row| {
                    let filters_json: String = row.get(2)?;
                    Ok(SavedFilterRecord {
                        id: row.get(0)?,
                        name: row.get(1)?,
                        filters: serde_json::from_str::<SearchFilters>(&filters_json).unwrap_or_default(),
                        sort_by: row.get(3)?,
                        show_on_homepage: row.get::<_, i64>(4)? == 1,
                        created_at: row.get(5)?,
                    })
                },
            )
            .optional()?;
        Ok(filter)
    }

    fn get_saved_filter_by_name(
        &self,
        conn: &Connection,
        name: &str,
    ) -> Result<Option<SavedFilterRecord>> {
        let filter = conn
            .query_row(
                "SELECT id, name, filters_json, sort_by, show_on_homepage, created_at
                 FROM saved_filters
                 WHERE name = ?1",
                params![name],
                |row| {
                    let filters_json: String = row.get(2)?;
                    Ok(SavedFilterRecord {
                        id: row.get(0)?,
                        name: row.get(1)?,
                        filters: serde_json::from_str::<SearchFilters>(&filters_json).unwrap_or_default(),
                        sort_by: row.get(3)?,
                        show_on_homepage: row.get::<_, i64>(4)? == 1,
                        created_at: row.get(5)?,
                    })
                },
            )
            .optional()?;
        Ok(filter)
    }

    fn get_tag_by_id(&self, conn: &Connection, tag_id: i64) -> Result<Option<TagRecord>> {
        let tag = conn
            .query_row(
                "SELECT id, name, category, color, is_system FROM tags WHERE id = ?1",
                params![tag_id],
                |row| {
                    Ok(TagRecord {
                        id: row.get(0)?,
                        name: row.get(1)?,
                        category: row.get(2)?,
                        color: row.get(3)?,
                        is_system: row.get::<_, i64>(4)? == 1,
                    })
                },
            )
            .optional()?;
        Ok(tag)
    }

    fn load_settings_from_conn(&self, conn: &Connection) -> Result<AppSettingsRecord> {
        let default_reminder_time = conn
            .query_row(
                "SELECT value FROM app_settings WHERE key = 'defaultReminderTime'",
                [],
                |row| row.get::<_, String>(0),
            )
            .optional()?
            .unwrap_or_else(|| DEFAULT_REMINDER_TIME.to_string());

        let last_backup_at = conn
            .query_row(
                "SELECT value FROM app_settings WHERE key = 'lastBackupAt'",
                [],
                |row| row.get::<_, String>(0),
            )
            .optional()?
            .filter(|value| !value.trim().is_empty());

        Ok(AppSettingsRecord {
            default_reminder_time,
            last_backup_at,
            database_path: self.db_path.display().to_string(),
        })
    }
}

fn map_profile_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<StudentProfileRecord> {
    Ok(StudentProfileRecord {
        id: row.get(0)?,
        full_name: row.get(1)?,
        nickname: row.get(2)?,
        phone: row.get(3)?,
        wechat_id: row.get(4)?,
        school: row.get(5)?,
        major: row.get(6)?,
        grade: row.get(7)?,
        city: row.get(8)?,
        identity_type: row.get(9)?,
        focus_directions: deserialize_string_array(&row.get::<_, String>(10)?),
        demand_summary: row.get(11)?,
        intention_level: row.get(12)?,
        urgency_level: row.get(13)?,
        verification_status: row.get(14)?,
        source_channel: row.get(15)?,
        current_status: row.get(16)?,
        owner_notes: row.get(17)?,
        created_at: row.get(18)?,
        updated_at: row.get(19)?,
        last_contact_at: row.get(20)?,
        next_follow_up_at: row.get(21)?,
        is_archived: row.get::<_, i64>(22)? == 1,
        tag_ids: Vec::new(),
        tags: Vec::new(),
    })
}

fn validate_profile_input(input: &ProfileInput) -> Result<()> {
    if normalize_required(&input.full_name).is_empty() {
        return Err(anyhow!("姓名不能为空"));
    }

    if normalize_optional(&input.phone).is_none() && normalize_optional(&input.wechat_id).is_none() {
        return Err(anyhow!("手机号和微信号至少填写一个"));
    }

    Ok(())
}

fn validate_interaction_input(input: &InteractionInput) -> Result<()> {
    if normalize_required(&input.summary).is_empty() {
        return Err(anyhow!("沟通摘要不能为空"));
    }
    if normalize_required(&input.channel).is_empty() {
        return Err(anyhow!("沟通方式不能为空"));
    }
    if normalize_required(&input.contacted_at).is_empty() {
        return Err(anyhow!("沟通时间不能为空"));
    }
    Ok(())
}

fn serialize_string_array(values: &[String]) -> Result<String> {
    Ok(serde_json::to_string(values)?)
}

fn deserialize_string_array(value: &str) -> Vec<String> {
    serde_json::from_str(value).unwrap_or_default()
}

fn normalize_required(value: &str) -> String {
    value.trim().to_string()
}

fn normalize_optional(value: &Option<String>) -> Option<String> {
    value
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
}

fn normalize_optional_ref(value: Option<&str>) -> Option<String> {
    value
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
}

fn bool_to_int(value: bool) -> i64 {
    if value {
        1
    } else {
        0
    }
}

fn now_iso() -> String {
    Utc::now().to_rfc3339()
}

fn parse_local_datetime(value: &str) -> Result<DateTime<Local>> {
    let fixed = DateTime::parse_from_rfc3339(value)
        .with_context(|| format!("invalid datetime value: {value}"))?;
    Ok(fixed.with_timezone(&Local))
}

fn profile_matches(profile: &StudentProfileRecord, filters: &SearchFilters) -> bool {
    if !filters.include_archived && profile.is_archived {
        return false;
    }

    if let Some(search_text) = filters
        .search_text
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
    {
        let needle = search_text.to_lowercase();
        let haystacks = [
            profile.full_name.as_str(),
            profile.nickname.as_deref().unwrap_or_default(),
            profile.phone.as_deref().unwrap_or_default(),
            profile.wechat_id.as_deref().unwrap_or_default(),
            profile.school.as_deref().unwrap_or_default(),
            profile.major.as_deref().unwrap_or_default(),
            profile.city.as_deref().unwrap_or_default(),
            profile.source_channel.as_deref().unwrap_or_default(),
            profile.owner_notes.as_deref().unwrap_or_default(),
            profile.demand_summary.as_deref().unwrap_or_default(),
        ];
        let matched = haystacks
            .iter()
            .any(|value| value.to_lowercase().contains(&needle));
        if !matched {
            return false;
        }
    }

    if let Some(grade) = filters.grade.as_deref().filter(|value| !value.is_empty()) {
        if profile.grade.as_deref() != Some(grade) {
            return false;
        }
    }

    if let Some(direction) = filters
        .focus_direction
        .as_deref()
        .filter(|value| !value.is_empty())
    {
        if !profile.focus_directions.iter().any(|item| item == direction) {
            return false;
        }
    }

    if let Some(status) = filters.status.as_deref().filter(|value| !value.is_empty()) {
        if profile.current_status != status {
            return false;
        }
    }

    if let Some(level) = filters
        .intention_level
        .as_deref()
        .filter(|value| !value.is_empty())
    {
        if profile.intention_level != level {
            return false;
        }
    }

    if let Some(status) = filters
        .verification_status
        .as_deref()
        .filter(|value| !value.is_empty())
    {
        if profile.verification_status != status {
            return false;
        }
    }

    if !filters.tag_ids.is_empty()
        && !filters
            .tag_ids
            .iter()
            .all(|tag_id| profile.tag_ids.contains(tag_id))
    {
        return false;
    }

    true
}

fn sort_profiles(profiles: &mut [StudentProfileRecord], sort_by: Option<&str>) {
    match sort_by.unwrap_or("updatedDesc") {
        "createdDesc" => profiles.sort_by(|left, right| right.created_at.cmp(&left.created_at)),
        "nextFollowUpAsc" => profiles
            .sort_by(|left, right| compare_optional_asc(&left.next_follow_up_at, &right.next_follow_up_at)),
        "lastContactDesc" => profiles
            .sort_by(|left, right| compare_optional_desc(&left.last_contact_at, &right.last_contact_at)),
        _ => profiles.sort_by(|left, right| right.updated_at.cmp(&left.updated_at)),
    }
}

fn compare_optional_asc(left: &Option<String>, right: &Option<String>) -> Ordering {
    match (left, right) {
        (Some(left), Some(right)) => left.cmp(right),
        (Some(_), None) => Ordering::Less,
        (None, Some(_)) => Ordering::Greater,
        (None, None) => Ordering::Equal,
    }
}

fn compare_optional_desc(left: &Option<String>, right: &Option<String>) -> Ordering {
    match (left, right) {
        (Some(left), Some(right)) => right.cmp(left),
        (Some(_), None) => Ordering::Less,
        (None, Some(_)) => Ordering::Greater,
        (None, None) => Ordering::Equal,
    }
}

fn default_tags() -> Vec<(&'static str, &'static str, &'static str, bool)> {
    vec![
        ("2025", "年级", "#2563eb", true),
        ("2026", "年级", "#0f766e", true),
        ("新生", "状态补充", "#f59e0b", true),
        ("转专业", "状态补充", "#dc2626", true),
        ("已进群", "状态补充", "#7c3aed", true),
        ("高意向", "状态补充", "#16a34a", true),
        ("待回访", "状态补充", "#ea580c", true),
        ("家长参与", "风险", "#7c2d12", true),
        ("信息不完整", "风险", "#b91c1c", true),
        ("重复咨询", "风险", "#475569", true),
    ]
}

fn take_limit(mut profiles: Vec<StudentProfileRecord>, limit: usize) -> Vec<StudentProfileRecord> {
    profiles.truncate(limit);
    profiles
}

fn write_profile_sheet(worksheet: &mut Worksheet, profiles: &[StudentProfileRecord]) -> Result<()> {
    let headers = [
        "姓名",
        "昵称",
        "手机号",
        "微信号",
        "学校",
        "专业",
        "年级",
        "城市",
        "身份类型",
        "关注方向",
        "当前需求",
        "报名意向",
        "紧急程度",
        "核实状态",
        "来源渠道",
        "当前状态",
        "标签",
        "最近联系",
        "下次跟进",
        "创建时间",
        "更新时间",
        "备注",
    ];

    for (column, header) in headers.iter().enumerate() {
        worksheet.write_string(0, column as u16, *header)?;
    }

    for (index, profile) in profiles.iter().enumerate() {
        let row = (index + 1) as u32;
        worksheet.write_string(row, 0, &profile.full_name)?;
        worksheet.write_string(row, 1, profile.nickname.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 2, profile.phone.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 3, profile.wechat_id.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 4, profile.school.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 5, profile.major.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 6, profile.grade.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 7, profile.city.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 8, &profile.identity_type)?;
        worksheet.write_string(row, 9, profile.focus_directions.join(" / "))?;
        worksheet.write_string(row, 10, profile.demand_summary.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 11, &profile.intention_level)?;
        worksheet.write_string(row, 12, &profile.urgency_level)?;
        worksheet.write_string(row, 13, &profile.verification_status)?;
        worksheet.write_string(row, 14, profile.source_channel.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 15, &profile.current_status)?;
        worksheet.write_string(
            row,
            16,
            profile
                .tags
                .iter()
                .map(|tag| tag.name.as_str())
                .collect::<Vec<_>>()
                .join(" / "),
        )?;
        worksheet.write_string(row, 17, profile.last_contact_at.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 18, profile.next_follow_up_at.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 19, &profile.created_at)?;
        worksheet.write_string(row, 20, &profile.updated_at)?;
        worksheet.write_string(row, 21, profile.owner_notes.as_deref().unwrap_or_default())?;
    }

    worksheet.autofit();
    Ok(())
}

fn write_interaction_sheet(
    worksheet: &mut Worksheet,
    database: &Database,
    profiles: &[StudentProfileRecord],
) -> Result<()> {
    let headers = ["姓名", "沟通时间", "沟通方式", "摘要", "详细备注", "结果判断", "待核实信息", "下次跟进"];

    for (column, header) in headers.iter().enumerate() {
        worksheet.write_string(0, column as u16, *header)?;
    }

    let mut row = 1u32;
    for profile in profiles {
        for interaction in database.list_interactions_by_profile(profile.id)? {
            worksheet.write_string(row, 0, &profile.full_name)?;
            worksheet.write_string(row, 1, &interaction.contacted_at)?;
            worksheet.write_string(row, 2, &interaction.channel)?;
            worksheet.write_string(row, 3, &interaction.summary)?;
            worksheet.write_string(row, 4, interaction.details.as_deref().unwrap_or_default())?;
            worksheet.write_string(row, 5, interaction.outcome.as_deref().unwrap_or_default())?;
            worksheet.write_string(row, 6, interaction.verification_todo.as_deref().unwrap_or_default())?;
            worksheet.write_string(row, 7, interaction.next_follow_up_at.as_deref().unwrap_or_default())?;
            row += 1;
        }
    }

    worksheet.autofit();
    Ok(())
}

fn write_single_profile_timeline_sheet(
    worksheet: &mut Worksheet,
    detail: &ProfileDetailRecord,
) -> Result<()> {
    let headers = ["沟通时间", "沟通方式", "摘要", "详细备注", "结果判断", "待核实信息", "下次跟进"];

    for (column, header) in headers.iter().enumerate() {
        worksheet.write_string(0, column as u16, *header)?;
    }

    for (index, interaction) in detail.interactions.iter().enumerate() {
        let row = (index + 1) as u32;
        worksheet.write_string(row, 0, &interaction.contacted_at)?;
        worksheet.write_string(row, 1, &interaction.channel)?;
        worksheet.write_string(row, 2, &interaction.summary)?;
        worksheet.write_string(row, 3, interaction.details.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 4, interaction.outcome.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 5, interaction.verification_todo.as_deref().unwrap_or_default())?;
        worksheet.write_string(row, 6, interaction.next_follow_up_at.as_deref().unwrap_or_default())?;
    }

    worksheet.autofit();
    Ok(())
}

fn write_profile_csv(writer: &mut Writer<std::fs::File>, profiles: &[StudentProfileRecord]) -> Result<()> {
    writer.write_record([
        "姓名",
        "昵称",
        "手机号",
        "微信号",
        "学校",
        "专业",
        "年级",
        "城市",
        "身份类型",
        "关注方向",
        "报名意向",
        "紧急程度",
        "核实状态",
        "来源渠道",
        "当前状态",
        "标签",
        "最近联系",
        "下次跟进",
        "创建时间",
        "更新时间",
    ])?;

    for profile in profiles {
        let focus_directions = profile.focus_directions.join(" / ");
        let tag_names = profile
            .tags
            .iter()
            .map(|tag| tag.name.as_str())
            .collect::<Vec<_>>()
            .join(" / ");

        let record = vec![
            profile.full_name.clone(),
            profile.nickname.clone().unwrap_or_default(),
            profile.phone.clone().unwrap_or_default(),
            profile.wechat_id.clone().unwrap_or_default(),
            profile.school.clone().unwrap_or_default(),
            profile.major.clone().unwrap_or_default(),
            profile.grade.clone().unwrap_or_default(),
            profile.city.clone().unwrap_or_default(),
            profile.identity_type.clone(),
            focus_directions,
            profile.intention_level.clone(),
            profile.urgency_level.clone(),
            profile.verification_status.clone(),
            profile.source_channel.clone().unwrap_or_default(),
            profile.current_status.clone(),
            tag_names,
            profile.last_contact_at.clone().unwrap_or_default(),
            profile.next_follow_up_at.clone().unwrap_or_default(),
            profile.created_at.clone(),
            profile.updated_at.clone(),
        ];
        writer.write_record(record)?;
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::{InteractionInput, ProfileInput, SearchFilters};
    use std::time::{SystemTime, UNIX_EPOCH};

    fn test_db() -> Database {
        let unique = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let path = std::env::temp_dir().join(format!("yingge-student-archive-{unique}.sqlite"));
        Database::new(path).unwrap()
    }

    fn sample_profile() -> ProfileInput {
        ProfileInput {
            full_name: "张同学".into(),
            nickname: Some("张三".into()),
            phone: Some("13800000000".into()),
            wechat_id: Some("zhangsan".into()),
            school: Some("院校甲".into()),
            major: Some("计算机".into()),
            grade: Some("2025".into()),
            city: Some("镇江".into()),
            identity_type: "在校生".into(),
            focus_directions: vec!["考研".into()],
            demand_summary: Some("了解考研规划".into()),
            intention_level: "中".into(),
            urgency_level: "中".into(),
            verification_status: "未核实".into(),
            source_channel: Some("微信".into()),
            current_status: "初次接触".into(),
            owner_notes: None,
            next_follow_up_at: None,
            tag_ids: Vec::new(),
        }
    }

    #[test]
    fn create_and_search_profile() {
        let database = test_db();
        let created = database.create_profile(sample_profile()).unwrap();
        assert_eq!(created.full_name, "张同学");

        let results = database
            .search_profiles(SearchFilters {
                search_text: Some("张同学".into()),
                ..SearchFilters::default()
            })
            .unwrap();
        assert_eq!(results.len(), 1);
    }

    #[test]
    fn interaction_refreshes_profile_and_reminder() {
        let database = test_db();
        let created = database.create_profile(sample_profile()).unwrap();
        let next_follow_up = (Utc::now() + Duration::days(2)).to_rfc3339();

        let detail = database
            .add_interaction_and_refresh_profile(InteractionInput {
                student_profile_id: created.id,
                contacted_at: Utc::now().to_rfc3339(),
                channel: "微信".into(),
                summary: "第一次沟通".into(),
                details: Some("了解考研计划".into()),
                outcome: Some("继续了解".into()),
                verification_todo: Some("确认备考时间".into()),
                next_follow_up_at: Some(next_follow_up.clone()),
                current_status: Some("跟进中".into()),
                intention_level: Some("高".into()),
                verification_status: Some("部分待确认".into()),
                tag_ids: None,
            })
            .unwrap();

        assert_eq!(detail.interactions.len(), 1);
        assert_eq!(detail.profile.current_status, "跟进中");
        assert_eq!(detail.profile.intention_level, "高");
        assert_eq!(
            detail.profile.next_follow_up_at.as_deref(),
            Some(next_follow_up.as_str())
        );
        assert_eq!(detail.reminders.len(), 1);
    }
}
