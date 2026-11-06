# Shared helpers for the scripts. Not meant to be run on its own.
SCRIPTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IP_FILE="$SCRIPTS_DIR/.tv-ip"
APP_ID="com.agentictv.launcher"

need_adb() {
  if ! command -v adb >/dev/null 2>&1; then
    echo "adb isn't installed yet. Run: scripts/setup-mac.sh"
    exit 1
  fi
}

# Makes sure we're connected to the TV, using the IP saved by connect.sh.
ensure_connected() {
  need_adb
  if [ ! -f "$IP_FILE" ]; then
    echo "I don't know the TV's address yet. Run: scripts/connect.sh <TV IP>"
    exit 1
  fi
  TV="$(cat "$IP_FILE")"
  adb connect "$TV" >/dev/null 2>&1 || true
  if ! adb -s "$TV" get-state >/dev/null 2>&1; then
    echo "Can't reach the TV at $TV. Is it on, on the same Wi-Fi, with debugging on?"
    exit 1
  fi
}

tv() { adb -s "$TV" "$@"; }

# Saves a settings file inside the app on the TV (only our app can read it).
# Sent base64-encoded so special characters in passwords survive the trip.
write_app_file() {
  local name="$1" content="$2"
  local b64; b64="$(printf '%s' "$content" | base64 | tr -d '\n')"
  tv shell am broadcast -n "$APP_ID/.ConfigReceiver" -a com.agentictv.SET_CONFIG \
    --es file "$name" --es b64 "$b64" >/dev/null
}
