export const QCE_REQUIRED_CAPABILITIES = [
  'CHOWN',
  'DAC_OVERRIDE',
  'FOWNER',
  'SETGID',
  'SETUID',
] as const

export function qceHostConfig(instanceName: string, networkName: string): Docker.HostConfig {
  return {
    RestartPolicy: { Name: 'unless-stopped' },
    NetworkMode: networkName,
    Mounts: [
      { Type: 'volume', Source: `${instanceName}-session`, Target: '/app/.config/QQ' },
      { Type: 'volume', Source: `${instanceName}-data`, Target: '/app/.qq-chat-exporter' },
      { Type: 'volume', Source: `${instanceName}-cache`, Target: '/app/napcat/cache' },
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
