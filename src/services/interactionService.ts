import { api } from './api'

export const interactionService = {
  addInteractionAndRefreshProfile: api.addInteractionAndRefreshProfile,
  listInteractionsByProfile: api.listInteractionsByProfile,
  updateNextFollowUp: api.updateNextFollowUp,
}
