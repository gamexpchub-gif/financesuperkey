#!/usr/bin/env bash
#
# Finance Super Key — device enrollment helper (run on the DEALER'S computer).
#
# Uses Android's built-in Wireless Debugging to install the Customer app
# (Finance Key) and set it as Device Owner — the same end result as the in-app
# method, done reliably with standard adb. No factory reset needed.
#
# Requirements on the computer:  adb (android platform-tools)
# Requirement on the phone:      NO Google/other account added yet
#                                (a fresh phone, or Settings > remove accounts)
#
# Usage:
#   ./enroll-device.sh path/to/finance-key.apk
#
set -euo pipefail

PKG_COMPONENT="com.financesuperkey.dpc/.DeviceOwnerReceiver"
APK="${1:-finance-key.apk}"

command -v adb >/dev/null 2>&1 || { echo "ERROR: adb not found. Install Android platform-tools."; exit 1; }
[ -f "$APK" ] || { echo "ERROR: APK not found: $APK"; echo "Pass the path: ./enroll-device.sh path/to/finance-key.apk"; exit 1; }

echo "================================================================"
echo " Finance Super Key — device enrollment"
echo "================================================================"
echo
echo "On the CUSTOMER's phone:"
echo "  1. Settings > About phone > tap 'Build number' 7 times (enables Developer options)"
echo "  2. Settings > System > Developer options > Wireless debugging > turn ON"
echo "  3. Make sure the phone and this computer are on the SAME Wi-Fi."
echo "  4. Tap 'Pair device with pairing code' — note the shown IP:PORT and 6-digit code."
echo

read -r -p "Pair address (IP:PORT from the pairing dialog, e.g. 192.168.0.5:37123): " PAIR_ADDR
read -r -p "6-digit pairing code: " PAIR_CODE

echo "Pairing…"
adb pair "$PAIR_ADDR" "$PAIR_CODE"

echo
echo "Now look at the MAIN Wireless debugging screen (not the pairing dialog)."
read -r -p "Connect address (IP:PORT shown there, e.g. 192.168.0.5:40011): " CONN_ADDR

echo "Connecting…"
adb connect "$CONN_ADDR"

echo "Installing Finance Key…"
adb -s "$CONN_ADDR" install -r "$APK" || echo "(install skipped — may already be installed)"

echo "Setting Device Owner…"
if adb -s "$CONN_ADDR" shell dpm set-device-owner "$PKG_COMPONENT"; then
  echo
  echo "================================================================"
  echo " ✓ SUCCESS — Finance Key is now Device Owner."
  echo "   Open Finance Key on the phone, enter the enrollment token"
  echo "   (from the Merchant app), and complete customer details."
  echo "   IMEI & device name fill in automatically. Factory reset is blocked."
  echo "================================================================"
else
  echo
  echo "✗ set-device-owner failed. Most common cause: an account is already on"
  echo "  the phone. Remove ALL accounts (Settings > Accounts) and try again,"
  echo "  or factory reset the phone first (then skip reset next time)."
  exit 1
fi
