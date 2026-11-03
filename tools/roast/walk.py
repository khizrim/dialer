#!/usr/bin/env python3
"""Проход по экранам Ozero Phone: скриншоты, тексты и видео переходов (основа: tools/emulator из треда обзора)."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

PKG = "com.khizrim.dialer"
OUT = os.environ.get("OUT", "shots"); os.makedirs(OUT, exist_ok=True)
L = os.environ.get("LANGSET", "en")
T = {
 "en": dict(keypad="Open keypad", hide="Hide keypad", journal="Recents", contacts="Contacts", settings="Settings",
            card="Open card", other="Another way", change="Change default", answer="Answer", role="Make default phone app",
            notnow="Not now", perms="Allow access", remove="Remove from home", edit="Edit contact"),
 "ru": dict(keypad="Открыть клавиатуру", hide="Скрыть клавиатуру", journal="Журнал", contacts="Контакты", settings="Настройки",
            card="Открыть карточку", other="Другой способ", change="Изменить по умолчанию", answer="Ответить", role="Сделать звонилкой",
            notnow="Не сейчас", perms="Разрешить", remove="Убрать с главной", edit="Изменить контакт"),
}[L]
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

if sys.argv[1] == "onboarding":
    start(); shot("onb-1-role")
    tap(T["role"], wait=2.5); shot("onb-role-system-dialog"); back(2)
    shot("onb-after-role-cancel")
    tap(T["notnow"], wait=2); shot("onb-2-perms")
    tap(T["perms"], wait=2.5); shot("onb-system-perm-dialog")
    sys.exit(0)

sh("shell", "cmd", "uimode", "night", "no")
start(); shot("home")
swipe(1500, 1600, 200, 1); shot("home-after-small-drag")
record("home-pull-up-search", 6, lambda: swipe(1700, 900, 300, 2.5)); shot("name-search")
keys("PE"); time.sleep(1); shot("name-search-typed"); back(); back()
start(); record("home-pull-down-journal", 6, lambda: swipe(700, 1600, 300, 2.5)); shot("journal-from-pull"); back()
start(); record("keypad-open-type-close", 10, lambda: (tap(T["keypad"]), keys("89161234567"), time.sleep(1.5), tap(T["hide"])))
start(); tap(T["keypad"]); shot("keypad-empty")
keys("259"); time.sleep(1); shot("keypad-t9-259")
for _ in range(3): sh("shell", "input", "keyevent", "KEYCODE_DEL")
keys("89161234567"); time.sleep(1); shot("keypad-number-ru")
sh("shell", "input", "keyevent", "KEYCODE_DEL"); keys("8"); time.sleep(0.5)
for _ in range(11): sh("shell", "input", "keyevent", "KEYCODE_DEL")
keys("4449990001"); time.sleep(1); shot("keypad-unknown-number")
back(); back()
start()
record("home-to-contacts", 6, lambda: tap(T["contacts"], wait=2.5)); shot("contacts")
swipe(1600, 700); shot("contacts-scrolled"); back()
start(); record("home-to-journal", 6, lambda: tap(T["journal"], wait=2.5)); shot("journal"); back()
start(); record("home-to-settings", 6, lambda: tap(T["settings"], wait=2.5)); shot("settings")
swipe(1700, 500); shot("settings-2"); swipe(1700, 500); shot("settings-3"); swipe(1700, 500); shot("settings-4"); back()
start(); record("long-press-menu", 6, lambda: tap("Мама", long=True, wait=2)); shot("person-menu")
record("menu-to-card", 6, lambda: tap(T["card"], wait=2.5)); shot("person")
record("card-other-way", 6, lambda: tap(T["other"], wait=2)); shot("sheet-other-way"); back()
tap(T["change"], wait=2); shot("sheet-change-default"); back()
record("card-back-home", 5, lambda: back(2)); shot("back-home")
start(); tap("+7 (926) 777-88-99", long=True, wait=2)
record("remove-from-home", 8, lambda: tap(T["remove"], wait=4)); shot("after-remove")
time.sleep(5); shot("after-toast")
# жесты
calls("before-stack-swipes")
start()
for y1, y2 in [(1500, 1700), (1800, 1500), (1400, 1650), (1650, 1350)]: swipe(y1, y2, 250, 0.8)
shot("stack-after-vertical-swipes"); calls("after-stack-swipes")
start(); p = find("Мама")
if p: record("home-swipe-row-left", 7, lambda: hswipe(p[1], wait=3)); shot("home-after-row-swipe")
start(); tap(T["journal"], wait=2.5); shot("journal-2")
rows = [b for t, d, x, y, b in nodes() if t and y > 400 and y < 2050]
note(f"journal rows: {len(rows)}")
jr = [y for t, d, x, y, b in nodes() if t.startswith("+7") and 1300 < y < 2000]
jy = jr[-1] if jr else 1900; note(f"journal swipe row y={jy}")
def fast(name):
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
def logrows(): return subprocess.run(["adb", "shell", "content query --uri content://call_log/calls --projection _id | wc -l"], capture_output=True, text=True).stdout.strip()
note(f"journal rows before swipe: {logrows()}")
sh("shell", "input", "swipe", "950", str(jy), "120", str(jy), "900"); t0 = time.time()
time.sleep(0.3); fast("undo-0.3s"); note(f"t={time.time()-t0:.1f} rows={logrows()}")
time.sleep(max(0, 1 - (time.time() - t0))); fast("undo-1s")
d = [t or dd for t, dd, x, y, b in nodes() if any(k in (t + dd) for k in ("Undo", "Вернуть", "Deleted", "Удал"))]
note(f"t={time.time()-t0:.1f} undo nodes: {d} rows={logrows()}")
time.sleep(max(0, 2 - (time.time() - t0))); fast("undo-2s"); note(f"t={time.time()-t0:.1f} rows={logrows()}")
time.sleep(max(0, 3.5 - (time.time() - t0))); fast("undo-3.5s"); note(f"t={time.time()-t0:.1f} rows={logrows()}")
time.sleep(max(0, 5 - (time.time() - t0))); fast("undo-5s"); note(f"t={time.time()-t0:.1f} rows={logrows()}"); shot("journal-after-row-swipe")
time.sleep(1); shot("journal-undo-toast")
record("journal-pull-up-home", 6, lambda: swipe(1700, 800, 300, 2.5)); shot("journal-after-pull-up")
start(); record("contacts-sheet-drag-down", 9, lambda: (swipe(1700, 900, 300, 2.5), shot("sheet-open"), drag(540, 1350, 2250, 400, 2.5)))
shot("after-sheet-drag-down")
start(); record("keypad-drag-down", 8, lambda: (tap(T["keypad"], wait=2), shot("keypad-before-drag"), drag(540, 1500, 2300, 400, 2.5)))
shot("after-keypad-drag-down")
start(); tap(T["keypad"], wait=2); drag(540, 700, 2000, 400, 2.5); shot("after-keypad-drag-from-suggestions")
start(); tap("Мама", long=True, wait=2); tap(T["card"], wait=2.5); shot("person-2")
num = [(x, y, t) for t, d, x, y, b in nodes() if t.startswith("+7")]
note(f"card numbers: {num}")
if num:
    x, y, _ = num[0]
    record("card-number-long", 6, lambda: sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")); shot("card-number-long")
    back(); calls("before-card-tap")
    sh("shell", "input", "tap", str(x), str(y)); time.sleep(3); shot("card-number-tap"); calls("after-card-tap")
    hangup(); time.sleep(2)
start(); tap("Мама", long=True, wait=2); tap(T["card"], wait=2.5)
record("card-swipe-down", 6, lambda: drag(540, 1350, 2250, 350, 2.5)); shot("after-card-swipe-down")
start(); tap(T["keypad"], wait=2); keys("89161234567"); time.sleep(1); shot("keypad-before-erase")
record("keypad-number-swipe-left", 6, lambda: (sh("shell", "input", "swipe", "900", "330", "150", "330", "250"), time.sleep(2.5))); shot("keypad-after-erase-top")
p2 = None
for t, d, x, y, b in nodes():
    if t and t[0] in "+8" and t.replace(" ","").replace("-","").replace("(","").replace(")","").lstrip("+").isdigit() and 900 < y < 1500: p2 = (x, y)
note(f"dialed field: {p2}")
if p2:
    record("keypad-number-swipe-left-2", 6, lambda: (sh("shell", "input", "swipe", "950", str(p2[1]), "120", str(p2[1]), "250"), time.sleep(2.5))); shot("keypad-after-erase")
back(); back()
start(); tap(T["journal"], wait=2.5); calls("before-journal-swipe-right")
jr = [y for t, d, x, y, b in nodes() if t.startswith("+7") and 1300 < y < 2000]
jy2 = jr[-1] if jr else 1900; note(f"journal swipe-right row y={jy2}")
record("journal-swipe-row-right", 8, lambda: (sh("shell", "input", "swipe", "120", str(jy2), "950", str(jy2), "900"), time.sleep(4))); shot("journal-after-swipe-right"); calls("after-journal-swipe-right")
hangup(); time.sleep(3)
# звонки
start(); record("incoming-contact", 14, lambda: (subprocess.run(["adb", "emu", "gsm", "call", "+79161112233"]), time.sleep(4), tap(T["answer"], wait=4)))
shot("in-call")
subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112233"]); time.sleep(1.2); shot("call-ended"); time.sleep(4)
subprocess.run(["adb", "emu", "gsm", "call", "+74997001122"]); time.sleep(4); shot("incoming-unknown")
subprocess.run(["adb", "emu", "gsm", "cancel", "+74997001122"]); time.sleep(4); shot("after-unknown")
# тёмная тема
sh("shell", "cmd", "uimode", "night", "yes"); time.sleep(2)
start(); shot("dark-home")
tap(T["keypad"]); keys("259"); time.sleep(1); shot("dark-keypad"); back(); back()
start(); tap("Мама", long=True, wait=2); tap(T["card"], wait=2.5); shot("dark-person"); back()
start(); tap(T["settings"], wait=2.5); shot("dark-settings"); back()
subprocess.run(["adb", "emu", "gsm", "call", "+79161112244"]); time.sleep(4); shot("dark-incoming")
tap(T["answer"], wait=4); shot("dark-in-call")
subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112244"]); time.sleep(3)
sh("shell", "cmd", "uimode", "night", "no")
# крупный шрифт (у Samsung часто увеличен)
sh("shell", "settings", "put", "system", "font_scale", "1.3"); time.sleep(1)
start(); shot("font130-home"); tap(T["keypad"]); time.sleep(1); shot("font130-keypad"); back(); back()
sh("shell", "settings", "put", "system", "font_scale", "1.0")
note("done")
