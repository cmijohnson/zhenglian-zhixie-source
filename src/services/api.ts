import { invoke } from '@tauri-apps/api/core'

import type {
  AppBootstrapData,
  AppSettingsRecord,
  CreateTagInput,
  DashboardData,
  DuplicateMatch,
  DuplicateQuery,
  ExportProfilesInput,
  ExportSingleProfileInput,
  InteractionInput,
  InteractionLogRecord,
  ProfileDetailRecord,
  ProfileInput,
  ReminderEventRecord,
  SavedFilterInput,
  SavedFilterRecord,
  SearchFilters,
  StudentProfileRecord,
  TagRecord,
  UpdateReminderInput,
  UpdateSettingsInput,
} from '../types'

export const api = {
  getBootstrapData: () => invoke<AppBootstrapData>('get_bootstrap_data'),
  searchProfiles: (filters: SearchFilters) =>
    invoke<StudentProfileRecord[]>('search_profiles', { filters }),
  getProfileDetail: (studentProfileId: number) =>
    invoke<ProfileDetailRecord>('get_profile_detail', { studentProfileId }),
  createProfile: (input: ProfileInput) =>
    invoke<StudentProfileRecord>('create_profile', { input }),
  updateProfile: (studentProfileId: number, input: ProfileInput) =>
    invoke<StudentProfileRecord>('update_profile', { studentProfileId, input }),
  archiveProfile: (studentProfileId: number) =>
    invoke<void>('archive_profile', { studentProfileId }),
  findPotentialDuplicates: (query: DuplicateQuery) =>
    invoke<DuplicateMatch[]>('find_potential_duplicates', { query }),
  addInteractionAndRefreshProfile: (input: InteractionInput) =>
    invoke<ProfileDetailRecord>('add_interaction_and_refresh_profile', { input }),
  listInteractionsByProfile: (studentProfileId: number) =>
    invoke<InteractionLogRecord[]>('list_interactions_by_profile', { studentProfileId }),
  updateNextFollowUp: (input: UpdateReminderInput) =>
    invoke<ProfileDetailRecord>('update_next_follow_up', { input }),
  saveFilter: (input: SavedFilterInput) =>
    invoke<SavedFilterRecord>('save_filter', { input }),
  deleteSavedFilter: (filterId: number) =>
    invoke<void>('delete_saved_filter', { filterId }),
  listSavedFilters: () => invoke<SavedFilterRecord[]>('list_saved_filters'),
  runSavedFilter: (filterId: number) =>
    invoke<StudentProfileRecord[]>('run_saved_filter', { filterId }),
  listHomepageFilters: () =>
    invoke<SavedFilterRecord[]>('list_homepage_filters'),
  listTags: () => invoke<TagRecord[]>('list_tags'),
  createTag: (input: CreateTagInput) => invoke<TagRecord>('create_tag', { input }),
  getDashboardData: () => invoke<DashboardData>('get_dashboard_data'),
  listDueReminders: () => invoke<ReminderEventRecord[]>('list_due_reminders'),
  markReminderSent: (reminderId: number) =>
    invoke<void>('mark_reminder_sent', { reminderId }),
  getSettings: () => invoke<AppSettingsRecord>('get_settings'),
  updateSettings: (input: UpdateSettingsInput) =>
    invoke<AppSettingsRecord>('update_settings', { input }),
  exportProfilesToXlsx: (input: ExportProfilesInput) =>
    invoke<string>('export_profiles_to_xlsx', { input }),
  exportProfilesToCsv: (input: ExportProfilesInput) =>
    invoke<string>('export_profiles_to_csv', { input }),
  exportSingleProfileTimeline: (input: ExportSingleProfileInput) =>
    invoke<string>('export_single_profile_timeline', { input }),
  backupDatabase: (filePath: string) =>
    invoke<string>('backup_database', { filePath }),
}
