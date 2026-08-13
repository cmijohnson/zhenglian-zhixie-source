#!/bin/sh
set -eu

revision="58fc533f1bfdbf4535be0f83af4e595d4d7ecf27"
image="${QCE_IMAGE:-yingge/napcat-qce:${revision}}"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT INT TERM

run_git() {
  if command -v git >/dev/null 2>&1; then
    git "$@"
  else
    docker run --rm -v "$workdir:$workdir" -w "$workdir" alpine/git:latest "$@"
  fi
}

run_git clone --filter=blob:none https://github.com/shuakami/qq-chat-exporter.git "$workdir/qq-chat-exporter"
run_git -C "$workdir/qq-chat-exporter" checkout --detach "$revision"
test "$(run_git -C "$workdir/qq-chat-exporter" rev-parse HEAD)" = "$revision"
test -f "$workdir/qq-chat-exporter/LICENSE"
docker build --label "org.opencontainers.image.source=https://github.com/shuakami/qq-chat-exporter" \
  --label "org.opencontainers.image.revision=$revision" \
  --label "org.opencontainers.image.licenses=GPL-3.0" \
  -f "$workdir/qq-chat-exporter/docker/Dockerfile" \
  -t "$image" "$workdir/qq-chat-exporter"

printf 'Built %s from pinned revision %s\n' "$image" "$revision"
