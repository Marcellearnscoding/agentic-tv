#!/usr/bin/env bash
# Connects your Mac to the TV over Wi-Fi and remembers its address.
# Usage: scripts/connect.sh 192.168.1.42
# Find the TV's IP in Settings > Network & Internet > (your Wi-Fi).
set -e
source "$(dirname "$0")/_common.sh"
need_adb

ADDR="${1:?Give me the TV's IP, e.g. scripts/connect.sh 192.168.1.42}"
case "$ADDR" in *:*) ;; *) ADDR="$ADDR:5555" ;; esac

echo "Connecting to $ADDR ... look at the TV: if it asks 'Allow USB debugging?', pick Always allow."
adb connect "$ADDR"
sleep 2
if adb -s "$ADDR" get-state >/dev/null 2>&1; then
  echo "$ADDR" > "$IP_FILE"
  echo "Connected to: $(adb -s "$ADDR" shell getprop ro.product.model)"
else
  echo "Not connected yet. Accept the prompt on the TV, then run this again."
  exit 1
fi
