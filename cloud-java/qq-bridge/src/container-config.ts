import { createHash } from 'node:crypto'

export const QCE_REQUIRED_CAPABILITIES = [
  'CHOWN',
  'DAC_OVERRIDE',
  'FOWNER',
  'SETGID',
  'SETUID',
] as const

export function qceStableMacAddress(instanceName: string): string {
  const suffix = createHash('sha256').update(instanceName).digest('hex').slice(0, 10)
  return `02:${suffix.match(/.{2}/g)?.join(':')}`
}

export function qceContainerCreateOptions(
  instanceName: string,
  networkName: string,
  image: string,
  labels: Record<string, string>,
): Docker.ContainerCreateOptions {
  const macAddress = qceStableMacAddress(instanceName)
  return {
    name: instanceName,
    // QQ persists a device-bound login session. Docker's random hostname and MAC
    // address change on recreation and make the preserved session look like a
    // different device, so keep both deterministic for this isolated instance.
    // 02 marks a locally administered unicast address.
    Hostname: instanceName,
    MacAddress: macAddress,
    Image: image,
    Env: ['DOCKER_CONTAINER=true'],
    Labels: labels,
    HostConfig: qceHostConfig(instanceName, networkName),
    // Custom Docker networks take their endpoint MAC from NetworkingConfig;
    // keep the top-level value as a compatibility fallback for older engines.
    NetworkingConfig: {
      EndpointsConfig: {
        [networkName]: { MacAddress: macAddress },
      },
    },
  }
}

export function qceHostConfig(instanceName: string, networkName: string): Docker.HostConfig {
  return {
    RestartPolicy: { Name: 'unless-stopped' },
    NetworkMode: networkName,
    Mounts: [
      { Type: 'volume', Source: `${instanceName}-session`, Target: '/app/.config/QQ' },
      { Type: 'volume', Source: `${instanceName}-data`, Target: '/app/.qq-chat-exporter' },
      { Type: 'volume', Source: `${instanceName}-cache`, Target: '/app/napcat/cache' },
      // Override the runtime image's anonymous config volume. This directory
      // contains generated NapCat configuration and must remain attributable to
      // one employee instance so revocation can remove it deterministically.
      { Type: 'volume', Source: `${instanceName}-config`, Target: '/app/napcat/config' },
    ],
    ReadonlyRootfs: false,
    // NapCat prints its generated WebUI token during startup. Do not persist
    // stdout/stderr in Docker's host log store; connection health is exposed by
    // the authenticated Bridge status endpoint instead.
    LogConfig: { Type: 'none', Config: {} },
    CapDrop: ['ALL'],
    // The upstream entrypoint creates configuration files, repairs ownership and
    // drops from root to its napcat user. These are the minimum capabilities it
    // needs; SYS_ADMIN, NET_RAW and host port publication remain unavailable.
    CapAdd: [...QCE_REQUIRED_CAPABILITIES],
  }
}
import type Docker from 'dockerode'
