import Docker from 'dockerode'
import express, { type NextFunction, type Request, type Response } from 'express'
import { Readable } from 'node:stream'
import { URL } from 'node:url'
import { WebSocket, WebSocketServer } from 'ws'
import { qceContainerCreateOptions } from './container-config.js'
import { readContainerFile } from './container-file.js'
import { removeRevokedInstance } from './instance-cleanup.js'
import { requireAllowedQceRoute, sanitizeQceConfig } from './qce-route-policy.js'
import { buildAutoLoginPersistenceCommand } from './session-persistence.js'
import { BridgeError, instanceId, messageQuery, validatedId } from './validation.js'

const port = Number(process.env.PORT ?? '40700')
const sharedToken = process.env.QQ_BRIDGE_SHARED_TOKEN ?? ''
const qceImage = process.env.QCE_IMAGE ?? 'yingge/napcat-qce:58fc533f1bfdbf4535be0f83af4e595d4d7ecf27'
const qceNetwork = process.env.QCE_NETWORK ?? 'yingge-qq-private'
const maxInstances = Math.max(1, Number(process.env.QCE_MAX_INSTANCES ?? '20'))
const quotaBytes = Math.max(1, Number(process.env.QCE_EXPORT_QUOTA_GIB ?? '10')) * 1024 * 1024 * 1024
const minimumFreePercent = Math.min(95, Math.max(1, Number(process.env.QCE_MIN_FREE_PERCENT ?? '15')))
const docker = new Docker({ socketPath: process.env.DOCKER_SOCKET ?? '/var/run/docker.sock' })
const app = express()
app.disable('x-powered-by')
app.use(express.json({ limit: '2mb' }))

function authenticate(request: Request, response: Response, next: NextFunction) {
  if (!sharedToken || request.header('authorization') !== `Bearer ${sharedToken}`) {
    response.status(401).json({ code: 'UNAUTHORIZED', message: 'bridge authentication required' })
    return
  }
  next()
}
app.use(authenticate)

function containerName(id: string): string { return `yingge-qce-${id}` }
function labels(id: string) { return { 'com.yingge.qq-bridge': 'true', 'com.yingge.qq-instance': id } }

async function bridgeContainers() {
  return docker.listContainers({ all: true, filters: { label: ['com.yingge.qq-bridge=true'] } })
}

async function ensureNetwork() {
  const found = await docker.listNetworks({ filters: { name: [qceNetwork] } })
  // QQ must be able to reach Tencent's servers. This network is private because no
  // container port is published, but it deliberately permits outbound traffic.
  if (!found.length) await docker.createNetwork({ Name: qceNetwork, Internal: false, CheckDuplicate: true })
}

async function qceToken(container: Docker.Container): Promise<string | undefined> {
  const content = await readContainerFile(container, '/app/.qq-chat-exporter/security.json')
  if (!content) return undefined
  try {
    const parsed = JSON.parse(content.toString('utf8')) as { accessToken?: string }
    return parsed.accessToken
  } catch { return undefined }
}

async function qceRequest(id: string, path: string, init: RequestInit = {}) {
  const container = docker.getContainer(containerName(id))
  const token = await qceToken(container)
  if (!token) throw new BridgeError(409, 'QCE_NOT_READY', 'QQ 尚未完成登录或导出服务尚未就绪')
  const response = await fetch(`http://${containerName(id)}:40653${path}`, {
    ...init,
    signal: AbortSignal.timeout(35_000),
    headers: { 'content-type': 'application/json', authorization: `Bearer ${token}`, ...init.headers },
  })
  const body = await response.json().catch(() => ({ code: 'QCE_INVALID_RESPONSE', message: 'invalid QCE response' }))
  if (!response.ok) throw new BridgeError(response.status, 'QCE_REQUEST_FAILED', 'QQ 导出器请求失败')
  return body
}

async function execOutput(container: Docker.Container, command: string[]): Promise<string> {
  const exec = await container.exec({ Cmd: command, AttachStdout: true, AttachStderr: true })
  const stream = await exec.start({ hijack: true, stdin: false })
  const stdout: Buffer[] = []
  const stderr: Buffer[] = []
  await new Promise<void>((resolve, reject) => {
    ;(container as any).modem.demuxStream(stream, {
      write: (chunk: Buffer) => stdout.push(Buffer.from(chunk)),
    }, {
      write: (chunk: Buffer) => stderr.push(Buffer.from(chunk)),
    })
    stream.on('end', resolve)
    stream.on('error', reject)
  })
  const inspected = await exec.inspect()
  if ((inspected.ExitCode ?? 1) !== 0) throw new Error(Buffer.concat(stderr).toString('utf8'))
  return Buffer.concat(stdout).toString('utf8').trim()
}

