<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, api, jsonBody } from '../lib/api'

const availability = ref<{ available: boolean; provider: string; model: string }>()
const mode = ref<'customer' | 'interaction'>('customer')
const input = ref('')
const result = ref<Record<string, unknown>>()
const briefing = ref('')
const busy = ref(false)
const error = ref('')
onMounted(async () => { availability.value = await api('/api/v1/assistant/availability') })

async function parse() {
  busy.value = true; error.value = ''; result.value = undefined
  const endpoint = mode.value === 'customer' ? 'customer-draft' : 'interaction-draft'
  try { result.value = await api(`/api/v1/assistant/${endpoint}`, { method: 'POST', body: jsonBody({ text: input.value }) }) }
  catch (reason) { error.value = reason instanceof ApiError ? reason.message : '智能助手调用失败' }
  finally { busy.value = false }
}

function streamBriefing() {
  briefing.value = ''; error.value = ''; busy.value = true
  const source = new EventSource('/api/v1/assistant/daily-briefing')
  source.addEventListener('delta', (event) => { briefing.value += (event as MessageEvent).data })
  source.addEventListener('done', () => { source.close(); busy.value = false })
  source.addEventListener('error', () => { source.close(); busy.value = false; if (!briefing.value) error.value = '简报生成中断' })
}
</script>

<template>
  <div class="page narrow-page">
    <header class="page-header"><div><p class="eyebrow">OPTIONAL AI ASSISTANT</p><h1>智能助手</h1><p class="muted">模型只接收已经过权限过滤的最小业务上下文。</p></div><span v-if="availability" :class="['availability', { online: availability.available }]">{{ availability.available ? `${availability.provider} · ${availability.model}` : '模型服务未配置' }}</span></header>
    <div v-if="availability && !availability.available" class="notice"><strong>基础功能不受影响</strong><span>管理员配置服务端模型密钥后，本页才会启用；客户、提醒、导入导出仍可正常使用。</span></div>
    <section class="panel assistant-card">
      <div class="assistant-tabs"><button :class="{ active: mode === 'customer' }" @click="mode = 'customer'">解析客户草稿</button><button :class="{ active: mode === 'interaction' }" @click="mode = 'interaction'">解析沟通草稿</button></div>
      <label>粘贴自然语言记录<textarea v-model.trim="input" rows="7" :placeholder="mode === 'customer' ? '例如：李同学，南大大二，微信…' : '例如：今天微信沟通了课程方向，下周二再联系…'"></textarea></label>
      <p class="field-hint">解析结果只是可编辑草稿，不会自动写入数据库。</p><button class="primary-button" :disabled="busy || !input || !availability?.available" @click="parse">{{ busy ? '正在生成…' : '生成结构化草稿' }}</button>
      <pre v-if="result" class="json-result">{{ JSON.stringify(result, null, 2) }}</pre>
    </section>
    <section class="panel briefing-card"><div><p class="eyebrow">DAILY BRIEFING</p><h2>今日智能简报</h2><p>仅分析你当前可见且近期需要关注的客户，最多 25 位。</p></div><button class="soft-button" :disabled="busy || !availability?.available" @click="streamBriefing">生成简报</button><div v-if="briefing" class="ai-output">{{ briefing }}</div></section>
    <p v-if="error" class="notice error">{{ error }}</p>
  </div>
</template>

