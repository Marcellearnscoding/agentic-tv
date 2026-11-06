#!/usr/bin/env bash
# One-time setup on your Mac: installs adb (the tool that talks to the TV).
set -e
if command -v adb >/dev/null 2>&1; then echo "adb is already installed."; exit 0; fi
if ! command -v brew >/dev/null 2>&1; then
  echo "Homebrew is needed first. Install it from https://brew.sh then run this again."
  exit 1
fi
brew install --cask android-platform-tools
echo "Done. Next: scripts/connect.sh <TV IP>"
