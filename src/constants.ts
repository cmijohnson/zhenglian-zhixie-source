import type { InteractionFormState, ProfileFormState, SearchFilters } from './types'

export const identityTypes = ['新生', '转专业', '在校生', '毕业生', '家长代问', '其他']
export const focusDirectionOptions = [
  '就业',
  '考研',
  '考公',
  '升学',
  '转专业',
  '技能提升',
  '暂不明确',
]
export const intentionLevels = ['低', '中', '高']
export const urgencyLevels = ['低', '中', '高']
export const verificationStatuses = ['未核实', '已核实', '部分待确认']
export const profileStatuses = [
  '初次接触',
  '待核实',
  '已沟通',
  '跟进中',
  '高意向',
  '待决定',
  '已转化',
  '暂缓',
  '无效',
  '归档',
]
export const interactionChannels = ['微信', '电话', '面聊', '活动', '群聊', '语音', '其他']
export const savedFilterSortOptions = [
  { value: 'updatedDesc', label: '最近更新' },
  { value: 'createdDesc', label: '最近新增' },
  { value: 'lastContactDesc', label: '最近联系' },
  { value: 'nextFollowUpAsc', label: '最近待跟进' },
]
export const tagCategories = ['年级', '需求方向', '状态补充', '风险', '自定义']
export const tagColorOptions = [
  '#2563eb',
  '#0f766e',
  '#ea580c',
  '#7c3aed',
  '#16a34a',
  '#dc2626',
  '#475569',
  '#d97706',
]

export const defaultSearchFilters: SearchFilters = {
  searchText: '',
  grade: '',
  focusDirection: '',
  status: '',
  intentionLevel: '',
  verificationStatus: '',
  tagIds: [],
  includeArchived: false,
  sortBy: 'updatedDesc',
}

export const emptyProfileForm = (): ProfileFormState => ({
  fullName: '',
  nickname: '',
  phone: '',
  wechatId: '',
  school: '',
  major: '',
  grade: '',
  city: '',
  identityType: '在校生',
  focusDirections: ['暂不明确'],
  demandSummary: '',
  intentionLevel: '中',
  urgencyLevel: '中',
  verificationStatus: '未核实',
  sourceChannel: '',
  currentStatus: '初次接触',
  ownerNotes: '',
  nextFollowUpAt: '',
  tagIds: [],
})

export const emptyInteractionForm = (fallbackStatus = '跟进中'): InteractionFormState => ({
  contactedAt: '',
  channel: '微信',
  summary: '',
  details: '',
  outcome: '',
  verificationTodo: '',
  nextFollowUpAt: '',
  currentStatus: fallbackStatus,
  intentionLevel: '',
  verificationStatus: '',
  tagIds: [],
})
