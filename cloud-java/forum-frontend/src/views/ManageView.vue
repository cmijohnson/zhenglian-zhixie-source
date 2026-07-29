<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, formatDate } from '../lib/api'
import type { Board, Overview, Page, PostDetail, PostSummary, Report } from '../types'

const overview = ref<Overview | null>(null)
const reports = ref<Report[]>([])
const boards = ref<Board[]>([])
const posts = ref<PostSummary[]>([])
const activeTab = ref<'overview' | 'content' | 'reports' | 'boards'>('overview')
const tabs: Array<{ key: 'overview' | 'content' | 'reports' | 'boards'; label: string }> = [
  { key: 'overview', label: '总览' },
  { key: 'content', label: '内容' },
  { key: 'reports', label: '举报' },
  { key: 'boards', label: '板块' },
]
const loading = ref(true)
const error = ref('')
const newBoard = ref({ name: '', slug: '', description: '', icon: '💬', color: '#3157d5' })

async function load() {
  loading.value = true
  try {
    const [overviewData, reportData, boardData, postData] = await Promise.all([
      api<Overview>('/api/v1/forum/manage/overview'),
      api<Report[]>('/api/v1/forum/manage/reports'),
      api<Board[]>('/api/v1/forum/manage/boards'),
      api<Page<PostSummary>>('/api/v1/forum/public/posts?size=50'),
    ])
    overview.value = overviewData
    reports.value = reportData
    boards.value = boardData
    posts.value = postData.items
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '管理数据加载失败'
  } finally {
    loading.value = false
  }
}

async function action(postId: string, name: string, reason = '') {
  await api<PostDetail>(`/api/v1/forum/manage/posts/${postId}/actions`, {
    method: 'POST',
    body: JSON.stringify({ action: name, reason }),
  })
  await load()
}

async function resolveReport(report: Report, actionName: 'HIDE' | 'RESTORE') {
  const reason = window.prompt(actionName === 'HIDE' ? '请输入隐藏原因' : '请输入处理备注', report.reason) ?? ''
  if (actionName === 'HIDE' && !reason.trim()) return
  await action(report.postId, actionName, reason)
}

async function createBoard() {
  await api<Board>('/api/v1/forum/manage/boards', {
    method: 'POST',
    body: JSON.stringify({ ...newBoard.value, sortOrder: boards.value.length * 10 + 10, active: true }),
  })
  newBoard.value = { name: '', slug: '', description: '', icon: '💬', color: '#3157d5' }
  await load()
}

