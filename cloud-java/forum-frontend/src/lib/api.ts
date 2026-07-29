import { reactive } from 'vue'
import type { Me } from '../types'

let csrfToken = ''

export const auth = reactive({
  user: null as Me | null,
  checked: false,
  get canWrite() {
    return this.user?.roles.some((role) => ['ORG_ADMIN', 'TEAM_MANAGER', 'MEMBER'].includes(role)) ?? false
  },
  get canModerate() {
    return this.user?.roles.some((role) => ['ORG_ADMIN', 'TEAM_MANAGER'].includes(role)) ?? false
  },
  get isAdmin() {
    return this.user?.roles.includes('ORG_ADMIN') ?? false
  },
})

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) {
    super(message)
  }
}

async function ensureCsrf() {
  if (csrfToken) return csrfToken
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new Error('无法获取安全令牌')
  csrfToken = ((await response.json()) as { token: string }).token
  return csrfToken
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-XSRF-TOKEN', await ensureCsrf())
  }
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })
  if (response.status === 204) return undefined as T
  const contentType = response.headers.get('content-type') ?? ''
  if (!response.ok) {
    const body = contentType.includes('json')
      ? (await response.json()) as { code?: string; message?: string }
      : { message: await response.text() }
    throw new ApiError(response.status, body.code ?? 'HTTP_ERROR', body.message ?? '请求失败')
  }
  return (await response.json()) as T
}

export async function ensureAuth(force = false) {
  if (auth.checked && !force) return auth.user
  try {
    auth.user = await api<Me>('/api/v1/auth/me')
  } catch {
    auth.user = null
  } finally {
    auth.checked = true
  }
  return auth.user
}

export async function login(username: string, password: string) {
  auth.user = await api<Me>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  auth.checked = true
}

export async function logout() {
  await api<void>('/api/v1/auth/logout', { method: 'POST' })
  auth.user = null
  auth.checked = true
  csrfToken = ''
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

export function renderMarkdown(source: string) {
  const escaped = source
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
  const inline = (value: string) => value
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1 ↗</a>')
    .replace(/(^|[\s>])(https?:\/\/[^\s<]+)/g, '$1<a href="$2" target="_blank" rel="noopener noreferrer">$2 ↗</a>')
  return escaped.split('\n').map((line) => {
    if (/^###\s+/.test(line)) return `<h3>${inline(line.replace(/^###\s+/, ''))}</h3>`
    if (/^##\s+/.test(line)) return `<h2>${inline(line.replace(/^##\s+/, ''))}</h2>`
    if (/^#\s+/.test(line)) return `<h1>${inline(line.replace(/^#\s+/, ''))}</h1>`
    if (/^[-*]\s+/.test(line)) return `<div class="md-list">• ${inline(line.replace(/^[-*]\s+/, ''))}</div>`
    if (/^\d+\.\s+/.test(line)) return `<div class="md-list">${inline(line)}</div>`
    return line ? `<p>${inline(line)}</p>` : '<div class="md-space"></div>'
  }).join('')
}

export function fileSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
