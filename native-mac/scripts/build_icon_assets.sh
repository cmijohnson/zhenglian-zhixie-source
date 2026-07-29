#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_PNG="$ROOT_DIR/Assets/Brand/logo-master.png"
ICONSET_DIR="$ROOT_DIR/Assets/Icon.iconset"
OUTPUT_ICNS="$ROOT_DIR/Assets/AppIcon.icns"

if [[ ! -f "$SOURCE_PNG" ]]; then
  echo "Logo source not found: $SOURCE_PNG" >&2
  exit 1
fi

rm -rf "$ICONSET_DIR"
mkdir -p "$ICONSET_DIR"

create_icon() {
  local size="$1"
  local name="$2"
  sips -z "$size" "$size" "$SOURCE_PNG" --out "$ICONSET_DIR/$name" >/dev/null
}

create_icon 16 icon_16x16.png
create_icon 32 icon_16x16@2x.png
create_icon 32 icon_32x32.png
create_icon 64 icon_32x32@2x.png
create_icon 128 icon_128x128.png
create_icon 256 icon_128x128@2x.png
create_icon 256 icon_256x256.png
create_icon 512 icon_256x256@2x.png
create_icon 512 icon_512x512.png
create_icon 1024 icon_512x512@2x.png

iconutil -c icns "$ICONSET_DIR" -o "$OUTPUT_ICNS"
echo "Generated app icon: $OUTPUT_ICNS"
