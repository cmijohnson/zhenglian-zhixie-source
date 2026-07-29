<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import CustomerCard from '../components/CustomerCard.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api } from '../lib/api'
import { customerQualityCode, isCustomerQualityTag } from '../lib/customerQuality'
import { dateTime } from '../lib/format'
import { useAuthStore } from '../stores/auth'
import type { Customer, PageResponse } from '../types'

type CustomerViewMode = 'table' | 'cards'
interface TeamOption { id: string; name: string }
interface MemberOption { membershipId: string; displayName: string; status: string; roles: string[]; teamNames?: string }
interface DictionaryOption { type: string; label: string }

const auth = useAuthStore()
const result = ref<PageResponse<Customer>>()
const teams = ref<TeamOption[]>([])
const members = ref<MemberOption[]>([])
const dictionarySchools = ref<string[]>([])
const teamLoadFailed = ref(false)
const search = ref('')
const status = ref('')
const intention = ref('')
const grade = ref('')
const schoolFilter = ref('')
const teamFilter = ref('')
const ownerFilter = ref('')
const sort = ref('updatedDesc')
const gradeOptions = ['2024', '2025', '2026', '2027', '2028']
const includeArchived = ref(false)
const page = ref(0)
const busy = ref(false)
let timer: number | undefined

const preferenceKey = computed(() => `yingge.customers.view-mode:${auth.user?.membershipId ?? auth.user?.userId ?? 'unknown'}`)

function initialViewMode(): CustomerViewMode {
  if (!auth.isAdmin) return 'cards'
  try { return window.localStorage.getItem(preferenceKey.value) === 'cards' ? 'cards' : 'table' }
  catch { return 'table' }
}

const viewMode = ref<CustomerViewMode>(initialViewMode())
const pageSize = computed(() => auth.isAdmin && viewMode.value === 'table' ? 50 : 30)
const schoolOptions = computed(() => {
  const visibleSchools = result.value?.items.flatMap((customer) => customer.school ? [customer.school] : []) ?? []
  return [...new Set([...dictionarySchools.value, ...visibleSchools])]
    .sort((left, right) => left.localeCompare(right, 'zh-CN'))
})
const activeOwners = computed(() => members.value
  .filter((member) => member.status === 'ACTIVE')
  .sort((left, right) => left.displayName.localeCompare(right.displayName, 'zh-CN')))
const hasAdminFilters = computed(() => Boolean(schoolFilter.value || teamFilter.value || ownerFilter.value))

function teamName(teamId?: string) {
  if (!teamId) return '未分组'
  if (teamLoadFailed.value) return '团队信息暂不可用'
  return teams.value.find((team) => team.id === teamId)?.name ?? '历史/已删除团队'
}

function reviewName(value: Customer['leadReviewStatus']) {
  if (value === 'VALID') return '有效线索'
  if (value === 'INVALID') return '无效线索'
  return '待审核'
}

function friendStageName(customer: Customer) {
  if (customer.friendQualifiedAt) return '已筛选'
  if (customer.friendTrustedAt) return '已信任'
  if (customer.friendAddedAt) return '已添加'
  return '待添加'
}

function contactSummary(customer: Customer) {
  return [customer.phone, customer.wechatId, customer.qqId].filter(Boolean).join(' / ') || '未填写联系方式'
}

function customerContacts(customer: Customer) {
  return [
    customer.phone ? { channel: '手机', value: customer.phone } : undefined,
    customer.wechatId ? { channel: '微信', value: customer.wechatId } : undefined,
    customer.qqId ? { channel: 'QQ', value: customer.qqId } : undefined,
  ].filter((contact): contact is { channel: string; value: string } => Boolean(contact))
}

function sheetTone(value?: string) {
  if (!value) return 'sheet-pill-gray'
  const tones = ['mint', 'rose', 'blue', 'amber', 'violet']
  const score = [...value].reduce((sum, character) => sum + character.charCodeAt(0), 0)
  return `sheet-pill-${tones[score % tones.length]}`
}

