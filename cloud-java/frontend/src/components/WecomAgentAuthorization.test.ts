import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WecomAgentAuthorization from './WecomAgentAuthorization.vue'
import AdminWecomAgentView from '../views/AdminWecomAgentView.vue'

const apiMock = vi.hoisted(() => vi.fn())
const actor = vi.hoisted(() => ({ isAdmin: false, canWriteCustomers: true }))
vi.mock('../stores/auth', () => ({ useAuthStore: () => actor }))
vi.mock('../lib/api', () => ({ api: apiMock, jsonBody: JSON.stringify, ApiError: class extends Error {} }))

describe('WeCom explicit authorization and delivery safety', () => {
  beforeEach(() => {
    apiMock.mockReset()
    actor.isAdmin = false; actor.canWriteCustomers = true
    apiMock.mockResolvedValue({ active: false, scopes: [] })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('requires binding and consent before any grant request', async () => {
    const wrapper = mount(WecomAgentAuthorization, { props: { bound: false } })
    await flushPromises()
    expect(wrapper.find('fieldset').attributes()).toHaveProperty('disabled')
    expect(wrapper.find('.primary-button').attributes()).toHaveProperty('disabled')
    await wrapper.find('.primary-button').trigger('click')
    expect(apiMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
    await wrapper.setProps({ bound: true })
    expect(wrapper.find('.primary-button').attributes()).toHaveProperty('disabled')
  })

  it('grants only selected scopes after explicit consent, without an organization parameter', async () => {
    const wrapper = mount(WecomAgentAuthorization, { props: { bound: true } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('组织管理操作')
    await wrapper.find('.risk input').setValue(true)
    await wrapper.find('.primary-button').trigger('click')
    await flushPromises()
    expect(apiMock).toHaveBeenCalledWith('/api/v1/wecom/agent/authorization', {
      method: 'POST', body: JSON.stringify({ scopes: ['yingge.read', 'yingge.write', 'yingge.ai'], consent: true }),
    })
    expect(wrapper.text()).toContain('Agent 已授权')
  })

  it('does not offer viewer write or admin scopes', async () => {
    actor.canWriteCustomers = false
    const wrapper = mount(WecomAgentAuthorization, { props: { bound: true } })
    await flushPromises()
    expect(wrapper.findAll('fieldset input')).toHaveLength(1)
    expect(wrapper.find('fieldset input').attributes('value')).toBe('yingge.read')
  })

  it('resumes only a server-stored action after human confirmation and blocks external approval links', async () => {
    apiMock.mockImplementation((path: string) => {
      if (path.endsWith('/authorization')) return Promise.resolve({ active: true, scopes: ['yingge.read'] })
      if (path.endsWith('/pending-actions')) return Promise.resolve([
        { id: 'pending-1', toolName: '合成危险操作', status: 'PENDING', approvalUrl: '/agent/approvals/12345678-1234-1234-1234-123456789abc' },
        { id: 'pending-2', toolName: '异常链接', status: 'UNCERTAIN', approvalUrl: 'https://untrusted.invalid/approval' },
      ])
      return Promise.resolve({ message: '已执行原操作' })
    })
    const wrapper = mount(WecomAgentAuthorization, { props: { bound: true } })
    await flushPromises()
    expect(wrapper.findAll('.pending-actions a')).toHaveLength(1)
    const button = wrapper.find('.pending-actions button')
    vi.mocked(window.confirm).mockReturnValueOnce(false)
    await button.trigger('click')
    expect(apiMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
    await button.trigger('click')
    await flushPromises()
    expect(apiMock).toHaveBeenCalledWith('/api/v1/wecom/agent/pending-actions/pending-1/resume', { method: 'POST' })
    expect(wrapper.text()).toContain('已执行原操作')
  })

  it('requires a separate human warning before retrying uncertain delivery', async () => {
    apiMock.mockImplementation((path: string) => {
      if (path.endsWith('/settings')) return Promise.resolve({ enabled: false, submissionChatId: 'business', broadcastChatId: 'broadcast', fullDataRiskAccepted: false, version: 3 })
      if (path.endsWith('/status')) return Promise.resolve({ configured: true, state: 'CONNECTED' })
      if (path.endsWith('/executions')) return Promise.resolve([])
      if (path.endsWith('/deliveries')) return Promise.resolve([{ id: 'delivery-1', status: 'UNCERTAIN', attempts: 1, createdAt: '2026-09-28T00:00:00Z' }])
      return Promise.resolve(undefined)
    })
    const wrapper = mount(AdminWecomAgentView)
    await flushPromises()
    const retry = wrapper.findAll('button').find(button => button.text() === '核对后重发')!
    vi.mocked(window.confirm).mockReturnValueOnce(false)
    await retry.trigger('click')
    expect(apiMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
    await retry.trigger('click')
    await flushPromises()
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('可能已经送达'))
    expect(apiMock).toHaveBeenCalledWith('/api/v1/wecom/agent/deliveries/delivery-1/retry', {
      method: 'POST', body: JSON.stringify({ verifiedNotDelivered: true }),
    })
  })
})
