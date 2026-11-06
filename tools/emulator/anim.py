#!/usr/bin/env python3
"""Видео переходов с касаниями по известным координатам (без задержки uiautomator)."""
import os, subprocess, time
PKG = "com.khizrim.dialer"; OUT = os.environ.get("OUT", "shots")

def sh(*a): subprocess.run(["adb", *a], capture_output=True)
def tap(x, y): sh("shell", "input", "tap", str(x), str(y))
def press(x, y): sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "900")
def back(): sh("shell", "input", "keyevent", "KEYCODE_BACK")
def start():
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(7)

def rec(name, action, after=2.5):
    remote = f"/sdcard/a-{name}.mp4"
    p = subprocess.Popen(["adb", "shell", "screenrecord", "--time-limit", "4", "--bit-rate", "12000000", remote])
    time.sleep(1.0); action(); time.sleep(after); p.wait(); time.sleep(0.5)
    sh("pull", remote, os.path.join(OUT, f"anim-{name}.mp4")); print("anim", name)

start()
rec("keypad-open", lambda: tap(933, 2211))
rec("keypad-type", lambda: [tap(540, 1456), time.sleep(.15), tap(198, 1640), time.sleep(.15), tap(882, 1824)])
rec("keypad-close", lambda: tap(201, 2211))
rec("settings-open", lambda: tap(754, 2211))
rec("settings-back", back)
rec("contacts-open", lambda: tap(388, 2211))
rec("contacts-back", back)
rec("journal-open", lambda: tap(145, 2211))
rec("journal-back", back)
rec("menu-open", lambda: press(300, 1450), after=2)
rec("menu-dismiss", back)
rec("pull-search", lambda: sh("shell", "input", "swipe", "540", "1500", "540", "700", "350"))
rec("search-back", back)
