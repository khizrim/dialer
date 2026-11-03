#!/usr/bin/env bash
# Дизайн-прожарка: проход по экранам сборки на английском (как у пользователя) и на русском.
set -x
P=com.khizrim.dialer
adb root; sleep 3; adb wait-for-device
# лаунчер на эмуляторе без GPU падает в «не отвечает» и закрывает собой экран: выключаем его
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'; sleep 20
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb install -r app/build/outputs/apk/debug/*.apk
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
mkdir -p shots
# 1. первый запуск без разрешений: онбординг
OUT=shots/onb LANGSET=en python3 tools/roast/walk.py onboarding || true
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
bash tools/roast/seed.sh
# 2. английский (системный язык пользователя)
OUT=shots/en LANGSET=en python3 tools/roast/walk.py main || true
# 3. русский
adb shell cmd locale set-app-locales $P --locales ru-RU || true
OUT=shots/ru LANGSET=ru python3 tools/roast/walk.py main || true
adb logcat -d -b crash > shots/crash.txt || true
exit 0
