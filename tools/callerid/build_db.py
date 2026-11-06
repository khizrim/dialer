#!/usr/bin/env python3
"""
Собирает офлайн-базу АОН (app/src/main/assets/callerid/ru.bin) из реестра
российской нумерации Минцифры (DEF/ABC-диапазоны: оператор и регион).

Источники (любой из двух):
  --npm DIR   распакованный npm-пакет phoneoperator (MIT, данные реестра,
              обновляются еженедельно): npm pack phoneoperator && tar xzf ...
  --csv F...  официальные CSV с opendata.digital.gov.ru (ABC-3xx.csv,
              ABC-4xx.csv, ABC-8xx.csv, DEF-9xx.csv; разделитель «;»).

Скрипт только читает данные: код пакета не выполняется.

Формат ru.bin (big-endian, DataOutputStream):
  "OZCID1", UTF updated,
  u16 nOps   × (UTF name, u8 flags)        flags: 1 = виртуальная АТС / IP-телефония
  u16 nPlaces × UTF short region
  u8 nTables × (u8 type[0=mobile,1=landline], int nRanges, int blockSize,
                int nBlocks × (long prevEnd, long firstStart, int offset),
                int payloadLen, payload)
  payload: на каждый диапазон varint gap, varint length, varint op, varint place,
  start = prevEnd + 1 + gap, end = start + length.
"""
import argparse, csv, glob, io, json, os, re, struct, sys

BRANDS = {
    '7740000076': 'МТС', '7812014560': 'МегаФон', '7713076301': 'Билайн', '7743895280': 'Т2',
    '7701725181': 'Yota', '7743200179': 'Т-Мобайл', '7736264044': 'СберМобайл',
    '9725152215': 'Альфа-Мобайл', '9705121882': 'ВТБ Мобайл', '6661079603': 'Мотив',
    '2308210371': 'Win mobile', '7718999159': 'Волна мобайл', '7707049388': 'Ростелеком',
    '7705017253': 'МТТ', '7710016640': 'МГТС', '5902202276': 'Дом.ру', '7713480948': 'Дом.ру',
    '7740000020': 'Газпром телеком', '7703383783': 'ГЛОНАСС', '7709501144': 'Манго Телеком',
    '9703126821': 'Новофон', '7718753204': 'Гравител', '7728339602': 'Sipnet', '7728339257': 'Sipnet',
    '9719033700': 'Делофон', '7727752084': 'МСН Телеком', '7810204213': 'ОБИТ',
    '1681000024': 'Таттелеком', '0274018377': 'Башинформсвязь', '7708503727': 'РЖД',
}
# Операторы, чьи номера почти всегда виртуальные (колл-центры, офисные АТС).
VIRTUAL = {'7705017253', '7709501144', '9703126821', '7718753204', '7728339602', '7728339257',
           '9719033700', '7727752084', '7810204213'}

LEGAL = r'^(ПАО|ОАО|ЗАО|НАО|АО|ООО|ФГУП|ГУП|МУП|ИП|ООО ИК|Акционерное общество|Публичное акционерное общество|Общество с ограниченной ответственностью)\s+'

def clean_name(inn, raw):
    if inn in BRANDS:
        return BRANDS[inn]
    m = re.search(r'["«“]([^"»”]+)["»”]', raw)
    name = m.group(1) if m else re.sub(LEGAL, '', raw.strip(), flags=re.I)
    name = name.strip().strip('"«»“” ')
    words = []
    for w in name.split():
        letters = re.sub(r'[^A-Za-zА-Яа-яЁё]', '', w)
        if letters.isupper() and len(letters) > 4:
            w = '-'.join(p[:1] + p[1:].lower() for p in w.split('-'))
        words.append(w)
    return ' '.join(words) or raw

SPECIAL = {
    'Город Москва': 'Москва', 'Город Санкт-Петербург': 'Санкт-Петербург',
    'Город Севастополь': 'Севастополь', 'Город Байконур': 'Байконур',
    'Российская Федерация': 'Россия',
    'Республика Саха (Якутия)': 'Якутия', 'Удмуртская Республика': 'Удмуртия',
    'Чувашская Республика - Чувашия': 'Чувашия', 'Кабардино-Балкарская Республика': 'Кабардино-Балкария',
    'Карачаево-Черкесская Республика': 'Карачаево-Черкесия', 'Чеченская Республика': 'Чечня',
    'Республика Северная Осетия - Алания': 'Северная Осетия', 'Донецкая Народная Республика': 'ДНР',
    'Луганская Народная Республика': 'ЛНР', 'Кемеровская область - Кузбасс': 'Кузбасс',
    'Ханты-Мансийский автономный округ - Югра': 'Югра', 'Ямало-Ненецкий автономный округ': 'Ямал',
    'Ненецкий автономный округ': 'Ненецкий АО', 'Чукотский автономный округ': 'Чукотка',
    'Еврейская автономная область': 'Еврейская АО', 'Республика Марий Эл': 'Марий Эл',
}

def short_one(p):
    p = re.sub(r'\s+', ' ', p.strip())
    if 'м.р-н' in p:  # в реестре так записаны районы Вологодской области
        return 'Вологодская обл.'
    if p in SPECIAL:
        return SPECIAL[p]
    p = re.sub(r'^г\.\s*', '', p)
    p = re.sub(r'^Республика\s+', '', p)
    p = re.sub(r'\s*\(.*?\)', '', p)
    p = re.sub(r' область$', ' обл.', p)
    return p

