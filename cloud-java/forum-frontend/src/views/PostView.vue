<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, auth, fileSize, formatDate, renderMarkdown } from '../lib/api'
import type { Attachment, ForumComment, PostDetail } from '../types'

const route = useRoute()
const router = useRouter()
const post = ref<PostDetail | null>(null)
const loading = ref(true)
const error = ref('')
const commentDraft = ref('')
const replyTo = ref<ForumComment | null>(null)
const actionBusy = ref(false)
const pdfPreviewUrls = ref<Record<string, string>>({})
const renderedContent = computed(() => post.value ? renderMarkdown(post.value.content) : '')

function attachmentKind(file: Attachment) {
  const type = file.contentType.toLowerCase()
  if (['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/avif', 'image/bmp'].includes(type)) return 'image'
  if (type === 'application/pdf' || file.fileName.toLowerCase().endsWith('.pdf')) return 'pdf'
  return 'file'
}

function releasePdfPreviews() {
  Object.values(pdfPreviewUrls.value).forEach((url) => URL.revokeObjectURL(url))
  pdfPreviewUrls.value = {}
}

async function preparePdfPreviews(files: Attachment[]) {
  releasePdfPreviews()
  await Promise.all(files.filter((file) => attachmentKind(file) === 'pdf').map(async (file) => {
    try {
      const response = await fetch(file.url, { credentials: 'same-origin' })
      if (!response.ok) return
      pdfPreviewUrls.value[file.id] = URL.createObjectURL(await response.blob())
    } catch {
      // Keep the normal file link available when an embedded preview cannot be prepared.
    }
  }))
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    post.value = await api<PostDetail>(`/api/v1/forum/public/posts/${route.params.id}`)
    await preparePdfPreviews(post.value.attachments)
    if (post.value.postType === 'ANNOUNCEMENT' && auth.user) {
      await api<void>(`/api/v1/forum/announcements/${post.value.id}/read`, { method: 'POST' })
    }
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '内容加载失败'
  } finally {
    loading.value = false
  }
}

async function submitComment() {
  if (!post.value || !commentDraft.value.trim()) return
  actionBusy.value = true
  try {
    const comment = await api<ForumComment>(`/api/v1/forum/posts/${post.value.id}/comments`, {
      method: 'POST',
      body: JSON.stringify({ parentId: replyTo.value?.id, content: commentDraft.value }),
    })
    post.value.comments.push(comment)
    post.value.commentCount += 1
    commentDraft.value = ''
    replyTo.value = null
  } finally {
    actionBusy.value = false
  }
}

async function like() {
  if (!post.value) return
  if (!auth.canWrite) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const result = await api<{ liked: boolean; likeCount: number }>(`/api/v1/forum/posts/${post.value.id}/like`, { method: 'POST' })
  post.value.likeCount = result.likeCount
}