async function persistAutoLoginAccount(container: Docker.Container, uin: string) {
  await execOutput(container, buildAutoLoginPersistenceCommand(uin))
}

async function ensureExportCapacity(id: string) {
  const container = docker.getContainer(containerName(id))
  const usedKb = Number((await execOutput(container, ['du', '-sk', '/app/.qq-chat-exporter'])).split(/\s+/)[0])
  const df = (await execOutput(container, ['df', '-Pk', '/app/.qq-chat-exporter'])).split('\n').at(-1)?.trim().split(/\s+/) ?? []
  const capacityKb = Number(df[1])
  const availableKb = Number(df[3])
  if (!Number.isFinite(usedKb) || usedKb * 1024 >= quotaBytes) {
    throw new BridgeError(507, 'QCE_EXPORT_QUOTA_EXCEEDED', 'QQ 导出空间已达到个人 10 GB 配额')
  }
  if (!Number.isFinite(capacityKb) || !Number.isFinite(availableKb)
      || capacityKb <= 0 || availableKb / capacityKb * 100 < minimumFreePercent) {
    throw new BridgeError(507, 'QCE_DISK_SPACE_LOW', '服务器剩余磁盘空间不足，已暂停新的 QQ 导出')
  }
}

async function qceProxyRequest(id: string, upstreamPath: string, request: Request, response: Response) {
  const route = requireAllowedQceRoute(request.method, upstreamPath)
  if (route.writesData) await ensureExportCapacity(id)
  const container = docker.getContainer(containerName(id))
  const token = await qceToken(container)
  if (!token) throw new BridgeError(409, 'QCE_NOT_READY', 'QQ 尚未完成登录或导出服务尚未就绪')
  let body: string | undefined
  if (!['GET', 'HEAD'].includes(request.method)) {
    const value = request.method === 'PUT' && upstreamPath.split('?', 1)[0] === '/api/config'
      ? sanitizeQceConfig(request.body)
      : request.body
    body = JSON.stringify(value ?? {})
  }
  const upstream = await fetch(`http://${containerName(id)}:40653${upstreamPath}`, {
    method: request.method,
    body,
    signal: AbortSignal.timeout(route.writesData ? 180_000 : 60_000),
    headers: {
      authorization: `Bearer ${token}`,
      'content-type': request.header('content-type') ?? 'application/json',
      accept: request.header('accept') ?? '*/*',
    },
    redirect: 'manual',
  })
  if (upstreamPath.split('?', 1)[0] === '/api/config') {
    const raw = await upstream.json().catch(() => ({}))
    response.status(upstream.status)
    response.setHeader('Cache-Control', 'no-store')
    response.setHeader('X-Content-Type-Options', 'nosniff')
    response.json(sanitizeQceConfig(raw))
    return
  }
  response.status(upstream.status)
  for (const header of ['content-type', 'content-disposition', 'content-length', 'last-modified', 'etag']) {
    const value = upstream.headers.get(header)
    if (value) response.setHeader(header, value)
  }
  response.setHeader('Cache-Control', 'no-store')
  response.setHeader('X-Content-Type-Options', 'nosniff')
  if (!upstream.body) { response.end(); return }
  Readable.fromWeb(upstream.body as any).pipe(response)
}

app.post('/v1/instances', async (request, response, next) => {
  try {
    const key = String(request.body?.key ?? '')
    const id = instanceId(key)
    const name = containerName(id)
    const existing = (await bridgeContainers()).find((row) => row.Names.includes(`/${name}`))
    if (existing) {
      if (existing.State !== 'running') await docker.getContainer(existing.Id).start()
      response.json({ instanceId: id })
      return
    }
    if ((await bridgeContainers()).length >= maxInstances) {
      throw new BridgeError(409, 'INSTANCE_LIMIT_REACHED', 'QQ 连接实例数量已达到服务器上限')
    }
    await ensureNetwork()
    const container = await docker.createContainer(
      qceContainerCreateOptions(name, qceNetwork, qceImage, labels(id)),
    )
    await container.start()
    response.status(201).json({ instanceId: id })
  } catch (error) { next(error) }
})

