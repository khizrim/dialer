#!/usr/bin/env bash
# Люди и журнал для прохода по сценариям: избранные с мессенджерами, человек с двумя номерами,
# пропущенные от своих и от незнакомых, 30+ контактов для указателя.
set -u
A="adb shell"
rid() { $A content query --uri content://com.android.contacts/raw_contacts --projection _id | tail -1 | sed 's/.*_id=\([0-9]*\).*/\1/' | tr -d '\r'; }
data() { # raw_id mimetype data1 [data2]
  if [ -n "${4:-}" ]; then $A content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$1 --bind mimetype:s:$2 --bind "data1:s:'$3'" --bind data2:i:$4
  else $A content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$1 --bind mimetype:s:$2 --bind "data1:s:'$3'"; fi
}
person() { # имя starred -> печатает raw id
  $A content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s:local --bind account_name:s:local --bind starred:i:$2 >/dev/null
  r=$(rid); data $r vnd.android.cursor.item/name "$1" >/dev/null; echo $r
}
PH=vnd.android.cursor.item/phone_v2
WA=vnd.android.cursor.item/vnd.com.whatsapp
TG=vnd.android.cursor.item/vnd.org.telegram.messenger.android

r=$(person "Мама" 1); data $r $PH +79161112233 2; data $r $WA.voip.call 79161112233@s.whatsapp.net; data $r $WA.video.call 79161112233@s.whatsapp.net; data $r $WA.profile 79161112233@s.whatsapp.net; data $r $TG.profile 101
r=$(person "Папа" 1); data $r $PH +79161112244 2
r=$(person "Дмитрий Козлов" 1); data $r $PH +79851234567 2; data $r $PH +74957001020 3; data $r vnd.android.cursor.item/nickname "Дима"; data $r $TG.call 102; data $r $TG.profile 102
r=$(person "Аня Смирнова" 0); data $r $PH +79035556677 2; data $r $WA.voip.call 79035556677@s.whatsapp.net; data $r $WA.profile 79035556677@s.whatsapp.net
r=$(person "Илья Петров" 0); data $r $PH +79267778899 2; data $r vnd.android.cursor.item/organization "Yandex"
r=$(person "Катя Иванова" 0); data $r $PH +79997654321 2
r=$(person "Anna Brown" 0); data $r $PH +447700900123 2
n=0
for nm in Алексей Борис Вера Галина Денис Елена Жанна Зоя Ирина Кирилл Лариса Михаил Наталья Олег Павел Роман Светлана Тимур Ульяна Фёдор Юлия Яна Brian Chloe David Emma; do
  n=$((n+1)); r=$(person "$nm" 0); data $r $PH "+7926100$(printf %04d $n)" 2 >/dev/null
done

now=$(($(date +%s)*1000))
call() { # номер тип минут_назад длительность   (1 вх, 2 исх, 3 проп)
  $A content insert --uri content://call_log/calls --bind number:s:"$1" --bind type:i:$2 --bind date:l:$((now-$3*60000)) --bind duration:l:$4 --bind new:i:1
}
call +79161112233 2 6000 60
call +79161112233 3 2500 0
call +79161112233 1 700 240
call +79161112233 2 5 120
call +79161112244 1 1500 600
call +79851234567 2 200 45
call +74957001020 1 95 300
call +79035556677 3 30 0
call +79035556677 3 25 0
call +79267778899 2 90 0
call +79997654321 2 3000 30
call +74997001122 3 300 0
call +78005553535 3 15 0
call +79001234567 1 1440 20
$A content query --uri content://com.android.contacts/contacts --projection display_name:starred | head -12
