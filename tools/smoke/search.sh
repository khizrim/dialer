#!/usr/bin/env bash
# Дымовая проверка в эмуляторе: приложение открывается, поиск (контакты) открывается и ищет, ничего не падает.
# Контакты подобраны так, чтобы ловить прошлые падения: «Ёлка» рядом с «Еленой», «Émile» рядом с «Ema», номер вместо имени.
set -x
P=com.khizrim.dialer
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
adb shell settings put global hide_error_dialogs 1 || true
sleep 20
adb install -r app/build/outputs/apk/debug/*.apk || exit 1
for p in READ_CONTACTS WRITE_CONTACTS READ_CALL_LOG WRITE_CALL_LOG CALL_PHONE READ_PHONE_STATE POST_NOTIFICATIONS; do adb shell pm grant $P android.permission.$p || true; done
adb shell cmd role add-role-holder android.app.role.DIALER $P 0 || true

i=0
for n in "Елена Петрова" "Ёлка Новая" "Егор" "Ema Stone" "Émile Zola" "Eva" "+7 999 123-45-67" "(Мама)" "Anna" "Борис"; do
  i=$((i+1))
  adb shell content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n: >/dev/null
  id=$(adb shell content query --uri content://com.android.contacts/raw_contacts --projection _id | tail -1 | sed 's/.*_id=\([0-9]*\).*/\1/' | tr -d '\r')
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind "data1:s:'$n'" >/dev/null
  adb shell content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:+7900000000$i --bind data2:i:2 >/dev/null
done
adb shell content query --uri content://com.android.contacts/contacts --projection display_name | head -20

adb logcat -c
adb shell am start -W -n $P/.MainActivity
sleep 6

tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml ui.xml >/dev/null 2>&1
  b=$(python3 - "$1" <<'PY'
import re,sys
x=open('ui.xml',encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', x):
    n=m.group(0)
    if f'text="{sys.argv[1]}"' in n:
        a=list(map(int,re.findall(r'\d+',re.search(r'bounds="([^"]+)"',n).group(1))))
        print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
)
  [ -n "$b" ] || { echo "no '$1' on screen"; return 1; }
  adb shell input tap $b
}

mkdir -p smoke
adb shell screencap -p /sdcard/home.png; adb pull /sdcard/home.png smoke/home.png
tap_text "Search" || exit 1
sleep 4
adb shell screencap -p /sdcard/contacts.png; adb pull /sdcard/contacts.png smoke/contacts.png
adb shell input text "e"
sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input swipe 540 1200 540 300 300; sleep 1
adb shell screencap -p /sdcard/after.png; adb pull /sdcard/after.png smoke/after.png
adb logcat -d -b crash > smoke/crash.txt
cat smoke/crash.txt
if grep -q "FATAL EXCEPTION" smoke/crash.txt; then echo "app crashed"; exit 1; fi
adb shell pidof $P || { echo "app is not running"; exit 1; }
echo "smoke ok"
