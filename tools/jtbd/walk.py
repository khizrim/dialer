#!/usr/bin/env python3
"""Проход по сценариям (JTBD) Ozero Phone в эмуляторе: каждый шаг — скриншот, дерево экрана и запись в журнал.
Шаги не падают: если чего-то нет на экране, это записывается как находка (MISSING) и проход идёт дальше."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "com.khizrim.dialer"
OUT = os.environ.get("OUT", "shots"); os.makedirs(OUT, exist_ok=True)
W, H = 1080, 2400
DP = 2.625
log = open(os.path.join(OUT, "log.txt"), "a"); step = [0]
scen = ["--"]


def sh(*a, timeout=30):
    try: return subprocess.run(["adb", *a], capture_output=True, text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired: return ""


def note(s):
    line = f"[{time.strftime('%H:%M:%S')}] {scen[0]} | {s}"
    print(line, flush=True); log.write(line + "\n"); log.flush()


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = sh("shell", "cat", "/sdcard/ui.xml")
    try: root = ET.fromstring(raw)
    except ET.ParseError: return [], raw
    out = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) != 4: continue
        x1, y1, x2, y2 = map(int, b)
        out.append(dict(t=n.get("text", ""), d=n.get("content-desc", ""), c=n.get("clickable") == "true",
                        lc=n.get("long-clickable") == "true", pkg=n.get("package", ""), b=(x1, y1, x2, y2),
                        x=(x1 + x2) // 2, y=(y1 + y2) // 2))
    # «не отвечает» у лаунчера на медленном эмуляторе — закрываем
    for n in out:
        if n["t"] in ("Wait", "Close app") and any("isn't responding" in m["t"] for m in out):
            sh("shell", "input", "tap", str(n["x"]), str(n["y"])); time.sleep(2); return dump()
    return out, raw


def shot(name, why=""):
    step[0] += 1
    base = os.path.join(OUT, f"{step[0]:03d}-{name}")
    with open(base + ".png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    ns, raw = dump()
    with open(base + ".txt", "w") as f:
        for n in ns:
            if n["t"] or n["d"] or n["c"]:
                f.write(f"{n['b']}\t{'C' if n['c'] else ' '}{'L' if n['lc'] else ' '}\t{n['t']}\t{n['d']}\n")
    note(f"shot {os.path.basename(base)} {why}")
    return ns


def find(label, exact_only=False, region=None):
    ns, _ = dump()
    cand = [n for n in ns if label in (n["t"], n["d"])]
    if not cand and not exact_only:
        cand = [n for n in ns if len(label) > 2 and (label in n["t"] or label in n["d"])]
    if region: cand = [n for n in cand if region[0] <= n["y"] <= region[1]]
    return cand[0] if cand else None


def tap_xy(x, y, wait=1.5, why=""):
    sh("shell", "input", "tap", str(x), str(y)); note(f"tap ({x},{y}) {why}"); time.sleep(wait)


def long_xy(x, y, wait=1.8, why=""):
    sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900"); note(f"long ({x},{y}) {why}"); time.sleep(wait)


def tap(label, long=False, wait=1.5, exact_only=False, region=None, dx=0):
    n = find(label, exact_only, region)
    if not n: time.sleep(1.5); n = find(label, exact_only, region)
    if not n: note(f"MISSING: «{label}»"); return None
    x, y = n["x"] + dx, n["y"]
    (long_xy if long else tap_xy)(x, y, wait, f"«{label}»")
    return n


def clickable_near(y, xmin=0, xmax=W, tol=90):
    """Кликабельные узлы на той же высоте (значки без подписи)."""
    ns, _ = dump()
    return sorted([n for n in ns if n["c"] and abs(n["y"] - y) < tol and xmin <= n["x"] <= xmax
                   and (n["b"][2] - n["b"][0]) < 400], key=lambda n: n["x"])


def back(wait=1.5): sh("shell", "input", "keyevent", "KEYCODE_BACK"); note("back"); time.sleep(wait)
def swipe(x1, y1, x2, y2, ms=350, wait=1.5): sh("shell", "input", "swipe", *map(str, (x1, y1, x2, y2, ms))); note(f"swipe ({x1},{y1})->({x2},{y2}) {ms}ms"); time.sleep(wait)
def text(s): sh("shell", "input", "text", s.replace(" ", "%s")); note(f"type «{s}»"); time.sleep(1.2)
def telecom(): return " ".join(re.findall(r"state=(\w+)", sh("shell", "dumpsys telecom | grep 'Call id='"))[:3])
def lastcall(): return sh("shell", "content query --uri content://call_log/calls --projection number:type:duration --sort 'date DESC' | head -1").strip()
def toast_text(ns):
    # тост — тёмная плашка внизу; ищем строки в нижней трети, которых нет в обычных экранах
    return [n["t"] for n in ns if n["t"] and n["y"] > 1500]


def hangup():
    sh("shell", "input", "keyevent", "KEYCODE_ENDCALL"); time.sleep(1.5)
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); sh("shell", "wm", "dismiss-keyguard"); time.sleep(1.5)


def start(wait=7):
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(wait)


def home():
    """Вернуться на главную: закрыть всё сверху."""
    for _ in range(4):
        if find("Search", exact_only=True, region=(1900, H)) and not find("Contacts", exact_only=True): break
        back(1.0)


def S(name):
    scen[0] = name; note("=== " + name)


def dock_chip(label):
    return find(label, exact_only=True, region=(1500, H))


# ------------------------------------------------------------------------------------------------
mode = sys.argv[1] if len(sys.argv) > 1 else "main"

if mode == "onboarding":
    S("J0 первый запуск")
    start(); shot("first-run")
    for b in ("Make default phone app", "Set as default", "Continue", "Allow access"):
        if find(b): tap(b, wait=2.5); shot("first-run-after-" + b.split()[0].lower()); back(2)
    shot("first-run-end")
    sys.exit(0)

sh("shell", "cmd", "uimode", "night", "no")

def card_icons(name):
    """Звезда и карандаш справа от имени в карточке."""
    nm = find(name, exact_only=True, region=(300, 2000))
    if not nm: note(f"MISSING: имя «{name}» в карточке"); return None, None
    ic = clickable_near(nm["y"] + 30, xmin=760, tol=110)
    note(f"значки у имени: {[(i['x'], i['y']) for i in ic]}")
    return (ic[-2] if len(ic) >= 2 else None), (ic[-1] if ic else None)


def chip(label):
    return find(label, exact_only=True, region=(1950, 2200))


S("J1 главная"); start(); shot("home")

S("J2 позвонить избранному")
c = chip("Папа")
if c:
    tap_xy(c["x"], c["y"], wait=1.5, why="тап по чипу Папа"); shot("fav-tap-1s", f"telecom={telecom()}")
    time.sleep(3); shot("fav-tap-4s", f"telecom={telecom()} last={lastcall()}"); hangup()
start()

S("J3 сменить способ избранного")
c = chip("Мама")
if c:
    long_xy(c["x"], c["y"], why="долгое на чип «Мама»"); shot("mom-card", "карточка из дока")
    star, edit = card_icons("Мама")
    if tap("Change", exact_only=True, wait=2): shot("mom-change-sheet")
    if tap("WhatsApp video", exact_only=True, wait=1.0): shot("mom-after-pick", "тост, карточка")
    time.sleep(2); shot("mom-card-after")
    back(); time.sleep(1); shot("dock-after-default", "значок у чипа Мама")
    # долгое на значок способа в карточке (скрытый способ)
    c = chip("Мама")
    if c:
        long_xy(c["x"], c["y"]); ns, _ = dump()
        tg = find("Telegram", exact_only=True)
        if tg:
            ic = clickable_near(tg["y"], xmin=700)
            if ic: long_xy(ic[0]["x"], ic[0]["y"], why="долгое на значок Telegram"); shot("mom-long-tg-icon", "сделать основным долгим")
        back()
    shot("dock-after-tg")
start()

S("J4 убрать из избранного")
c = chip("Папа")
if c:
    long_xy(c["x"], c["y"], why="долгое на чип «Папа»"); shot("papa-card")
    star, edit = card_icons("Папа")
    if star:
        tap_xy(star["x"], star["y"], wait=0.7, why="звезда"); shot("papa-star-0.7s")
        time.sleep(2); shot("papa-star-3s", "звезда погасла?")
        tap_xy(star["x"], star["y"], wait=2.5, why="звезда ещё раз (видя, что не погасла)"); shot("papa-star-again")
    back(); shot("dock-after-unstar")
note("Папа: " + sh("shell", "content query --uri content://com.android.contacts/contacts --projection display_name:starred --where \"display_name='Папа'\"").strip())
start(); shot("dock-after-restart")

S("J4b «Изменить» у избранных")
start()
tap("Search", exact_only=True, region=(2100, H), wait=3); back(1); shot("contacts-favs", "«Избранные · Изменить»")
if tap("Edit", exact_only=True, wait=1.5):
    shot("fav-editor", "ручки, способ, крестики")
    d = find("Дмитрий Козлов", exact_only=True, region=(300, 1200))
    if d:
        swipe(60, d["y"], 60, d["y"] - 170, 900, wait=1.5); shot("fav-editor-dragged", "Дмитрий поднят выше")
    m = find("Call", exact_only=True, region=(300, 1200)) or find("Video", exact_only=True, region=(300, 1200))
    if m:
        tap_xy(m["x"], m["y"], wait=2, why="способ в редакторе"); shot("fav-editor-method-sheet")
        if tap("WhatsApp chat", exact_only=True, wait=2): shot("fav-editor-after-method")
    mama = find("Мама", exact_only=True, region=(300, 1200))
    if mama:
        ic = clickable_near(mama["y"], xmin=950)
        if ic: tap_xy(ic[-1]["x"], ic[-1]["y"], wait=1.2, why="крестик у Мамы"); shot("fav-editor-removed", "тост с Undo")
        if tap("Undo", exact_only=True, wait=2): shot("fav-editor-undo")
    tap("Done", exact_only=True, wait=1.5); shot("fav-editor-done")
swipe(540, 1600, 540, 400, 300); shot("contacts-scripts", "А–Я / A–Z")
start(); shot("dock-order", "порядок в доке после перестановки")
a = find("Аня Смирнова", exact_only=True)
if a: long_xy(a["x"], a["y"], why="долгое на строку Ани"); shot("feed-menu-person", "есть «В избранное» и «Что по нажатию»")
start()

S("J5 карточка из поиска и звезда")
tap("Search", exact_only=True, region=(2100, H), wait=3)
text("Ilya"); shot("search-ilya", "латиница с мягким знаком")
for _ in range(5): sh("shell", "input", "keyevent", "KEYCODE_DEL")
text("Petrov"); shot("search-petrov")
tap("Илья Петров", wait=2.5); shot("ilya-card-with-ime", "клавиатура закрывает карточку?")
back(1.5); shot("ilya-card-after-back", "назад: убрало клавиатуру или карточку?")
star, edit = card_icons("Илья Петров")
if star: tap_xy(star["x"], star["y"], wait=2.5, why="звезда"); shot("ilya-starred")
start(); shot("home-ilya-in-dock")


S("J8 карточка с двумя номерами")
c = chip("Дима")
if c:
    long_xy(c["x"], c["y"]); shot("dima-card")
    if tap("History", wait=1.5): shot("dima-history")
    w = find("+7 (495) 700-10-20")
    if w: tap_xy(w["x"], w["y"], wait=1.5, why="рабочий номер"); shot("dima-work-call", f"telecom={telecom()} last={lastcall()}"); time.sleep(2); hangup()
start()

S("J6b карточка из ленты")
a = find("Аня Смирнова", exact_only=True)
if a:
    tap_xy(a["x"], a["y"], wait=2.5, why="имя Ани в ленте"); shot("ann-card")
    if tap("History", wait=1.5): shot("ann-history")
    star, edit = card_icons("Аня Смирнова")
    if edit: tap_xy(edit["x"], edit["y"], wait=4, why="карандаш"); shot("ann-edit-system", "системный редактор")
    back(2); back(2); shot("after-editor")
start()

S("J11 набор с маской")
c = find("Search", exact_only=True, region=(2100, H))
if c: tap_xy(W - 120, c["y"], wait=2)
for d in "89161234567": sh("shell", "input", "keyevent", f"KEYCODE_{d}"); time.sleep(0.15)
time.sleep(1); shot("keypad-8916", "маска для 8…")
for _ in range(11): sh("shell", "input", "keyevent", "KEYCODE_DEL")
for d in "4951112233": sh("shell", "input", "keyevent", f"KEYCODE_{d}"); time.sleep(0.15)
time.sleep(1); shot("keypad-495", "10 цифр без кода")
ns, _ = dump()
cb = [n for n in ns if n["c"] and n["y"] > 2050 and 380 < n["x"] < 700]
if cb: tap_xy(cb[0]["x"], cb[0]["y"], wait=1.5, why="кнопка вызова"); shot("outgoing-1s", f"telecom={telecom()}")
time.sleep(3); shot("outgoing-4s", f"telecom={telecom()}")
hangup(); start()

S("J12 входящий, приложение свернуто")
sh("shell", "input", "keyevent", "KEYCODE_HOME"); time.sleep(2)
sh("emu", "gsm", "call", "+79161112233"); time.sleep(5); shot("incoming-bg", f"telecom={telecom()}")
a = find("Answer", exact_only=True)
if a: tap_xy(a["x"], a["y"], wait=3); shot("answered")
time.sleep(2); shot("in-call")
for lbl in ("Speaker", "Mute", "Microphone", "Digits", "More"):
    n = find(lbl, exact_only=True)
    note(f"в звонке «{lbl}»: {'есть' if n else 'нет'}")
sh("emu", "gsm", "cancel", "+79161112233"); time.sleep(3); shot("call-ended")
sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); sh("shell", "wm", "dismiss-keyguard")
sh("shell", "input", "keyevent", "KEYCODE_SLEEP"); time.sleep(2)
sh("emu", "gsm", "call", "+79035556677"); time.sleep(5); sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); time.sleep(2); shot("incoming-locked")
sh("emu", "gsm", "cancel", "+79035556677"); time.sleep(3)
sh("shell", "wm", "dismiss-keyguard"); time.sleep(1)
sh("shell", "cmd", "statusbar", "expand-notifications"); time.sleep(2); shot("missed-notif-plus"); sh("shell", "cmd", "statusbar", "collapse")
start(); shot("home-end")
note("done")
