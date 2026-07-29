<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ApiError, api } from '../lib/api'
import { dateTime } from '../lib/format'
import type { AgentAuthorization } from '../types'
const rows = ref<AgentAuthorization[]>([]); const error = ref('')
async function load() { try { rows.value = await api('/api/v1/admin/agent/authorizations') } catch (reason) { error.value = reason instanceof ApiError ? reason.message : 'Agent 授权加载失败' } }
async function revoke(id: string) { if (!window.confirm('强制撤销该员工的 Agent 授权？')) return; try { await api<void>(`/api/v1/admin/agent/authorizations/${id}`, { method: 'DELETE' }); await load() } catch (reason) { error.value = reason instanceof ApiError ? reason.message : '撤销失败' } }
onMounted(load)
</script>

<template><div class="page"><header class="page-header"><div><p class="eyebrow">ORGANIZATION AGENT GOVERNANCE</p><h1>Agent 管理</h1><p class="muted">查看本组织 Agent 连接、Scope、最近使用和到期时间；系统不会显示任何 Token。</p></div></header><p v-if="error" class="notice error">{{ error }}</p><section class="panel table-wrap"><table><thead><tr><th>员工</th><th>客户端</th><th>Scope</th><th>状态</th><th>最近使用</th><th>到期</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td><strong>{{ row.displayName }}</strong></td><td>{{ row.clientName }}<small class="block">{{ row.clientId }}</small></td><td><div class="scope-list"><span v-for="scope in row.scopes" :key="scope">{{ scope }}</span></div></td><td><span :class="['table-status', row.status === 'ACTIVE' ? 'ready' : 'failed']">{{ row.status }}</span></td><td>{{ dateTime(row.lastUsedAt) }}</td><td>{{ dateTime(row.expiresAt) }}</td><td><button v-if="row.status === 'ACTIVE'" class="danger-link" @click="revoke(row.id)">强制撤销</button><span v-else>—</span></td></tr></tbody></table><div v-if="!rows.length" class="empty-state">暂无 Agent 授权。</div></section></div></template>