async function report() {
  if (!post.value) return
  if (!auth.canWrite) {
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const reason = window.prompt('请简要说明举报原因')
  if (!reason?.trim()) return
  await api<void>(`/api/v1/forum/posts/${post.value.id}/report`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
  window.alert('举报已提交，管理员会尽快处理。')
}

onMounted(load)
onBeforeUnmount(releasePdfPreviews)
</script>

<template>
  <div class="page detail-page">
    <button class="back-link" type="button" @click="router.back()">← 返回内容流</button>
    <div v-if="loading" class="detail-skeleton skeleton"></div>
    <div v-else-if="error" class="empty-state detail-empty">
      <span>404</span><h2>没有找到这篇内容</h2><p>{{ error }}</p><RouterLink to="/">返回首页</RouterLink>
    </div>
    <template v-else-if="post">
      <article class="post-detail">
        <header class="detail-header">
          <div class="detail-badges">
            <span v-if="post.postType === 'ANNOUNCEMENT'" class="badge badge-amber">团队公告</span>
            <span v-if="post.pinned" class="badge">置顶</span>
            <span v-if="post.locked" class="badge badge-gray">已锁定</span>
            <span class="board-label">{{ post.boardName }}</span>
          </div>
          <h1>{{ post.title }}</h1>
          <div class="author-line">
            <span class="avatar">{{ post.authorDisplayName.slice(0, 1) }}</span>
            <span><strong>{{ post.authorDisplayName }}</strong><small>{{ formatDate(post.publishedAt) }} 发布 · {{ post.viewCount }} 次阅读</small></span>
          </div>
        </header>

        <div class="markdown-body" v-html="renderedContent"></div>

        <section v-if="post.attachments.length" class="attachment-list">
          <h3>附件</h3>
          <article v-for="file in post.attachments" :key="file.id" class="attachment-preview-card">
            <a v-if="attachmentKind(file) === 'image'" class="attachment-preview-media" :href="file.url" target="_blank" rel="noopener">
              <img :src="file.url" :alt="`${file.fileName} 预览`" loading="lazy">
            </a>
            <div v-else-if="attachmentKind(file) === 'pdf'" class="attachment-preview-media">
              <object v-if="pdfPreviewUrls[file.id]" :data="`${pdfPreviewUrls[file.id]}#toolbar=0&navpanes=0`" type="application/pdf" :aria-label="`${file.fileName} PDF 预览`">
                <a :href="file.url" target="_blank" rel="noopener">打开 PDF</a>
              </object>
              <span v-else>PDF 预览加载中…</span>
            </div>
            <div v-else class="attachment-preview-media compact"><span aria-hidden="true">FILE</span></div>
            <footer>
              <span><strong>{{ file.fileName }}</strong><small>{{ fileSize(file.fileSize) }} · {{ attachmentKind(file) === 'image' ? '图片' : attachmentKind(file) === 'pdf' ? 'PDF' : '附件' }}</small></span>
              <a :href="file.url" target="_blank" rel="noopener">{{ attachmentKind(file) === 'file' ? '下载' : '打开预览' }}</a>
            </footer>
          </article>
        </section>

        <div v-if="post.tags.length" class="tag-row detail-tags">
          <RouterLink v-for="tag in post.tags" :key="tag" :to="{ path: '/', query: { tag } }"># {{ tag }}</RouterLink>
        </div>
        <footer class="detail-actions">
          <button type="button" @click="like">♡ {{ post.likeCount }} 点赞</button>
          <a href="#comments">◌ {{ post.commentCount }} 评论</a>
          <button type="button" @click="report">! 举报</button>
        </footer>
      </article>

      <section id="comments" class="comments-panel">
        <div class="section-heading">
          <div><p class="eyebrow">DISCUSSION</p><h2>讨论与回复</h2></div><span>{{ post.commentCount }} 条</span>
        </div>
        <div v-if="!post.comments.length" class="comment-empty">还没有评论，欢迎分享你的看法。</div>
        <article v-for="comment in post.comments" :key="comment.id" :class="['comment', { reply: comment.parentId }]">
          <span class="avatar small">{{ comment.authorDisplayName.slice(0, 1) }}</span>
          <div><header><strong>{{ comment.authorDisplayName }}</strong><time>{{ formatDate(comment.createdAt) }}</time></header>
            <p>{{ comment.content }}</p>
            <button v-if="auth.canWrite && !post.locked" type="button" @click="replyTo = comment">回复</button>
          </div>
        </article>
        <form v-if="auth.canWrite && !post.locked" class="comment-form" @submit.prevent="submitComment">
          <div v-if="replyTo" class="reply-target">回复 {{ replyTo.authorDisplayName }} <button type="button" @click="replyTo = null">×</button></div>
          <textarea v-model="commentDraft" rows="4" maxlength="4000" placeholder="写下你的看法，支持使用 @ 提及同事…" required></textarea>
          <button class="primary-button" :disabled="actionBusy" type="submit">发布评论</button>
        </form>
        <div v-else-if="post.locked" class="comment-empty">帖子已锁定，暂不接受新评论。</div>
        <RouterLink v-else to="/login" class="comment-login">登录员工账号后参与讨论 →</RouterLink>
      </section>
    </template>
  </div>
</template>