def short_region(raw):
    raw = (raw or '').strip()
    parts = [x.strip() for x in raw.split(',') if x.strip()]
    if not parts:
        return ''
    if len(parts) > 3:
        return 'Россия'
    s = set(parts)
    if s == {'Город Москва', 'Московская область'}:
        return 'Москва и область'
    if s == {'Город Санкт-Петербург', 'Ленинградская область'}:
        return 'Петербург и область'
    return short_one(parts[0])

def varint(n, out):
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return

def utf(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b

# ---------- чтение источников ----------

def read_npm(root):
    tables = []
    updated = ''
    for kind, fname in ((0, 'mobile'), (1, 'landline')):
        src = open(os.path.join(root, 'dist', 'data', fname + '.js'), encoding='utf-8').read()
        d = {}
        for m in re.finditer(r'export const (\w+) = (.*?);\n', src, re.S):
            v = m.group(2)
            d[m.group(1)] = v.strip("'") if v.startswith("'") else json.loads(v)
        updated = max(updated, d['UPDATED'])
        places = [p.split('|')[-1] for p in d['PLACES']]
        ranges = []
        prev = -1
        for e in d['RANGES'].split(';'):
            p = [int(x, 36) if x else 0 for x in e.split(',')] + [0, 0, 0, 0]
            g, l, o, pl = p[:4]
            st = prev + 1 + g
            en = st + l
            prev = en
            inn, name = d['OPERATORS'][o]
            ranges.append((st, en, inn, name, places[pl]))
        tables.append((kind, ranges))
    return updated, tables

def read_csv(files):
    by = {0: [], 1: []}
    for f in files:
        raw = open(f, 'rb').read()
        try:
            text = raw.decode('utf-8-sig')
        except UnicodeDecodeError:
            text = raw.decode('cp1251')
        rows = csv.reader(io.StringIO(text), delimiter=';')
        next(rows, None)
        for r in rows:
            if len(r) < 6:
                continue
            code, a, b = r[0].strip(), r[1].strip(), r[2].strip()
            if not (code.isdigit() and a.isdigit() and b.isdigit()):
                continue
            inn = r[7].strip() if len(r) > 7 else ''
            st, en = int(code + a.zfill(7)), int(code + b.zfill(7))
            by[0 if code.startswith('9') else 1].append((st, en, inn, r[4].strip(), r[5].strip()))
    return os.environ.get('UPDATED', ''), [(k, sorted(v)) for k, v in by.items() if v]

# ---------- запись ----------

def build(updated, tables, out_path, block=128):
    ops, op_idx, places, pl_idx = [], {}, [], {}
    def op_of(inn, name):
        key = inn or name
        if key not in op_idx:
            op_idx[key] = len(ops)
            ops.append((clean_name(inn, name), 1 if inn in VIRTUAL else 0))
        return op_idx[key]
    def place_of(p):
        s = short_region(p)
        if s not in pl_idx:
            pl_idx[s] = len(places)
            places.append(s)
        return pl_idx[s]

    encoded = []
    for kind, ranges in tables:
        recs = []
        for st, en, inn, name, place in ranges:
            o, p = op_of(inn, name), place_of(place)
            if recs and recs[-1][1] + 1 == st and recs[-1][2] == o and recs[-1][3] == p:
                recs[-1] = (recs[-1][0], en, o, p)
            else:
                recs.append((st, en, o, p))
        payload, blocks, prev = bytearray(), [], -1
        for i, (st, en, o, p) in enumerate(recs):
            if st <= prev:
                raise SystemExit(f'overlap at {st}')
            if i % block == 0:
                blocks.append((prev, st, len(payload)))
            varint(st - prev - 1, payload)
            varint(en - st, payload)
            varint(o, payload)
            varint(p, payload)
            prev = en
        encoded.append((kind, len(recs), blocks, bytes(payload)))

    out = bytearray(b'OZCID1')
    out += utf(updated)
    out += struct.pack('>H', len(ops))
    for name, flags in ops:
        out += utf(name) + bytes([flags])
    out += struct.pack('>H', len(places))
    for p in places:
        out += utf(p)
    out += bytes([len(encoded)])
    for kind, n, blocks, payload in encoded:
        out += struct.pack('>BiiI', kind, n, block, len(blocks))
        for prev, first, off in blocks:
            out += struct.pack('>qqi', prev, first, off)
        out += struct.pack('>i', len(payload)) + payload
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    open(out_path, 'wb').write(out)
    print(f'{out_path}: {len(out)} bytes, updated {updated}, ops {len(ops)}, places {len(places)}, '
          + ', '.join(f'type{k}={n}' for k, n, _, _ in encoded))

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--npm')
    ap.add_argument('--csv', nargs='*')
    ap.add_argument('--out', default=os.path.join(os.path.dirname(__file__), '..', '..', 'app', 'src', 'main', 'assets', 'callerid', 'ru.bin'))
    a = ap.parse_args()
    if a.npm:
        updated, tables = read_npm(a.npm)
    elif a.csv:
        updated, tables = read_csv(a.csv)
    else:
        sys.exit('нужен --npm DIR или --csv FILES')
    build(updated, tables, os.path.normpath(a.out))
