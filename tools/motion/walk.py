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
    try: actions()
    except Exception as e: note(f"ERR {name}: {e}")
    p.wait(); time.sleep(1)
    sh("pull", remote, os.path.join(OUT, f"{name}.mp4")); sh("shell", "rm", remote); note(f"video {name}")
def start(wait=6):
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(wait)
    nodes()

def walk(theme):
    sh("shell", "cmd", "uimode", "night", "yes" if theme == "dark" else "no"); time.sleep(1.5)
    t = lambda n: f"{theme}-{n}"
    # журнал: жест вниз со стопки, кнопка, возврат назад и жестом вверх
    start(); record(t("01-home-pull-down-journal-fast"), 4, lambda: swipe(540, 700, 540, 1600, 250, 2.2))
    record(t("02-journal-back-home"), 4, lambda: back(2.2))
    record(t("03-home-pull-down-journal-slow"), 7, lambda: slow_drag(540, 700, 1500, hold_at=0.8, wait=2.2))
    record(t("04-journal-pull-up-home"), 5, lambda: slow_drag(540, 1800, 900, wait=2.2))
    start(); record(t("05-home-tap-journal"), 4, lambda: tap("Журнал", wait=2.2))
    record(t("06-journal-back-again"), 4, lambda: back(2.2))
    record(t("07-home-pull-down-cancel"), 5, lambda: slow_drag(540, 900, 1050, hold_at=0.5, wait=2.0))
    # контакты: жест вверх, кнопка, назад, тянуть вниз
    start(); record(t("08-home-pull-up-contacts-fast"), 4, lambda: swipe(540, 1700, 540, 900, 250, 2.2))
    record(t("09-contacts-back-home"), 4, lambda: back(1.0) or back(2.0))
    record(t("10-home-pull-up-contacts-slow"), 7, lambda: slow_drag(540, 1700, 900, hold_at=0.8, wait=2.2))
    record(t("11-contacts-drag-down-close"), 6, lambda: (back(1.0), slow_drag(540, 600, 1900, wait=2.2)))
    start(); record(t("12-home-tap-contacts"), 4, lambda: tap("Контакты", wait=2.2))
    # настройки
    start(); record(t("13-home-to-settings"), 4, lambda: tap("Настройки", wait=2.2))
    record(t("14-settings-back"), 4, lambda: back(2.2))
    # меню человека и карточка
    record(t("15-long-press-menu"), 5, lambda: tap("Мама", long=True, wait=2.2))
    record(t("16-menu-drag-down"), 5, lambda: slow_drag(540, 1600, 2300, wait=2.0))
    tap("Мама", long=True, wait=2)
    record(t("17-menu-to-card"), 4, lambda: tap("Открыть карточку", wait=2.2))
    record(t("18-card-other-way-sheet"), 5, lambda: (tap("Другой способ", wait=1.8), back(1.8)))
    record(t("19-card-drag-down"), 5, lambda: slow_drag(540, 900, 2000, wait=2.2))
    # клавиатура
    start(); record(t("20-keypad-open-type"), 7, lambda: (tap("Открыть клавиатуру", wait=1.4), keys("8916"), time.sleep(1.2)))
    record(t("21-keypad-erase-close"), 7, lambda: (keys("DEL" if False else ""), [sh("shell", "input", "keyevent", "KEYCODE_DEL") for _ in range(4)], time.sleep(0.8), back(2.0)))
    start(); tap("Открыть клавиатуру", wait=1.6)
    record(t("22-keypad-drag-down"), 5, lambda: slow_drag(540, 1300, 2300, wait=2.0))
    # уборка главной: смахнуть строку
    start(); p = find("Аня Смирнова") or find("Мама")
    if p: record(t("23-home-row-swipe-away"), 7, lambda: swipe(950, p[1], 120, p[1], 250, 4.5))
    # журнал: удалить звонок свайпом, фильтр
    start(); tap("Журнал", wait=2.2)
    record(t("24-journal-row-swipe-delete"), 7, lambda: swipe(950, 1900, 120, 1900, 250, 4.5))
    p = find("Пропущенные")
    if p: record(t("25-journal-filter"), 6, lambda: (tap("Пропущенные", wait=1.8), tap("Все", wait=1.8)))
    back(1.5)
    # звонок: входящий -> разговор -> конец
    start(); record(t("26-incoming-answer-end"), 14, lambda: (subprocess.run(["adb", "emu", "gsm", "call", "+79161112233"]), time.sleep(3.5),
                                                         tap("Ответить", wait=3.5), subprocess.run(["adb", "emu", "gsm", "cancel", "+79161112233"]), time.sleep(4)))
    start(); record(t("27-cold-start"), 5, lambda: None)

walk("light")
walk("dark")
note("done")
