import assert from 'node:assert/strict'
import test from 'node:test'
import { buildAutoLoginPersistenceCommand } from './session-persistence.js'

test('QQ session persistence passes a validated account identifier as a separate argument', () => {
  const command = buildAutoLoginPersistenceCommand('123456789')
  assert.deepEqual(command.slice(0, 2), ['sh', '-c'])
  assert.equal(command.at(-1), '123456789')
  assert.equal(command[2].includes('123456789'), false)
  assert.equal(command[2].includes('/app/napcat/config/webui.json'), true)
  assert.equal(command[2].includes('jq --arg uin'), true)
})

test('QQ session persistence rejects invalid account identifiers', () => {
  for (const value of ['', '1234', '1234567890123', '12;touch /tmp/x', 'abc123']) {
    assert.throws(() => buildAutoLoginPersistenceCommand(value), /invalid QQ account identifier/)
  }
})
