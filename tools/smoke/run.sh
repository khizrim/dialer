#!/usr/bin/env bash
# Дымовой прогон релизной сборки: ставим подписанный APK, проходим главные сценарии, ловим падения.
set -x
P=com.khizrim.dialer
S=shots; mkdir -p $S
shot() { sleep ${2:-3}; adb exec-out screencap -p > $S/$1.png; }
adb wait-for-device
adb install -r -g app/build/outputs/apk/release/*.apk || exit 1
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
bash tools/smoke/seed.sh >/dev/null
adb logcat -c; adb logcat -b crash -c
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1; shot 01-home 8
adb shell am start -a android.intent.action.DIAL -d tel:8916; shot 02-keypad
adb shell input keyevent BACK; adb shell input keyevent HOME
adb emu gsm call +79161112233; shot 03-incoming 6
adb emu gsm accept +79161112233; shot 04-active 4
adb emu gsm cancel +79161112233; shot 05-after 4
adb emu gsm call +74997001122; sleep 6; adb emu gsm cancel +74997001122; sleep 3
adb shell cmd statusbar expand-notifications; shot 06-missed 3
adb shell cmd statusbar collapse
adb shell am start -a android.intent.action.CALL -d tel:+79035556677; shot 07-outgoing 5
adb emu gsm cancel +79035556677; sleep 3
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1; shot 08-home-after 4
adb shell dumpsys package $P | grep -E "versionCode|versionName" > $S/version.txt
adb logcat -d -b crash > $S/crash.txt
adb logcat -d | grep -E "$P|AndroidRuntime" | tail -300 > $S/logcat.txt
if grep -q "$P" $S/crash.txt; then echo CRASH; cat $S/crash.txt; exit 1; fi
exit 0
