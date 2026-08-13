<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ApiError, api, jsonBody } from '../lib/api'

interface Settings {
  enabled: boolean; autoProfileAnalysis: boolean; autoTimelineAnalysis: boolean
  embeddingBaseUrl: string; embeddingModel: string
  recentInteractionLimit: number; semanticChunkLimit: number
  backfillEnabled: boolean; backfillPaused: boolean; nightlyRatePerMinute: number
  pendingJobs: number; completedCustomers: number; totalCustomers: number
  updatedAt?: string; version: number
}

const settings = ref<Settings>()
const form = reactive({
  enabled: true, autoProfileAnalysis: true, autoTimelineAnalysis: true,
  embeddingBaseUrl: 'http://embedding:8080', embeddingModel: 'bge-m3',
  recentInteractionLimit: 10, semanticChunkLimit: 12,
  backfillEnabled: true, backfillPaused: false, nightlyRatePerMinute: 10,
})
const busy = ref(false)
const error = ref('')
const success = ref('')

function apply(value: Settings) {
  settings.value = value
  Object.assign(form, {
    enabled: value.enabled, autoProfileAnalysis: value.autoProfileAnalysis,
    autoTimelineAnalysis: value.autoTimelineAnalysis,
    embeddingBaseUrl: value.embeddingBaseUrl, embeddingModel: value.embeddingModel,
    recentInteractionLimit: value.recentInteractionLimit,
    semanticChunkLimit: value.semanticChunkLimit, backfillEnabled: value.backfillEnabled,
    backfillPaused: value.backfillPaused, nightlyRatePerMinute: value.nightlyRatePerMinute,
  })
}

async function load() {
  try { apply(await api<Settings>('/api/v1/admin/customer-intelligence/settings')) }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '客户智能分析配置加载失败' }
}

async function save() {
  if (!settings.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    apply(await api<Settings>('/api/v1/admin/customer-intelligence/settings', {
      method: 'PUT', body: jsonBody({ ...form, expectedVersion: settings.value.version }),
    }))
    success.value = '客户智能分析策略已保存。'
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '配置保存失败'
  } finally { busy.value = false }
}

async function toggleBackfill(paused: boolean) {
  busy.value = true; error.value = ''; success.value = ''
  try {
    apply(await api<Settings>(`/api/v1/admin/customer-intelligence/backfill/${paused ? 'pause' : 'resume'}`, { method: 'POST' }))
    success.value = paused ? '历史客户回填已暂停。' : '历史客户回填已恢复，仅会在 00:00–06:00 处理。'
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '无法更新回填状态'
  } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page customer-intelligence-admin-page">
    <header class="page-header"><div><p class="eyebrow">CUSTOMER INTELLIGENCE CONTROL</p><h1>客户智能分析</h1><p class="muted">管理自动分析、BGE-M3 向量服务与历史回填；分析结果始终只读，不会自动改写客户档案。</p></div></header>
    <p v-if="error" class="notice error">{{ error }}</p><p v-if="success" class="notice success">{{ success }}</p>
    <template v-if="settings">
      <section class="intelligence-admin-kpis">
        <article><span>已生成分析</span><strong>{{ settings.completedCustomers }}</strong><small>/ {{ settings.totalCustomers }} 位客户</small></article>
        <article><span>队列任务</span><strong>{{ settings.pendingJobs }}</strong><small>单工作器串行执行</small></article>
        <article><span>向量模型</span><strong>BGE-M3</strong><small>1024 维 · 本地服务</small></article>
      </section>
      <form class="panel intelligence-admin-form" @submit.prevent="save">
        <div class="setting-toggle-grid">
          <label class="toggle-row"><input v-model="form.enabled" type="checkbox"><span><strong>启用客户智能分析</strong><small>总开关关闭时不再执行新分析。</small></span></label>
          <label class="toggle-row"><input v-model="form.autoProfileAnalysis" type="checkbox"><span><strong>画像更新后自动分析</strong><small>新建客户和关键画像变更触发。</small></span></label>
          <label class="toggle-row"><input v-model="form.autoTimelineAnalysis" type="checkbox"><span><strong>时间线更新后自动分析</strong><small>30 秒去抖，密集录入只执行最新一次。</small></span></label>
          <label class="toggle-row"><input v-model="form.backfillEnabled" type="checkbox"><span><strong>历史客户回填</strong><small>仅在北京时间 00:00–06:00 运行。</small></span></label>
        </div>
        <div class="form-grid two">
          <label>向量服务地址<input v-model.trim="form.embeddingBaseUrl" type="url" required></label>
          <label>向量模型<input v-model.trim="form.embeddingModel" required></label>
          <label>最近时间线数<input v-model.number="form.recentInteractionLimit" type="number" min="1" max="30" required></label>
          <label>语义检索块数<input v-model.number="form.semanticChunkLimit" type="number" min="1" max="30" required></label>
          <label>夜间每分钟回填客户数<input v-model.number="form.nightlyRatePerMinute" type="number" min="1" max="10" required></label>
        </div>
        <div class="intelligence-admin-note"><strong>数据边界</strong><p>语义检索始终同时限定组织和客户 ID；未确认的信息必须显式标记，每个版本保留证据引用。</p></div>
        <footer class="form-actions"><button type="button" class="ghost-button" :disabled="busy" @click="toggleBackfill(!form.backfillPaused)">{{ form.backfillPaused ? '恢复回填' : '暂停回填' }}</button><button class="primary-button" :disabled="busy">{{ busy ? '正在保存…' : '保存分析策略' }}</button></footer>
      </form>
    </template>
  </div>
</template>