function followUpSummary(customer: Customer) {
  return customer.ownerNotes || customer.demandSummary || '暂无跟进摘要'
}

function qualityTag(customer: Customer) {
  return customer.tags.find(isCustomerQualityTag)
}

async function load() {
  busy.value = true
  const params = new URLSearchParams({
    page: String(page.value),
    size: String(pageSize.value),
    sort: sort.value,
  })
  if (search.value) params.set('search', search.value)
  if (status.value) params.set('status', status.value)
  if (intention.value) params.set('intention', intention.value)
  if (grade.value) params.set('grade', grade.value)
  if (schoolFilter.value) params.set('school', schoolFilter.value)
  if (teamFilter.value) params.set('teamId', teamFilter.value)
  if (ownerFilter.value) params.set('ownerMembershipId', ownerFilter.value)
  if (includeArchived.value) params.set('includeArchived', 'true')
  try { result.value = await api<PageResponse<Customer>>(`/api/v1/customers?${params}`) }
  finally { busy.value = false }
}

async function loadAdminFilters() {
  const [teamResult, memberResult, dictionaryResult] = await Promise.allSettled([
    api<TeamOption[]>('/api/v1/admin/teams'),
    api<MemberOption[]>('/api/v1/admin/members'),
    api<DictionaryOption[]>('/api/v1/dictionaries'),
  ])
  if (teamResult.status === 'fulfilled' && Array.isArray(teamResult.value)) {
    teams.value = teamResult.value
    teamLoadFailed.value = false
  } else {
    teams.value = []
    teamLoadFailed.value = true
  }
  members.value = memberResult.status === 'fulfilled' && Array.isArray(memberResult.value) ? memberResult.value : []
  dictionarySchools.value = dictionaryResult.status === 'fulfilled' && Array.isArray(dictionaryResult.value)
    ? dictionaryResult.value.filter((item) => item.type === 'SCHOOL').map((item) => item.label)
    : []
}

function setViewMode(nextMode: CustomerViewMode) {
  if (!auth.isAdmin || viewMode.value === nextMode) return
  viewMode.value = nextMode
  try { window.localStorage.setItem(preferenceKey.value, nextMode) } catch { /* Browser storage may be disabled. */ }
  page.value = 0
  load()
}

function clearAdminFilters() {
  schoolFilter.value = ''
  teamFilter.value = ''
  ownerFilter.value = ''
}

watch([search, schoolFilter], () => {
  page.value = 0
  window.clearTimeout(timer)
  timer = window.setTimeout(load, 250)
})
watch([status, intention, grade, includeArchived, sort, teamFilter, ownerFilter], () => { page.value = 0; load() })
onMounted(async () => {
  const requests: Promise<unknown>[] = [load()]
  if (auth.isAdmin) requests.push(loadAdminFilters())
  await Promise.all(requests)
})
</script>

