import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../lib/api'
import { useAuthStore } from '../stores/auth'
import DashboardView from './DashboardView.vue'
import ConsultationsView from './ConsultationsView.vue'
import QqChatView from './QqChatView.vue'
import AdminCustomerEnrichmentView from './AdminCustomerEnrichmentView.vue'

vi.mock('../lib/api', async (loadOriginal) => {
  const original = await loadOriginal<typeof import('../lib/api')>()
  return { ...original, api: vi.fn() }
})

const apiMock = vi.mocked(api)

async function context(path = '/') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const auth = useAuthStore()
  auth.user = { userId: 'u1', membershipId: 'm1', organizationId: 'o1', username: 'member',
    displayName: '员工甲', roles: ['MEMBER'], passwordChangeRequired: false }
  auth.checked = true
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/customers/:id', component: { template: '<div />' } },
    { path: '/consultations', component: { template: '<div />' } },
    { path: '/qq-chat', component: { template: '<div />' } },
  ] })
  await router.push(path); await router.isReady()
  return { pinia, router, auth }
}

beforeEach(() => apiMock.mockReset())

describe('new customer collaboration workflows', () => {
  it('shows full A-class recruitment context and claims through the constrained endpoint', async () => {
    apiMock.mockImplementation(async (path, init) => {
      if (path === '/api/v1/dashboard') return { stats: { totalActive: 1, archivedCount: 0, todayDueCount: 0, overdueCount: 0, highIntentionCount: 1 }, dueToday: [], overdue: [], staleHighIntention: [], recentUpdated: [] } as never
      if (path === '/api/v1/follow-up-recruitments' && init?.method === 'POST') return {} as never
      if (path === '/api/v1/follow-up-recruitments') return [{ id: 'r1', customerId: 'c1', fullName: '张三', school: '院校甲', major: '计算机', grade: '2026', highSchool: '示例中学', sourceLocation: '示例地区', demandSummary: '希望转专业', ownerDisplayName: '负责人甲', requiredCount: 1, claimedCount: 0, openedAt: new Date().toISOString(), recentTimeline: [{ contactedAt: new Date().toISOString(), channel: '微信', summary: '确认需求' }] }] as never
      if (path === '/api/v1/follow-up-recruitments/r1/claim') return {} as never
      return [] as never
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { pinia, router } = await context()
    const wrapper = mount(DashboardView, { global: { plugins: [pinia, router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('重点客户协作招募')
    expect(wrapper.text()).toContain('示例中学 / 示例地区')
    await wrapper.get('.recruitment-card .primary-button').trigger('click')
    await flushPromises()
    expect(apiMock).toHaveBeenCalledWith('/api/v1/follow-up-recruitments/r1/claim', { method: 'POST' })
  })

  it('loads consultation capacity, candidates and actionable handoff pipeline', async () => {
    apiMock.mockImplementation(async (path) => {
      if (path === '/api/v1/consultations') return [{ id: 'h1', customerId: 'c1', customerName: '张三', createdByMembershipId: 'm2', createdByName: '员工乙', consultantMembershipId: 'm1', consultantName: '员工甲', sourceInteractionIds: ['i1'], consultationReason: '确认升学方案', customerQuotes: '想先了解课程', goalsMotivation: '升学', actionsTaken: '已沟通', mainConcerns: '时间', decisionParticipantConsent: false, productFit: '待核实', suggestedFocus: '课程匹配', explicitTaboos: '不强推', expectedResult: '明确下一步', scheduledStart: '2026-08-20T02:00:00Z', scheduledEnd: '2026-08-20T03:00:00Z', status: 'PENDING_ACCEPTANCE', createdAt: '', updatedAt: '', version: 0 }] as never
      if (String(path).startsWith('/api/v1/customers?')) return { items: [] } as never
      if (path === '/api/v1/consultations/candidates') return [{ membershipId: 'm1', displayName: '员工甲', roles: ['MEMBER'], teams: ['一组'] }] as never
      if (path === '/api/v1/consultation-settings') return { businessStart: '09:00', businessEnd: '21:00', minimumLeadMinutes: 30, allowedDurations: [30, 60], dailyCapacity: 6, feedbackDueHours: 48, version: 0 } as never
      if (path === '/api/v1/consultation-failure-reasons') return [] as never
      if (path === '/api/v1/consultations/h1/accept') return {} as never
      return [] as never
    })
    const { pinia, router } = await context('/consultations')
    const wrapper = mount(ConsultationsView, { global: { plugins: [pinia, router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('确认升学方案')
    expect(wrapper.text()).toContain('48 小时内闭环')
    await wrapper.get('.consultation-row .soft-button').trigger('click')
    await flushPromises()
    expect(apiMock).toHaveBeenCalledWith('/api/v1/consultations/h1/accept', expect.objectContaining({ method: 'POST' }))
  })

  it('keeps QR and QCE token boundary while loading only one-to-one friend choices', async () => {
    apiMock.mockImplementation(async (path) => {
      if (path === '/api/v1/qq-connections/me') return { id: 'q1', status: 'CONNECTED', maskedQq: '12***89', createdAt: '' } as never
      if (String(path).startsWith('/api/v1/customers?')) return { items: [] } as never
      if (path === '/api/v1/qq-connections/friends?page=1&limit=200') return { items: [{ uid: 'peer-1', uin: '123456', nick: '客户甲', remark: '张同学' }] } as never
      return [] as never
    })
    const { pinia, router } = await context('/qq-chat')
    const wrapper = mount(QqChatView, { global: { plugins: [pinia, router] } })
    await flushPromises()
    expect(wrapper.text()).toContain('连接成功')
    expect(wrapper.text()).toContain('张同学 · 123456')
    expect(wrapper.html()).not.toContain('accessToken')
  })

  it('renders enrichment preview outcomes without treating conflicts as ready rows', async () => {
    const job = { id: 'j1', filename: '学生所在地补全.xlsx', fileSha256: 'abcdef0123456789', status: 'COMPLETED', totalRows: 583, readyRows: 534, updatedRows: 500, skippedRows: 49, conflictRows: 34, expiresAt: '', createdAt: '2026-08-13T00:00:00Z' }
    apiMock.mockImplementation(async (path) => {
      if (path === '/api/v1/admin/customer-enrichment-jobs') return [job] as never
      if (path === '/api/v1/admin/customer-enrichment-jobs/j1') return { job, rows: [{ rowNumber: 2, fullName: '张三', major: '计算机', grade: '2026', highSchool: '示例中学', sourceLocation: '示例地区', mappingStatus: '已匹配', status: 'CONFLICT', message: 'CRM 已有不同高中', appliedHighSchool: false, appliedSourceLocation: false }] } as never
      return [] as never
    })
    const { pinia, router } = await context()
    const wrapper = mount(AdminCustomerEnrichmentView, { global: { plugins: [pinia, router] } })
    await flushPromises()
    await wrapper.get('.job-row').trigger('click'); await flushPromises()
    expect(wrapper.text()).toContain('资料冲突')
    expect(wrapper.text()).toContain('CRM 已有不同高中')
    expect(wrapper.findAll('button.primary-button').some((button) => button.text().includes('确认提交'))).toBe(false)
  })
})
