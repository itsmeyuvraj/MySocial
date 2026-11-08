#!/usr/bin/env bash
# Bridge physical Android device (via USB) and Android Emulator on host machine
# so that offline mesh P2P Wi-Fi / TCP gossip packets synchronize seamlessly.

set -e

PHONE_DEVICE=$(adb devices | grep -v "List" | grep -v "emulator" | grep "device" | awk '{print $1}' | head -n 1)
EMULATOR_DEVICE=$(adb devices | grep "emulator" | grep "device" | awk '{print $1}' | head -n 1)

if [ -z "$PHONE_DEVICE" ]; then
    echo "⚠️ No physical device found via adb. Looking for any connected device..."
fi

if [ -z "$EMULATOR_DEVICE" ]; then
    echo "⚠️ No emulator found via adb."
fi

echo "Setting up ADB mesh bridge..."
echo "Physical Phone : ${PHONE_DEVICE:-Not Found}"
echo "Emulator       : ${EMULATOR_DEVICE:-Not Found}"

if [ -n "$PHONE_DEVICE" ]; then
    # Forward host port 42530 to phone server port 42530
    adb -s "$PHONE_DEVICE" forward tcp:42530 tcp:42530
    # Reverse phone port 42535 to host port 42535
    adb -s "$PHONE_DEVICE" reverse tcp:42535 tcp:42535
    echo "✅ Phone forward & reverse established."
fi

if [ -n "$EMULATOR_DEVICE" ]; then
    # Forward host port 42535 to emulator server port 42530
    adb -s "$EMULATOR_DEVICE" forward tcp:42535 tcp:42530
    echo "✅ Emulator forward established."
fi

echo "Active port forwards:"
adb forward --list
if [ -n "$PHONE_DEVICE" ]; then
    adb -s "$PHONE_DEVICE" reverse --list
fi

echo "🚀 ADB bridge active! Posts will sync bidirectionally between Phone and Emulator."
