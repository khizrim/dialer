#!/usr/bin/env bash
# Тихий незнакомый во время разговора: не должно быть ни экрана, ни уведомления с кнопками, ни сигнала.
set -x
P=com.khizrim.dialer
S=shots; mkdir -p $S
shot() { sleep ${2:-3}; adb exec-out screencap -p > $S/$1.png; adb shell dumpsys notification --noredact | grep -E "pkg=|android.title=|android.text=|actions=|category=|fullscreenIntent" > $S/$1-notif.txt; }
adb wait-for-device
adb install -r -g app/build/outputs/apk/debug/*.apk || exit 1
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
bash tools/smoke/seed.sh >/dev/null
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1; sleep 6
adb shell am force-stop $P
for f in $(adb shell run-as $P ls shared_prefs | tr -d '\r'); do
  adb shell "run-as $P sed -i 's#<boolean name=\"silenceUnknown\" value=\"false\" />##; s#</map>#<boolean name=\"silenceUnknown\" value=\"true\" /></map>#' shared_prefs/$f"
done
adb shell "run-as $P sh -c 'cat shared_prefs/ozero.xml'" | grep -i silence > $S/prefs.txt
adb shell monkey -p $P -c android.intent.category.LAUNCHER 1; sleep 5
adb logcat -c
# 1) незнакомый в простое
adb emu gsm call +79991230001; shot 01-idle-unknown 6
adb shell dumpsys audio | grep -iE "ringtone|player.*(RING|usage=6)" | head -5 > $S/01-audio.txt
adb emu gsm cancel +79991230001; sleep 4
# 2) разговор с Мамой, потом незнакомый
adb emu gsm call +79161112233; sleep 4; adb emu gsm accept +79161112233; sleep 4
adb emu gsm call +79991230002; shot 02-waiting-unknown 6
adb shell dumpsys telecom | grep -iE "call waiting|CallWaiting|silen|Ringer|isRinging|state=" | head -40 > $S/02-telecom.txt
shot 03-waiting-later 6
adb emu gsm cancel +79991230002; adb emu gsm cancel +79161112233; sleep 3
adb logcat -d | grep -iE "Telecom|Ringer|InCallTone|dialer" | grep -iE "silen|wait|tone|ring" | tail -80 > $S/logcat.txt
adb logcat -d -b crash > $S/crash.txt
exit 0
