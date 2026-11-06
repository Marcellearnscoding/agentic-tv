#!/usr/bin/env bash
# Sets the town for the weather on the home screen.
# It looks the town up on Open-Meteo (free, no account) and saves only the name
# and coordinates on the TV. Never in GitHub.
# Usage: scripts/set-weather.sh "Hamburg"
set -e
source "$(dirname "$0")/_common.sh"
ensure_connected

TOWN="${1:-}"
if [ -z "$TOWN" ]; then read -r -p "Town for the weather: " TOWN; fi

# Ask Open-Meteo where the town is. Python is only used to read the answer.
ANSWER="$(curl -fsS --get "https://geocoding-api.open-meteo.com/v1/search" \
  --data-urlencode "name=$TOWN" --data-urlencode "count=1" --data-urlencode "language=en")"
JSON="$(printf '%s' "$ANSWER" | python3 -c '
import json, sys
r = json.load(sys.stdin).get("results") or []
if not r: sys.exit("Town not found. Try another spelling.")
p = r[0]
print(json.dumps({"name": p["name"], "latitude": round(p["latitude"], 2), "longitude": round(p["longitude"], 2)}))
')"

write_app_file weather.json "$JSON"
echo "Saved: $JSON"
echo "Press Home on the TV to see the weather."
