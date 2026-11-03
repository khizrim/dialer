#!/usr/bin/env python3
"""Покадровый разбор: для каждого видео — лента кадров с движением и метрики (вспышки, скачки)."""
import glob, json, os, subprocess, sys
from PIL import Image, ImageDraw
import numpy as np

SRC = sys.argv[1]; FPS = 30
report = {}
for v in sorted(glob.glob(os.path.join(SRC, "*.mp4"))):
    name = os.path.basename(v)[:-4]; d = os.path.join(SRC, "_f", name); os.makedirs(d, exist_ok=True)
    subprocess.run(["ffmpeg", "-loglevel", "error", "-i", v, "-vf", f"fps={FPS},scale=270:-2", os.path.join(d, "%04d.png")])
    files = sorted(glob.glob(os.path.join(d, "*.png")))
    if not files: continue
    arr = [np.asarray(Image.open(f).convert("L"), dtype=np.float32) for f in files]
    lum = [float(a.mean()) for a in arr]
    diff = [0.0] + [float(np.abs(arr[i] - arr[i - 1]).mean()) for i in range(1, len(arr))]
    moving = [i for i, x in enumerate(diff) if x > 0.4]
    if moving:
        a, b = max(0, moving[0] - 2), min(len(files), moving[-1] + 3)
    else:
        a, b = 0, min(len(files), 12)
    b = min(b, a + 60)
    # «вспышка»: яркость в середине движения уходит от обоих концов к фону
    base = (lum[a] + lum[b - 1]) / 2
    dips = [i for i in range(a + 1, b - 1) if abs(lum[i] - base) > 6 and abs(lum[i] - lum[a]) > 6 and abs(lum[i] - lum[b - 1]) > 6]
    # «скачок»: кадр, где изменение резко больше соседей (рывок, а не плавное движение)
    jumps = [i for i in range(a + 1, b - 1) if diff[i] > 3 and diff[i] > 2.5 * max(diff[i - 1], diff[i + 1], 0.5)]
    report[name] = dict(frames=b - a, start=a, motion_ms=round((moving[-1] - moving[0] + 1) * 1000 / FPS) if moving else 0,
                        lum=[round(x, 1) for x in lum[a:b]], diff=[round(x, 2) for x in diff[a:b]], dips=dips, jumps=jumps)
    sel = files[a:b]; cols = 10; rows = (len(sel) + cols - 1) // cols
    w, h = Image.open(sel[0]).size
    sheet = Image.new("RGB", (cols * w, rows * (h + 18)), (40, 40, 40)); dr = ImageDraw.Draw(sheet)
    for k, f in enumerate(sel):
        x, y = (k % cols) * w, (k // cols) * (h + 18); i = a + k
        sheet.paste(Image.open(f).convert("RGB"), (x, y + 18))
        mark = " FLASH" if i in dips else (" JUMP" if i in jumps else "")
        dr.text((x + 4, y + 3), f"{(i - a) * 1000 // FPS} ms  d={diff[i]:.1f}{mark}", fill=(255, 90, 90) if mark else (230, 230, 230))
    sheet.save(os.path.join(SRC, f"{name}-strip.png"))
json.dump(report, open(os.path.join(SRC, "metrics.json"), "w"), ensure_ascii=False, indent=1)
for k, r in report.items(): print(f"{k:45s} motion={r['motion_ms']:5d}ms dips={r['dips']} jumps={r['jumps']}")
