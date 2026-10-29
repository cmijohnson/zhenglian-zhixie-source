import assert from 'node:assert/strict'
import { EventEmitter } from 'node:events'
import test from 'node:test'
import { readContainerFile } from './container-file.js'

function mockContainer(stdout: Buffer, options: { exitCode?: number; streamError?: Error } = {}) {
  const stream = new EventEmitter()
  const exec = {
    start: async () => stream,
    inspect: async () => ({ ExitCode: options.exitCode ?? 0 }),
  }
  const container = {
    exec: async () => exec,
    modem: {
      demuxStream: (_stream: unknown, stdoutSink: { write: (chunk: Buffer) => void }) => {
        queueMicrotask(() => {
          stdoutSink.write(stdout)
          if (options.streamError) stream.emit('error', options.streamError)
          else stream.emit('end')
        })
      },
    },
  }
  return { container: container as any, stream }
}

test('container file reader returns binary content from a successful bounded exec', async () => {
  const expected = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x00, 0xff])
  const { container } = mockContainer(expected)
  assert.deepEqual(await readContainerFile(container, '/app/napcat/cache/qrcode.png'), expected)
})

test('container file reader turns a truncated transport into an absent transient file', async () => {
  const { container } = mockContainer(Buffer.from('partial'), { streamError: new Error('Unexpected end of data') })
  assert.equal(await readContainerFile(container, '/app/napcat/cache/qrcode.png'), undefined)
})

test('container file reader leaves a late stream error handled after end', async () => {
  const { container, stream } = mockContainer(Buffer.from('ok'))
  assert.deepEqual(await readContainerFile(container, '/app/file'), Buffer.from('ok'))
  assert.doesNotThrow(() => stream.emit('error', new Error('late Docker teardown error')))
})

test('container file reader rejects oversized or failed reads without returning partial data', async () => {
  const oversized = mockContainer(Buffer.alloc(5))
  assert.equal(await readContainerFile(oversized.container, '/app/file', 4), undefined)

  const failed = mockContainer(Buffer.from('not found'), { exitCode: 1 })
  assert.equal(await readContainerFile(failed.container, '/app/file'), undefined)
})
