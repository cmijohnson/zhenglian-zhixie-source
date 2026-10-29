import type Docker from 'dockerode'

type DockerCleanupClient = Pick<Docker, 'getContainer' | 'getVolume'>

export function dockerStatus(error: unknown): number | undefined {
  return typeof error === 'object' && error !== null && 'statusCode' in error
    ? Number((error as { statusCode?: unknown }).statusCode)
    : undefined
}

export async function removeRevokedInstance(
  docker: DockerCleanupClient,
  name: string,
  wait: (milliseconds: number) => Promise<unknown> = (milliseconds) =>
    new Promise((resolve) => setTimeout(resolve, milliseconds)),
) {
  const container = docker.getContainer(name)
  try {
    const state = await container.inspect()
    if (state.State.Running) await container.stop({ t: 10 })
    await container.remove()
  } catch (error) {
    if (dockerStatus(error) !== 404) throw error
  }

  // Docker can briefly report a named volume as still in use immediately after
  // container removal. Retry that narrow, expected race so revocation is
  // idempotent and never leaves the database pointing at a deleted container.
  for (const suffix of ['session', 'data', 'cache', 'config']) {
    const volume = docker.getVolume(`${name}-${suffix}`)
    for (let attempt = 0; attempt < 5; attempt += 1) {
      try {
        await volume.remove()
        break
      } catch (error) {
        const status = dockerStatus(error)
        if (status === 404) break
        if (status !== 409 || attempt === 4) throw error
        await wait(200 * (attempt + 1))
      }
    }
  }
}
