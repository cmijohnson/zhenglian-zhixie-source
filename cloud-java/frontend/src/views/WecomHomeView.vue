<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError, api } from '../lib/api'
import { useAuthStore } from '../stores/auth'

interface Binding {
  botAvailable: boolean
  bound: boolean
  maskedWecomUserId?: string
}

const auth = useAuthStore()
const binding = ref<Binding>()
const error = ref('')

onMounted(async () => {
  try { binding.value = await api<Binding>('/api/v1/wecom/aibot/binding') }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '企业微信身份状态读取失败' }
})

async function logout() {
  await auth.logout()
  window.location.assign('/login')
}
</script>

<template>
  <main class="wecom-launcher">
    <section class="launcher-shell">
      <header class="launcher-header">
        <div class="brand-mark">Y</div>
        <div><p class="eyebrow">YINGGE × WECOM</p><h1>英格云企业微信入口</h1><p>完整 CRM 与快捷成单分开进入，业务权限与英格云保持一致。</p></div>
      </header>

      <p v-if="error" class="state-card error-state">{{ error }}</p>
      <section v-else class="identity-card">
        <span class="wecom-mark">企</span>
        <div><strong>{{ auth.user?.displayName || '英格云用户' }}</strong><p>{{ binding?.bound ? `已绑定 ${binding.maskedWecomUserId || '企业微信账号'}` : '企业微信机器人尚未绑定' }}</p></div>
        <RouterLink class="identity-action" to="/settings">{{ binding?.bound ? '管理绑定' : '去绑定' }}</RouterLink>
      </section>

      <div class="entry-grid">
        <RouterLink class="entry-card crm-card" to="/dashboard">
          <span class="entry-icon">云</span><p class="eyebrow">FULL CRM</p><h2>进入完整 CRM</h2>
          <p>客户、时间线、提醒、任务、日报、公共池、内容与管理权限均继续使用原系统。</p><strong>打开完整系统 →</strong>
        </RouterLink>
        <RouterLink v-if="auth.canWriteCustomers" class="entry-card deal-card" to="/customers">
          <span class="entry-icon">单</span><p class="eyebrow">QUICK DEAL</p><h2>客户与成单</h2>
          <p>搜索或建档客户，打开档案后发起成单申请；服务端继续执行幂等和审核。</p><strong>进入客户列表 →</strong>
        </RouterLink>
        <section v-else class="entry-card disabled-card">
          <span class="entry-icon">锁</span><p class="eyebrow">QUICK DEAL</p><h2>客户与成单</h2><p>当前账号为只读角色，无法发起成单。</p>
        </section>
      </div>

      <div class="secondary-actions"><RouterLink to="/settings">账号设置</RouterLink><button @click="logout">退出英格云</button></div>
    </section>
  </main>
</template>

<style scoped>
.wecom-launcher{min-height:100dvh;padding:clamp(20px,5vw,64px);background:radial-gradient(circle at 88% 4%,#dff8ed 0,transparent 34%),radial-gradient(circle at 4% 92%,#e8f0ff 0,transparent 38%),#f5f8f7;color:#14213a}.launcher-shell{width:min(980px,100%);margin:auto}.launcher-header{display:flex;align-items:center;gap:18px;margin-bottom:28px}.brand-mark{display:grid;place-items:center;width:64px;height:64px;border-radius:21px;color:#fff;font-size:28px;font-weight:800;background:linear-gradient(145deg,#3375e8,#20b77a);box-shadow:0 18px 40px #167d6530}.launcher-header h1{margin:4px 0 8px;font-size:clamp(30px,5vw,52px);letter-spacing:-.04em}.launcher-header p{margin:0;color:#65718b}.eyebrow{margin:0;color:#16805c!important;font-size:12px;font-weight:800;letter-spacing:.18em}.identity-card,.state-card{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:14px;padding:18px 20px;margin-bottom:18px;border:1px solid #d7e8e0;border-radius:20px;background:#ffffffdb;box-shadow:0 14px 36px #40527f10}.identity-card p{margin:4px 0 0;color:#69758c}.wecom-mark{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;color:#fff;font-weight:800;background:linear-gradient(145deg,#20b77a,#087f5b)}.identity-action{padding:8px 12px;border-radius:999px;color:#087d62;background:#e5faf4;text-decoration:none;font-weight:700}.entry-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:18px}.entry-card{display:grid;align-content:start;min-height:300px;padding:30px;border:1px solid #dbe5e0;border-radius:28px;color:inherit;text-decoration:none;box-shadow:0 24px 60px #37517b12;transition:transform .18s ease,box-shadow .18s ease}.entry-card:hover{transform:translateY(-3px);box-shadow:0 28px 68px #37517b20}.crm-card{background:linear-gradient(145deg,#fff,#eff8f4)}.deal-card{background:linear-gradient(145deg,#102f29,#176e58);color:#fff;border-color:#1d725d}.deal-card .eyebrow{color:#a9f0d7!important}.entry-icon{display:grid;place-items:center;width:54px;height:54px;margin-bottom:36px;border-radius:18px;background:#e1f5ec;color:#16785a;font-weight:850;font-size:21px}.deal-card .entry-icon{background:#ffffff1c;color:#fff}.entry-card h2{font-size:28px;margin:8px 0 12px}.entry-card>p:not(.eyebrow){min-height:70px;margin:0 0 28px;color:#69758c;line-height:1.7}.deal-card>p:not(.eyebrow){color:#c5e9dc}.entry-card>strong{margin-top:auto}.disabled-card{background:#f3f4f7;color:#828ba0}.secondary-actions{display:flex;justify-content:flex-end;gap:18px;margin-top:20px}.secondary-actions a,.secondary-actions button{border:0;background:none;color:#55627a;font:inherit;text-decoration:none;cursor:pointer}.error-state{display:block;color:#b23838}@media(max-width:680px){.wecom-launcher{padding:calc(18px + env(safe-area-inset-top)) 16px calc(28px + env(safe-area-inset-bottom))}.launcher-header{align-items:flex-start}.brand-mark{width:52px;height:52px;border-radius:17px}.entry-grid{grid-template-columns:1fr}.entry-card{min-height:250px;padding:24px}.entry-icon{margin-bottom:24px}.identity-card{grid-template-columns:auto 1fr}.identity-action{grid-column:2;justify-self:start}.entry-card h2{font-size:25px}}@media(prefers-reduced-motion:reduce){.entry-card{transition:none}}
</style>
