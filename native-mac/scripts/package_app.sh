#!/bin/zsh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/.build/arm64-apple-macosx/release"
DIST_DIR="$ROOT_DIR/dist"
APP_NAME="英格学生档案"
APP_DIR="$DIST_DIR/$APP_NAME.app"
CONTENTS_DIR="$APP_DIR/Contents"
MACOS_DIR="$CONTENTS_DIR/MacOS"
RESOURCES_DIR="$CONTENTS_DIR/Resources"
EXECUTABLE_NAME="YinggeNativeApp"
EXECUTABLE_PATH="$BUILD_DIR/$EXECUTABLE_NAME"
INFO_PLIST_TEMPLATE="$ROOT_DIR/scripts/Info.plist"
ZIP_BUNDLE_PATH="$BUILD_DIR/ZIPFoundation_ZIPFoundation.bundle"
ZIP_PATH="$DIST_DIR/$APP_NAME-macOS.zip"
APP_ICON_PATH="$ROOT_DIR/Assets/AppIcon.icns"

echo "==> Building release executable"
cd "$ROOT_DIR"
./scripts/build_icon_assets.sh
swift build -c release --product "$EXECUTABLE_NAME"

if [[ ! -f "$EXECUTABLE_PATH" ]]; then
  echo "Release executable not found at: $EXECUTABLE_PATH" >&2
  exit 1
fi

echo "==> Creating app bundle"
rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR" "$RESOURCES_DIR"
cp "$EXECUTABLE_PATH" "$MACOS_DIR/$EXECUTABLE_NAME"
chmod +x "$MACOS_DIR/$EXECUTABLE_NAME"
cp "$INFO_PLIST_TEMPLATE" "$CONTENTS_DIR/Info.plist"
cp "$APP_ICON_PATH" "$RESOURCES_DIR/AppIcon.icns"

if [[ -d "$ZIP_BUNDLE_PATH" ]]; then
  cp -R "$ZIP_BUNDLE_PATH" "$RESOURCES_DIR/"
fi

echo "==> Ad-hoc signing bundle"
codesign --force --deep --sign - "$APP_DIR"

echo "==> Creating zip archive"
mkdir -p "$DIST_DIR"
rm -f "$ZIP_PATH"
ditto -c -k --sequesterRsrc --keepParent "$APP_DIR" "$ZIP_PATH"

echo "App bundle: $APP_DIR"
echo "Zip archive: $ZIP_PATH"
