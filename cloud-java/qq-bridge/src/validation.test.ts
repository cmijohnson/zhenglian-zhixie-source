import assert from 'node:assert/strict'
import test from 'node:test'
import { QCE_REQUIRED_CAPABILITIES, qceHostConfig } from './container-config.js'
import { BridgeError, instanceId, privateMessageQuery, validatedId } from './validation.js'

test('instance ids are stable, opaque and validated', () => {
  const id = instanceId('organization-id:membership-id')
  assert.match(id, /^[a-f0-9]{24}$/)
  assert.equal(instanceId('organization-id:membership-id'), id)
  assert.equal(validatedId(id), id)
  assert.throws(() => validatedId('../docker.sock'), (error: unknown) =>
    error instanceof BridgeError && error.code === 'INVALID_INSTANCE_ID')
})

test('private message query is restricted to one peer and bounded pagination', () => {
  assert.deepEqual(privateMessageQuery({ peerUid: 'friend-1', startTime: 1000, endTime: 2000, page: -4, limit: 99999 }), {
    peerUid: 'friend-1', startTime: 1000, endTime: 2000, page: 1, limit: 2000,
  })
  assert.throws(() => privateMessageQuery({ peerUid: '', startTime: 2000, endTime: 1000 }),
    (error: unknown) => error instanceof BridgeError && error.code === 'INVALID_PRIVATE_MESSAGE_QUERY')
})

test('QCE containers keep only the capabilities required by the upstream entrypoint', () => {
  const config = qceHostConfig('yingge-qce-test', 'yingge-private')
  assert.deepEqual(config.CapDrop, ['ALL'])
  assert.deepEqual(config.CapAdd, [...QCE_REQUIRED_CAPABILITIES])
  assert.equal('SecurityOpt' in config, false)
  assert.deepEqual(config.LogConfig, { Type: 'none', Config: {} })
  assert.equal(config.NetworkMode, 'yingge-private')
  assert.equal(config.Mounts?.length, 3)
})
