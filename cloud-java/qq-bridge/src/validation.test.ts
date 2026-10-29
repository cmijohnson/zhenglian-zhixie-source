import assert from 'node:assert/strict'
import test from 'node:test'
import {
  QCE_REQUIRED_CAPABILITIES,
  qceContainerCreateOptions,
  qceHostConfig,
  qceStableMacAddress,
} from './container-config.js'
import { requireAllowedQceRoute, sanitizeQceConfig } from './qce-route-policy.js'
import { BridgeError, instanceId, messageQuery, validatedId } from './validation.js'

test('instance ids are stable, opaque and validated', () => {
  const id = instanceId('organization-id:membership-id')
  assert.match(id, /^[a-f0-9]{24}$/)
  assert.equal(instanceId('organization-id:membership-id'), id)
  assert.equal(validatedId(id), id)
  assert.throws(() => validatedId('../docker.sock'), (error: unknown) =>
    error instanceof BridgeError && error.code === 'INVALID_INSTANCE_ID')
})

test('message query supports private, temporary and group sessions with bounded pagination', () => {
  assert.deepEqual(messageQuery({ chatType: 2, peerUid: 'group-1', startTime: 1000, endTime: 2000, page: -4, limit: 99999 }), {
    chatType: 2, peerUid: 'group-1', startTime: 1000, endTime: 2000, page: 1, limit: 2000,
  })
  assert.throws(() => messageQuery({ peerUid: '', startTime: 2000, endTime: 1000 }),
    (error: unknown) => error instanceof BridgeError && error.code === 'INVALID_MESSAGE_QUERY')
})

test('the workbench proxy is an explicit capability allowlist rather than a generic proxy', () => {
  assert.equal(requireAllowedQceRoute('GET', '/api/friends?page=1').method, 'GET')
  assert.equal(requireAllowedQceRoute('POST', '/api/messages/fetch').method, 'POST')
  assert.equal(requireAllowedQceRoute('GET', '/downloads/群聊_测试记录_20260814.json').downloadsData, true)
  assert.throws(() => requireAllowedQceRoute('POST', '/api/server/host'),
    (error: unknown) => error instanceof BridgeError && error.code === 'QCE_ROUTE_FORBIDDEN')
  assert.throws(() => requireAllowedQceRoute('GET', '/api/security/ip-whitelist'),
    (error: unknown) => error instanceof BridgeError && error.code === 'QCE_ROUTE_FORBIDDEN')
  assert.throws(() => requireAllowedQceRoute('GET', '/api/friends/../../security.json'),
    (error: unknown) => error instanceof BridgeError && error.code === 'INVALID_QCE_PATH')
  assert.throws(() => requireAllowedQceRoute('GET', '/downloads/群聊\\security.json'),
    (error: unknown) => error instanceof BridgeError && error.code === 'INVALID_QCE_PATH')
})

