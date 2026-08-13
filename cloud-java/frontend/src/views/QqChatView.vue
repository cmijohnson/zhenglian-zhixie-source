<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api, jsonBody } from '../lib/api'
import { localInput, toIso } from '../lib/format'
import type { Customer, PageResponse, QqConnection, QqConversationDraft, QqFriend } from '../types'

interface QqPreview {
  importId: string
  customerId: string
  peerQq: string
  messageCount: number
  rangeStart: string
  rangeEnd: string
  expiresAt: string
  draft: QqConversationDraft
  messages: unknown[]
}

const route = useRoute()
const router = useRouter()
const connection = ref<QqConnection | null>()
const customers = ref<Customer[]>([])
const friends = ref<QqFriend[]>([])
const preview = ref<QqPreview>()
const selectedCustomerId = ref(typeof route.query.customerId === 'string' ? route.query.customerId : '')
const selectedPeerUid = ref('')
const selectedPeerQq = ref('')
const bindMissingQq = ref(false)
const range = reactive({ start: localInput(new Date(Date.now() - 7 * 86400000).toISOString()), end: localInput() })
const draft = reactive<QqConversationDraft>({ summary: '', customerQuotes: [], goalsMotivation: '', actionsTaken: '', mainConcerns: '', decisionParticipants: '', productFit: '', suggestedFocus: '', explicitTaboos: '', outcome: '', nextStep: '', uncertainties: [] })
const busy = ref(false)
const error = ref('')
const success = ref('')

const selectedCustomer = computed(() => customers.value.find((item) => item.id === selectedCustomerId.value))
const selectedFriend = computed(() => friends.value.find((item) => item.uid === selectedPeerUid.value))

async function load() {
  error.value = ''
  try {
    const [loadedConnection, loadedCustomers] = await Promise.all([
      api<QqConnection | null>('/api/v1/qq-connections/me'),
      api<PageResponse<Customer>>('/api/v1/customers?size=100&sort=updatedAt,desc'),
    ])
    connection.value = loadedConnection
    customers.value = loadedCustomers.items
    if (loadedConnection?.status === 'CONNECTED') await loadFriends()
  } catch (reason) { error.value = message(reason, 'QQ 对话页面加载失败') }
}

async function connect() {
  busy.value = true; error.value = ''
  try { connection.value = await api<QqConnection>('/api/v1/qq-connections', { method: 'POST' }) }
  catch (reason) { error.value = message(reason, '无法创建 QQ 连接') }
  finally { busy.value = false }
}

async function refresh() {
  try {
    connection.value = await api<QqConnection | null>('/api/v1/qq-connections/me')
    if (connection.value?.status === 'CONNECTED') await loadFriends()
  } catch (reason) { error.value = message(reason, '连接状态刷新失败') }
}

async function revoke() {
  if (!window.confirm('确认撤销当前工作 QQ 连接？之后需要重新扫码。')) return
  await api<void>('/api/v1/qq-connections/me', { method: 'DELETE' })
  connection.value = null
  friends.value = []
}

async function loadFriends() {
  const page = await api<{ items: QqFriend[] }>('/api/v1/qq-connections/friends?page=1&limit=200')
  friends.value = page.items
}

function chooseFriend() {
  const friend = selectedFriend.value
  selectedPeerQq.value = friend?.uin ?? ''
}

async function createPreview() {
  if (!selectedCustomerId.value || !selectedPeerUid.value || !selectedPeerQq.value) {
    error.value = '请选择客户和对应 QQ 好友'; return
  }
  busy.value = true; error.value = ''; success.value = ''
  try {
    const value = await api<QqPreview>('/api/v1/qq-imports/preview', {
      method: 'POST',
      body: jsonBody({ customerId: selectedCustomerId.value, peerUid: selectedPeerUid.value,
        peerQq: selectedPeerQq.value, rangeStart: toIso(range.start), rangeEnd: toIso(range.end), bindMissingQq: bindMissingQq.value }),
    })
    preview.value = value
    Object.assign(draft, value.draft, { customerQuotes: [...(value.draft.customerQuotes ?? [])], uncertainties: [...(value.draft.uncertainties ?? [])] })
  } catch (reason) { error.value = message(reason, 'QQ 对话预览失败') }
  finally { busy.value = false }
}

async function confirm() {
  if (!preview.value) return
  busy.value = true; error.value = ''
  try {
    const result = await api<{ interactionId: string }>(`/api/v1/qq-imports/${preview.value.importId}/confirm`, {
      method: 'POST', body: jsonBody({ idempotencyKey: crypto.randomUUID(), draft: { ...draft, nextFollowUpAt: toIso(draft.nextFollowUpAt) } }),
    })
    success.value = 'QQ 对话已写入该客户的统一时间线。'
    await router.push(`/customers/${preview.value.customerId}#interaction-${result.interactionId}`)
  } catch (reason) { error.value = message(reason, '确认写入失败') }
  finally { busy.value = false }
}

function split(value: string) { return value.split(/\n|；/).map((item) => item.trim()).filter(Boolean) }
function message(reason: unknown, fallback: string) { return reason instanceof ApiError ? `${reason.message}${reason.body.traceId ? `（${reason.body.traceId}）` : ''}` : fallback }
onMounted(load)
</script>

