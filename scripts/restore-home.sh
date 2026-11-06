#!/usr/bin/env bash
# Brings back the TV's original home screen (undoes set-home.sh --force).
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected

for pkg in com.google.android.tvlauncher com.google.android.apps.tv.launcherx \
           $(cat "$SCRIPTS_DIR/.disabled-launchers" 2>/dev/null); do
  tv shell pm enable "$pkg" >/dev/null 2>&1 && echo "Switched back on: $pkg" || true
done
rm -f "$SCRIPTS_DIR/.disabled-launchers"
echo "Done. Press Home on the remote."
