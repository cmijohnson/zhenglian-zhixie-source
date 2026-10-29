import { api } from './api'

export const filterService = {
  saveFilter: api.saveFilter,
  runSavedFilter: api.runSavedFilter,
  listHomepageFilters: api.listHomepageFilters,
  listSavedFilters: api.listSavedFilters,
  deleteSavedFilter: api.deleteSavedFilter,
}
