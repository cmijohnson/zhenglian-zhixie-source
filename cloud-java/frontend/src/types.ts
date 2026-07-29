export interface Me {
  userId: string
  membershipId: string
  organizationId: string
  username: string
  displayName: string
  roles: string[]
  passwordChangeRequired: boolean
}

export interface Tag {
  id: string
  name: string
  category: string
  color: string
  system: boolean
}

export interface DictionaryItem {
  id: string
  type: string
  code: string
  label: string
  sortOrder: number
}

export interface Customer {
  id: string
  ownerMembershipId: string
  ownerDisplayName: string
  createdByMembershipId: string
  creatorDisplayName: string
  teamId?: string
  visibility: 'PRIVATE' | 'TEAM' | 'ORGANIZATION'
  fullName: string
  nickname?: string
  phone?: string
  wechatId?: string
  qqId?: string
  school?: string
  major?: string
  grade?: string
  city?: string
  identityType: string
  focusDirections: string[]
  demandSummary?: string
  intentionLevel: string
  urgencyLevel: string
  verificationStatus: string
  sourceDictionaryId?: string
  sourceChannel?: string
  currentStatus: string
  ownerNotes?: string
  lastContactAt?: string
  nextFollowUpAt?: string
  friendAddedAt?: string
  friendTrustedAt?: string
  friendQualifiedAt?: string
  archivedAt?: string
  leadReviewStatus: 'PENDING' | 'VALID' | 'INVALID'
  leadReviewReason?: string
  leadReviewedByMembershipId?: string
  leadReviewerDisplayName?: string
  leadReviewedAt?: string
  createdAt: string
  updatedAt: string
  version: number
  tags: Tag[]
}

export interface Interaction {
  id: string
  customerId: string
  contactedAt: string
  channel: string
  summary: string
  details?: string
  outcome?: string
  verificationTodo?: string
  nextFollowUpAt?: string
  createdByMembershipId?: string
  createdByDisplayName?: string
  createdAt: string
}

export interface Reminder {
  id: string
  customerId: string
  remindAt: string
  title: string
  status: string
  deliveryStatus: string
  overdue: boolean
}

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ApiErrorBody {
  code: string
  message: string
  fieldErrors?: { field: string; message: string }[]
  traceId?: string
}

export interface AgentAuthorization {
  id: string
  clientId: string
  clientName: string
  displayName: string
  scopes: string[]
  status: 'ACTIVE' | 'REVOKED'
  createdAt: string
  expiresAt: string
  lastUsedAt?: string
  revokedAt?: string
}

export interface PersonalAgentToken {
  grantId: string
  clientId: string
  accessToken: string
  scopes: string[]
  resource: string
  expiresAt: string
}

export interface OAuthConsentRequest {
  requestId: string
  clientId: string
  clientName: string
  redirectUri: string
  requestedScopes: string[]
  grantableScopes: string[]
  expiresAt: string
}

export interface AgentApproval {
  id: string
  clientId: string
  toolName: string
  summary: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CONSUMED'
  createdAt: string
  expiresAt: string
  decidedAt?: string
  consumedAt?: string
}
