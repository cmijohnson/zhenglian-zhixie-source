<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError, api, jsonBody } from '../lib/api'
import { dateTime } from '../lib/format'
import { useAuthStore } from '../stores/auth'
import WecomAgentAuthorization from '../components/WecomAgentAuthorization.vue'
const auth = useAuthStore(); const success = ref(''); const error = ref('')
const form = reactive({ currentPassword: '', newPassword: '', confirm: '' })
interface FeishuBinding { bound: boolean; oauthAvailable: boolean; httpsRequired: boolean; bindingId?: string; tenantKey?: string; feishuUserId?: string; displayName?: string; boundAt?: string; lastLoginAt?: string }
interface WecomBinding { botAvailable: boolean; bound: boolean; bindingId?: string; maskedWecomUserId?: string; boundAt?: string; lastUsedAt?: string }
interface WecomBindingCode { code: string; expiresAt: string }
const feishu = ref<FeishuBinding>()
const feishuBusy = ref(false)
const unbindPassword = ref('')
const showUnbind = ref(false)
const wecom = ref<WecomBinding>()
const wecomCode = ref<WecomBindingCode>()
const wecomBusy = ref(false)
const wecomUnbindPassword = ref('')
const showWecomUnbind = ref(false)
async function change() { error.value = ''; success.value = ''; if (form.newPassword !== form.confirm) { error.value = '两次输入的新密码不一致'; return } try { await api('/api/v1/auth/password', { method: 'POST', body: jsonBody(form) }); Object.assign(form, { currentPassword: '', newPassword: '', confirm: '' }); success.value = '密码已更新' } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '修改失败' } }

async function loadFeishu() {
  try { feishu.value = await api<FeishuBinding>('/api/v1/auth/feishu/binding') }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '飞书绑定状态加载失败' }
}

function bindFeishu() {
  const query = new URLSearchParams({ purpose: 'BIND', returnTo: '/settings?feishu=bound' })
  window.location.assign(`/api/v1/auth/feishu/start?${query.toString()}`)
}

async function unbindFeishu() {
  feishuBusy.value = true; error.value = ''; success.value = ''
  try {
    await api('/api/v1/auth/feishu/binding', { method: 'DELETE', body: jsonBody({ currentPassword: unbindPassword.value }) })
    unbindPassword.value = ''; showUnbind.value = false; success.value = '飞书账号已解绑，密码登录不受影响。'
    await loadFeishu()
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '解绑失败' }
  finally { feishuBusy.value = false }
}

async function loadWecom() {
  try { wecom.value = await api<WecomBinding>('/api/v1/wecom/aibot/binding') }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '企业微信绑定状态加载失败' }
}

async function issueWecomCode() {
  wecomBusy.value = true; error.value = ''; success.value = ''
  try {
    wecomCode.value = await api<WecomBindingCode>('/api/v1/wecom/aibot/binding-code', { method: 'POST' })
    success.value = '绑定码已生成，请在 5 分钟内发送给企业微信机器人。'
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '绑定码生成失败' }
  finally { wecomBusy.value = false }
}

async function unbindWecom() {
  wecomBusy.value = true; error.value = ''; success.value = ''
  try {
    await api('/api/v1/wecom/aibot/binding', { method: 'DELETE', body: jsonBody({ currentPassword: wecomUnbindPassword.value }) })
    wecomUnbindPassword.value = ''; showWecomUnbind.value = false; wecomCode.value = undefined
    success.value = '企业微信账号已解绑，英格云账号密码不受影响。'
    await loadWecom()
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '解绑失败' }
  finally { wecomBusy.value = false }
}

function masked(value?: string) {
  if (!value) return '—'
  return value.length <= 8 ? value : `${value.slice(0, 4)}…${value.slice(-4)}`
}

onMounted(() => { void Promise.all([loadFeishu(), loadWecom()]) })
</script>

