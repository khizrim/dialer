#!/usr/bin/env bash
set -x
P=com.khizrim.dialer
adb root; sleep 3; adb wait-for-device
adb install -r -g app/build/outputs/apk/debug/*.apk
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
adb shell cmd locale set-app-locales $P --locales ru-RU || true
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
bash tools/emulator/seed.sh
adb logcat -c
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS; sleep 20; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
mkdir -p shots; OUT=shots python3 tools/emulator/anim.py > shots/anim-log.txt 2>&1 || true
OUT=shots python3 tools/emulator/review.py || true
adb logcat -d -b crash > shots/crash.txt || true
adb logcat -d | grep -E "$P|AndroidRuntime|Choreographer" | tail -400 > shots/logcat.txt || true
adb shell dumpsys gfxinfo $P > shots/gfxinfo.txt || true
exit 0
