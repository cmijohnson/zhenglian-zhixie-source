<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PostCard from '../components/PostCard.vue'
import { api, auth } from '../lib/api'
import type { Board, Page, PostSummary } from '../types'

const route = useRoute()
const router = useRouter()
const boards = ref<Board[]>([])
const posts = ref<Page<PostSummary>>({ items: [], page: 0, size: 12, total: 0, pages: 0 })
const loading = ref(true)
const error = ref('')
const searchDraft = ref(String(route.query.q ?? ''))
const selectedBoard = computed(() => String(route.query.board ?? ''))
const selectedTag = computed(() => String(route.query.tag ?? ''))
const page = computed(() => Number(route.query.page ?? 0))
const announcements = computed(() => posts.value.items.filter((post) => post.postType === 'ANNOUNCEMENT'))
const regularPosts = computed(() => posts.value.items.filter((post) => post.postType !== 'ANNOUNCEMENT'))
const popularTags = computed(() => {
  const counts = new Map<string, number>()
  posts.value.items.flatMap((post) => post.tags).forEach((tag) => counts.set(tag, (counts.get(tag) ?? 0) + 1))
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    if (route.query.q) params.set('query', String(route.query.q))
    if (selectedBoard.value) params.set('board', selectedBoard.value)
    if (selectedTag.value) params.set('tag', selectedTag.value)
    params.set('page', String(page.value))
    params.set('size', '12')
    ;[boards.value, posts.value] = await Promise.all([
      api<Board[]>('/api/v1/forum/public/boards'),
      api<Page<PostSummary>>(`/api/v1/forum/public/posts?${params}`),
    ])
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function search() {
  router.push({ path: '/', query: { ...route.query, q: searchDraft.value || undefined, page: undefined } })
}

function filterBoard(slug?: string) {
  router.push({ path: '/', query: { q: route.query.q, board: slug || undefined } })
}

function filterTag(tag?: string) {
  router.push({ path: '/', query: { ...route.query, tag: tag || undefined, page: undefined } })
}

watch(() => route.fullPath, load)
onMounted(load)
</script>

<template>
  <div class="page home-page">
    <section class="hero">
      <div>
        <p class="eyebrow">YINGGE TEAM COMMUNITY</p>
        <h1>汇聚每一个好想法。</h1>
        <p class="hero-copy">记录经验、分享资源、讨论问题，让团队智慧被看见、被复用。</p>
      </div>
      <RouterLink v-if="auth.canWrite" to="/compose" class="primary-button">＋ 发布新内容</RouterLink>
      <RouterLink v-else-if="!auth.user" to="/login" class="primary-button">员工登录</RouterLink>
    </section>

    <section class="search-bar">
      <span>⌕</span>
      <input v-model.trim="searchDraft" placeholder="搜索标题、正文或关键词" @keyup.enter="search">
      <button type="button" @click="search">搜索</button>
    </section>

    <section class="board-strip" aria-label="论坛板块">
      <button :class="{ active: !selectedBoard }" type="button" @click="filterBoard()">
        <span class="board-icon">✦</span><strong>全部内容</strong><small>{{ posts.total }} 篇</small>
      </button>
      <button v-for="board in boards" :key="board.id" :class="{ active: selectedBoard === board.slug }"
              type="button" @click="filterBoard(board.slug)">
        <span class="board-icon" :style="{ color: board.color }">{{ board.icon }}</span>
        <strong>{{ board.name }}</strong><small>{{ board.postCount }} 篇</small>
      </button>
    </section>

    <div class="content-grid">
      <section class="feed">
        <div class="section-heading">
          <div><p class="eyebrow">LATEST NOTES</p><h2>{{ selectedBoard ? boards.find((item) => item.slug === selectedBoard)?.name : '最新动态' }}</h2></div>
          <span>{{ posts.total }} 篇内容</span>
        </div>

        <div v-if="loading" class="loading-stack">
          <div v-for="item in 3" :key="item" class="skeleton"></div>
        </div>
        <div v-else-if="error" class="empty-state"><span>!</span><h3>暂时无法加载内容</h3><p>{{ error }}</p><button @click="load">重新加载</button></div>
        <div v-else-if="!posts.items.length" class="empty-state">
          <span>✦</span><h3>这里还没有内容</h3>
          <p>{{ route.query.q || selectedBoard || selectedTag ? '换个筛选条件试试。' : '成为第一个分享经验的人吧。' }}</p>
          <RouterLink v-if="auth.canWrite" to="/compose" class="primary-button">发布第一篇</RouterLink>
        </div>
        <template v-else>
          <div v-if="announcements.length" class="announcement-stack">
            <PostCard v-for="post in announcements" :key="post.id" :post="post" />
          </div>
          <div class="post-stack">
            <PostCard v-for="post in regularPosts" :key="post.id" :post="post" />
          </div>
          <nav v-if="posts.pages > 1" class="pagination">
            <button :disabled="page <= 0" @click="router.push({ query: { ...route.query, page: page - 1 } })">上一页</button>
            <span>{{ page + 1 }} / {{ posts.pages }}</span>
            <button :disabled="page + 1 >= posts.pages" @click="router.push({ query: { ...route.query, page: page + 1 } })">下一页</button>
          </nav>
        </template>
      </section>

      <aside class="right-rail">
        <section class="rail-card">
          <p class="eyebrow">COMMUNITY GUIDE</p>
          <h3>社区公约</h3>
          <ul><li>分享真实、有帮助的信息</li><li>尊重每一种观点和经验</li><li>客户隐私不得发布到公开论坛</li></ul>
        </section>
        <section class="rail-card">
          <div class="rail-title"><h3>热门标签</h3><button v-if="selectedTag" @click="filterTag()">清除</button></div>
          <div v-if="popularTags.length" class="tag-cloud">
            <button v-for="[tag, count] in popularTags" :key="tag" :class="{ active: selectedTag === tag }"
                    @click="filterTag(tag)"># {{ tag }} <small>{{ count }}</small></button>
          </div>
          <p v-else class="muted">内容发布后，热门标签会出现在这里。</p>
        </section>
      </aside>
    </div>
  </div>
</template>