<template>
  <div class="page narrow-page">
    <header class="page-header"><div><p class="eyebrow">ACCOUNT</p><h1>账户设置</h1><p class="muted">登录身份与安全设置。</p></div></header>
    <p v-if="error" class="form-error">{{ error }}</p><p v-if="success" class="form-success">{{ success }}</p>
    <section class="panel account-summary"><span class="avatar large">{{ auth.user?.displayName.slice(0, 1) }}</span><div><h2>{{ auth.user?.displayName }}</h2><p>{{ auth.user?.username }} · {{ auth.user?.roles.join(' / ') }}</p></div></section>

    <section class="panel feishu-binding">
      <div><p class="eyebrow">FEISHU IDENTITY</p><h2>飞书账号</h2><p>绑定后可使用飞书快捷登录；英格云账号密码仍永久保留。</p></div>
      <div v-if="feishu?.bound" class="binding-card"><span class="feishu-icon">飞</span><div><strong>{{ feishu.displayName || '已绑定飞书用户' }}</strong><p>用户标识 {{ masked(feishu.feishuUserId) }} · 绑定于 {{ feishu.boundAt ? dateTime(feishu.boundAt) : '—' }}</p></div><span class="binding-state">已绑定</span></div>
      <div v-else class="binding-empty"><p>{{ feishu?.oauthAvailable ? '当前尚未绑定飞书账号。' : '组织管理员尚未启用飞书登录。' }}</p><button class="primary-button" :disabled="!feishu?.oauthAvailable" @click="bindFeishu">绑定飞书账号</button></div>
      <p v-if="feishu?.httpsRequired" class="transport-warning">正式飞书登录只允许可信 HTTPS 入口；内网 HTTP 仅用于管理员明确开启的受限联调。</p>
      <div v-if="feishu?.bound" class="binding-actions"><RouterLink class="ghost-button" to="/feishu-workbench">打开飞书协同工作台</RouterLink></div>
      <div v-if="feishu?.bound" class="unbind-area"><button v-if="!showUnbind" class="danger-link" @click="showUnbind = true">解绑飞书</button><form v-else class="unbind-form" @submit.prevent="unbindFeishu"><label>验证当前英格云密码<input v-model="unbindPassword" type="password" autocomplete="current-password" required></label><div><button class="danger-button" :disabled="feishuBusy">确认解绑</button><button type="button" class="ghost-button" @click="showUnbind = false">取消</button></div></form></div>
    </section>

    <section class="panel wecom-binding">
      <div><p class="eyebrow">WECOM IDENTITY</p><h2>企业微信助手</h2><p>绑定后，机器人按你的英格云账号权限搜索客户、查看成单榜并打开 CRM。</p></div>
      <div v-if="wecom?.bound" class="binding-card wecom-card"><span class="wecom-icon">企</span><div><strong>已绑定企业微信</strong><p>用户标识 {{ wecom.maskedWecomUserId || '—' }} · 绑定于 {{ wecom.boundAt ? dateTime(wecom.boundAt) : '—' }}</p></div><span class="binding-state">已绑定</span></div>
      <div v-else class="binding-empty">
        <p>{{ wecom?.botAvailable ? '生成一次性绑定码，再在企业微信中向机器人发送。' : '组织管理员尚未启用企业微信助手。' }}</p>
        <button class="primary-button" :disabled="!wecom?.botAvailable || wecomBusy" @click="issueWecomCode">生成企业微信绑定码</button>
      </div>
      <div v-if="wecomCode" class="binding-code" aria-live="polite"><strong>{{ wecomCode.code }}</strong><p>在企业微信中发送：<code>/bind {{ wecomCode.code }}</code></p><small>有效期至 {{ dateTime(wecomCode.expiresAt) }}</small></div>
      <p class="transport-warning">指定业务群可能展示完整客户资料。不要在群内发送绑定码、密码或密钥；绑定码请私聊机器人使用。</p>
      <WecomAgentAuthorization :bound="wecom?.bound ?? false" />
      <div v-if="wecom?.bound" class="unbind-area"><button v-if="!showWecomUnbind" class="danger-link" @click="showWecomUnbind = true">解绑企业微信</button><form v-else class="unbind-form" @submit.prevent="unbindWecom"><label>验证当前英格云密码<input v-model="wecomUnbindPassword" type="password" autocomplete="current-password" required></label><div><button class="danger-button" :disabled="wecomBusy">确认解绑</button><button type="button" class="ghost-button" @click="showWecomUnbind = false">取消</button></div></form></div>
    </section>

    <form class="panel settings-form" @submit.prevent="change"><div><p class="eyebrow">SECURITY</p><h2>修改密码</h2><p>系统不设置最低长度或复杂度要求，密码不能为空。</p></div><label>当前密码<input v-model="form.currentPassword" type="password" autocomplete="current-password" required></label><label>新密码<input v-model="form.newPassword" type="password" autocomplete="new-password" maxlength="200" required></label><label>确认新密码<input v-model="form.confirm" type="password" autocomplete="new-password" maxlength="200" required></label><button class="primary-button">更新密码</button></form>
  </div>
</template>

<style scoped>
@media (max-width: 900px) {
  .account-summary { align-items: center; padding: 18px 16px; }
  .account-summary p { overflow-wrap: anywhere; }
  .settings-form { padding: 20px 16px; }
  .settings-form input { min-height: 48px; font-size: 16px; }
  .settings-form .primary-button { width: 100%; min-height: 48px; }
  .binding-card { grid-template-columns:auto 1fr; }
  .binding-state { grid-column:2; justify-self:start; }
  .binding-empty .primary-button { width:100%; min-height:48px; }
  .binding-code code { display:block; margin-top:8px; overflow-wrap:anywhere; }
  .unbind-form > div { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
}
.feishu-binding{display:grid;gap:18px}.binding-card{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:14px;padding:16px;border:1px solid #cfe5df;border-radius:16px;background:#f2fbf8}.feishu-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:13px;background:linear-gradient(145deg,#2f80ed,#00b899);color:#fff;font-weight:800}.binding-card p,.binding-empty p{margin:4px 0 0;color:var(--muted);overflow-wrap:anywhere}.binding-state{padding:6px 10px;border-radius:999px;color:#14745b;background:#d7f6eb;font-size:12px;font-weight:800}.transport-warning{margin:0;padding:12px;border-radius:12px;background:#fff8e8;color:#925b13}.unbind-area{border-top:1px solid var(--line);padding-top:14px}.unbind-form{display:grid;gap:12px}.unbind-form>div{display:flex;gap:10px}.unbind-form input{font-size:16px;min-height:46px}
.binding-actions{display:flex}.binding-actions a{text-decoration:none}
.wecom-binding{display:grid;gap:18px}.wecom-card{border-color:#cfe9df;background:#f2fbf7}.wecom-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:13px;background:linear-gradient(145deg,#20b77a,#087f5b);color:#fff;font-weight:800}.binding-code{padding:16px;border:1px dashed #7da6ff;border-radius:14px;background:#f5f8ff}.binding-code strong{font-size:24px;letter-spacing:.08em;color:#214ecf}.binding-code p,.binding-code small{margin:6px 0 0;color:var(--muted)}.binding-code code{font-size:14px;color:var(--ink)}
</style>
