#!/usr/bin/env bash
# Звонки от незнакомых: видны ли в ленте, раскрывается ли группа «без звука», открывается ли карточка номера.
set -x
P=com.khizrim.dialer
S=shots; mkdir -p $S
shot() { sleep ${2:-3}; adb exec-out screencap -p > $S/$1.png; adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1; adb shell cat /sdcard/u.xml | tr '>' '\n' | grep -o 'text="[^"]*"\|content-desc="[^"]*"' | grep -v '=""' > $S/$1.txt; }
tap_text() { # первый узел с этим текстом или описанием
  adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1
  b=$(adb shell cat /sdcard/u.xml | tr '>' '\n' | grep -m1 -F "$1" | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
  set -- $b; [ -n "$4" ] && adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 ))
}
adb wait-for-device
adb install -r -g app/build/outputs/apk/debug/*.apk || exit 1
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true
adb shell cmd role add-role-holder android.app.role.CALL_SCREENING $P 0 || true
bash tools/smoke/seed.sh >/dev/null
now=$(($(date +%s)*1000))
for n in "+79991110000:6:15:0" "+79991110011:6:25:0" "+74951112200:1:10:60" "+79031234500:2:20:30" "+78005553535:3:40:0" "+74997001199:3:70:0"; do
  IFS=: read num t m d <<< "$n"
  adb shell content insert --uri content://call_log/calls --bind number:s:"$num" --bind type:i:$t --bind date:l:$((now-m*60000)) --bind duration:l:$d --bind new:i:1
done
adb logcat -c; adb logcat -b crash -c
for mode in off on; do
  adb shell am force-stop $P
  adb shell run-as $P mkdir -p shared_prefs
  adb shell "run-as $P sh -c 'cat shared_prefs/*.xml'" > $S/prefs-$mode.txt 2>&1
  if [ $mode = on ]; then
    for f in $(adb shell run-as $P ls shared_prefs | tr -d '\r'); do
      adb shell "run-as $P sed -i 's#<boolean name=\"silenceUnknown\" value=\"false\" />##; s#</map>#<boolean name=\"silenceUnknown\" value=\"true\" /></map>#' shared_prefs/$f"
    done
  fi
  adb shell monkey -p $P -c android.intent.category.LAUNCHER 1; shot $mode-01-home 8
  tap_text "unknown, silenced"; shot $mode-02-silent-open 3
  tap_text "text=\"+7 (495) 111-22-00"; shot $mode-03-unknown-card 4
  adb shell input keyevent BACK; sleep 2
  adb shell input swipe 540 1400 540 600 300; shot $mode-04-contacts 3
  adb shell input text 8005; shot $mode-05-search-number 3
  adb shell input keyevent BACK; adb shell input keyevent BACK; adb shell input keyevent HOME
done
adb logcat -d -b crash > $S/crash.txt
exit 0
