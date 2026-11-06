#!/usr/bin/env bash
# Lists the apps installed on the TV (handy for editing Favorites.kt).
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected
tv shell pm list packages -3 | sed 's/package://' | sort
