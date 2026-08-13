import crypto from 'node:crypto'

export class BridgeError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message) }
}

export function instanceId(key: string): string {
  if (!/^[a-zA-Z0-9:_-]{1,200}$/.test(key)) throw new BridgeError(400, 'INVALID_INSTANCE_KEY', 'invalid instance key')
  return crypto.createHash('sha256').update(key).digest('hex').slice(0, 24)
}

export function validatedId(value: string): string {
  if (!/^[a-f0-9]{24}$/.test(value)) throw new BridgeError(400, 'INVALID_INSTANCE_ID', 'invalid instance id')
  return value
}

export function privateMessageQuery(body: unknown) {
  const input = body && typeof body === 'object' ? body as Record<string, unknown> : {}
  const peerUid = String(input.peerUid ?? '')
  const startTime = Number(input.startTime)
  const endTime = Number(input.endTime)
  const page = Math.max(1, Math.min(1000, Number(input.page ?? 1)))
  const limit = Math.max(1, Math.min(2000, Number(input.limit ?? 1000)))
  if (!peerUid || !Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime < startTime) {
    throw new BridgeError(400, 'INVALID_PRIVATE_MESSAGE_QUERY', '私聊好友和时间范围无效')
  }
  return { peerUid, startTime, endTime, page, limit }
}
