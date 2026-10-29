use tauri::State;

use crate::{
    models::{
        AppBootstrapData, AppSettingsRecord, CreateTagInput, DashboardData, DuplicateMatch,
        DuplicateQuery, ExportProfilesInput, ExportSingleProfileInput, InteractionInput,
        InteractionLogRecord, ProfileDetailRecord, ProfileInput, ReminderEventRecord,
        SavedFilterInput, SavedFilterRecord, SearchFilters, StudentProfileRecord, TagRecord,
        UpdateReminderInput, UpdateSettingsInput,
    },
    state::AppState,
};

fn to_command_error(error: anyhow::Error) -> String {
    error.to_string()
}

#[tauri::command]
pub fn get_bootstrap_data(state: State<'_, AppState>) -> Result<AppBootstrapData, String> {
    state.database.bootstrap().map_err(to_command_error)
}

#[tauri::command]
pub fn search_profiles(
    state: State<'_, AppState>,
    filters: SearchFilters,
) -> Result<Vec<StudentProfileRecord>, String> {
    state.database.search_profiles(filters).map_err(to_command_error)
}

#[tauri::command]
pub fn get_profile_detail(
    state: State<'_, AppState>,
    student_profile_id: i64,
) -> Result<ProfileDetailRecord, String> {
    state
        .database
        .get_profile_detail(student_profile_id)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn create_profile(
    state: State<'_, AppState>,
    input: ProfileInput,
) -> Result<StudentProfileRecord, String> {
    state.database.create_profile(input).map_err(to_command_error)
}

#[tauri::command]
pub fn update_profile(
    state: State<'_, AppState>,
    student_profile_id: i64,
    input: ProfileInput,
) -> Result<StudentProfileRecord, String> {
    state
        .database
        .update_profile(student_profile_id, input)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn archive_profile(
    state: State<'_, AppState>,
    student_profile_id: i64,
) -> Result<(), String> {
    state
        .database
        .archive_profile(student_profile_id)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn find_potential_duplicates(
    state: State<'_, AppState>,
    query: DuplicateQuery,
) -> Result<Vec<DuplicateMatch>, String> {
    state
        .database
        .find_potential_duplicates(query)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn add_interaction_and_refresh_profile(
    state: State<'_, AppState>,
    input: InteractionInput,
) -> Result<ProfileDetailRecord, String> {
    state
        .database
        .add_interaction_and_refresh_profile(input)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn list_interactions_by_profile(
    state: State<'_, AppState>,
    student_profile_id: i64,
) -> Result<Vec<InteractionLogRecord>, String> {
    state
        .database
        .list_interactions_by_profile(student_profile_id)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn update_next_follow_up(
    state: State<'_, AppState>,
    input: UpdateReminderInput,
) -> Result<ProfileDetailRecord, String> {
    state
        .database
        .update_next_follow_up(input)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn save_filter(
    state: State<'_, AppState>,
    input: SavedFilterInput,
) -> Result<SavedFilterRecord, String> {
    state.database.save_filter(input).map_err(to_command_error)
}

#[tauri::command]
pub fn delete_saved_filter(state: State<'_, AppState>, filter_id: i64) -> Result<(), String> {
    state
        .database
        .delete_saved_filter(filter_id)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn list_saved_filters(state: State<'_, AppState>) -> Result<Vec<SavedFilterRecord>, String> {
    state.database.list_saved_filters().map_err(to_command_error)
}

#[tauri::command]
pub fn run_saved_filter(
    state: State<'_, AppState>,
    filter_id: i64,
) -> Result<Vec<StudentProfileRecord>, String> {
    state.database.run_saved_filter(filter_id).map_err(to_command_error)
}

#[tauri::command]
pub fn list_homepage_filters(
    state: State<'_, AppState>,
) -> Result<Vec<SavedFilterRecord>, String> {
    state
        .database
        .list_homepage_filters()
        .map_err(to_command_error)
}

#[tauri::command]
pub fn list_tags(state: State<'_, AppState>) -> Result<Vec<TagRecord>, String> {
    state.database.list_tags().map_err(to_command_error)
}

#[tauri::command]
pub fn create_tag(
    state: State<'_, AppState>,
    input: CreateTagInput,
) -> Result<TagRecord, String> {
    state.database.create_tag(input).map_err(to_command_error)
}

#[tauri::command]
pub fn get_dashboard_data(state: State<'_, AppState>) -> Result<DashboardData, String> {
    state.database.dashboard().map_err(to_command_error)
}

#[tauri::command]
pub fn list_due_reminders(
    state: State<'_, AppState>,
) -> Result<Vec<ReminderEventRecord>, String> {
    state.database.list_due_reminders().map_err(to_command_error)
}

#[tauri::command]
pub fn mark_reminder_sent(state: State<'_, AppState>, reminder_id: i64) -> Result<(), String> {
    state
        .database
        .mark_reminder_sent(reminder_id)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn get_settings(state: State<'_, AppState>) -> Result<AppSettingsRecord, String> {
    state.database.get_settings().map_err(to_command_error)
}

#[tauri::command]
pub fn update_settings(
    state: State<'_, AppState>,
    input: UpdateSettingsInput,
) -> Result<AppSettingsRecord, String> {
    state.database.update_settings(input).map_err(to_command_error)
}

#[tauri::command]
pub fn export_profiles_to_xlsx(
    state: State<'_, AppState>,
    input: ExportProfilesInput,
) -> Result<String, String> {
    state
        .database
        .export_profiles_to_xlsx(input)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn export_profiles_to_csv(
    state: State<'_, AppState>,
    input: ExportProfilesInput,
) -> Result<String, String> {
    state
        .database
        .export_profiles_to_csv(input)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn export_single_profile_timeline(
    state: State<'_, AppState>,
    input: ExportSingleProfileInput,
) -> Result<String, String> {
    state
        .database
        .export_single_profile_timeline(input)
        .map_err(to_command_error)
}

#[tauri::command]
pub fn backup_database(
    state: State<'_, AppState>,
    file_path: String,
) -> Result<String, String> {
    state.database.backup_database(file_path).map_err(to_command_error)
}
