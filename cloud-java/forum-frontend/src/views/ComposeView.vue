<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, auth, fileSize } from '../lib/api'
import type { Board, PostDetail } from '../types'

type PreviewKind = 'image' | 'pdf' | 'file'
interface SelectedFile {
  id: string
  file: File
  url: string
  kind: PreviewKind
}

let fileSequence = 0
const router = useRouter()
const boards = ref<Board[]>([])
const boardId = ref('')
const title = ref('')
const content = ref('')
const tags = ref('')
const postType = ref<'POST' | 'ANNOUNCEMENT'>('POST')
const files = ref<SelectedFile[]>([])
const error = ref('')
const busy = ref(false)

onMounted(async () => {
  boards.value = await api<Board[]>('/api/v1/forum/public/boards')
  boardId.value = boards.value[0]?.id ?? ''
})

function previewKind(file: File): PreviewKind {
  const type = file.type.toLowerCase()
  if (['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/avif', 'image/bmp'].includes(type)) return 'image'
  if (type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf')) return 'pdf'
  return 'file'
}

function chooseFiles(event: Event) {
  error.value = ''
  const input = event.target as HTMLInputElement
  const selected = [...(input.files ?? [])]
  const oversized = selected.find((file) => file.size > 25 * 1024 * 1024)
  if (oversized) error.value = `${oversized.name} 超过 25MB，未加入附件。`
  for (const file of selected.filter((item) => item.size <= 25 * 1024 * 1024)) {
    files.value.push({
      id: `${file.name}-${file.size}-${file.lastModified}-${++fileSequence}`,
      file,
      url: URL.createObjectURL(file),
      kind: previewKind(file),
    })
  }
  input.value = ''
}

function removeFile(index: number) {
  const [removed] = files.value.splice(index, 1)
  if (removed) URL.revokeObjectURL(removed.url)
}

function releasePreviewUrls() {
  files.value.forEach((selected) => URL.revokeObjectURL(selected.url))
}

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const created = await api<PostDetail>('/api/v1/forum/posts', {
      method: 'POST',
      body: JSON.stringify({
        boardId: boardId.value,
        title: title.value,
        content: content.value,
        tags: tags.value.split(/[,，#]/).map((tag) => tag.trim()).filter(Boolean),
        postType: postType.value,
      }),
    })
    for (const selected of files.value) {
      const data = new FormData()
      data.append('file', selected.file)
      await api(`/api/v1/forum/posts/${created.id}/attachments`, { method: 'POST', body: data })
    }
    await router.push(`/posts/${created.id}`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '发布失败'
  } finally {
    busy.value = false
  }
}

onBeforeUnmount(releasePreviewUrls)
</script>

<template>
  <div class="page narrow-page">
    <header class="page-header">
      <div><p class="eyebrow">SHARE A NOTE</p><h1>发布新内容</h1><p class="muted">把经验写清楚，让下一位同事更快抵达答案。</p></div>
    </header>
    <form class="compose-card" @submit.prevent="submit">
      <div class="form-grid two">
        <label>发布板块<select v-model="boardId" required><option v-for="board in boards" :key="board.id" :value="board.id">{{ board.icon }} {{ board.name }}</option></select></label>
        <label v-if="auth.isAdmin">内容类型<select v-model="postType"><option value="POST">普通帖子</option><option value="ANNOUNCEMENT">团队公告</option></select></label>
      </div>
      <label>标题<input v-model.trim="title" maxlength="180" required placeholder="用一句话说明这篇内容能帮大家解决什么"></label>
      <label>正文<textarea v-model="content" rows="14" maxlength="50000" required placeholder="支持 Markdown：# 标题、**加粗**、[链接](https://...)"></textarea><small>请勿发布客户联系方式、密码或其他敏感信息。</small></label>
      <label>标签<input v-model.trim="tags" placeholder="例如：销售经验，客户沟通，新人工具"><small>使用逗号分隔，最多 8 个。</small></label>
      <label class="file-picker">附件<input type="file" multiple @change="chooseFiles"><span>选择文件</span><small>{{ files.length ? `已选择 ${files.length} 个文件，可在发布前预览` : '图片和 PDF 可实时预览，单个附件不超过 25MB' }}</small></label>
      <section v-if="files.length" class="upload-preview-list" aria-label="待上传附件预览">
        <article v-for="(selected, index) in files" :key="selected.id" class="upload-preview-card">
          <div class="upload-preview-media">
            <img v-if="selected.kind === 'image'" :src="selected.url" :alt="`${selected.file.name} 预览`">
            <object v-else-if="selected.kind === 'pdf'" :data="`${selected.url}#toolbar=0&navpanes=0`" type="application/pdf" :aria-label="`${selected.file.name} PDF 预览`">
              <span>当前浏览器无法内嵌 PDF，可发布后打开查看。</span>
            </object>
            <span v-else aria-hidden="true">FILE</span>
          </div>
          <footer>
            <span><strong>{{ selected.file.name }}</strong><small>{{ fileSize(selected.file.size) }} · {{ selected.kind === 'image' ? '图片' : selected.kind === 'pdf' ? 'PDF' : '附件' }}</small></span>
            <button type="button" @click="removeFile(index)">移除</button>
          </footer>
        </article>
      </section>
      <p v-if="error" class="form-error">{{ error }}</p>
      <div class="form-actions"><RouterLink to="/" class="ghost-button">取消</RouterLink><button class="primary-button" :disabled="busy" type="submit">{{ busy ? '发布中…' : '发布内容' }}</button></div>
    </form>
  </div>
</template>
