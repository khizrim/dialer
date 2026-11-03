#!/usr/bin/env python3
"""Проход по экранам Ozero Phone: скриншоты, тексты и видео переходов (основа: tools/emulator из треда обзора)."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "com.khizrim.dialer"
OUT = os.environ.get("OUT", "shots"); os.makedirs(OUT, exist_ok=True)
L = os.environ.get("LANGSET", "en")
T0 = {
 "en": dict(keypad="Open keypad", hide="Hide keypad", journal="Recents", contacts="Contacts", settings="Settings",
            card="Open card", other="Another way", change="Change default", answer="Answer", role="Make default phone app",
            notnow="Not now", perms="Allow access", remove="Remove from home", edit="Edit contact"),
 "ru": dict(keypad="Открыть клавиатуру", hide="Скрыть клавиатуру", journal="Журнал", contacts="Контакты", settings="Настройки",
            card="Открыть карточку", other="Другой способ", change="Изменить по умолчанию", answer="Ответить", role="Сделать звонилкой",
            notnow="Не сейчас", perms="Разрешить", remove="Убрать с главной", edit="Изменить контакт"),
}[L]
T = dict(T0)
T.update({"en": dict(undo="Undo", hidebtn="Hide", allc="All contacts", call="Call"),
          "ru": dict(undo="Вернуть", hidebtn="Скрыть", allc="Все контакты", call="Позвонить")}[L])
log = open(os.path.join(OUT, "log.txt"), "w"); step = [0]

def sh(*a): return subprocess.run(["adb", *a], capture_output=True, text=True).stdout
def note(s): print(s); log.write(s + "\n"); log.flush()
def nodes(retry=True):
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    try: root = ET.fromstring(sh("shell", "cat", "/sdcard/ui.xml"))
    except ET.ParseError: return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((n.get("text", ""), n.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2, (x1, y1, x2, y2)))
    # системное окно «не отвечает» (лаунчер на медленном эмуляторе) закрываем и смотрим заново
    if retry and any("isn't responding" in t or "не отвечает" in t for t, *_ in out):
        for t, d, x, y, b in out:
            if t in ("Wait", "Close app", "Подождать", "Закрыть приложение"):
                sh("shell", "input", "tap", str(x), str(y)); time.sleep(2); break
        return nodes(False)
    return out
def shot(name):
    nodes()
    step[0] += 1; base = os.path.join(OUT, f"{step[0]:02d}-{name}")
    with open(base + ".png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    with open(base + ".txt", "w") as f:
        for t, d, x, y, b in nodes():
            if t or d: f.write(f"{b}\t{t}\t{d}\n")
    note(f"shot {base}")
def find(label):
    for t, d, x, y, b in nodes():
        if label == t or label == d or (len(label) > 3 and (label in t or label in d)): return x, y
    return None
BAR = {"Recents": (143, 2211), "Журнал": (143, 2211), "Contacts": (376, 2211), "Контакты": (376, 2211),
       "Settings": (754, 2211), "Настройки": (754, 2211), "Open keypad": (933, 2211), "Открыть клавиатуру": (933, 2211)}
def tap(label, long=False, wait=1.5):
    p = find(label)
    if not p: time.sleep(1.5); p = find(label)
    if not p and label in BAR: p = BAR[label]; note(f"fallback coords: {label}")
    if not p: note(f"MISSING: {label}"); return False
    x, y = p
    sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900") if long else sh("shell", "input", "tap", str(x), str(y))
    note(f"tap {label}"); time.sleep(wait); return True
def back(wait=1.5): sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(wait)
def swipe(y1, y2, ms=350, wait=1.5): sh("shell", "input", "swipe", "540", str(y1), "540", str(y2), str(ms)); time.sleep(wait)
def hswipe(y, x1=950, x2=120, ms=300, wait=1.5): sh("shell", "input", "swipe", str(x1), str(y), str(x2), str(y), str(ms)); time.sleep(wait)
def drag(x, y1, y2, ms=400, wait=1.5): sh("shell", "input", "swipe", str(x), str(y1), str(x), str(y2), str(ms)); time.sleep(wait)
def calls(tag): note(f"call-log rows {tag}: " + subprocess.run(["adb", "shell", "content query --uri content://call_log/calls --projection _id | wc -l"], capture_output=True, text=True).stdout.strip() + " / in-call: " + subprocess.run(["adb", "shell", "dumpsys telecom | grep -c 'state=DIALING\\|state=CONNECTING\\|state=ACTIVE'"], capture_output=True, text=True).stdout.strip())
def hangup():
    # ENDCALL без звонка усыпляет экран: кладём трубку через telecom-кнопку, потом будим
    sh("shell", "input", "keyevent", "KEYCODE_ENDCALL"); time.sleep(1.5)
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); sh("shell", "wm", "dismiss-keyguard"); time.sleep(1)
def keys(s):
    for c in s: sh("shell", "input", "keyevent", f"KEYCODE_{c}"); time.sleep(0.25)
def record(name, seconds, actions):
    remote = f"/sdcard/{name}.mp4"
    p = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(seconds), "--bit-rate", "10000000", remote])
    time.sleep(1.0); actions(); p.wait(); time.sleep(1)
    sh("pull", remote, os.path.join(OUT, f"{name}.mp4")); note(f"video {name}")
def start(wait=8):
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(wait)


def fast(name):
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    note(f"fast {name}")
def logrows(): return subprocess.run(["adb", "shell", "content query --uri content://call_log/calls --projection _id | wc -l"], capture_output=True, text=True).stdout.strip()
def feed_rows():
    # строки ленты: кликабельные узлы с описанием во всю ширину в нижней половине
    return [(x, y, d) for t, d, x, y, b in nodes() if d and b[2] - b[0] > 800 and 900 < y < 2100]
def onscreen(): return [t or d for t, d, x, y, b in nodes() if t or d]

if sys.argv[1] == "onboarding":
    start(); shot("onb-1-role")
    tap(T["role"], wait=2.5); shot("onb-role-system-dialog"); back(2)
    tap(T["notnow"], wait=2); shot("onb-2-perms")
    sys.exit(0)

sh("shell", "cmd", "uimode", "night", "no")
start(); shot("home"); note(f"feed rows: {feed_rows()}")
if sys.argv[1] == "short":
    tap(T["keypad"], wait=2); shot("keypad"); back(); back()
    start(); record("pull-contacts", 6, lambda: swipe(1700, 800, 300, 2.5)); shot("contacts"); back(); back()
    start(); r = feed_rows()
    if r: tap(r[0][2], long=True, wait=2); shot("row-menu"); tap(T["card"], wait=2.5); shot("card")
    start(); tap(T["settings"], wait=2.5); shot("settings")
    sys.exit(0)

# 1. лента: вертикальные свайпы не звонят
c0 = logrows()
for y1, y2 in [(1500, 1700), (1800, 1500), (1400, 1650), (1650, 1350)]: swipe(y1, y2, 250, 0.8)
time.sleep(2); shot("after-vertical-swipes"); note(f"calls before/after vertical swipes: {c0} -> {logrows()}")
# 2. тяга вверх к контактам, указатель
start(); record("pull-up-contacts", 7, lambda: swipe(1700, 700, 300, 3)); shot("contacts-sheet")
back(1.2); shot("contacts-keyboard-hidden")
idx = [b for t, d, x, y, b in nodes() if d and len(d) > 20 and all(len(w) == 1 for w in d.split())]
note(f"index bounds: {idx}")
if idx:
    x1, y1, x2, y2 = idx[0]; xc = (x1 + x2) // 2
    sh("shell", "input", "tap", str(xc), str(y1 + (y2 - y1) * 3 // 4)); time.sleep(1.5); shot("index-tap-lower")
    record("index-scrub", 6, lambda: (sh("shell", "input", "swipe", str(xc), str(y1 + 10), str(xc), str(y2 - 10), "2500"), time.sleep(1.5)))
    shot("index-after-scrub")
swipe(1500, 700); shot("contacts-scrolled")
back(); back()
# 3. тап по строке ленты
start(); r = feed_rows(); note(f"rows: {r}")
if r:
    c0 = logrows(); x, y, d = r[0]
    record("feed-row-tap", 7, lambda: (sh("shell", "input", "tap", str(x), str(y)), time.sleep(4)))
    shot("after-feed-row-tap"); note(f"row tap '{d}': calls {c0} -> {logrows()}")
    hangup()
# 4. долгое нажатие: меню, «Скрыть», «Вернуть»
start(); r = feed_rows()
if r:
    x, y, d = r[0]
    sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900"); time.sleep(2); shot("feed-row-menu")
    if tap(T["hidebtn"], wait=0.5) or tap(T["remove"], wait=0.5):
        fast("hide-0.5s"); time.sleep(1); fast("hide-1.5s")
        note(f"undo visible: {[s for s in onscreen() if T['undo'] in s]}")
        tap(T["undo"], wait=2); shot("after-undo")
# 5. свайп строки ленты влево
start(); r = feed_rows()
if r:
    x, y, d = r[-1]
    record("feed-row-swipe-left", 8, lambda: (sh("shell", "input", "swipe", "950", str(y), "120", str(y), "700"), time.sleep(1), fast("feed-swipe-1s"), time.sleep(3)))
    shot("after-feed-swipe-left")
# 6. карточка
start(); r = feed_rows()
card_ok = False
if r:
    x, y, d = [q for q in r if "Мама" in q[2]][0] if any("Мама" in q[2] for q in r) else r[0]
    record("feed-to-card", 6, lambda: (sh("shell", "input", "tap", str(x), str(y)), time.sleep(3)))
    card_ok = True
if card_ok:
    shot("card"); note(f"card nodes: {onscreen()[:40]}")
    swipe(1700, 900); shot("card-scrolled")
    hist = [(x, y, t or d) for t, d, x, y, b in nodes() if b[2] - b[0] > 800 and (b[3] - b[1]) < 260 and 600 < y < 2100]
    note(f"card rows: {hist}")
    if hist:
        hx, hy, ht = hist[-1]
        record("card-history-swipe", 7, lambda: (sh("shell", "input", "swipe", "950", str(hy), "120", str(hy), "700"), time.sleep(3)))
        shot("card-after-history-swipe")
    record("card-pull-down", 6, lambda: drag(540, 1100, 2250, 350, 2.5)); shot("after-card-pull-down")
# 7. клавиатура
start(); record("keypad-open", 5, lambda: tap(T["keypad"], wait=2.5)); shot("keypad-empty")
keys("259"); time.sleep(1); shot("keypad-t9")
for _ in range(3): sh("shell", "input", "keyevent", "KEYCODE_DEL")
keys("89161234567"); time.sleep(1); shot("keypad-number")
record("keypad-pull-from-keys", 6, lambda: drag(540, 1500, 2300, 400, 2.5)); shot("after-keypad-pull-from-keys")
start(); tap(T["keypad"], wait=2); drag(540, 700, 2000, 400, 2.5); shot("after-keypad-pull-from-suggestions")
# буфер обмена с номером: «Вставить номер»
sh("shell", "am", "broadcast", "-a", "clipper.set", "-e", "text", "+79031234567")
start(); tap(T["keypad"], wait=2); shot("keypad-paste-check"); back(); back()
# 8. настройки
start(); record("to-settings", 6, lambda: tap(T["settings"], wait=2.5)); shot("settings")
swipe(1700, 500); shot("settings-2"); swipe(1700, 500); shot("settings-3"); back()
# 9. звонки
start(); record("incoming", 14, lambda: (subprocess.run(["adb", "emu", "gsm", "call", "+79161112233"]), time.sleep(4), shot("incoming-full"), tap(T["answer"], wait=1), fast("answer-1s"), time.sleep(2)))
shot("in-call"); note("telecom: " + subprocess.run(["adb", "shell", "dumpsys telecom | grep -m3 -E 'state=(ACTIVE|RINGING)'"], capture_output=True, text=True).stdout.strip())
subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112233"]); time.sleep(1.2); shot("call-ended"); time.sleep(4)
subprocess.run(["adb", "emu", "gsm", "call", "+74997001122"]); time.sleep(4); shot("incoming-unknown")
subprocess.run(["adb", "emu", "gsm", "cancel", "+74997001122"]); time.sleep(4); shot("after-unknown")
# 10. тёмная тема и крупный шрифт
sh("shell", "cmd", "uimode", "night", "yes"); time.sleep(2)
start(); shot("dark-home"); tap(T["keypad"]); keys("259"); time.sleep(1); shot("dark-keypad"); back(); back()
start(); swipe(1700, 700, 300, 3); shot("dark-contacts"); back(); back()
start(); tap(T["settings"], wait=2.5); shot("dark-settings"); back()
sh("shell", "cmd", "uimode", "night", "no")
sh("shell", "settings", "put", "system", "font_scale", "1.3"); time.sleep(1)
start(); shot("font130-home"); tap(T["keypad"]); time.sleep(1); shot("font130-keypad"); back(); back()
sh("shell", "settings", "put", "system", "font_scale", "1.0")
note("done")
