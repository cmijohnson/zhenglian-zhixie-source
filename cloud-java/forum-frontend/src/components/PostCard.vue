<script setup lang="ts">
import type { PostSummary } from '../types'
import { formatDate } from '../lib/api'

defineProps<{ post: PostSummary }>()
</script>

<template>
  <article :class="['post-card', { announcement: post.postType === 'ANNOUNCEMENT' }]">
    <header>
      <span class="avatar">{{ post.authorDisplayName.slice(0, 1) }}</span>
      <div>
        <strong>{{ post.authorDisplayName }}</strong>
        <small>{{ formatDate(post.publishedAt) }} · {{ post.boardName }}</small>
      </div>
      <span v-if="post.postType === 'ANNOUNCEMENT'" class="badge badge-amber">公告</span>
      <span v-else-if="post.pinned" class="badge">置顶</span>
    </header>
    <RouterLink :to="`/posts/${post.id}`" class="post-link" :aria-label="`查看帖子：${post.title}`">
      <h2>{{ post.title }}</h2>
      <p>{{ post.excerpt || '打开查看帖子内容' }}</p>
    </RouterLink>
    <div v-if="post.tags.length" class="tag-row">
      <span v-for="tag in post.tags" :key="tag"># {{ tag }}</span>
    </div>
    <footer>
      <RouterLink :to="`/posts/${post.id}`" class="metric" aria-label="查看帖子详情">◉ {{ post.viewCount }}</RouterLink>
      <RouterLink :to="`/posts/${post.id}`" class="metric" aria-label="查看点赞">♡ {{ post.likeCount }}</RouterLink>
      <RouterLink :to="`/posts/${post.id}#comments`" class="metric" aria-label="查看评论">◌ {{ post.commentCount }}</RouterLink>
      <RouterLink :to="`/posts/${post.id}`" class="read-more">阅读全文 <span>→</span></RouterLink>
    </footer>
  </article>
</template>
