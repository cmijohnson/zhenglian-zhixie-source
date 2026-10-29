export interface TagRecord {
  id: number
  name: string
  category: string
  color: string
  isSystem: boolean
}

export interface StudentProfileRecord {
  id: number
  fullName: string
  nickname?: string | null
  phone?: string | null
  wechatId?: string | null
  school?: string | null
  major?: string | null
  grade?: string | null
  city?: string | null
  identityType: string
  focusDirections: string[]
  demandSummary?: string | null
  intentionLevel: string
  urgencyLevel: string
  verificationStatus: string
  sourceChannel?: string | null
  currentStatus: string
  ownerNotes?: string | null
  createdAt: string
  updatedAt: string
  lastContactAt?: string | null
  nextFollowUpAt?: string | null
  isArchived: boolean
  tagIds: number[]
  tags: TagRecord[]
}

export interface InteractionLogRecord {
  id: number
  studentProfileId: number
  contactedAt: string
  channel: string
  summary: string
  details?: string | null
  outcome?: string | null
  verificationTodo?: string | null
  nextFollowUpAt?: string | null
  createdAt: string
}

export interface ReminderEventRecord {
  id: number
  studentProfileId: number
  interactionLogId?: number | null
  remindAt: string
  title: string
  status: string
  notificationSent: boolean
  createdAt: string
  updatedAt: string
}

export interface SavedFilterRecord {
  id: number
  name: string
  filters: SearchFilters
  sortBy: string
  showOnHomepage: boolean
  createdAt: string
}

export interface DuplicateMatch {
  profile: StudentProfileRecord
  reasons: string[]
}

export interface ProfileDetailRecord {
  profile: StudentProfileRecord
  interactions: InteractionLogRecord[]
  reminders: ReminderEventRecord[]
}

export interface DashboardStats {
  totalActive: number
  archivedCount: number
  todayDueCount: number
  overdueCount: number
  highIntentionCount: number
}

export interface DashboardData {
  stats: DashboardStats
  dueToday: StudentProfileRecord[]
  overdue: StudentProfileRecord[]
  recentCreated: StudentProfileRecord[]
  recentUpdated: StudentProfileRecord[]
  staleHighIntention: StudentProfileRecord[]
  homepageFilters: SavedFilterRecord[]
}

export interface AppSettingsRecord {
  defaultReminderTime: string
  lastBackupAt?: string | null
  databasePath: string
}

export interface AppBootstrapData {
  tags: TagRecord[]
  savedFilters: SavedFilterRecord[]
  settings: AppSettingsRecord
  dashboard: DashboardData
}

export interface SearchFilters {
  searchText?: string | null
  grade?: string | null
  focusDirection?: string | null
  status?: string | null
  intentionLevel?: string | null
  verificationStatus?: string | null
  tagIds: number[]
  includeArchived: boolean
  sortBy?: string | null
}

export interface ProfileInput {
  fullName: string
  nickname?: string | null
  phone?: string | null
  wechatId?: string | null
  school?: string | null
  major?: string | null
  grade?: string | null
  city?: string | null
  identityType: string
  focusDirections: string[]
  demandSummary?: string | null
  intentionLevel: string
  urgencyLevel: string
  verificationStatus: string
  sourceChannel?: string | null
  currentStatus: string
  ownerNotes?: string | null
  nextFollowUpAt?: string | null
  tagIds: number[]
}

export interface InteractionInput {
  studentProfileId: number
  contactedAt: string
  channel: string
  summary: string
  details?: string | null
  outcome?: string | null
  verificationTodo?: string | null
  nextFollowUpAt?: string | null
  currentStatus?: string | null
  intentionLevel?: string | null
  verificationStatus?: string | null
  tagIds?: number[] | null
}

export interface SavedFilterInput {
  name: string
  filters: SearchFilters
  sortBy: string
  showOnHomepage: boolean
}

export interface DuplicateQuery {
  fullName: string
  phone?: string | null
  wechatId?: string | null
}

export interface UpdateReminderInput {
  studentProfileId: number
  nextFollowUpAt?: string | null
}

export interface CreateTagInput {
  name: string
  category: string
  color: string
}

export interface UpdateSettingsInput {
  defaultReminderTime: string
}

export interface ExportProfilesInput {
  filePath: string
  filters: SearchFilters
}

export interface ExportSingleProfileInput {
  filePath: string
  studentProfileId: number
}

export interface ProfileFormState {
  fullName: string
  nickname: string
  phone: string
  wechatId: string
  school: string
  major: string
  grade: string
  city: string
  identityType: string
  focusDirections: string[]
  demandSummary: string
  intentionLevel: string
  urgencyLevel: string
  verificationStatus: string
  sourceChannel: string
  currentStatus: string
  ownerNotes: string
  nextFollowUpAt: string
  tagIds: number[]
}

export interface InteractionFormState {
  contactedAt: string
  channel: string
  summary: string
  details: string
  outcome: string
  verificationTodo: string
  nextFollowUpAt: string
  currentStatus: string
  intentionLevel: string
  verificationStatus: string
  tagIds: number[]
}

export interface TagDraft {
  name: string
  category: string
  color: string
}
