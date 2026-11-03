#!/usr/bin/env python3
"""Проходит основные экраны Ozero Phone в эмуляторе: скриншоты и видео переходов."""
import os, re, subprocess, time, xml.etree.ElementTree as ET

PKG = "com.khizrim.dialer"
OUT = os.environ.get("OUT", "shots")
os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "log.txt"), "w")
step = [0]

def sh(*a, check=False):
    r = subprocess.run(["adb", *a], capture_output=True, text=True)
    return r.stdout

def note(s):
    print(s); log.write(s + "\n"); log.flush()

def shot(name):
    step[0] += 1
    path = os.path.join(OUT, f"{step[0]:02d}-{name}.png")
    with open(path, "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout)
    note(f"shot {path}")

def nodes():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = sh("shell", "cat", "/sdcard/ui.xml")
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((n.get("text", ""), n.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2))
    return out

def find(label):
    for t, d, x, y in nodes():
        if label in t or label in d:
            return x, y
    return None

def tap(label, long=False, wait=1.2):
    p = find(label)
    if not p:
        note(f"MISSING: {label}")
        return False
    x, y = p
    if long:
        sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")
    else:
        sh("shell", "input", "tap", str(x), str(y))
    note(f"tap {'long ' if long else ''}{label} @ {x},{y}")
    time.sleep(wait)
    return True

def back(wait=1.0):
    sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(wait)

def record(name, seconds, actions):
    """Пишем экран, пока выполняются действия: видно анимации."""
    remote = f"/sdcard/{name}.mp4"
    p = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(seconds), "--bit-rate", "8000000", remote])
    time.sleep(1.0)
    actions()
    p.wait()
    time.sleep(1)
    sh("pull", remote, os.path.join(OUT, f"{name}.mp4"))
    note(f"video {name}.mp4")

def texts(name):
    with open(os.path.join(OUT, f"{step[0]:02d}-{name}.txt"), "w") as f:
        for t, d, x, y in nodes():
            if t or d:
                f.write(f"{x},{y}\t{t}\t{d}\n")

def start():
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(4)

start()
shot("first-screen"); texts("first-screen")
# если показан первый запуск, пробуем пройти его
for label in ["Продолжить", "Начать", "Разрешить", "Готово", "Не сейчас", "Пропустить"]:
    if find(label):
        tap(label, wait=2); shot("onboarding")
start()
shot("home"); texts("home")

record("keypad-open-close", 8, lambda: (tap("Открыть клавиатуру", wait=1.5), sh("shell", "input", "text", "259"), time.sleep(1.5), tap("Скрыть клавиатуру", wait=1.5)))
tap("Открыть клавиатуру", wait=1.5); shot("keypad-empty")
for d in "259":
    sh("shell", "input", "keyevent", f"KEYCODE_{d}"); time.sleep(0.3)
time.sleep(1); shot("keypad-t9-259"); texts("keypad")
back()

record("home-to-contacts", 6, lambda: tap("Все контакты", wait=2.5))
shot("contacts"); texts("contacts")
record("contacts-to-settings", 6, lambda: tap("Настройки", wait=2.5))
shot("settings"); texts("settings")
sh("shell", "input", "swipe", "540", "1600", "540", "500", "400"); time.sleep(1); shot("settings-scrolled")
back(); back(1.5)
shot("back-home")

# карточка человека
record("open-person", 7, lambda: (tap("Мама", long=True, wait=1.5), tap("Открыть карточку", wait=2.5)))
shot("person"); texts("person")
back(1.5)

# долгое нажатие и уборка
tap("Аня", long=True, wait=1.5); shot("menu"); texts("menu")
record("remove-from-home", 7, lambda: tap("Убрать с главной", wait=3))
shot("after-remove"); texts("after-remove")
time.sleep(5); shot("after-toast")

# недавние
start()
tap("Все контакты", wait=2)
tap("Недавние", wait=2); shot("recents"); texts("recents")
back(1.5); back(1.5)

# входящий звонок от контакта и незнакомого
record("incoming-contact", 12, lambda: (subprocess.run(["adb", "emu", "gsm", "call", "+79161112233"]), time.sleep(4), tap("Ответить", wait=3)))
shot("in-call"); texts("in-call")
subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112233"]); time.sleep(3); shot("call-ended")
time.sleep(3)
subprocess.run(["adb", "emu", "gsm", "call", "+74997001122"]); time.sleep(4); shot("incoming-unknown"); texts("incoming-unknown")
subprocess.run(["adb", "emu", "gsm", "cancel", "+74997001122"]); time.sleep(4); shot("after-unknown")

# тёмная тема
sh("shell", "cmd", "uimode", "night", "yes"); time.sleep(2); start(); shot("home-dark")
sh("shell", "cmd", "uimode", "night", "no")
note("done")
