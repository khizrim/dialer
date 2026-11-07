#!/usr/bin/env bash
# Проход по сценариям JTBD в эмуляторе (как у пользователя: английский интерфейс, регион RU).
set -x
P=com.khizrim.dialer
adb root; sleep 3; adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'; sleep 20
adb shell svc power stayon true || true
adb shell setprop gsm.sim.operator.iso-country ru || true
adb shell setprop gsm.operator.iso-country ru || true
adb shell settings put system screen_off_timeout 1800000 || true
adb shell settings put global hide_error_dialogs 1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb install -r app/build/outputs/apk/debug/*.apk
mkdir -p shots
OUT=shots python3 tools/jtbd/walk.py onboarding || true
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
bash tools/jtbd/seed.sh > shots/seed.txt 2>&1
OUT=shots timeout 1800 python3 tools/jtbd/walk.py main || true
adb logcat -d -b crash > shots/crash.txt || true
exit 0
