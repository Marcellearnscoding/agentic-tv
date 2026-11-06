#!/usr/bin/env bash
# Puts the newest version of the home screen app on the TV.
# It downloads the APK that GitHub built, or uses a file you give it:
#   scripts/install.sh               (newest build from GitHub)
#   scripts/install.sh path/to.apk   (a specific file)
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected

APK="$1"
if [ -z "$APK" ]; then
  TMP="$(mktemp -d)"
  echo "Downloading the newest build from GitHub..."
  if command -v gh >/dev/null 2>&1; then
    gh release download latest --repo Marcellearnscoding/agentic-tv --pattern '*.apk' --dir "$TMP"
  else
    curl -fL -o "$TMP/agentic-tv.apk" \
      https://github.com/Marcellearnscoding/agentic-tv/releases/download/latest/agentic-tv.apk \
      || { echo "Download failed. If the repo is private, install the GitHub CLI: brew install gh && gh auth login"; exit 1; }
  fi
  APK="$(ls "$TMP"/*.apk | head -1)"
fi

echo "Installing on the TV..."
tv install -r "$APK"
echo "Opening it..."
tv shell am start -n "$APP_ID/.MainActivity" >/dev/null
echo "Done! It's on the TV now."
