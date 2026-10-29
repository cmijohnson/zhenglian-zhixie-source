const WEBUI_CONFIG_PATH = '/app/napcat/config/webui.json'

export function buildAutoLoginPersistenceCommand(uin: string): string[] {
  if (!/^\d{5,12}$/.test(uin)) {
    throw new Error('invalid QQ account identifier')
  }
  const script = [
    'set -eu',
    'uin="$1"',
    `path='${WEBUI_CONFIG_PATH}'`,
    'tmp="${path}.yingge.tmp"',
    'case "$uin" in (*[!0-9]*|????|?????????????*) exit 2;; esac',
    'jq --arg uin "$uin" ".autoLoginAccount = \$uin" "$path" > "$tmp"',
    // Truncate the existing file rather than renaming over it, preserving the
    // ownership and mode established by the upstream NapCat entrypoint.
    'cat "$tmp" > "$path"',
    'rm -f "$tmp"',
  ].join(';')
  // Keep the account identifier as a separate argv entry. It must never be
  // interpolated into executable code or emitted to Bridge logs.
  return ['sh', '-c', script, 'yingge-session-persistence', uin]
}
