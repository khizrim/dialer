#!/usr/bin/env python3
"""Проходит основные экраны Ozero Phone в эмуляторе: скриншоты, тексты экранов и видео переходов."""
import os, re, subprocess, time, xml.etree.ElementTree as ET

PKG = "com.khizrim.dialer"
OUT = os.environ.get("OUT", "shots")
os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "log.txt"), "w")
step = [0]


def sh(*a):
    return subprocess.run(["adb", *a], capture_output=True, text=True).stdout


def note(s):
    print(s); log.write(s + "\n"); log.flush()


def nodes():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    try:
        root = ET.fromstring(sh("shell", "cat", "/sdcard/ui.xml"))
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((n.get("text", ""), n.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def shot(name):
    step[0] += 1
    base = os.path.join(OUT, f"{step[0]:02d}-{name}")
    with open(base + ".png", "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    with open(base + ".txt", "w") as f:
        for t, d, x, y in nodes():
            if t or d:
                f.write(f"{x},{y}\t{t}\t{d}\n")
    note(f"shot {base}")


def find(label, exact=False):
    for t, d, x, y in nodes():
        if (label in (t, d)) if exact else (label in t or label in d):
            return x, y
    return None


def tap(label, long=False, wait=1.5, exact=False):
    p = find(label, exact)
    if not p:
        note(f"MISSING: {label}"); return False
    x, y = p
    if long:
        sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")
    else:
        sh("shell", "input", "tap", str(x), str(y))
    note(f"tap {'long ' if long else ''}{label} @ {x},{y}")
    time.sleep(wait)
    return True


def back(wait=1.5):
    sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(wait)


def record(name, seconds, actions):
    remote = f"/sdcard/{name}.mp4"
    p = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(seconds), "--bit-rate", "8000000", remote])
    time.sleep(0.8)
    actions()
    p.wait(); time.sleep(1)
    sh("pull", remote, os.path.join(OUT, f"{name}.mp4"))
    note(f"video {name}.mp4")


def start(wait=7):
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(wait)


def step_safe(fn):
    try:
        fn()
    except Exception as e:  # один упавший шаг не должен обрывать весь прогон
        note(f"ERROR in {fn.__name__}: {e!r}")


def cold():
    sh("shell", "am", "force-stop", PKG)
    r = sh("shell", "am", "start", "-W", "-n", f"{PKG}/.MainActivity")
    note("cold start: " + " ".join(l for l in r.splitlines() if "Time" in l))
    time.sleep(1.5); shot("cold-1.5s")
    time.sleep(5); shot("home")
    for label in ["Пропустить", "Не сейчас", "Продолжить"]:
        if find(label):
            tap(label, wait=2); shot("onboarding")


def keypad():
    def typing():
        tap("Открыть клавиатуру", wait=1.2)
        for d in "916":
            tap(f"Набрать {d}", wait=0.35)
        time.sleep(1)
    record("keypad", 7, typing)
    shot("keypad-916")
    sh("shell", "input", "keyevent", "KEYCODE_5"); time.sleep(1); shot("keypad-hw-key")
    record("keypad-close", 4, lambda: tap("Скрыть клавиатуру", wait=1.5))
    shot("keypad-closed")


def settings():
    record("to-settings", 5, lambda: tap("Настройки", wait=2))
    shot("settings")
    sh("shell", "input", "swipe", "540", "1700", "540", "500", "500"); time.sleep(1); shot("settings-2")
    sh("shell", "input", "swipe", "540", "1700", "540", "500", "500"); time.sleep(1); shot("settings-3")
    record("settings-back", 4, lambda: back(2))
    shot("home-again")


def contacts():
    record("to-contacts", 5, lambda: tap("Контакты", wait=2, exact=True))
    shot("contacts")
    record("contacts-scroll", 5, lambda: (sh("shell", "input", "swipe", "540", "1500", "540", "800", "300"), time.sleep(2)))
    shot("contacts-scrolled")
    record("open-person", 5, lambda: tap("Аня", wait=2))
    shot("person")
    back(); back()


def journal():
    start()
    record("to-journal", 5, lambda: tap("Журнал", wait=2, exact=True))
    shot("journal")
    tap("Пропущенные", wait=1.5); shot("journal-missed")
    tap("Все", wait=1.5, exact=True)
    if tap("Очистить журнал", wait=2):
        shot("clear-dialog")
        for label in ["Разрешить", "Allow"]:
            if find(label, exact=True):
                tap(label, wait=2, exact=True); shot("perm")
        record("clear-confirm", 5, lambda: tap("Очистить", wait=2.5, exact=True))
        shot("journal-cleared")
    start()
    shot("home-after-clear")


def home_menu():
    first = None
    for t, d, x, y in nodes():
        if t and 300 < y < 2000 and t not in ("Журнал", "Контакты"):
            first = t; break
    note(f"first row: {first}")
    if not first:
        return
    tap(first, long=True, wait=1.5); shot("menu")
    if tap("Добавить в избранное", wait=2):
        shot("after-fav")
    tap(first, long=True, wait=1.5)
    record("remove-from-home", 6, lambda: tap("Убрать с главной", wait=3))
    shot("after-remove")
    time.sleep(5); shot("after-remove-5s")
    if tap("Очистить главную", wait=1.5):
        shot("clear-home-dialog")
        record("clear-home", 5, lambda: tap("Очистить", wait=2.5, exact=True))
        shot("home-cleared")


def incoming():
    start()

    def ring():
        subprocess.run(["adb", "emu", "gsm", "call", "+79161112233"]); time.sleep(5)
    record("incoming-in-app", 8, ring)
    shot("incoming-in-app")
    sh("shell", "cmd", "statusbar", "expand-notifications"); time.sleep(1.5); shot("incoming-shade")
    sh("shell", "cmd", "statusbar", "collapse"); time.sleep(1)
    if tap("Ответить", wait=3) or tap("Answer", wait=3):
        shot("in-call")
    subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112233"]); time.sleep(4); shot("after-call")

    sh("shell", "input", "keyevent", "KEYCODE_HOME"); time.sleep(2)
    subprocess.run(["adb", "emu", "gsm", "call", "+79161112244"]); time.sleep(5); shot("incoming-from-launcher")
    with open(os.path.join(OUT, "notifications.txt"), "w") as f:
        f.write(sh("shell", "dumpsys", "notification", "--noredact"))
    subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112244"]); time.sleep(3)

    sh("shell", "input", "keyevent", "KEYCODE_SLEEP"); time.sleep(2)
    subprocess.run(["adb", "emu", "gsm", "call", "+79035556677"]); time.sleep(6); shot("incoming-screen-off")
    subprocess.run(["adb", "emu", "gsm", "cancel", "+79035556677"]); time.sleep(3)
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP"); sh("shell", "wm", "dismiss-keyguard"); time.sleep(2)


def dark():
    sh("shell", "cmd", "uimode", "night", "yes"); time.sleep(2); start(); shot("home-dark")
    tap("Открыть клавиатуру", wait=1.5); shot("keypad-dark")
    sh("shell", "cmd", "uimode", "night", "no")


for fn in (cold, keypad, settings, contacts, journal, home_menu, incoming, dark):
    step_safe(fn)
note("done")
