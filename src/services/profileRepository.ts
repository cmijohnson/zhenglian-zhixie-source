import { api } from './api'

export const profileRepository = {
  createProfile: api.createProfile,
  updateProfile: api.updateProfile,
  searchProfiles: api.searchProfiles,
  archiveProfile: api.archiveProfile,
  findPotentialDuplicates: api.findPotentialDuplicates,
  getProfileDetail: api.getProfileDetail,
}
