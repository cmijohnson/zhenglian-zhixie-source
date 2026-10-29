mod commands;
mod db;
mod models;
mod state;

use anyhow::Context;
use tauri::Manager;

use state::AppState;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            if cfg!(debug_assertions) {
                app.handle().plugin(
                    tauri_plugin_log::Builder::default()
                        .level(log::LevelFilter::Info)
                        .build(),
                )?;
            }

            let app_data_dir = app
                .path()
                .app_data_dir()
                .context("unable to resolve app data dir")?;
            let database_path = app_data_dir.join("yingge-student-archive.sqlite");
            app.manage(AppState::new(database_path)?);
            Ok(())
        })
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_notification::init())
        .invoke_handler(tauri::generate_handler![
            commands::get_bootstrap_data,
            commands::search_profiles,
            commands::get_profile_detail,
            commands::create_profile,
            commands::update_profile,
            commands::archive_profile,
            commands::find_potential_duplicates,
            commands::add_interaction_and_refresh_profile,
            commands::list_interactions_by_profile,
            commands::update_next_follow_up,
            commands::save_filter,
            commands::delete_saved_filter,
            commands::list_saved_filters,
            commands::run_saved_filter,
            commands::list_homepage_filters,
            commands::list_tags,
            commands::create_tag,
            commands::get_dashboard_data,
            commands::list_due_reminders,
            commands::mark_reminder_sent,
            commands::get_settings,
            commands::update_settings,
            commands::export_profiles_to_xlsx,
            commands::export_profiles_to_csv,
            commands::export_single_profile_timeline,
            commands::backup_database
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
