import Docker from 'dockerode'
import express, { type NextFunction, type Request, type Response } from 'express'
import tar from 'tar-stream'
import { qceHostConfig } from './container-config.js'
import { BridgeError, instanceId, privateMessageQuery, validatedId } from './validation.js'

const port = Number(process.env.PORT ?? '40700')
const sharedToken = process.env.QQ_BRIDGE_SHARED_TOKEN ?? ''
const qceImage = process.env.QCE_IMAGE ?? 'yingge/napcat-qce:58fc533f1bfdbf4535be0f83af4e595d4d7ecf27'
const qceNetwork = process.env.QCE_NETWORK ?? 'yingge-qq-private'
const maxInstances = Math.max(1, Number(process.env.QCE_MAX_INSTANCES ?? '20'))
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

async function readArchiveFile(container: Docker.Container, path: string): Promise<Buffer | undefined> {
  try {
    const stream = await container.getArchive({ path })
    const extract = tar.extract()
    return await new Promise<Buffer | undefined>((resolve, reject) => {
      let resolved = false
      extract.on('entry', (_header, entry, next) => {
        const chunks: Buffer[] = []
        entry.on('data', (chunk: Buffer) => chunks.push(chunk))
        entry.on('end', () => {
          if (!resolved) { resolved = true; resolve(Buffer.concat(chunks)) }
          next()
        })
        entry.resume()
      })
      extract.on('finish', () => { if (!resolved) resolve(undefined) })
      extract.on('error', reject)
      stream.on('error', reject)
      stream.pipe(extract)
    })
  } catch { return undefined }
}

async function qceToken(container: Docker.Container): Promise<string | undefined> {
  const content = await readArchiveFile(container, '/app/.qq-chat-exporter/security.json')
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
    const container = await docker.createContainer({
      name,
      Image: qceImage,
      Env: ['DOCKER_CONTAINER=true'],
      Labels: labels(id),
      HostConfig: qceHostConfig(name, qceNetwork),
    })
    await container.start()
    response.status(201).json({ instanceId: id })
  } catch (error) { next(error) }
})

app.get('/v1/instances/:id', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    const container = docker.getContainer(containerName(id))
    const state = await container.inspect()
    const qr = await readArchiveFile(container, '/app/napcat/cache/qrcode.png')
    const token = await qceToken(container)
    let connected = false
    let maskedQq: string | undefined
    if (state.State.Running && token) {
      try {
        const info = await qceRequest(id, '/api/system/info') as Record<string, any>
        const data = info.data ?? info
        connected = data?.napcat?.online === true
        const uin = String(data?.napcat?.selfInfo?.uin ?? '')
        if (uin) maskedQq = uin.length <= 4 ? '*'.repeat(uin.length) : `${uin.slice(0, 2)}***${uin.slice(-2)}`
      } catch { connected = false }
    }
    response.json({
      instanceId: id,
      status: connected ? 'CONNECTED' : state.State.Running ? 'PAIRING' : 'DISCONNECTED',
      qrCodeDataUrl: !connected && qr ? `data:image/png;base64,${qr.toString('base64')}` : undefined,
      maskedQq,
      accessToken: token,
    })
  } catch (error) { next(error) }
})

app.delete('/v1/instances/:id', async (request, response, next) => {
  try {
    const id = validatedId(request.params.id)
    const container = docker.getContainer(containerName(id))
    const state = await container.inspect()
    if (state.State.Running) await container.stop({ t: 10 })
    await container.remove()
    // Revocation must require a fresh QR login. Keeping these named volumes would
    // silently restore the previous QQ session when the deterministic instance is
    // created again.
    for (const suffix of ['session', 'data', 'cache']) {
      try { await docker.getVolume(`${containerName(id)}-${suffix}`).remove() }
      catch (error: any) {
        if (error?.statusCode !== 404) throw error
      }
    }
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
    const { peerUid, startTime, endTime, page, limit } = privateMessageQuery(request.body)
    response.json(await qceRequest(id, '/api/messages/fetch', {
      method: 'POST',
      body: JSON.stringify({
        peer: { chatType: 1, peerUid },
        filter: { startTime, endTime },
        batchSize: 5000,
        page,
        limit,
      }),
    }))
  } catch (error) { next(error) }
})

app.get('/health', (_request, response) => response.json({ status: 'UP' }))
app.use((error: unknown, _request: Request, response: Response, _next: NextFunction) => {
  const bridgeError = error instanceof BridgeError ? error : new BridgeError(500, 'BRIDGE_ERROR', 'QQ Bridge 操作失败')
  response.status(bridgeError.status).json({ code: bridgeError.code, message: bridgeError.message })
})

app.listen(port, '0.0.0.0')
