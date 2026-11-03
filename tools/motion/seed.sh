#!/usr/bin/env bash
# Тестовые контакты и журнал звонков для обзора в эмуляторе.
set -u
A="adb shell"
i=0
add() { # имя номер starred
  i=$((i+1))
  $A content insert --uri content://com.android.contacts/raw_contacts --bind account_type:s:local --bind account_name:s:local --bind starred:i:$3
  $A content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$i --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:"$1"
  $A content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$i --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:"$2" --bind data2:i:2
}
add "Мама" "+79161112233" 1
add "Папа" "+79161112244" 1
add "Аня Смирнова" "+79035556677" 0
add "Илья Петров" "+79267778899" 0
add "Дима" "+79851234567" 1
add "Катя Иванова" "+79997654321" 0
add "Сергей Работа" "+74951234567" 0
add "Никита" "+79101112233" 0
now=$(($(date +%s)*1000))
call() { # номер тип минут_назад длительность
  $A content insert --uri content://call_log/calls --bind number:s:"$1" --bind type:i:$2 --bind date:l:$((now-$3*60000)) --bind duration:l:$4 --bind new:i:1
}
call "+79161112233" 2 5 120
call "+79035556677" 3 30 0
call "+79267778899" 1 90 300
call "+79851234567" 2 200 45
call "+74997001122" 3 300 0
call "+79161112244" 1 1500 600
call "+79997654321" 2 3000 30
call "+79101112233" 3 4000 0
add "Александра Константинопольская" "+79031112200" 0
add "+7 916 555-00-11 Доставка" "+79165550011" 0
