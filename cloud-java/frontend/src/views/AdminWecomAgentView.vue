<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError, api, jsonBody } from '../lib/api'
import { dateTime } from '../lib/format'

interface Policy { enabled: boolean; submissionChatId: string; broadcastChatId: string; fullDataRiskAccepted: boolean; version: number }
interface Runtime { configured: boolean; state: string; lastErrorCode?: string; lastMessageAt?: string }
interface Execution { id: string; command?: string; status: string; errorCode?: string; createdAt: string }
interface Delivery { id: string; status: string; attempts: number; errorCode?: string; createdAt: string }
interface ObservedChat { chatId: string; lastSeenAt: string; eventCount: number }
const form = reactive({ enabled: false, submissionChatId: '', broadcastChatId: '', acceptFullDataRisk: false, expectedVersion: 0 })
const runtime = ref<Runtime>()
const executions = ref<Execution[]>([])
const deliveries = ref<Delivery[]>([])
const observedChats = ref<ObservedChat[]>([])
const busy = ref(false)
const error = ref('')
const success = ref('')

async function load() {
  error.value = ''
  try {
    const [policy, state, events, messages, chats] = await Promise.all([
      api<Policy>('/api/v1/wecom/agent/settings'), api<Runtime>('/api/v1/wecom/aibot/status'),
      api<Execution[]>('/api/v1/wecom/agent/executions'), api<Delivery[]>('/api/v1/wecom/agent/deliveries'),
      api<ObservedChat[]>('/api/v1/wecom/agent/observed-chats'),
    ])
    Object.assign(form, { enabled: policy.enabled, submissionChatId: policy.submissionChatId, broadcastChatId: policy.broadcastChatId, acceptFullDataRisk: policy.fullDataRiskAccepted, expectedVersion: policy.version })
    runtime.value = state; executions.value = events; deliveries.value = messages; observedChats.value = chats ?? []
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '企业微信 Agent 状态读取失败' }
}
async function save() {
  if (busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try { await api('/api/v1/wecom/agent/settings', { method: 'PUT', body: jsonBody(form) }); await load(); success.value = '配置已保存。成员还需在账户设置明确授权 Agent。' }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '保存失败' }
  finally { busy.value = false }
}
async function retry(delivery: Delivery) {
  const uncertain = delivery.status === 'UNCERTAIN'
  if (!window.confirm(uncertain ? '该消息可能已经送达。请在企业微信核实尚未送达后再重试，否则可能产生重复广播。已核实尚未送达？' : '重新发送此消息？仅重发消息，不重复业务操作。')) return
  busy.value = true
  try { await api(`/api/v1/wecom/agent/deliveries/${delivery.id}/retry`, { method: 'POST', body: jsonBody({ verifiedNotDelivered: uncertain }) }); await load() }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '重试失败' }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-header"><div><p class="eyebrow">WECOM AGENT</p><h1>企业微信 Agent</h1><p class="muted">自然语言与 Slash 双模式，共享草稿、权限和成单账本。</p></div><button class="ghost-button" :disabled="busy" @click="load">刷新</button></header>
    <p v-if="error" role="alert" class="form-error">{{ error }}</p><p v-if="success" role="status" class="form-success">{{ success }}</p>
    <section class="panel"><h2>连接状态：{{ runtime?.state || '读取中' }}</h2><p>{{ runtime?.configured ? '机器人服务端配置已就绪' : '机器人配置未就绪；需要仓库外 Bot ID / Secret 和 Agent 加密密钥' }}</p><p>最近消息：{{ runtime?.lastMessageAt ? dateTime(runtime.lastMessageAt) : '暂无' }} · 错误：{{ runtime?.lastErrorCode || '无' }}</p><p>模型通道固定 MiniMax-M3，凭据不回显。模型故障时 Slash 仍可使用。</p></section>
    <section class="panel"><h2>最近发现的群</h2><p class="muted">请在目标群 @机器人发送 /status，再刷新此页；仅记录群 ID 与时间，不保存未授权群的消息内容。按发送时间核对群，不能凭群名猜测。</p><div class="records"><article v-for="chat in observedChats" :key="chat.chatId"><strong>{{ chat.chatId }}</strong><p>最近收到：{{ dateTime(chat.lastSeenAt) }} · {{ chat.eventCount }} 条</p><button type="button" class="ghost-button" @click="form.submissionChatId = chat.chatId">填入业务群</button><button type="button" class="ghost-button" @click="form.broadcastChatId = chat.chatId">填入广播群</button></article><p v-if="!observedChats.length">近 7 天尚未收到群消息。</p></div></section>
    <form class="panel agent-form" @submit.prevent="save">
      <h2>业务群与广播</h2>
      <label>业务群 chat_id<input v-model="form.submissionChatId" maxlength="256" autocomplete="off" placeholder="使用企业微信实际群 ID，不是群名"></label>
      <label>广播群 chat_id<input v-model="form.broadcastChatId" maxlength="256" autocomplete="off"></label>
      <label class="check"><input v-model="form.acceptFullDataRisk" type="checkbox">我确认指定业务群可公开有权查询的完整客户资料，广播群公开客户姓名、订单金额与奖金；群成员可见范围由群管理负责。</label>
      <label class="check"><input v-model="form.enabled" type="checkbox">启用自然语言及 Slash 业务操作</label>
      <button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '保存配置' }}</button>
    </form>
    <section class="panel"><h2>最近执行（最多100条）</h2><p class="muted">仅展示运行元数据，不展示会话、联系方式或令牌。</p><div class="records"><article v-for="event in executions" :key="event.id"><strong>{{ event.command || '消息' }} · {{ event.status }}</strong><p>{{ dateTime(event.createdAt) }} · {{ event.errorCode || '无错误' }}</p><small>{{ event.id }}</small></article><p v-if="!executions.length">暂无执行。</p></div></section>
    <section class="panel"><h2>消息投递</h2><div class="records"><article v-for="delivery in deliveries" :key="delivery.id"><strong>{{ delivery.status }} · 已尝试 {{ delivery.attempts }} 次</strong><p>{{ dateTime(delivery.createdAt) }} · {{ delivery.errorCode || '无错误' }}</p><small>{{ delivery.id }}</small><button v-if="['FAILED','UNCERTAIN'].includes(delivery.status)" class="ghost-button" :disabled="busy" @click="retry(delivery)">核对后重发</button></article><p v-if="!deliveries.length">暂无消息。</p></div></section>
  </div>
</template>

<style scoped>
.agent-form{display:grid;gap:16px}.agent-form label{display:grid;gap:8px}.agent-form input:not([type=checkbox]){width:100%;font-size:16px;min-height:44px}.agent-form .check{display:flex;align-items:flex-start;gap:10px;line-height:1.7;min-height:44px}.check input{width:20px;height:20px;flex-shrink:0;margin-top:4px}.records{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,290px),1fr));gap:12px}.records article{padding:14px;border:1px solid var(--line);border-radius:14px;overflow-wrap:anywhere}.records p{color:var(--muted)}.records button{display:block;margin-top:12px;min-height:44px}.page>.panel{margin-bottom:18px}.panel>p{overflow-wrap:anywhere;line-height:1.7}@media(max-width:900px){.panel{padding:18px 16px}.agent-form button{width:100%}}
</style>
