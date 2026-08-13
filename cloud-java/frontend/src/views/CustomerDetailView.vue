<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import StatusBadge from '../components/StatusBadge.vue'
import { ApiError, api, jsonBody } from '../lib/api'
import { formatCustomerGrade, isGradeTag } from '../lib/customerProfile'
import { customerQualityCode, customerQualityDescription, isCustomerQualityTag } from '../lib/customerQuality'
import { dateTime, localInput, toIso } from '../lib/format'
import { safeTeamDataReturnTo } from '../lib/teamDataNavigation'
import type { Customer, Interaction, Tag } from '../types'

interface CollaborationMember { membershipId: string; displayName: string; teamNames: string }
interface CollaborationOverview {
  ownerMembershipId: string
  canManage: boolean
  canTransferOwnership: boolean
  canEditProfile: boolean
  canArchive: boolean
  canEditTimeline: boolean
  collaborators: CollaborationMember[]
  candidates: CollaborationMember[]
  transferCandidates: CollaborationMember[]
}
interface IntelligenceEvidence { sourceType: string; sourceKey: string; sourceAt?: string; excerpt: string }
interface IntelligenceVersion {
  id: string; versionNumber: number; triggerType: string; status: string
  result?: Record<string, unknown>; narrative?: string; evidence: IntelligenceEvidence[]
  provider?: string; model?: string; sourceWatermark?: string; createdAt: string
}
interface IntelligenceOverview {
  customerId: string; enabled: boolean; processing: boolean; processingStatus?: string
  latest?: IntelligenceVersion
}

const route = useRoute()
const router = useRouter()
const id = String(route.params.id)
const customer = ref<Customer>()
const interactions = ref<Interaction[]>([])
const tags = ref<Tag[]>([])
const collaboration = ref<CollaborationOverview>()
const intelligence = ref<IntelligenceOverview>()
const intelligenceBusy = ref(false)
const panelOpen = ref(false)
const sharingOpen = ref(false)
const transferOpen = ref(false)
const selectedCollaborators = ref<string[]>([])
const busy = ref(false)
const sharingBusy = ref(false)
const transferBusy = ref(false)
const error = ref('')
const transfer = reactive({ targetMembershipId: '', reason: '' })
const record = reactive({ contactedAt: localInput(), channel: '微信', summary: '', details: '', outcome: '', verificationTodo: '', nextFollowUpAt: '', currentStatus: '', intentionLevel: '', verificationStatus: '', tagIds: [] as string[] })
const contact = computed(() => [customer.value?.phone, customer.value?.wechatId, customer.value?.qqId].filter(Boolean).join(' · '))
const qualityTags = computed(() => tags.value.filter(isCustomerQualityTag))
const regularTags = computed(() => tags.value.filter((tag) => !isCustomerQualityTag(tag)))
const customerQualityTag = computed(() => customer.value?.tags.find(isCustomerQualityTag))
const visibleCustomerTags = computed(() => customer.value?.tags
  .filter((tag) => !isGradeTag(tag) && !isCustomerQualityTag(tag)) ?? [])
const recordQualityTagId = computed({
  get: () => record.tagIds.find((tagId) => qualityTags.value.some((tag) => tag.id === tagId)) ?? '',
  set: (value: string) => {
    const qualityIds = new Set(qualityTags.value.map((tag) => tag.id))
    record.tagIds = record.tagIds.filter((tagId) => !qualityIds.has(tagId))
    if (value) record.tagIds.push(value)
  },
})
const friendStage = computed(() => {
  if (customer.value?.friendQualifiedAt) return '已筛选'
  if (customer.value?.friendTrustedAt) return '已信任'
  if (customer.value?.friendAddedAt) return '已添加'
  return '待添加'
})
const teamDataReturnTo = computed(() => safeTeamDataReturnTo(route.query.returnTo))
const parentLabel = computed(() => teamDataReturnTo.value ? '返回团队数据' : '返回客户列表')
const editLocation = computed(() => ({
  path: `/customers/${id}/edit`,
  query: teamDataReturnTo.value ? { returnTo: teamDataReturnTo.value } : {},
}))
const selectedTransferTarget = computed(() => collaboration.value?.transferCandidates
  .find((member) => member.membershipId === transfer.targetMembershipId))
