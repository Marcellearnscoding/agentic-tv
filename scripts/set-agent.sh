#!/usr/bin/env bash
# Tells the TV where the assistant's brain is and its password (TV_TOKEN).
# Saved inside the app on the TV only. Never in GitHub.
# Usage: scripts/set-agent.sh <function URL>     (it asks for the token)
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected

URL="${1:?Give me the function URL, e.g. https://xyz.supabase.co/functions/v1/tv-agent}"
read -r -s -p "TV token (hidden): " TOKEN; echo
write_app_file agent.json "{\"url\":\"$URL\",\"token\":\"$TOKEN\"}"
echo "Saved. Open the assistant on the TV and try it."
