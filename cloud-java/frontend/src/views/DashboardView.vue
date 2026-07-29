<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import CustomerCard from '../components/CustomerCard.vue'
import { api } from '../lib/api'
import type { Customer } from '../types'

interface Dashboard {
  stats: { totalActive: number; archivedCount: number; todayDueCount: number; overdueCount: number; highIntentionCount: number }
  dueToday: Customer[]
  overdue: Customer[]
  staleHighIntention: Customer[]
  recentUpdated: Customer[]
}

const data = ref<Dashboard>()
const error = ref('')
onMounted(async () => {
  try { data.value = await api<Dashboard>('/api/v1/dashboard') }
  catch { error.value = '工作台暂时无法加载' }
})
</script>

<template>
  <div class="page page-dashboard">
    <header class="page-header hero-header">
      <div><p class="eyebrow">今日业务概览</p><h1>工作台</h1><p class="muted">优先处理到期跟进，再回看近期有变化的客户。</p></div>
      <RouterLink to="/customers/new" class="primary-button">＋ 快速建档</RouterLink>
    </header>
    <p v-if="error" class="notice error">{{ error }}</p>
    <div v-if="data" class="metric-grid">
      <article><span>活跃客户</span><strong>{{ data.stats.totalActive }}</strong><small>当前可见范围</small></article>
      <article class="warm"><span>今日待跟进</span><strong>{{ data.stats.todayDueCount }}</strong><small>请及时处理</small></article>
      <article class="danger"><span>已经逾期</span><strong>{{ data.stats.overdueCount }}</strong><small>优先补充记录</small></article>
      <article><span>高意向客户</span><strong>{{ data.stats.highIntentionCount }}</strong><small>持续保持联系</small></article>
    </div>
    <div v-if="data" class="dashboard-grid">
      <section class="panel span-2">
        <div class="panel-title"><div><p class="eyebrow">FOLLOW UP</p><h2>今日与逾期</h2></div><RouterLink to="/reminders">查看提醒 →</RouterLink></div>
        <div v-if="!data.dueToday.length && !data.overdue.length" class="empty-state">今天没有需要处理的跟进。</div>
        <div class="card-grid compact">
          <CustomerCard v-for="customer in [...data.overdue, ...data.dueToday].slice(0, 8)" :key="customer.id" :customer="customer" />
        </div>
      </section>
      <section class="panel">
        <div class="panel-title"><div><p class="eyebrow">WATCHLIST</p><h2>高意向沉默客户</h2></div></div>
        <div v-if="!data.staleHighIntention.length" class="empty-state small">暂无需要提醒的客户。</div>
        <RouterLink v-for="customer in data.staleHighIntention" :key="customer.id" :to="`/customers/${customer.id}`" class="mini-row">
          <span class="avatar small">{{ customer.fullName.slice(0, 1) }}</span><span><strong>{{ customer.fullName }}</strong><small>{{ customer.school || customer.sourceChannel || '未补充来源' }}</small></span><b>高</b>
        </RouterLink>
      </section>
      <section class="panel span-3">
        <div class="panel-title"><div><p class="eyebrow">RECENT</p><h2>近期更新</h2></div><RouterLink to="/customers">全部客户 →</RouterLink></div>
        <div class="card-grid"><CustomerCard v-for="customer in data.recentUpdated" :key="customer.id" :customer="customer" /></div>
      </section>
    </div>
    <div v-else-if="!error" class="loading-state">正在整理今日工作…</div>
  </div>
</template>

