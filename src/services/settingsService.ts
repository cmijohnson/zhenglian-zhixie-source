import { api } from './api'

export const settingsService = {
  getBootstrapData: api.getBootstrapData,
  getSettings: api.getSettings,
  updateSettings: api.updateSettings,
  listTags: api.listTags,
  createTag: api.createTag,
  getDashboardData: api.getDashboardData,
  backupDatabase: api.backupDatabase,
}