<template>
  <div class="page customer-directory-page">
    <header class="page-header">
      <div><p class="eyebrow">CUSTOMER DIRECTORY</p><h1>客户档案</h1><p class="muted">仅展示当前账户有权访问的数据。</p></div>
      <div class="customer-header-actions">
        <div v-if="auth.isAdmin" class="view-switch" data-testid="customer-view-switch" aria-label="客户展示模式">
          <button type="button" :class="{ active: viewMode === 'table' }" :aria-pressed="viewMode === 'table'" @click="setViewMode('table')">表格</button>
          <button type="button" :class="{ active: viewMode === 'cards' }" :aria-pressed="viewMode === 'cards'" @click="setViewMode('cards')">卡片</button>
        </div>
        <RouterLink to="/customers/new" class="primary-button">＋ 新建客户</RouterLink>
      </div>
    </header>
    <section class="toolbar customer-toolbar panel-flat">
      <div class="customer-search-group">
        <input v-model.trim="search" aria-label="搜索客户" placeholder="搜索姓名、学校、手机、微信或 QQ" @keyup.enter="page = 0; load()">
        <button type="button" class="customer-search-button" aria-label="执行客户搜索" title="搜索" @click="page = 0; load()">⌕</button>
      </div>
      <select v-model="status" aria-label="客户状态"><option value="">全部状态</option><option>新客户</option><option>跟进中</option><option>已转化</option><option>暂缓</option></select>
      <select v-model="intention" aria-label="意向级别"><option value="">全部意向</option><option>高</option><option>中</option><option>低</option><option>未知</option></select>
      <select v-model="sort" aria-label="客户排序"><option value="updatedDesc">最近更新</option><option value="createdDesc">最近创建</option><option value="lastContactDesc">最近沟通</option><option value="nextFollowUpAsc">下次跟进</option></select>
      <label class="check-label"><input v-model="includeArchived" type="checkbox">包含归档</label>
    </section>
    <section class="grade-quick-filter panel-flat" data-testid="grade-filter" aria-label="按年级快速筛选">
      <span>年级快捷筛选</span>
      <div>
        <button :class="{ active: !grade }" type="button" @click="grade = ''">全部</button>
        <button v-for="option in gradeOptions" :key="option" :class="{ active: grade === option }" type="button" @click="grade = option">{{ option }}</button>
      </div>
      <small>自动兼容“{{ grade || '2026' }}级 / {{ (grade || '2026').slice(2) }}级”等写法</small>
    </section>
    <section v-if="auth.isAdmin && viewMode === 'table'" class="admin-sheet-filters panel-flat" data-testid="customer-admin-filters">
      <label>院校
        <input v-model.trim="schoolFilter" list="customer-school-options" placeholder="输入或选择院校" aria-label="筛选院校">
        <datalist id="customer-school-options"><option v-for="school in schoolOptions" :key="school" :value="school" /></datalist>
      </label>
      <label>团队
        <select v-model="teamFilter" aria-label="筛选团队"><option value="">全部团队</option><option v-for="team in teams" :key="team.id" :value="team.id">{{ team.name }}</option></select>
      </label>
      <label>负责人
        <select v-model="ownerFilter" aria-label="筛选负责人"><option value="">全部负责人</option><option v-for="member in activeOwners" :key="member.membershipId" :value="member.membershipId">{{ member.displayName }}{{ member.teamNames ? ` · ${member.teamNames}` : '' }}</option></select>
      </label>
      <button type="button" class="ghost-button" :disabled="!hasAdminFilters" @click="clearAdminFilters">清除表格筛选</button>
    </section>
    <div class="result-meta"><span>{{ result?.totalElements ?? 0 }} 位客户 · 每页 {{ pageSize }} 条</span><span v-if="busy">正在更新…</span></div>

    <section v-if="viewMode === 'table' && result?.items.length" class="panel customer-table-panel table-wrap" data-testid="customer-table">
      <table class="customer-admin-table">
        <thead><tr><th>客户</th><th>院校</th><th>专业 / 年级</th><th>来源</th><th>负责人</th><th>联系方式</th><th>团队 / 录入人</th><th>意向 / 线索审核</th><th>客户状态</th><th>好友进度</th><th>跟进摘要</th><th>最近沟通</th><th>下次跟进</th><th>更新时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="customer in result.items" :key="customer.id" :class="{ archived: customer.archivedAt }">
            <td class="customer-name-cell">
              <RouterLink :to="`/customers/${customer.id}`" class="customer-table-name"><span class="avatar small">{{ customer.fullName.slice(0, 1) }}</span><span><strong :title="customer.fullName">{{ customer.fullName }}</strong><small :title="customer.nickname">{{ customer.nickname || '无昵称' }}</small><em v-if="qualityTag(customer)" :class="['customer-quality-inline', `quality-${customerQualityCode(qualityTag(customer)).toLowerCase()}`]">{{ qualityTag(customer)?.name }}</em><em v-else class="customer-quality-inline quality-unset">未评级</em></span></RouterLink>
            </td>
            <td><span :class="['sheet-pill', sheetTone(customer.school)]" :title="customer.school">{{ customer.school || '院校未填' }}</span></td>
            <td><div class="customer-table-stack"><span :class="['sheet-pill', sheetTone(customer.major)]" :title="customer.major">{{ customer.major || '专业未填' }}</span><small>{{ customer.grade || '年级未填' }}</small></div></td>
            <td><span :class="['sheet-pill', sheetTone(customer.sourceChannel)]" :title="customer.sourceChannel">{{ customer.sourceChannel || '来源未填' }}</span></td>
            <td><span :class="['sheet-pill', sheetTone(customer.ownerDisplayName)]" :title="customer.ownerDisplayName">{{ customer.ownerDisplayName || '负责人未知' }}</span></td>
            <td :title="contactSummary(customer)">
              <div v-if="customerContacts(customer).length" class="customer-table-stack contact-stack">
                <span v-for="(contact, index) in customerContacts(customer)" :key="contact.channel" :class="{ 'contact-primary': index === 0 }">{{ contact.channel }} {{ contact.value }}</span>
              </div>
              <span v-else class="contact-empty">暂无联系方式</span>
            </td>
            <td><div class="customer-table-stack"><span class="team-pill" :title="teamName(customer.teamId)">{{ teamName(customer.teamId) }}</span><small :title="customer.creatorDisplayName">录入：{{ customer.creatorDisplayName || '未知' }}</small></div></td>
            <td><div class="customer-table-badges"><StatusBadge :value="`${customer.intentionLevel}意向`" :tone="customer.intentionLevel === '高' ? 'green' : 'blue'" /><StatusBadge :value="reviewName(customer.leadReviewStatus)" :tone="customer.leadReviewStatus === 'VALID' ? 'green' : customer.leadReviewStatus === 'INVALID' ? 'red' : 'amber'" /></div></td>
            <td><div class="customer-table-stack"><strong>{{ customer.currentStatus || '状态未填' }}</strong><small v-if="customer.archivedAt" class="archived-label">已归档</small><small v-else>{{ customer.verificationStatus || '核实状态未填' }}</small></div></td>
            <td><span :class="['friend-stage-pill', { trusted: customer.friendTrustedAt, qualified: customer.friendQualifiedAt }]">{{ friendStageName(customer) }}</span></td>
            <td class="follow-up-cell" :title="followUpSummary(customer)">{{ followUpSummary(customer) }}</td>
            <td class="date-cell">{{ dateTime(customer.lastContactAt) }}</td>
            <td class="date-cell">{{ dateTime(customer.nextFollowUpAt) }}</td>
            <td class="date-cell">{{ dateTime(customer.updatedAt) }}</td>
            <td><div class="customer-table-actions"><RouterLink :to="`/customers/${customer.id}`" class="text-button">查看</RouterLink><RouterLink :to="`/customers/${customer.id}/edit`" class="text-button">编辑</RouterLink></div></td>
          </tr>
        </tbody>
      </table>
    </section>
    <div v-else-if="viewMode === 'cards' && result?.items.length" class="card-grid customer-list"><CustomerCard v-for="customer in result.items" :key="customer.id" :customer="customer" /></div>
    <div v-else-if="!busy" class="empty-state panel">没有符合条件的客户。可以调整筛选，或创建第一份档案。</div>
    <div v-if="result && result.totalPages > 1" class="pagination">
      <button :disabled="page === 0" @click="page--; load()">上一页</button><span>{{ page + 1 }} / {{ result.totalPages }}</span><button :disabled="page + 1 >= result.totalPages" @click="page++; load()">下一页</button>
    </div>
  </div>
</template>
