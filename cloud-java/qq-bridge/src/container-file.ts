import type Docker from 'dockerode'
import { Writable } from 'node:stream'

export const DEFAULT_CONTAINER_FILE_LIMIT = 8 * 1024 * 1024
const STDERR_LIMIT = 64 * 1024

/**
 * Reads one trusted, fixed container path without Docker's tar archive API.
 *
 * getArchive() can produce a truncated tar stream while NapCat atomically
 * replaces its QR code. tar-stream may then emit a second, late error after the
 * caller has already handled the first rejection, which used to terminate the
 * Bridge process. A bounded `cat` exec keeps that transient race local to this
 * request and never exposes the container path to a shell.
 */
export async function readContainerFile(
  container: Docker.Container,
  path: string,
  maximumBytes = DEFAULT_CONTAINER_FILE_LIMIT,
): Promise<Buffer | undefined> {
  if (!path.startsWith('/') || !Number.isSafeInteger(maximumBytes) || maximumBytes < 1) return undefined

  try {
    const exec = await container.exec({ Cmd: ['cat', path], AttachStdout: true, AttachStderr: true })
    const stream = await exec.start({ hijack: true, stdin: false })
    const stdout: Buffer[] = []
    const stderr: Buffer[] = []
    let stdoutBytes = 0
    let stderrBytes = 0
    let tooLarge = false

    const stdoutSink = new Writable({
      write(chunk: Buffer | string, _encoding, callback) {
        const value = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
        stdoutBytes += value.length
        if (stdoutBytes <= maximumBytes) stdout.push(Buffer.from(value))
        else tooLarge = true
        callback()
      },
    })
    const stderrSink = new Writable({
      write(chunk: Buffer | string, _encoding, callback) {
        const value = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
        if (stderrBytes < STDERR_LIMIT) {
          const remaining = STDERR_LIMIT - stderrBytes
          const captured = value.subarray(0, remaining)
          stderr.push(Buffer.from(captured))
          stderrBytes += captured.length
        }
        callback()
      },
    })

    await new Promise<void>((resolve, reject) => {
      let settled = false
      const finish = (error?: Error) => {
        if (settled) return
        settled = true
        if (error) reject(error)
        else resolve()
      }

      // Keep the error listener installed even after `end`. Some Docker stream
      // implementations can surface a late transport error during teardown.
      stream.once('error', (error) => finish(error instanceof Error ? error : new Error(String(error))))
      stream.once('end', () => finish())
      stream.once('close', () => finish())
      try {
        ;(container as any).modem.demuxStream(stream, stdoutSink, stderrSink)
      } catch (error) {
        finish(error instanceof Error ? error : new Error(String(error)))
      }
    })

    const inspected = await exec.inspect()
    if ((inspected.ExitCode ?? 1) !== 0 || tooLarge) return undefined
    return Buffer.concat(stdout, stdoutBytes)
  } catch {
    return undefined
  }
}
