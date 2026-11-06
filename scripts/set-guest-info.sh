#!/usr/bin/env bash
# Puts the guest Wi-Fi details on the TV, for the welcome card and its QR code.
# They're saved inside the app on the TV only. Never in GitHub.
# Usage: scripts/set-guest-info.sh            (it asks you)
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected

read -r -p "Wi-Fi name for guests: " WIFI_NAME
read -r -s -p "Wi-Fi password (hidden, leave empty if none): " WIFI_PASS; echo
read -r -p "Welcome line (Enter for the default): " WELCOME

# Make the text safe to put inside JSON.
esc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }
JSON="{\"wifiName\":\"$(esc "$WIFI_NAME")\",\"wifiPassword\":\"$(esc "$WIFI_PASS")\",\"welcome\":\"$(esc "$WELCOME")\"}"

write_app_file guest.json "$JSON"
echo "Saved. Press Home on the TV to see it."