app.get('/v1/instances/:id', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    const container = docker.getContainer(containerName(id))
    const state = await container.inspect()
    const qr = await readContainerFile(container, '/app/napcat/cache/qrcode.png')
    const token = await qceToken(container)
    let connected = false
    let sessionPersistent = false
    let maskedQq: string | undefined
    if (state.State.Running && token) {
      try {
        const info = await qceRequest(id, '/api/system/info') as Record<string, any>
        const data = info.data ?? info
        connected = data?.napcat?.online === true
        const uin = String(data?.napcat?.selfInfo?.uin ?? '')
        if (uin) {
          maskedQq = uin.length <= 4 ? '*'.repeat(uin.length) : `${uin.slice(0, 2)}***${uin.slice(-2)}`
          if (connected) {
            try {
              await persistAutoLoginAccount(container, uin)
              sessionPersistent = true
            } catch {
              // The QQ connection is still usable. Keep reporting CONNECTED,
              // while the explicit flag lets operations detect that a later
              // container restart may require a fresh scan.
              sessionPersistent = false
            }
          }
        }
      } catch { connected = false }
    }
    response.json({
      instanceId: id,
      status: connected ? 'CONNECTED' : state.State.Running ? 'PAIRING' : 'DISCONNECTED',
      qrCodeDataUrl: !connected && qr ? `data:image/png;base64,${qr.toString('base64')}` : undefined,
      maskedQq,
      sessionPersistent,
    })
  } catch (error) { next(error) }
})

app.delete('/v1/instances/:id', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    // Revocation must require a fresh QR login. Keeping these named volumes would
    // silently restore the previous QQ session when the deterministic instance is
    // created again.
    await removeRevokedInstance(docker, containerName(id))
    response.status(204).end()
  } catch (error) { next(error) }
})

app.get('/v1/instances/:id/friends', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    const page = Math.max(1, Math.min(1000, Number(request.query.page ?? '1')))
    const limit = Math.max(1, Math.min(200, Number(request.query.limit ?? '100')))
    response.json(await qceRequest(id, `/api/friends?page=${page}&limit=${limit}`))
  } catch (error) { next(error) }
})

app.post('/v1/instances/:id/messages', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    const { chatType, peerUid, startTime, endTime, page, limit } = messageQuery(request.body)
    response.json(await qceRequest(id, '/api/messages/fetch', {
      method: 'POST',
      body: JSON.stringify({
        peer: { chatType, peerUid },
        filter: { startTime, endTime },
        batchSize: 5000,
        page,
        limit,
      }),
    }))
  } catch (error) { next(error) }
})

app.all('/v1/instances/:id/proxy', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    await qceProxyRequest(id, String(request.query.path ?? ''), request, response)
  } catch (error) { next(error) }
})

app.get('/health', (_request, response) => response.json({ status: 'UP' }))
app.use((error: unknown, _request: Request, response: Response, _next: NextFunction) => {
  const bridgeError = error instanceof BridgeError ? error : new BridgeError(500, 'BRIDGE_ERROR', 'QQ Bridge 操作失败')
  response.status(bridgeError.status).json({ code: bridgeError.code, message: bridgeError.message })
})

const server = app.listen(port, '0.0.0.0')
const websocketServer = new WebSocketServer({ noServer: true })
server.on('upgrade', async (request, socket, head) => {
  let upstream: WebSocket | undefined
  try {
    if (!sharedToken || request.headers.authorization !== `Bearer ${sharedToken}`) throw new BridgeError(401, 'UNAUTHORIZED', 'bridge authentication required')
    const url = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`)
    const match = url.pathname.match(/^\/v1\/instances\/([a-f0-9]{24})\/ws$/)
    if (!match) throw new BridgeError(404, 'WEBSOCKET_NOT_FOUND', 'websocket endpoint not found')
    const id = validatedId(match[1])
    const token = await qceToken(docker.getContainer(containerName(id)))
    if (!token) throw new BridgeError(409, 'QCE_NOT_READY', 'QQ 尚未完成登录或导出服务尚未就绪')
    upstream = new WebSocket(`ws://${containerName(id)}:40653/`, { headers: { authorization: `Bearer ${token}` } })
    upstream.once('open', () => websocketServer.handleUpgrade(request, socket, head, (client) => {
      const closeBoth = () => {
        if (client.readyState === WebSocket.OPEN) client.close()
        if (upstream?.readyState === WebSocket.OPEN) upstream.close()
      }
      client.on('message', (data, binary) => {
        if (upstream?.readyState === WebSocket.OPEN) upstream.send(data, { binary })
      })
      upstream?.on('message', (data, binary) => {
        if (client.readyState === WebSocket.OPEN) client.send(data, { binary })
      })
      client.on('close', closeBoth)
      upstream?.on('close', closeBoth)
      upstream?.on('error', closeBoth)
    }))
    upstream.once('error', () => socket.destroy())
  } catch {
    upstream?.close()
    socket.destroy()
  }
})
