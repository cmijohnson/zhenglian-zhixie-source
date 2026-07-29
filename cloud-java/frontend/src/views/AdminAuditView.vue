<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../lib/api'
import { dateTime } from '../lib/format'
import type { PageResponse } from '../types'
interface Audit { action: string; entityType: string; entityId?: string; outcome: string; details?: Record<string, unknown>; createdAt: string }
const result = ref<PageResponse<Audit>>()
onMounted(async () => { result.value = await api('/api/v1/admin/audit?size=50') })
</script>

<template><div class="page"><header class="page-header"><div><p class="eyebrow">APPEND-ONLY AUDIT</p><h1>审计日志</h1><p class="muted">登录、权限、客户、导入导出、迁移和 AI 操作均在服务端留痕。</p></div></header><section class="panel table-wrap"><table><thead><tr><th>时间</th><th>动作</th><th>对象</th><th>结果</th><th>详情</th></tr></thead><tbody><tr v-for="item in result?.items" :key="item.createdAt + item.action"><td>{{ dateTime(item.createdAt) }}</td><td><strong>{{ item.action }}</strong></td><td>{{ item.entityType }}<small v-if="item.entityId" class="block">{{ item.entityId.slice(0, 8) }}…</small></td><td><span :class="['table-status', item.outcome === 'SUCCESS' ? 'ready' : 'failed']">{{ item.outcome }}</span></td><td><code>{{ item.details ? JSON.stringify(item.details) : '—' }}</code></td></tr></tbody></table><div v-if="!result?.items.length" class="empty-state">暂无审计日志。</div></section></div></template>

