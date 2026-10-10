#!/bin/bash
# เปิดแอปบนอีมูเลเตอร์ แล้วเก็บภาพหน้าจอ/ล็อก เพื่อตรวจว่ารันบน Android เก่าได้จริง
API="$1"
OUT="smoke-out"
mkdir -p "$OUT"
PKG=com.tadiotv.app
adb shell getprop ro.build.version.release > "$OUT/android.txt"
adb install -r app-debug.apk > "$OUT/install.txt" 2>&1
cat "$OUT/install.txt"
adb logcat -c
adb shell am start -n $PKG/.MainActivity > "$OUT/start.txt" 2>&1
sleep 40
adb shell screencap -p /sdcard/s1.png && adb pull /sdcard/s1.png "$OUT/1-after-40s.png"
adb shell input swipe 240 700 240 300 400; sleep 2
adb shell input swipe 240 700 240 300 400; sleep 2
adb shell screencap -p /sdcard/s2.png && adb pull /sdcard/s2.png "$OUT/2-after-scroll.png"
sleep 90
adb shell screencap -p /sdcard/s3.png && adb pull /sdcard/s3.png "$OUT/3-after-2min.png"
adb shell "ps | grep $PKG" > "$OUT/alive.txt" 2>&1
adb logcat -d -v time > "$OUT/logcat-full.txt" 2>&1
grep -E "AndroidRuntime|FATAL|OutOfMemory|SSLHandshake|CertPath|Trust anchor|ExoPlayer|tadiotv" "$OUT/logcat-full.txt" | tail -200 > "$OUT/logcat-filtered.txt"
adb shell run-as $PKG cat files/crash.log > "$OUT/crash.log" 2>&1
echo "API=$API alive:"; cat "$OUT/alive.txt"
exit 0
