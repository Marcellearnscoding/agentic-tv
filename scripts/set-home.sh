#!/usr/bin/env bash
# Makes our app the TV's home screen, so the Home button opens it.
#   scripts/set-home.sh          gentle: just asks Android to use our app as home
#   scripts/set-home.sh --force  also switches off the TV's own home screen
# Undo anytime with scripts/restore-home.sh. Nothing is deleted.
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected

current_home() {
  tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1 | tr -d '\r'
}

echo "Home screen right now: $(current_home)"
tv shell cmd package set-home-activity "$APP_ID/.MainActivity" >/dev/null 2>&1 || true

if [ "$1" = "--force" ]; then
  OLD="$(current_home | cut -d/ -f1)"
  if [ -n "$OLD" ] && [ "$OLD" != "$APP_ID" ]; then
    echo "Switching off the TV's own home screen: $OLD"
    tv shell pm disable-user --user 0 "$OLD"
    echo "$OLD" >> "$SCRIPTS_DIR/.disabled-launchers"
  fi
fi

echo "Home screen now: $(current_home)"
echo "Press Home on the remote to check. If the old one still shows, run: scripts/set-home.sh --force"