test('the Bridge allowlist covers every business route shape in the pinned upstream router', () => {
  const reads = [
    '/api/system/info', '/api/system/status', '/api/config', '/api/groups',
    '/api/groups/123', '/api/groups/123/members', '/api/group-system-notify',
    '/api/groups/123/join-requests', '/api/groups/123/essence', '/api/friends',
    '/api/friends/u-1', '/api/recent-contacts', '/api/users/lookup', '/api/users/u-1',
    '/api/tasks', '/api/tasks/t-1', '/api/scheduled-exports', '/api/scheduled-exports/s-1',
    '/api/scheduled-exports/s-1/history', '/api/sticker-packs', '/api/sticker-packs/export-records',
    '/api/groups/123/albums', '/api/groups/123/albums/a-1/media',
    '/api/group-albums/export-records', '/api/groups/123/files', '/api/groups/123/files/count',
    '/api/group-files/export-records', '/api/exports/files', '/api/exports/files/export.zip/info',
    '/api/exports/files/export.zip/preview', '/api/resources/index', '/api/resources/files',
    '/api/merge-resources/available-tasks',
  ]
  for (const path of reads) assert.equal(requireAllowedQceRoute('GET', path).writesData, undefined, path)

  const posts = [
    '/api/messages/export', '/api/messages/export-streaming-zip', '/api/messages/export-streaming-jsonl',
    '/api/groups/123/essence/export', '/api/groups/123/avatars/export',
    '/api/groups/123/albums/export', '/api/groups/123/files/download',
    '/api/groups/123/files/export', '/api/groups/123/files/export-with-download',
    '/api/tasks/t-1/cancel', '/api/scheduled-exports', '/api/scheduled-exports/trigger-all',
    '/api/scheduled-exports/trigger-batch', '/api/scheduled-exports/update-batch',
    '/api/scheduled-exports/delete-batch', '/api/scheduled-exports/s-1/trigger',
    '/api/sticker-packs/export', '/api/sticker-packs/export-all', '/api/merge-resources',
  ]
  for (const path of posts) assert.equal(requireAllowedQceRoute('POST', path).writesData, true, path)
  for (const path of ['/api/config', '/api/scheduled-exports/s-1']) {
    assert.equal(requireAllowedQceRoute('PUT', path).writesData, true, path)
  }
  for (const path of ['/api/tasks/t-1', '/api/tasks/t-1/original-files',
    '/api/scheduled-exports/s-1', '/api/exports/files/export.zip']) {
    assert.equal(requireAllowedQceRoute('DELETE', path).writesData, true, path)
  }
  assert.equal(requireAllowedQceRoute('GET', '/api/exports/files/export.zip/resources/images/a.png').downloadsData, true)
  assert.equal(requireAllowedQceRoute('GET', '/api/resources/export/export.zip').downloadsData, true)

  for (const [method, path] of [['POST', '/auth'], ['POST', '/api/server/host'],
    ['GET', '/api/security/ip-whitelist'], ['POST', '/api/open-file-location'],
    ['POST', '/api/open-export-directory']]) {
    assert.throws(() => requireAllowedQceRoute(method, path),
      (error: unknown) => error instanceof BridgeError && error.code === 'QCE_ROUTE_FORBIDDEN', path)
  }
})

test('QCE configuration strips platform paths, listeners and credentials recursively', () => {
  assert.deepEqual(sanitizeQceConfig({ theme: 'dark', port: 40653, nested: { outputDir: '/host', compact: true }, accessToken: 'secret' }), {
    theme: 'dark', nested: { compact: true },
  })
})

test('QCE containers keep only the capabilities required by the upstream entrypoint', () => {
  const config = qceHostConfig('yingge-qce-test', 'yingge-private')
  assert.deepEqual(config.CapDrop, ['ALL'])
  assert.deepEqual(config.CapAdd, [...QCE_REQUIRED_CAPABILITIES])
  assert.equal('SecurityOpt' in config, false)
  assert.deepEqual(config.LogConfig, { Type: 'none', Config: {} })
  assert.equal(config.NetworkMode, 'yingge-private')
  assert.equal(config.Mounts?.length, 4)
  assert.equal(config.Mounts?.find((mount) => mount.Target === '/app/napcat/config')?.Source,
    'yingge-qce-test-config')
})

test('QCE container identity remains stable across recreation for QQ session persistence', () => {
  const config = qceContainerCreateOptions(
    'yingge-qce-test',
    'yingge-private',
    'yingge/qce:test',
    { 'com.yingge.qq-instance': 'test' },
  )
  assert.equal(config.name, 'yingge-qce-test')
  assert.equal(config.Hostname, 'yingge-qce-test')
  assert.equal(config.MacAddress, qceStableMacAddress('yingge-qce-test'))
  assert.equal(config.NetworkingConfig?.EndpointsConfig?.['yingge-private']?.MacAddress,
    qceStableMacAddress('yingge-qce-test'))
  assert.match(String(config.MacAddress), /^02(?::[0-9a-f]{2}){5}$/)
  assert.notEqual(qceStableMacAddress('yingge-qce-test'), qceStableMacAddress('yingge-qce-other'))
  assert.equal(config.HostConfig?.NetworkMode, 'yingge-private')
  assert.deepEqual(config.Env, ['DOCKER_CONTAINER=true'])
})