<template>
  <div class="page qq-chat-page">
    <header class="page-header hero-header"><div><p class="eyebrow">QQ CONVERSATION</p><h1>QQ 对话整理</h1><p class="muted">扫码连接自己的工作 QQ，核对客户与好友后预览；AI 只生成草稿，必须由你确认才写入时间线。</p></div></header>
    <p v-if="error" class="notice error">{{ error }}</p><p v-if="success" class="notice success">{{ success }}</p>
    <section class="panel connection-panel">
      <div class="panel-title"><div><p class="eyebrow">STEP 01</p><h2>工作 QQ 连接</h2></div><span class="status-pill">{{ connection?.status || '未连接' }}</span></div>
      <div v-if="!connection" class="empty-state"><p>每名员工仅绑定一个独立 QQ 实例。</p><button class="primary-button" :disabled="busy" @click="connect">创建连接并扫码</button></div>
      <div v-else class="connection-content">
        <img v-if="connection.qrCodeDataUrl && connection.status !== 'CONNECTED'" :src="connection.qrCodeDataUrl" alt="QQ 登录二维码" class="qq-qr">
        <div><strong>{{ connection.status === 'CONNECTED' ? '连接成功' : '请使用工作 QQ 扫码' }}</strong><p class="muted">账号：{{ connection.maskedQq || '仅显示脱敏信息' }}</p><div class="header-actions"><button class="ghost-button" @click="refresh">刷新状态</button><button class="danger-link" @click="revoke">撤销连接</button></div></div>
      </div>
    </section>
    <section class="panel">
      <div class="panel-title"><div><p class="eyebrow">STEP 02</p><h2>选择客户与对话范围</h2></div></div>
      <div class="form-grid two">
        <label>客户<select v-model="selectedCustomerId"><option value="">请选择客户</option><option v-for="item in customers" :key="item.id" :value="item.id">{{ item.fullName }} · QQ {{ item.qqId || '未填写' }}</option></select></label>
        <label>QQ 好友<select v-model="selectedPeerUid" :disabled="connection?.status !== 'CONNECTED'" @change="chooseFriend"><option value="">请选择一对一好友</option><option v-for="friend in friends" :key="friend.uid" :value="friend.uid">{{ friend.remark || friend.nick || friend.uin }} · {{ friend.uin }}</option></select></label>
        <label>开始时间<input v-model="range.start" type="datetime-local"></label><label>结束时间<input v-model="range.end" type="datetime-local"></label>
        <label v-if="selectedCustomer && !selectedCustomer.qqId" class="span-2 checkbox-line"><input v-model="bindMissingQq" type="checkbox"> 我已核对好友身份，同意把 QQ {{ selectedPeerQq || '—' }} 绑定到该客户档案</label>
      </div>
      <footer class="form-actions"><button class="primary-button" :disabled="busy || connection?.status !== 'CONNECTED'" @click="createPreview">{{ busy ? '读取与整理中…' : '预览对话与 AI 草稿' }}</button></footer>
    </section>
    <section v-if="preview" class="panel">
      <div class="panel-title"><div><p class="eyebrow">STEP 03</p><h2>人工核对结构化草稿</h2></div><span>{{ preview.messageCount }} 条消息 · 原文 {{ new Date(preview.expiresAt).toLocaleDateString() }} 后清理</span></div>
      <div class="form-grid two">
        <label class="span-2">对话摘要 *<textarea v-model.trim="draft.summary" rows="4"></textarea></label>
        <label class="span-2">客户关键原话<textarea :value="draft.customerQuotes.join('\n')" rows="4" @input="draft.customerQuotes = split(($event.target as HTMLTextAreaElement).value)"></textarea></label>
        <label>目标与动机<textarea v-model.trim="draft.goalsMotivation" rows="4"></textarea></label><label>已做行动<textarea v-model.trim="draft.actionsTaken" rows="4"></textarea></label>
        <label>主要顾虑<textarea v-model.trim="draft.mainConcerns" rows="4"></textarea></label><label>决策参与人<textarea v-model.trim="draft.decisionParticipants" rows="4"></textarea></label>
        <label>产品适配<textarea v-model.trim="draft.productFit" rows="4"></textarea></label><label>建议重点<textarea v-model.trim="draft.suggestedFocus" rows="4"></textarea></label>
        <label>明确禁忌<textarea v-model.trim="draft.explicitTaboos" rows="4"></textarea></label><label>沟通结果<textarea v-model.trim="draft.outcome" rows="4"></textarea></label>
        <label>下一步<textarea v-model.trim="draft.nextStep" rows="4"></textarea></label><label>建议跟进时间<input v-model="draft.nextFollowUpAt" type="datetime-local"></label>
        <label class="span-2">待核实事项<textarea :value="draft.uncertainties.join('\n')" rows="3" @input="draft.uncertainties = split(($event.target as HTMLTextAreaElement).value)"></textarea></label>
      </div>
      <details><summary>查看本次导入原始消息（仅当前授权成员可见）</summary><pre class="raw-preview">{{ JSON.stringify(preview.messages, null, 2) }}</pre></details>
      <footer class="form-actions"><button class="primary-button" :disabled="busy || !draft.summary" @click="confirm">确认并写入客户时间线</button></footer>
    </section>
  </div>
</template>

<style scoped>
.connection-content{display:flex;align-items:center;gap:24px}.qq-qr{width:220px;height:220px;object-fit:contain;border:1px solid #dfe5f2;border-radius:16px}.status-pill{padding:8px 14px;border-radius:999px;background:#eef3ff;color:#2e55c8}.checkbox-line{display:flex;gap:10px;align-items:center}.checkbox-line input{width:auto}.raw-preview{max-height:420px;overflow:auto;padding:16px;border-radius:12px;background:#10182f;color:#d9e2ff;white-space:pre-wrap}.connection-panel{margin-bottom:24px}
</style>
