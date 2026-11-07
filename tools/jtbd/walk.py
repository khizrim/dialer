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

# J1 — главная: что я вижу и что могу сделать
S("J1 главная")
start(); ns = shot("home", "что на главной без действий")

# J2 — позвонить близкому основным способом (тап по избранному)
S("J2 позвонить избранному")
if tap("Папа", exact_only=True, region=(1500, H), wait=4):
    shot("fav-tap-calls", f"telecom={telecom()}")
    hangup(); shot("after-hangup")
start()

# J3 — сменить способ по умолчанию для избранного (как это найдёт человек)
S("J3 сменить способ избранного")
c = dock_chip("Мама")
if c:
    long_xy(c["x"], c["y"], why="долгое на «Мама» в доке"); shot("fav-long-press", "что открылось по долгому нажатию")
    if find("Change", exact_only=True):
        tap("Change", exact_only=True, wait=2); shot("card-change-sheet", "лист «что по нажатию»")
        if tap("WhatsApp video", exact_only=True, wait=1.2): shot("after-pick-default", "тост и состояние")
        time.sleep(1.5); shot("card-after-default")
    else:
        note("FINDING: в карточке нет «Change»")
    back(); shot("dock-after-default", "значок у чипа поменялся?")
else:
    note("MISSING: чип «Мама» в доке")
# способ 2: через лист «Другой способ» с долгого нажатия на значок в ленте
start()

# J4 — убрать человека из избранного
S("J4 убрать из избранного")
c = dock_chip("Папа")
if c:
    long_xy(c["x"], c["y"], why="долгое на «Папа»"); ns = shot("card-papa")
    nm = find("Папа", exact_only=True, region=(0, 1900))
    if nm:
        icons = clickable_near(nm["y"] + 20, xmin=700)
        note(f"значки рядом с именем: {[(i['x'], i['y']) for i in icons]}")
        if len(icons) >= 2:
            tap_xy(icons[-2]["x"], icons[-2]["y"], wait=0.6, why="звезда"); shot("star-tapped-0.6s", "сразу после нажатия")
            time.sleep(1.5); shot("star-tapped-2s", "звезда погасла? карточка обновилась?")
            # нажать ещё раз — вернуть (проверка, что вторая попытка делает то, что видно)
            tap_xy(icons[-2]["x"], icons[-2]["y"], wait=2, why="звезда ещё раз"); shot("star-tapped-again")
            tap_xy(icons[-2]["x"], icons[-2]["y"], wait=2, why="звезда третий раз"); shot("star-tapped-third")
    back(); shot("dock-after-unstar", "Папа ушёл из дока?")
else:
    note("MISSING: чип «Папа»")
q = sh("shell", "content query --uri content://com.android.contacts/contacts --projection display_name:starred --where \"display_name='Папа'\"")
note(f"Папа в контактах: {q.strip()}")

# J5 — добавить в избранное (через поиск)
S("J5 добавить в избранное")
start()
tap("Search", exact_only=True, region=(1900, H), wait=3); shot("search-open", "поиск открыт, клавиатура?")
text("Katya") ; shot("search-latin-katya", "латиница: найдётся Катя?")
sh("shell", "input", "keyevent", "KEYCODE_MOVE_END")
for _ in range(6): sh("shell", "input", "keyevent", "KEYCODE_DEL")
text("Ilya"); shot("search-ilya")
tap("Илья Петров", wait=2.5); shot("card-ilya", "карточка из поиска")
nm = find("Илья Петров", exact_only=True)
if nm:
    icons = clickable_near(nm["y"] + 20, xmin=700)
    if len(icons) >= 2:
        tap_xy(icons[-2]["x"], icons[-2]["y"], wait=2, why="звезда"); shot("ilya-starred")
back(); back(); shot("home-after-star", "Илья появился в доке?")

