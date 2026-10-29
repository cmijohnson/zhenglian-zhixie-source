import assert from 'node:assert/strict'
import test from 'node:test'
import { removeRevokedInstance } from './instance-cleanup.js'

function dockerError(statusCode: number) { return Object.assign(new Error('docker error'), { statusCode }) }

test('revocation is idempotent when the container and volumes are already absent', async () => {
  const removed: string[] = []
  const docker = {
    getContainer: () => ({ inspect: async () => { throw dockerError(404) } }),
    getVolume: (name: string) => ({ remove: async () => { removed.push(name); throw dockerError(404) } }),
  } as any

  await removeRevokedInstance(docker, 'yingge-qce-test', async () => undefined)
  assert.deepEqual(removed, [
    'yingge-qce-test-session', 'yingge-qce-test-data',
    'yingge-qce-test-cache', 'yingge-qce-test-config',
  ])
})

test('revocation retries the short Docker volume-in-use race', async () => {
  let attempts = 0
  const waits: number[] = []
  const docker = {
    getContainer: () => ({
      inspect: async () => ({ State: { Running: true } }),
      stop: async () => undefined,
      remove: async () => undefined,
    }),
    getVolume: () => ({ remove: async () => {
      attempts += 1
      if (attempts <= 2) throw dockerError(409)
    } }),
  } as any

  await removeRevokedInstance(docker, 'yingge-qce-test', async (milliseconds) => { waits.push(milliseconds) })
  assert.deepEqual(waits, [200, 400])
  assert.equal(attempts, 6)
})
