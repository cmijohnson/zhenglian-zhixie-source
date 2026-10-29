use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StudentProfileRecord {
    pub id: i64,
    pub full_name: String,
    pub nickname: Option<String>,
    pub phone: Option<String>,
    pub wechat_id: Option<String>,
    pub school: Option<String>,
    pub major: Option<String>,
    pub grade: Option<String>,
    pub city: Option<String>,
    pub identity_type: String,
    pub focus_directions: Vec<String>,
    pub demand_summary: Option<String>,
    pub intention_level: String,
    pub urgency_level: String,
    pub verification_status: String,
    pub source_channel: Option<String>,
    pub current_status: String,
    pub owner_notes: Option<String>,
    pub created_at: String,
    pub updated_at: String,
    pub last_contact_at: Option<String>,
    pub next_follow_up_at: Option<String>,
    pub is_archived: bool,
    pub tag_ids: Vec<i64>,
    pub tags: Vec<TagRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TagRecord {
    pub id: i64,
    pub name: String,
    pub category: String,
    pub color: String,
    pub is_system: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InteractionLogRecord {
    pub id: i64,
    pub student_profile_id: i64,
    pub contacted_at: String,
    pub channel: String,
    pub summary: String,
    pub details: Option<String>,
    pub outcome: Option<String>,
    pub verification_todo: Option<String>,
    pub next_follow_up_at: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReminderEventRecord {
    pub id: i64,
    pub student_profile_id: i64,
    pub interaction_log_id: Option<i64>,
    pub remind_at: String,
    pub title: String,
    pub status: String,
    pub notification_sent: bool,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SavedFilterRecord {
    pub id: i64,
    pub name: String,
    pub filters: SearchFilters,
    pub sort_by: String,
    pub show_on_homepage: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DuplicateMatch {
    pub profile: StudentProfileRecord,
    pub reasons: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileDetailRecord {
    pub profile: StudentProfileRecord,
    pub interactions: Vec<InteractionLogRecord>,
    pub reminders: Vec<ReminderEventRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DashboardStats {
    pub total_active: usize,
    pub archived_count: usize,
    pub today_due_count: usize,
    pub overdue_count: usize,
    pub high_intention_count: usize,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DashboardData {
    pub stats: DashboardStats,
    pub due_today: Vec<StudentProfileRecord>,
    pub overdue: Vec<StudentProfileRecord>,
    pub recent_created: Vec<StudentProfileRecord>,
    pub recent_updated: Vec<StudentProfileRecord>,
    pub stale_high_intention: Vec<StudentProfileRecord>,
    pub homepage_filters: Vec<SavedFilterRecord>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppSettingsRecord {
    pub default_reminder_time: String,
    pub last_backup_at: Option<String>,
    pub database_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppBootstrapData {
    pub tags: Vec<TagRecord>,
    pub saved_filters: Vec<SavedFilterRecord>,
    pub settings: AppSettingsRecord,
    pub dashboard: DashboardData,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileInput {
    pub full_name: String,
    pub nickname: Option<String>,
    pub phone: Option<String>,
    pub wechat_id: Option<String>,
    pub school: Option<String>,
    pub major: Option<String>,
    pub grade: Option<String>,
    pub city: Option<String>,
    pub identity_type: String,
    pub focus_directions: Vec<String>,
    pub demand_summary: Option<String>,
    pub intention_level: String,
    pub urgency_level: String,
    pub verification_status: String,
    pub source_channel: Option<String>,
    pub current_status: String,
    pub owner_notes: Option<String>,
    pub next_follow_up_at: Option<String>,
    pub tag_ids: Vec<i64>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct InteractionInput {
    pub student_profile_id: i64,
    pub contacted_at: String,
    pub channel: String,
    pub summary: String,
    pub details: Option<String>,
    pub outcome: Option<String>,
    pub verification_todo: Option<String>,
    pub next_follow_up_at: Option<String>,
    pub current_status: Option<String>,
    pub intention_level: Option<String>,
    pub verification_status: Option<String>,
    pub tag_ids: Option<Vec<i64>>,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct SearchFilters {
    pub search_text: Option<String>,
    pub grade: Option<String>,
    pub focus_direction: Option<String>,
    pub status: Option<String>,
    pub intention_level: Option<String>,
    pub verification_status: Option<String>,
    pub tag_ids: Vec<i64>,
    pub include_archived: bool,
    pub sort_by: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DuplicateQuery {
    pub full_name: String,
    pub phone: Option<String>,
    pub wechat_id: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SavedFilterInput {
    pub name: String,
    pub filters: SearchFilters,
    pub sort_by: String,
    pub show_on_homepage: bool,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateReminderInput {
    pub student_profile_id: i64,
    pub next_follow_up_at: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateTagInput {
    pub name: String,
    pub category: String,
    pub color: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateSettingsInput {
    pub default_reminder_time: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportProfilesInput {
    pub file_path: String,
    pub filters: SearchFilters,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ExportSingleProfileInput {
    pub file_path: String,
    pub student_profile_id: i64,
}