# J6 — перезвонить на пропущенный
S("J6 перезвонить на пропущенный")
start(); ns = shot("home-missed")
a = find("Аня Смирнова", exact_only=True)
if a:
    tap_xy(W - 120, a["y"] + 20, wait=4, why="значок справа у Ани"); shot("callback-ann", f"telecom={telecom()}")
    hangup(); shot("after-callback")
start()

# J7 — понять, что за незнакомый номер, и что с ним сделать
S("J7 незнакомый номер")
ns, _ = dump()
num = next((n for n in ns if re.search(r"\+7|8 800|800 555", n["t"]) and n["y"] < 2000), None)
if num:
    tap_xy(300, num["y"] + 20, wait=2.5, why=f"имя-номер {num['t']}"); shot("number-card")
    if tap("History", wait=1.5): shot("number-history")
    if tap("Block", exact_only=True, wait=2): shot("block-confirm"); back()
    back()
    long_xy(300, num["y"] + 20, why="долгое на номер"); shot("number-row-menu"); back()
else:
    note("MISSING: строка незнакомого номера")

# J8 — человек с двумя номерами: позвонить на рабочий
S("J8 два номера")
start()
tap("Search", exact_only=True, region=(1900, H), wait=3); text("Dima"); shot("search-dima-nick", "находится по нику?")
for _ in range(6): sh("shell", "input", "keyevent", "KEYCODE_DEL")
text("Kozlov"); shot("search-kozlov")
if not tap("Дмитрий Козлов", wait=2.5):
    back(); start(); c = dock_chip("Дима") or dock_chip("Дмитрий")
    if c: long_xy(c["x"], c["y"])
shot("card-dima", "два номера: какой основной, как позвонить на рабочий")
if tap("History", wait=1.5): shot("card-dima-history")
w = find("+7 495", region=(0, 2000)) or find("495")
if w: tap_xy(w["x"], w["y"], wait=4, why="рабочий номер"); shot("call-work", f"telecom={telecom()} last={lastcall()}"); hangup()
start()

# J9 — один раз связаться другим способом (WhatsApp вместо обычного)
S("J9 другой способ разово")
a = find("Аня Смирнова", exact_only=True)
if a:
    long_xy(W - 120, a["y"] + 20, why="долгое на значок Ани"); shot("other-way-sheet")
    if tap("WhatsApp call", exact_only=True, wait=2.5): shot("after-wa-call", "без WhatsApp: что скажет?")
    start(); shot("return-offer", "предложение сделать основным?")
start()

# J10 — убрать лишнее с главной
S("J10 почистить главную")
ns, _ = dump()
rows = [n for n in ns if n["t"] and 300 < n["y"] < 1700 and n["x"] < 600 and len(n["t"]) > 2]
if rows:
    r = rows[-1]
    swipe(900, r["y"] + 20, 150, r["y"] + 20, 250, wait=0.8); shot("row-swiped", f"скрыли {r['t']}")
    if tap("Undo", exact_only=True, wait=1.5): shot("undo")
for _ in range(4): swipe(540, 600, 540, 1700, 250, wait=0.6)
shot("feed-top", "самый верх ленты")
if tap("Hide everyone", wait=2): shot("hide-everyone-confirm"); back()
start()

# J11 — набрать номер вручную
S("J11 набрать номер")
c = find("Search", exact_only=True, region=(1900, H))
if c: tap_xy(W - 120, c["y"], wait=2, why="кнопка клавиатуры справа от поиска")
shot("keypad")
for d in "7916": sh("shell", "input", "keyevent", f"KEYCODE_{d}"); time.sleep(0.2)
time.sleep(1); shot("keypad-7916", "подсказки людей")
for d in "1234567": sh("shell", "input", "keyevent", f"KEYCODE_{d}"); time.sleep(0.2)
time.sleep(1); shot("keypad-unknown", "незнакомый: добавить в контакты?")
sh("shell", "input", "keyevent", "KEYCODE_ENTER"); time.sleep(4); shot("dial-from-keypad", f"telecom={telecom()}")
time.sleep(3); shot("in-call")
if tap("Speaker", wait=1): shot("in-call-speaker")
if tap("Keypad", exact_only=True, wait=1) or tap("Digits", wait=1): shot("in-call-dtmf")
hangup(); shot("after-dial")
start()
c = find("Search", exact_only=True, region=(1900, H))
if c: tap_xy(W - 120, c["y"], wait=2)
for d in "5282": sh("shell", "input", "keyevent", f"KEYCODE_{d}"); time.sleep(0.2)
time.sleep(1); shot("keypad-t9-kate", "T9 «Катя»=5282")
back(); start()