async function toggleBoard(board: Board) {
  await api<Board>(`/api/v1/forum/manage/boards/${board.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ ...board, active: !board.active }),
  })
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page manage-page">
    <header class="page-header">
      <div><p class="eyebrow">FORUM OPERATIONS</p><h1>论坛管理</h1><p class="muted">维护内容秩序，观察团队知识流动。</p></div>
    </header>
    <nav class="tab-nav">
      <button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key">{{ tab.label }}</button>
    </nav>
    <div v-if="loading" class="skeleton detail-skeleton"></div>
    <div v-else-if="error" class="empty-state"><h3>管理数据加载失败</h3><p>{{ error }}</p><button @click="load">重试</button></div>
    <template v-else-if="overview">
      <template v-if="activeTab === 'overview'">
        <section class="metric-grid five">
          <article><span>已发布内容</span><strong>{{ overview.publishedPosts }}</strong></article>
          <article><span>评论讨论</span><strong>{{ overview.comments }}</strong></article>
          <article><span>获得点赞</span><strong>{{ overview.likes }}</strong></article>
          <article><span>活跃成员</span><strong>{{ overview.members }}</strong></article>
          <article class="warm"><span>待处理举报</span><strong>{{ overview.openReports }}</strong></article>
        </section>
        <section class="admin-grid">
          <div class="admin-panel"><div class="panel-title"><h2>近期内容</h2></div>
            <RouterLink v-for="post in overview.popularPosts" :key="post.id" :to="`/posts/${post.id}`" class="admin-row">
              <span class="badge">{{ post.boardName }}</span><strong>{{ post.title }}</strong><small>{{ post.viewCount }} 阅读 · {{ post.commentCount }} 评论</small>
            </RouterLink>
          </div>
          <div class="admin-panel"><div class="panel-title"><h2>公告已读</h2></div>
            <div v-if="!overview.announcementReads.length" class="small-empty">暂无公告</div>
            <div v-for="item in overview.announcementReads" :key="item.postId" class="read-row">
              <strong>{{ item.title }}</strong><span>{{ item.readCount }} / {{ item.totalMembers }}</span>
              <div><i :style="{ width: `${item.totalMembers ? item.readCount / item.totalMembers * 100 : 0}%` }"></i></div>
            </div>
          </div>
        </section>
      </template>
      <section v-else-if="activeTab === 'content'" class="admin-panel">
        <div class="panel-title"><h2>内容巡检</h2><span>{{ posts.length }} 篇</span></div>
        <article v-for="post in posts" :key="post.id" class="content-admin-row">
          <div><span class="badge">{{ post.boardName }}</span><strong>{{ post.title }}</strong><small>{{ post.authorDisplayName }} · {{ formatDate(post.publishedAt) }}</small></div>
          <RouterLink :to="`/posts/${post.id}`">查看</RouterLink>
          <button @click="action(post.id, post.pinned ? 'UNPIN' : 'PIN')">{{ post.pinned ? '取消置顶' : '置顶' }}</button>
          <button @click="action(post.id, post.locked ? 'UNLOCK' : 'LOCK')">{{ post.locked ? '解锁' : '锁定' }}</button>
          <button class="danger" @click="action(post.id, 'HIDE', '管理员巡检隐藏')">隐藏</button>
        </article>
      </section>
      <section v-else-if="activeTab === 'reports'" class="admin-panel">
        <div class="panel-title"><h2>举报队列</h2><span>{{ reports.filter((item) => item.status === 'OPEN').length }} 待处理</span></div>
        <div v-if="!reports.length" class="small-empty">暂无举报记录</div>
        <article v-for="report in reports" :key="report.id" class="report-row">
          <span :class="['badge', report.status === 'OPEN' ? 'badge-amber' : 'badge-gray']">{{ report.status === 'OPEN' ? '待处理' : '已处理' }}</span>
          <div><strong>{{ report.postTitle }}</strong><p>{{ report.reason }}</p><small>{{ report.reporterDisplayName }} · {{ formatDate(report.createdAt) }}</small></div>
          <RouterLink :to="`/posts/${report.postId}`">查看内容</RouterLink>
          <button v-if="report.status === 'OPEN'" class="danger" @click="resolveReport(report, 'HIDE')">隐藏并结案</button>
        </article>
      </section>
      <section v-else class="admin-panel">
        <div class="panel-title"><h2>板块管理</h2><span>{{ boards.length }} 个</span></div>
        <article v-for="board in boards" :key="board.id" class="board-admin-row">
          <span class="board-icon" :style="{ color: board.color }">{{ board.icon }}</span>
          <div><strong>{{ board.name }}</strong><small>/{{ board.slug }} · {{ board.postCount }} 篇内容</small><p>{{ board.description }}</p></div>
          <span :class="['badge', board.active ? '' : 'badge-gray']">{{ board.active ? '启用' : '停用' }}</span>
          <button @click="toggleBoard(board)">{{ board.active ? '停用' : '启用' }}</button>
        </article>
        <form class="board-create" @submit.prevent="createBoard">
          <h3>新增板块</h3>
          <div class="form-grid two"><label>名称<input v-model.trim="newBoard.name" required></label><label>英文标识<input v-model.trim="newBoard.slug" pattern="[a-z0-9-]+" required placeholder="sales-tips"></label></div>
          <label>简介<input v-model.trim="newBoard.description"></label>
          <div class="form-grid two"><label>图标<input v-model.trim="newBoard.icon" maxlength="24"></label><label>颜色<input v-model="newBoard.color" type="color"></label></div>
          <button class="primary-button" type="submit">创建板块</button>
        </form>
      </section>
    </template>
  </div>
</template>
