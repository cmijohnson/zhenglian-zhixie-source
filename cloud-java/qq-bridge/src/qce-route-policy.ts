import { BridgeError } from './validation.js'

export interface AllowedQceRoute {
  method: string
  path: RegExp
  writesData?: boolean
  downloadsData?: boolean
}

const routes: AllowedQceRoute[] = [
  { method: 'GET', path: /^\/(?:qce(?:\/.*)?|static\/qce\/.*|health|security-status)$/ },
  { method: 'GET', path: /^\/(?:text-logo\.png|text-full-logo\.png|placeholder-logo\.(?:png|svg)|placeholder-user\.jpg|placeholder\.(?:jpg|svg)|_vercel\/insights\/script\.js)$/ },
  { method: 'GET', path: /^\/(?:downloads|scheduled-downloads|resources)\/[^\u0000-\u001F\u007F?#\\]+$/u, downloadsData: true },
  { method: 'GET', path: /^\/api\/(?:system\/(?:info|status)|config|groups|group-system-notify|friends|recent-contacts|users\/lookup|tasks|scheduled-exports|sticker-packs|group-albums\/export-records|group-files\/export-records|exports\/files|resources\/(?:index|files)|merge-resources\/available-tasks)$/ },
  { method: 'GET', path: /^\/api\/groups\/[^/]+(?:\/members|\/join-requests|\/essence|\/albums|\/files|\/files\/count)?$/ },
  { method: 'GET', path: /^\/api\/groups\/[^/]+\/albums\/[^/]+\/media$/ },
  { method: 'GET', path: /^\/api\/friends\/[^/]+$/ },
  { method: 'GET', path: /^\/api\/users\/[^/]+$/ },
  { method: 'GET', path: /^\/api\/tasks\/[^/]+$/ },
  { method: 'GET', path: /^\/api\/scheduled-exports\/[^/]+(?:\/history)?$/ },
  { method: 'GET', path: /^\/api\/exports\/files\/[^/]+\/(?:info|preview)$/ },
  { method: 'GET', path: /^\/api\/exports\/files\/[^/]+\/resources\/[A-Za-z0-9._~!$&'()+,;=:@%\/-]+$/, downloadsData: true },
  { method: 'GET', path: /^\/api\/resources\/export\/[^/]+$/, downloadsData: true },
  { method: 'GET', path: /^\/api\/download-file$/, downloadsData: true },
  { method: 'POST', path: /^\/api\/messages\/fetch$/ },
  { method: 'POST', path: /^\/api\/messages\/(?:export|export-streaming-zip|export-streaming-jsonl)$/, writesData: true },
  { method: 'POST', path: /^\/api\/groups\/[^/]+\/(?:essence\/export|avatars\/export|albums\/export|files\/(?:download|export|export-with-download))$/, writesData: true },
  { method: 'POST', path: /^\/api\/tasks\/[^/]+\/cancel$/, writesData: true },
  { method: 'DELETE', path: /^\/api\/tasks\/[^/]+(?:\/original-files)?$/, writesData: true },
  { method: 'POST', path: /^\/api\/scheduled-exports$/, writesData: true },
  { method: 'POST', path: /^\/api\/scheduled-exports\/(?:trigger-all|trigger-batch|update-batch|delete-batch)$/, writesData: true },
  { method: 'GET', path: /^\/api\/scheduled-exports\/[^/]+$/ },
  { method: 'PUT', path: /^\/api\/scheduled-exports\/[^/]+$/, writesData: true },
  { method: 'DELETE', path: /^\/api\/scheduled-exports\/[^/]+$/, writesData: true },
  { method: 'POST', path: /^\/api\/scheduled-exports\/[^/]+\/trigger$/, writesData: true },
  { method: 'POST', path: /^\/api\/sticker-packs\/(?:export|export-all)$/, writesData: true },
  { method: 'GET', path: /^\/api\/sticker-packs\/export-records$/ },
  { method: 'DELETE', path: /^\/api\/exports\/files\/[^/]+$/, writesData: true },
  { method: 'POST', path: /^\/api\/merge-resources$/, writesData: true },
  { method: 'PUT', path: /^\/api\/config$/, writesData: true },
]

export function normalizeUpstreamPath(value: unknown): string {
  const input = String(value ?? '')
  if (!input.startsWith('/') || input.length > 2048 || input.includes('\\') || input.includes('\0')) {
    throw new BridgeError(400, 'INVALID_QCE_PATH', 'QQ 工作台路径无效')
  }
  let decoded: string
  try { decoded = decodeURIComponent(input) }
  catch { throw new BridgeError(400, 'INVALID_QCE_PATH', 'QQ 工作台路径编码无效') }
  const path = decoded.split('?', 1)[0]
  if (path.split('/').some((part) => part === '..' || part === '.')) {
    throw new BridgeError(400, 'INVALID_QCE_PATH', 'QQ 工作台路径无效')
  }
  return input
}

export function requireAllowedQceRoute(method: string, value: unknown): AllowedQceRoute {
  const normalized = normalizeUpstreamPath(value)
  const path = normalized.split('?', 1)[0]
  const route = routes.find((candidate) => candidate.method === method.toUpperCase() && candidate.path.test(path))
  if (!route) throw new BridgeError(403, 'QCE_ROUTE_FORBIDDEN', '该 QQ 工作台能力未开放')
  return route
}

const forbiddenConfigKeys = /^(?:host|hostname|bind|listen|port|outputdir|outputpath|path|paths|token|accesstoken|security|ipwhitelist|allowedips|docker|socket)$/i

export function sanitizeQceConfig(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sanitizeQceConfig)
  if (!value || typeof value !== 'object') return value
  const source = value as Record<string, unknown>
  return Object.fromEntries(Object.entries(source)
    .filter(([key]) => !forbiddenConfigKeys.test(key))
    .map(([key, item]) => [key, sanitizeQceConfig(item)]))
}

export function routeCount(): number { return routes.length }