const intelligenceResult = computed(() => intelligence.value?.latest?.result ?? {})

function intelligenceList(key: string) {
  const value = intelligenceResult.value[key]
  if (Array.isArray(value)) return value.map(String).filter(Boolean)
  if (typeof value === 'string' && value.trim()) return [value]
  return []
}

async function loadIntelligence() {
  try { intelligence.value = await api<IntelligenceOverview>(`/api/v1/customers/${id}/intelligence`) }
  catch { intelligence.value = undefined }
}

async function load() {
  const [loadedCustomer, loadedInteractions, loadedTags, loadedCollaboration] = await Promise.all([
    api<Customer>(`/api/v1/customers/${id}`), api<Interaction[]>(`/api/v1/customers/${id}/interactions`), api<Tag[]>('/api/v1/tags'),
    api<CollaborationOverview>(`/api/v1/customers/${id}/collaboration`),
  ])
  customer.value = loadedCustomer
  interactions.value = loadedInteractions
  tags.value = loadedTags.filter((tag) => !isGradeTag(tag))
  collaboration.value = loadedCollaboration
  selectedCollaborators.value = loadedCollaboration.collaborators.map((member) => member.membershipId)
  record.tagIds = loadedCustomer.tags.filter((tag) => tags.value.some((option) => option.id === tag.id)).map((tag) => tag.id)
  await loadIntelligence()
}

async function reanalyze() {
  intelligenceBusy.value = true
  error.value = ''
  try {
    intelligence.value = await api<IntelligenceOverview>(`/api/v1/customers/${id}/intelligence/reanalyze`, { method: 'POST' })
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '无法发起客户智能分析'
  } finally { intelligenceBusy.value = false }
}

function openSharing() {
  selectedCollaborators.value = collaboration.value?.collaborators.map((member) => member.membershipId) ?? []
  error.value = ''
  sharingOpen.value = true
}

function openTransfer() {
  transfer.targetMembershipId = ''
  transfer.reason = ''
  error.value = ''
  transferOpen.value = true
}

async function returnToParent() {
  const target = teamDataReturnTo.value ?? '/customers'
  if (router.options.history.state.back === target) {
    router.back()
    return
  }
  await router.replace(target)
}

async function saveSharing() {
  sharingBusy.value = true
  error.value = ''
  try {
    collaboration.value = await api<CollaborationOverview>(`/api/v1/customers/${id}/collaborators`, {
      method: 'PUT', body: jsonBody({ membershipIds: selectedCollaborators.value }),
    })
    selectedCollaborators.value = collaboration.value.collaborators.map((member) => member.membershipId)
    sharingOpen.value = false
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '客户共享保存失败'
  } finally { sharingBusy.value = false }
}

async function saveInteraction() {
  busy.value = true; error.value = ''
  try {
    await api(`/api/v1/customers/${id}/interactions`, { method: 'POST', body: jsonBody({ ...record, contactedAt: toIso(record.contactedAt), nextFollowUpAt: toIso(record.nextFollowUpAt) }) })
    panelOpen.value = false
    Object.assign(record, { contactedAt: localInput(), summary: '', details: '', outcome: '', verificationTodo: '', nextFollowUpAt: '', currentStatus: '', intentionLevel: '', verificationStatus: '' })
    await load()
  } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '沟通记录保存失败' }
  finally { busy.value = false }
}

async function transferCustomer() {
  if (!customer.value || !selectedTransferTarget.value) {
    error.value = '请选择客户接收人'
    return
  }
  const target = selectedTransferTarget.value
  if (!window.confirm(`确认把“${customer.value.fullName}”从“${customer.value.ownerDisplayName}”转接给“${target.displayName}”？待办提醒将同时移交。`)) return
  transferBusy.value = true
  error.value = ''
  try {
    await api<Customer>(`/api/v1/customers/${id}/transfer`, {
      method: 'POST',
      body: jsonBody({
        targetMembershipId: target.membershipId,
        expectedVersion: customer.value.version,
        reason: transfer.reason || undefined,
      }),
    })
    transferOpen.value = false
    await returnToParent()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '客户转接失败'
  } finally { transferBusy.value = false }
}

