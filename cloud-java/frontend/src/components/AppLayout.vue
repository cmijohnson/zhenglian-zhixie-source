<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const primaryLinks = computed(() => [
  { to: '/dashboard', label: '工作台', icon: '▦', show: true },
  { to: '/customers', label: '客户档案', icon: '◉', show: true },
  { to: '/reminders', label: '跟进提醒', icon: '◷', show: true },
  { to: '/tasks', label: '任务中心', icon: '☑', show: true },
  { to: '/circle', label: '内容圈子', icon: '◎', show: true },
  { to: '/announcements', label: '公告中心', icon: '◫', show: true },
  { to: '/leadership-dashboard', label: '领导大屏', icon: '◈', show: auth.canViewTeamData },
  { to: '/team-data', label: '团队数据', icon: '◩', show: auth.canViewTeamData },
])
const dailyExecutionLinks = [
  { to: '/performance', label: '绩效中心', icon: '◆', show: true },
  { to: '/consultations', label: '咨询交接', icon: '⇄', show: true },
  { to: '/daily-report', label: '每日汇报', icon: '≡', show: true },
  { to: '/content-tasks', label: '内容任务', icon: '◈', show: true },
]
const toolLinks = computed(() => [
  { to: '/assistant', label: '智能助手', icon: '✦', show: true },
  { to: '/qq-chat', label: 'QQ 对话', icon: '◌', show: auth.canWriteCustomers },
  { to: '/agent', label: '我的 Agent', icon: '⌘', show: true },
  { to: '/imports', label: '批量导入', icon: '⇥', show: auth.canBulkTransfer },
  { to: '/exports', label: '数据导出', icon: '⇤', show: auth.canBulkTransfer },
])

async function logout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">Y</span>
        <div><strong>英格客户云</strong><small>营销团队工作台</small></div>
      </div>
      <nav class="nav-list">
        <RouterLink v-for="link in primaryLinks.filter((item) => item.show)" :key="link.to" :to="link.to">
          <span>{{ link.icon }}</span>{{ link.label }}
        </RouterLink>
        <div class="nav-caption">每日执行</div>
        <RouterLink v-for="link in dailyExecutionLinks" :key="link.to" :to="link.to">
          <span>{{ link.icon }}</span>{{ link.label }}
        </RouterLink>
        <div class="nav-caption">效率工具</div>
        <RouterLink v-for="link in toolLinks.filter((item) => item.show)" :key="link.to" :to="link.to">
          <span>{{ link.icon }}</span>{{ link.label }}
        </RouterLink>
        <div v-if="auth.isAdmin" class="nav-caption">组织管理</div>
        <RouterLink v-if="auth.isAdmin" to="/admin/members"><span>♙</span>成员账户</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/teams"><span>◇</span>团队设置</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/dictionaries"><span>⌘</span>字典标签</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/audit"><span>≣</span>审计日志</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/assistant"><span>✦</span>智能助手配置</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/leadership-screen"><span>◈</span>大屏设置</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/agents"><span>⌘</span>Agent 管理</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/performance"><span>◆</span>绩效管理</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/follow-up"><span>⇄</span>跟进与咨询设置</RouterLink>
        <RouterLink v-if="auth.isAdmin" to="/admin/customer-enrichment"><span>⊕</span>客户资料补全</RouterLink>
      </nav>
      <div class="sidebar-footer">
        <RouterLink to="/settings" class="profile-link">
          <span class="avatar">{{ auth.user?.displayName.slice(0, 1) }}</span>
          <span><strong>{{ auth.user?.displayName }}</strong><small>{{ auth.user?.roles.join(' · ') }}</small></span>
        </RouterLink>
        <button class="text-button" @click="logout">退出</button>
      </div>
    </aside>
    <main class="main-area"><RouterView /></main>
  </div>
</template>
