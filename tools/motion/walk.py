#!/usr/bin/env python3
"""Записывает каждый переход Ozero Phone отдельным видео (screenrecord) для покадрового разбора."""
import os, re, subprocess, time, xml.etree.ElementTree as ET

PKG = "com.khizrim.dialer"
OUT = os.environ.get("OUT", "motion"); os.makedirs(OUT, exist_ok=True)
log = open(os.path.join(OUT, "log.txt"), "w")

def sh(*a): return subprocess.run(["adb", *a], capture_output=True, text=True).stdout
def note(s): print(s, flush=True); log.write(s + "\n"); log.flush()
def nodes():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    try: root = ET.fromstring(sh("shell", "cat", "/sdcard/ui.xml"))
    except ET.ParseError: return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"\d+", n.get("bounds", ""))
        if len(b) == 4:
            x1, y1, x2, y2 = map(int, b)
            out.append((n.get("text", ""), n.get("content-desc", ""), (x1 + x2) // 2, (y1 + y2) // 2))
    for t, d, x, y in out:
        if t in ("Wait", "Подождать"): sh("shell", "input", "tap", str(x), str(y)); time.sleep(2); return nodes()
    return out
def find(label):
    for t, d, x, y in nodes():
        if label == t or label == d or (len(label) > 3 and (label in t or label in d)): return x, y
    return None
def tap(label, long=False, wait=1.5):
    p = find(label)
    if not p: time.sleep(1.5); p = find(label)
    if not p: note(f"MISSING: {label}"); return False
    x, y = p
    if long: sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")
    else: sh("shell", "input", "tap", str(x), str(y))
    time.sleep(wait); return True
def back(wait=1.5): sh("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(wait)
def swipe(x1, y1, x2, y2, ms=300, wait=2.0): sh("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms)); time.sleep(wait)
def slow_drag(x, y1, y2, hold_at=None, steps=24, wait=2.0, dx=0):
    """Медленный жест пальцем: видно, идёт ли слой за пальцем и что будет после отпускания."""
    sh("shell", "input", "motionevent", "DOWN", str(x), str(y1))
    for i in range(1, steps + 1):
        y = y1 + (y2 - y1) * i // steps; xx = x + dx * i // steps
        sh("shell", "input", "motionevent", "MOVE", str(xx), str(y)); time.sleep(0.03)
        if hold_at is not None and i == steps // 2: time.sleep(hold_at)
    sh("shell", "input", "motionevent", "UP", str(x + dx), str(y2)); time.sleep(wait)
def keys(s):
    for c in s: sh("shell", "input", "keyevent", f"KEYCODE_{c}"); time.sleep(0.2)
def record(name, seconds, actions):
    remote = f"/sdcard/m-{name}.mp4"
    p = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", str(seconds + 2), "--bit-rate", "12000000", remote])
    time.sleep(1.2)
    sh("shell", "log", "-t", "OzMotion", f"walk {name} actions")
    try: actions()
    except Exception as e: note(f"ERR {name}: {e}")
    p.wait(); time.sleep(1)
    sh("pull", remote, os.path.join(OUT, f"{name}.mp4")); sh("shell", "rm", remote); note(f"video {name}")
def start(wait=6):
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(wait)
    nodes()

# Переходы интерфейса «с нуля» (сборки 264+): лента, док, экран человека, листы, клавиатура, звонок.
CARD = "Никита"            # не в избранном: нажатие на имя открывает экран человека
TWO = "Ирина Два Номера"   # два номера: переключатель номеров на экране человека

def keypad():
    # круглая кнопка клавиатуры справа от «Поиска» (у неё нет текста для uiautomator)
    if not tap("Открыть клавиатуру", wait=0.1): sh("shell", "input", "tap", "975", "2240")

def card(name=CARD):
    start(); tap(name, wait=2.5)
def contacts():
    start(); tap("Поиск", wait=2.5)

def walk(theme):
    sh("shell", "cmd", "uimode", "night", "yes" if theme == "dark" else "no"); time.sleep(1.5)
    t = lambda n: f"{theme}-{n}"
    # каждый переход — с чистого запуска, чтобы сбой одного не ломал следующие
    start(); record(t("01-home-tap-person"), 4, lambda: tap(CARD, wait=2.2))
    card(); record(t("02-person-back"), 4, lambda: back(2.2))
    card(); record(t("03-person-drag-down-slow"), 6, lambda: slow_drag(540, 700, 1900, hold_at=0.6, wait=2.2))
    card(); record(t("04-person-swipe-down-fast"), 4, lambda: swipe(540, 700, 540, 2000, 200, 2.2))
    card(); record(t("05-person-drag-cancel"), 5, lambda: slow_drag(540, 700, 1000, hold_at=0.5, wait=2.0))
    card(); record(t("06-person-star"), 5, lambda: (tap("Добавить в избранное", wait=2.0), tap("Убрать из избранного", wait=2.0)))
    start(); tap("Поиск", wait=2.5); (tap(TWO, wait=2.5) or tap("Сергей Работа", wait=2.5))
    record(t("07-person-two-numbers"), 6, lambda: (tap("+7 495", wait=1.6), tap("+7 916", wait=1.6)))
    start(); record(t("08-home-tap-search"), 4, lambda: tap("Поиск", wait=2.2))
    contacts(); record(t("09-contacts-type-erase"), 7, lambda: (subprocess.run(["adb", "shell", "input", "text", "an"]), time.sleep(1.8),
                                                                 [sh("shell", "input", "keyevent", "KEYCODE_DEL") for _ in range(2)], time.sleep(1.8)))
    contacts(); back(1.0); record(t("10-contacts-drag-down"), 6, lambda: slow_drag(540, 600, 2000, wait=2.2))
    start(); record(t("11-dock-pull-up-slow"), 6, lambda: slow_drag(540, 2250, 1000, hold_at=0.6, wait=2.2))
    start(); record(t("12-keypad-open-type"), 7, lambda: (keypad(), time.sleep(1.4), keys("8916"), time.sleep(1.5)))
    record(t("13-keypad-erase-close"), 7, lambda: ([sh("shell", "input", "keyevent", "KEYCODE_DEL") for _ in range(4)], time.sleep(1.0), back(2.0)))
    start(); record(t("14-row-menu"), 5, lambda: tap(CARD, long=True, wait=2.2))
    record(t("15-menu-to-other-way"), 5, lambda: tap("Другой способ", wait=2.2))
    record(t("16-sheet-drag-down"), 5, lambda: slow_drag(540, 1900, 2350, wait=2.0))
    contacts(); record(t("17-to-settings-and-back"), 7, lambda: (tap("Настройки", wait=2.2), back(2.2)))
    start(); p = find(CARD)
    if p: record(t("18-row-swipe-away"), 7, lambda: swipe(950, p[1], 120, p[1], 250, 4.5))
    start(); record(t("19-incoming-answer-end"), 14, lambda: (subprocess.run(["adb", "emu", "gsm", "call", "+79161112233"]), time.sleep(3.5),
                                                         tap("Ответить", wait=3.5), subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112233"]), time.sleep(4)))
    card(); record(t("20-person-call"), 8, lambda: (tap("Позвонить", wait=4.0), hangup_now()))

def hangup_now():
    subprocess.run(["adb", "emu", "gsm", "cancel", "+79035556677"]); sh("shell", "input", "keyevent", "KEYCODE_ENDCALL"); time.sleep(2.5)
    sh("shell", "input", "keyevent", "KEYCODE_WAKEUP")

walk("light")
walk("dark")
note("done")