# J12 — входящий от своего и от незнакомого
S("J12 входящие")
sh("emu", "gsm", "call", "79161112233"); time.sleep(5); shot("incoming-mom", f"telecom={telecom()}")
if tap("Answer", wait=3): shot("answered-mom")
time.sleep(2); shot("in-call-mom")
sh("emu", "gsm", "cancel", "79161112233"); time.sleep(3); shot("mom-ended")
sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); time.sleep(1)
sh("emu", "gsm", "call", "74951112233"); time.sleep(5); shot("incoming-unknown")
if tap("Decline", wait=3): shot("declined")
sh("emu", "gsm", "cancel", "74951112233"); time.sleep(2)
sh("emu", "gsm", "call", "79035556677"); time.sleep(4); sh("emu", "gsm", "cancel", "79035556677"); time.sleep(3)
sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); sh("shell", "wm", "dismiss-keyguard"); time.sleep(1)
sh("shell", "cmd", "statusbar", "expand-notifications"); time.sleep(2); shot("missed-notification"); sh("shell", "cmd", "statusbar", "collapse")
start(); shot("home-after-incoming")

# J13 — найти настройки и тишину для незнакомых
S("J13 настройки")
swipe(540, 2250, 540, 900, 400, wait=2.5); shot("dock-pull-contacts", "потянули док вверх")
back(1); shot("pulled-back-ime")
if tap("Settings", exact_only=True, wait=2.5) is None:
    ns, _ = dump(); hero = find("Contacts", exact_only=True)
    if hero:
        icons = clickable_near(hero["y"], xmin=800)
        if icons: tap_xy(icons[-1]["x"], icons[-1]["y"], wait=2.5, why="шестерёнка")
shot("settings")
swipe(540, 1900, 540, 700, 400); shot("settings-2")
if tap("Blocked numbers", wait=2): shot("blocked"); back()
back(); shot("settings-back")
home()

# J14 — изменить контакт / создать новый
S("J14 изменить контакт")
start()
c = dock_chip("Мама")
if c:
    long_xy(c["x"], c["y"]); nm = find("Мама", exact_only=True, region=(0, 1900))
    if nm:
        icons = clickable_near(nm["y"] + 20, xmin=700)
        if icons: tap_xy(icons[-1]["x"], icons[-1]["y"], wait=3, why="карандаш"); shot("edit-system")
    back(2); back(2); shot("after-edit")
start()
tap("Search", exact_only=True, region=(1900, H), wait=3); back(1)
shot("contacts-list", "список: избранные, буквы, указатель")
swipe(540, 1600, 540, 500, 300); shot("contacts-scrolled")
r = find("Ю", exact_only=True)
shot("contacts-rail")
back(); back()

# J15 — тёмная тема, то же самое
S("J15 тёмная")
sh("shell", "cmd", "uimode", "night", "yes"); start(); shot("dark-home")
c = dock_chip("Мама")
if c: long_xy(c["x"], c["y"]); shot("dark-card")
sh("shell", "cmd", "uimode", "night", "no")

# J16 — русский
S("J16 русский")
sh("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", "ru-RU"); start(); shot("ru-home")
c = dock_chip("Мама")
if c: long_xy(c["x"], c["y"]); shot("ru-card")
if tap("Изменить", exact_only=True, wait=2): shot("ru-change-sheet")
sh("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", "")
note("done")
