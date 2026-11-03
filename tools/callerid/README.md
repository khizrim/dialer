# База АОН

`app/src/main/assets/callerid/ru.bin` — офлайн-реестр российской нумерации
(Минцифры): диапазон номеров → оператор и регион. Приложение ищет по нему на
телефоне, номера никуда не отправляются.

Обновить перед релизом (реестр меняется понемногу, раз в неделю):

```sh
# вариант 1: npm-пакет phoneoperator (MIT, еженедельная копия реестра)
npm pack phoneoperator && mkdir -p /tmp/po && tar xzf phoneoperator-*.tgz -C /tmp/po
python3 -I tools/callerid/build_db.py --npm /tmp/po/package

# вариант 2: официальные CSV с opendata.digital.gov.ru (ABC-3xx, ABC-4xx, ABC-8xx, DEF-9xx)
UPDATED=2026-11-01 python3 -I tools/callerid/build_db.py --csv ABC-3xx.csv ABC-4xx.csv ABC-8xx.csv DEF-9xx.csv
```

Потом прогнать `./gradlew testDebugUnitTest` (CallerIdTest проверяет базу).
