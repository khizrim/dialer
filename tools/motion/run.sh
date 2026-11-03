#!/usr/bin/env bash
# Запись всех переходов Ozero Phone в эмуляторе: видео каждого перехода -> покадровые ленты.
set -x
P=com.khizrim.dialer
adb root; sleep 3; adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
# окна «не отвечает» на медленном эмуляторе без GPU закрывают кадр: прячем их и даём системе отдышаться
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true
sleep 45
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb install -r app/build/outputs/apk/debug/*.apk
for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 1; done
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
bash tools/motion/seed.sh >/dev/null 2>&1
adb shell cmd locale set-app-locales $P --locales ru-RU || true
mkdir -p motion
OUT=motion python3 tools/motion/walk.py || true
adb logcat -d -b crash > motion/crash.txt || true
exit 0
