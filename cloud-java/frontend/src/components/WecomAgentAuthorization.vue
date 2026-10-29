<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, api, jsonBody } from '../lib/api'
import { useAuthStore } from '../stores/auth'

const props = defineProps<{ bound: boolean }>()
const auth = useAuthStore()
interface Grant { active: boolean; longLived: boolean; scopes: string[]; grantId?: string }
interface PendingAction { id: string; toolName: string; approvalUrl: string; status: string; expiresAt: string }
const grant = ref<Grant>()
const pending = ref<PendingAction[]>([])
const selected = ref<string[]>([])
const consent = ref(false)
const busy = ref(false)
const error = ref('')
const status = ref('')
const scopes = computed(() => [
  { key: 'yingge.read', label: '查询本人权限范围内的 CRM 数据' },
  ...(auth.canWriteCustomers ? [
    { key: 'yingge.write', label: '记录沟通、客户维护、任务及日报等写入' },
    { key: 'yingge.ai', label: 'AI 分析与业务草稿' },
  ] : []),
  ...(auth.isAdmin ? [
    { key: 'yingge.admin.read', label: '组织管理查询' },
    { key: 'yingge.admin', label: '已开放的组织管理操作（危险操作仍须人工审批）' },
  ] : []),
])
async function load() {
  try {
    grant.value = await api<Grant>('/api/v1/wecom/agent/authorization')
    selected.value = grant.value.active ? grant.value.scopes : scopes.value.map(s => s.key)
    await loadPending()
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '授权状态加载失败' }
}
async function loadPending() {
  const rows = await api<PendingAction[]>('/api/v1/wecom/agent/pending-actions')
  pending.value = Array.isArray(rows) ? rows : []
}
function approvalPath(row: PendingAction) {
  // Render only the bound internal approval path, never a model-generated external link.
  return /^\/agent\/approvals\/[0-9a-f-]{36}$/i.test(row.approvalUrl) ? row.approvalUrl : ''
}
async function resume(row: PendingAction) {
  if (busy.value || !window.confirm('仅继续你在审批页已批准的原操作，参数不可修改。是否继续执行？')) return
  busy.value = true; error.value = ''
  try {
    const result = await api<{ message: string }>(`/api/v1/wecom/agent/pending-actions/${row.id}/resume`, { method: 'POST' })
    status.value = result.message
    await loadPending()
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '执行结果未确认，请查询业务记录，不要重复执行' }
  finally { busy.value = false }
}
async function authorize() {
  if (!consent.value || !props.bound || busy.value) return
  busy.value = true; error.value = ''; status.value = ''
  try {
    grant.value = await api<Grant>('/api/v1/wecom/agent/authorization', {
      method: 'POST', body: jsonBody({ scopes: selected.value, consent: true }),
    })
    status.value = 'Agent 已授权；可在指定企业微信群 @机器人 使用自然语言或 Slash。'
    consent.value = false
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '授权失败' }
  finally { busy.value = false }
}
async function revoke() {
  if (busy.value || !window.confirm('撤销后机器人将无法继续调用你的 CRM 权限，是否撤销？')) return
  busy.value = true
  try { await api('/api/v1/wecom/agent/authorization', { method: 'DELETE' }); await load(); status.value = '授权已撤销。' }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '撤销失败' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <section class="agent-authorization">
    <h3>企业微信 Agent 权限</h3>
    <p>{{ grant?.active ? '已授权 · 长期有效、随时可撤销' : '未授权；绑定身份后仍需本人明确授予权限。' }}</p>
    <p>授权不会扩大账号权限。普通明确写入可直接执行；转接、成单和危险管理操作必须确认。停用、解绑或密码安全事件会立即阻止执行。</p>
    <fieldset :disabled="busy || !bound"><legend>本次授权范围</legend>
      <label v-for="scope in scopes" :key="scope.key"><input v-model="selected" type="checkbox" :value="scope.key">{{ scope.label }}</label>
    </fieldset>
    <label class="risk"><input v-model="consent" type="checkbox" :disabled="busy || !bound">我确认上述权限长期授予此 Agent，并理解指定业务群中的查询结果可能被其他群成员看到。</label>
    <div class="actions"><button class="primary-button" :disabled="busy || !bound || !consent || selected.length === 0" @click="authorize">{{ busy ? '处理中…' : '确认授权 / 更新范围' }}</button><button v-if="grant?.active" class="danger-link" :disabled="busy" @click="revoke">撤销授权</button></div>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p><p v-if="status" role="status" class="form-success">{{ status }}</p>
    <section v-if="pending.length" class="pending-actions">
      <h4>危险操作人工确认</h4><p>先打开原审批页核对并批准，再继续执行。结果不确定时只查询，不盲目重试。</p>
      <article v-for="row in pending" :key="row.id">
        <strong>{{ row.toolName }} · {{ row.status }}</strong>
        <a v-if="approvalPath(row)" :href="approvalPath(row)" target="_blank" rel="noopener">打开本人审批页</a>
        <button v-if="row.status === 'PENDING'" class="soft-button" :disabled="busy" @click="resume(row)">已批准，继续原操作</button>
      </article>
    </section>
  </section>
</template>

<style scoped>
.agent-authorization{display:grid;gap:12px;border-top:1px solid var(--line);padding-top:18px}.agent-authorization h3,.agent-authorization p{margin:0}.agent-authorization p{line-height:1.7;color:var(--muted);overflow-wrap:anywhere}fieldset{display:grid;gap:6px;border:1px solid var(--line);border-radius:12px;padding:12px}label{display:flex;align-items:flex-start;gap:10px;min-height:44px;line-height:1.6}input[type=checkbox]{width:20px;height:20px;flex-shrink:0;margin-top:3px}.risk{padding:12px;background:#fff7e8;border-radius:12px;color:#7d5620}.actions{display:flex;flex-wrap:wrap;gap:12px}.actions button{min-height:44px}
</style>