async function archiveCustomer() {
  if (!window.confirm('确认归档该客户？所有未完成提醒将同时取消。')) return
  await api(`/api/v1/customers/${id}/archive`, { method: 'POST' })
  await returnToParent()
}

onMounted(load)
</script>

<template>
  <div v-if="customer" class="page">
    <header class="profile-header panel">
      <div class="profile-identity"><span class="avatar large">{{ customer.fullName.slice(0, 1) }}</span><div><div class="title-line"><h1>{{ customer.fullName }}</h1><span v-if="customerQualityTag" :class="['customer-quality-badge', `quality-${customerQualityCode(customerQualityTag).toLowerCase()}`]"><b>{{ customerQualityCode(customerQualityTag) }}</b>{{ customerQualityTag.name }}</span><StatusBadge v-else value="未评级" tone="gray" /><StatusBadge :value="customer.intentionLevel + '意向'" :tone="customer.intentionLevel === '高' ? 'green' : 'blue'" /><StatusBadge :value="customer.currentStatus" /><StatusBadge :value="friendStage" :tone="customer.friendQualifiedAt ? 'green' : customer.friendAddedAt ? 'blue' : 'gray'" /></div><p>{{ contact || '无联系方式' }}</p><p class="muted">{{ customer.school || '学校未补充' }} · {{ customer.major || '专业未补充' }} · {{ formatCustomerGrade(customer.grade) || '年级未补充' }} · {{ customer.focusDirections.join(' / ') }}</p></div></div>
      <div class="header-actions"><button class="ghost-button" data-testid="customer-parent-return" @click="returnToParent">{{ parentLabel }}</button><button v-if="collaboration?.canTransferOwnership && !customer.archivedAt" class="ghost-button" data-testid="transfer-customer" @click="openTransfer">转接客户</button><button v-if="collaboration?.canManage && !customer.archivedAt" class="ghost-button" data-testid="share-customer" @click="openSharing">共享跟进</button><button v-if="collaboration?.canEditTimeline && !customer.archivedAt" class="ghost-button" @click="router.push({ path: '/consultations', query: { customerId: id } })">发起咨询交接</button><button v-if="collaboration?.canEditTimeline && !customer.archivedAt" class="ghost-button" @click="router.push({ path: '/qq-chat', query: { customerId: id } })">导入 QQ 对话</button><button v-if="collaboration?.canEditProfile && !customer.archivedAt" class="ghost-button" data-testid="edit-customer" @click="router.push(editLocation)">编辑画像</button><a :href="`/api/v1/exports/customers/${id}/timeline?format=xlsx`" class="ghost-button">导出时间线</a><button v-if="collaboration?.canEditTimeline && !customer.archivedAt" class="primary-button" @click="panelOpen = true">＋ 记录沟通</button></div>
    </header>
    <section v-if="intelligence?.enabled" class="panel customer-intelligence-panel" data-testid="customer-intelligence">
      <header class="panel-title">
        <div><p class="eyebrow">CUSTOMER INTELLIGENCE</p><h2>AI 客户洞察</h2><p class="muted">仅根据当前画像和已记录时间线分析，不会自动改写正式档案。</p></div>
        <div class="header-actions">
          <span v-if="intelligence.processing" class="intelligence-processing"><i></i>分析队列中</span>
          <button v-if="collaboration?.canEditTimeline" class="ghost-button" type="button" :disabled="intelligenceBusy || intelligence.processing" @click="reanalyze">{{ intelligenceBusy ? '正在发起…' : '重新分析' }}</button>
        </div>
      </header>
      <template v-if="intelligence.latest">
        <div class="intelligence-summary">
          <span>EXECUTIVE SUMMARY</span><p>{{ intelligence.latest.narrative }}</p>
          <small>版本 {{ intelligence.latest.versionNumber }} · {{ dateTime(intelligence.latest.createdAt) }} · {{ intelligence.latest.model }}</small>
        </div>
        <div class="intelligence-grid">
          <article v-for="section in [
            { key: 'goals', title: '目标与动机' },
            { key: 'progress', title: '已有进展' },
            { key: 'concerns', title: '主要顾虑' },
            { key: 'risks', title: '转化风险' },
            { key: 'recommendedActions', title: '建议动作' },
            { key: 'unresolvedQuestions', title: '待核实事项' },
          ]" :key="section.key">
            <h3>{{ section.title }}</h3>
            <ul v-if="intelligenceList(section.key).length"><li v-for="item in intelligenceList(section.key)" :key="item">{{ item }}</li></ul>
            <p v-else>未确认</p>
          </article>
        </div>
        <details class="intelligence-evidence"><summary>查看分析证据 · {{ intelligence.latest.evidence.length }} 条</summary><div><article v-for="source in intelligence.latest.evidence" :key="source.sourceKey"><b>{{ source.sourceType }}</b><span>{{ dateTime(source.sourceAt) }}</span><p>{{ source.excerpt }}</p></article></div></details>
      </template>
      <div v-else class="empty-state">
        {{ intelligence.processing ? '首次分析正在队列中，结果将以可追溯版本保存。' : '新增客户画像或沟通时间线后，系统将自动生成首次洞察。' }}
      </div>
    </section>
    <div class="profile-grid">
      <aside class="panel profile-facts">
        <div class="panel-title"><div><p class="eyebrow">PROFILE</p><h2>客户画像</h2></div></div>
        <div class="profile-friend-funnel">
          <div :class="{ reached: customer.friendAddedAt }"><b>01</b><span>添加好友</span><small>{{ dateTime(customer.friendAddedAt) }}</small></div>
          <i></i>
          <div :class="{ reached: customer.friendTrustedAt }"><b>02</b><span>建立信任</span><small>{{ dateTime(customer.friendTrustedAt) }}</small></div>
          <i></i>
          <div :class="{ reached: customer.friendQualifiedAt }"><b>03</b><span>筛选确认</span><small>{{ dateTime(customer.friendQualifiedAt) }}</small></div>
        </div>
        <div :class="['profile-quality-card', customerQualityTag ? `quality-${customerQualityCode(customerQualityTag).toLowerCase()}` : 'quality-unset']">
          <span>客户质量</span>
          <strong>{{ customerQualityTag?.name || '未评级' }}</strong>
          <small>{{ customerQualityTag ? customerQualityDescription(customerQualityTag) : '等待团队进一步核实客户需求' }}</small>
        </div>
        <dl><div><dt>主负责人</dt><dd>{{ customer.ownerDisplayName || '未知' }}</dd></div><div><dt>协作跟进</dt><dd class="collaborator-names">{{ collaboration?.collaborators.map((member) => member.displayName).join('、') || '暂无' }}</dd></div><div><dt>录入人</dt><dd>{{ customer.creatorDisplayName || '未知' }}</dd></div><div><dt>身份</dt><dd>{{ customer.identityType }}</dd></div><div><dt>高中学校</dt><dd>{{ customer.highSchool || '—' }}</dd></div><div><dt>学生生源地</dt><dd>{{ customer.sourceLocation || '—' }}</dd></div><div><dt>核实状态</dt><dd>{{ customer.verificationStatus }}</dd></div><div><dt>紧急程度</dt><dd>{{ customer.urgencyLevel }}</dd></div><div><dt>来源</dt><dd>{{ customer.sourceChannel || '—' }}</dd></div><div><dt>下次跟进</dt><dd>{{ dateTime(customer.nextFollowUpAt) }}</dd></div><div><dt>最近沟通</dt><dd>{{ dateTime(customer.lastContactAt) }}</dd></div></dl>
        <div class="fact-block"><span>需求摘要</span><p>{{ customer.demandSummary || '尚未补充需求摘要。' }}</p></div>
        <div class="fact-block"><span>标签</span><div class="tag-list"><i v-for="tag in visibleCustomerTags" :key="tag.id" :style="{ borderColor: tag.color }">{{ tag.name }}</i><small v-if="!visibleCustomerTags.length">暂无标签</small></div></div>
        <div class="fact-block"><span>内部备注</span><p>{{ customer.ownerNotes || '—' }}</p></div>
        <button v-if="collaboration?.canArchive && !customer.archivedAt" class="danger-link" @click="archiveCustomer">归档客户</button>
      </aside>
      <section class="panel timeline-panel">
        <div class="panel-title"><div><p class="eyebrow">TIMELINE</p><h2>沟通时间线</h2></div><span>{{ interactions.length }} 条记录</span></div>
        <div v-if="!interactions.length" class="empty-state">还没有沟通记录。</div>
        <article v-for="item in interactions" :key="item.id" class="timeline-item">
          <div class="timeline-dot"></div><div class="timeline-content"><header><strong>{{ item.channel }} · {{ item.summary }}</strong><time>{{ dateTime(item.contactedAt) }}</time></header><small class="timeline-author">记录人：{{ item.createdByDisplayName || '历史成员' }}</small><p v-if="item.details">{{ item.details }}</p><div v-if="item.outcome" class="timeline-note"><span>沟通结果</span>{{ item.outcome }}</div><div v-if="item.nextFollowUpAt" class="timeline-next">下一次跟进：{{ dateTime(item.nextFollowUpAt) }}</div></div>
        </article>
      </section>
    </div>
    <div v-if="panelOpen" class="drawer-backdrop" @click.self="panelOpen = false">
      <aside class="drawer">
        <header><div><p class="eyebrow">NEW INTERACTION</p><h2>记录沟通</h2></div><button class="icon-button" @click="panelOpen = false">×</button></header>
        <form @submit.prevent="saveInteraction">
          <div class="form-grid two"><label>沟通时间 *<input v-model="record.contactedAt" type="datetime-local" required></label><label>沟通渠道 *<select v-model="record.channel"><option>微信</option><option>电话</option><option>QQ</option><option>面谈</option><option>其他</option></select></label></div>
          <label>摘要 *<input v-model.trim="record.summary" required maxlength="500" placeholder="一句话记录本次沟通重点"></label>
          <label>详细记录<textarea v-model.trim="record.details" rows="5"></textarea></label>
          <label>沟通结果<textarea v-model.trim="record.outcome" rows="2"></textarea></label>
          <label>下次跟进<input v-model="record.nextFollowUpAt" type="datetime-local"></label>
          <details><summary><span>↻</span><div><strong>同步客户信息</strong><small>状态、意向、核实状态、客户质量与标签</small></div></summary><div class="details-body form-grid two"><label>当前状态<select v-model="record.currentStatus"><option value="">保持不变</option><option v-for="value in ['初次接触','待核实','已沟通','跟进中','高意向','待决定','已转化','暂缓','无效']" :key="value">{{ value }}</option></select></label><label>报名意向<select v-model="record.intentionLevel"><option value="">保持不变</option><option>高</option><option>中</option><option>低</option></select></label><label>核实状态<select v-model="record.verificationStatus"><option value="">保持不变</option><option>未核实</option><option>部分待确认</option><option>已核实</option></select></label><div class="span-2 customer-quality-field compact"><div class="quality-field-heading"><span>客户质量</span><small>本次沟通后可同步调整</small></div><div class="customer-quality-picker"><label :class="{ selected: !recordQualityTagId }"><input v-model="recordQualityTagId" type="radio" value=""><b>—</b><span><strong>未评级</strong></span></label><label v-for="tag in qualityTags" :key="tag.id" :class="[`quality-${customerQualityCode(tag).toLowerCase()}`, { selected: recordQualityTagId === tag.id }]"><input v-model="recordQualityTagId" type="radio" :value="tag.id"><b>{{ customerQualityCode(tag) }}</b><span><strong>{{ tag.name }}</strong></span></label></div></div><label class="span-2">其他标签<div class="chip-picker"><label v-for="tag in regularTags" :key="tag.id"><input v-model="record.tagIds" type="checkbox" :value="tag.id"><span>{{ tag.name }}</span></label></div></label></div></details>
          <p v-if="error" class="form-error">{{ error }}</p><footer class="form-actions"><button type="button" class="ghost-button" @click="panelOpen = false">取消</button><button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '完成记录' }}</button></footer>
        </form>
      </aside>
    </div>
    <div v-if="sharingOpen" class="drawer-backdrop" @click.self="sharingOpen = false">
      <aside class="drawer collaboration-drawer" data-testid="collaboration-drawer">
        <header><div><p class="eyebrow">SHARED FOLLOW-UP</p><h2>共享客户跟进</h2><p class="muted">协作者共用这一份客户档案，可编辑客户画像并新增沟通时间线；主负责人保持不变。</p></div><button class="icon-button" @click="sharingOpen = false">×</button></header>
        <div class="collaboration-owner"><span>主负责人</span><strong>{{ customer.ownerDisplayName || '未知' }}</strong></div>
        <div class="collaboration-options">
          <label v-for="member in collaboration?.candidates" :key="member.membershipId">
            <input v-model="selectedCollaborators" type="checkbox" :value="member.membershipId">
            <span><strong>{{ member.displayName }}</strong><small>{{ member.teamNames || '未分组' }}</small></span>
          </label>
          <div v-if="!collaboration?.candidates.length" class="empty-state small">暂无可共享的启用成员。</div>
        </div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <footer class="form-actions"><button type="button" class="ghost-button" @click="sharingOpen = false">取消</button><button type="button" class="primary-button" data-testid="save-collaborators" :disabled="sharingBusy" @click="saveSharing">{{ sharingBusy ? '保存中…' : '保存共享' }}</button></footer>
      </aside>
    </div>
    <div v-if="transferOpen" class="drawer-backdrop" @click.self="transferOpen = false">
      <aside class="drawer collaboration-drawer" data-testid="ownership-transfer-drawer">
        <header><div><p class="eyebrow">OWNERSHIP TRANSFER</p><h2>转接客户所有权</h2><p class="muted">主负责人和待办提醒将移交给接收人；录入人、历史时间线和现有协作者保持不变。</p></div><button class="icon-button" @click="transferOpen = false">×</button></header>
        <div class="collaboration-owner"><span>当前主负责人</span><strong>{{ customer.ownerDisplayName || '未知' }}</strong></div>
        <label>客户接收人 *
          <select v-model="transfer.targetMembershipId" data-testid="ownership-transfer-target">
            <option value="">请选择接收人</option>
            <option v-for="member in collaboration?.transferCandidates" :key="member.membershipId" :value="member.membershipId">{{ member.displayName }}{{ member.teamNames ? ` · ${member.teamNames}` : ' · 未分组' }}</option>
          </select>
        </label>
        <label>转接说明（选填）
          <textarea v-model.trim="transfer.reason" rows="3" maxlength="500" placeholder="例如：客户交接、排班调整或团队协同"></textarea>
        </label>
        <div class="notice"><strong>转接影响</strong><span>私有客户转接后，原负责人可能不再能查看该档案。接收人所在团队将成为客户归属团队。</span></div>
        <div v-if="!collaboration?.transferCandidates.length" class="empty-state small">暂无符合当前权限范围的启用接收人。</div>
        <p v-if="error" class="form-error">{{ error }}</p>
        <footer class="form-actions"><button type="button" class="ghost-button" @click="transferOpen = false">取消</button><button type="button" class="primary-button" data-testid="confirm-ownership-transfer" :disabled="transferBusy || !transfer.targetMembershipId" @click="transferCustomer">{{ transferBusy ? '转接中…' : '确认转接' }}</button></footer>
      </aside>
    </div>
  </div>
  <div v-else class="loading-state">正在加载客户档案…</div>
</template>
