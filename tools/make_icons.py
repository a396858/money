#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成记账本 App 的 PWA 图标（纯标准库，不依赖 Pillow）。
图形：蓝紫渐变圆角方块 + 白色人民币符号 ¥

用法： python make_icons.py <输出目录>
"""
import math
import os
import struct
import sys
import zlib

# ---------- PNG 编码 ----------

def _chunk(tag, data):
    return (struct.pack('>I', len(data)) + tag + data
            + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff))


def write_png(path, w, h, rows):
    """rows: list[bytes]，每行 RGBA 原始字节"""
    raw = b''.join(b'\x00' + r for r in rows)
    out = (b'\x89PNG\r\n\x1a\n'
           + _chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
           + _chunk(b'IDAT', zlib.compress(raw, 6))
           + _chunk(b'IEND', b''))
    with open(path, 'wb') as f:
        f.write(out)


# ---------- 几何 ----------

def rrect_sdf(px, py, c, hh, r):
    """圆角矩形有符号距离（正方形，中心在 c,c），<0 表示在内部"""
    qx = abs(px - c) - (hh - r)
    qy = abs(py - c) - (hh - r)
    ax = qx if qx > 0.0 else 0.0
    ay = qy if qy > 0.0 else 0.0
    outside = math.sqrt(ax * ax + ay * ay)
    inside = qx if qx > qy else qy
    if inside > 0.0:
        inside = 0.0
    return outside + inside - r


BASE_STROKES = [
    # (x1, y1, x2, y2, half_width) —— 512 基准坐标，整体居中
    (256.0, 152.0, 256.0, 360.0, 23.0),   # 竖线
    (256.0, 250.0, 170.0, 155.0, 23.0),   # 左斜
    (256.0, 250.0, 342.0, 155.0, 23.0),   # 右斜
    (162.0, 286.0, 350.0, 286.0, 18.0),   # 横线一
    (162.0, 340.0, 350.0, 340.0, 18.0),   # 横线二
]

SUP = ((0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75))
TRANSPARENT = b'\x00\x00\x00\x00'

# 渐变：顶 #5B8CFF → 底 #2962E2
TOP_RGB = (91.0, 140.0, 255.0)
BOT_RGB = (41.0, 98.0, 226.0)


def scale_strokes(size, glyph_scale):
    """把 512 基准几何变换到 size 画布"""
    k = (size / 512.0) * glyph_scale
    off = size / 2.0 - 256.0 * k
    out = []
    for (x1, y1, x2, y2, hw) in BASE_STROKES:
        out.append((x1 * k + off, y1 * k + off,
                    x2 * k + off, y2 * k + off, hw * k))
    return out


def seg_hit(px, py, strokes):
    """点是否落在任意一条粗线段内部（圆头）"""
    for (x1, y1, x2, y2, hw) in strokes:
        dx = x2 - x1
        dy = y2 - y1
        l2 = dx * dx + dy * dy
        if l2 > 0.0:
            t = ((px - x1) * dx + (py - y1) * dy) / l2
            if t < 0.0:
                t = 0.0
            elif t > 1.0:
                t = 1.0
            ex = px - (x1 + t * dx)
            ey = py - (y1 + t * dy)
        else:
            ex = px - x1
            ey = py - y1
        if ex * ex + ey * ey < hw * hw:
            return True
    return False


def seg_near(px, py, strokes, margin):
    """点是否接近任意线条（用于抗锯齿边缘检测）"""
    for (x1, y1, x2, y2, hw) in strokes:
        dx = x2 - x1
        dy = y2 - y1
        l2 = dx * dx + dy * dy
        if l2 > 0.0:
            t = ((px - x1) * dx + (py - y1) * dy) / l2
            if t < 0.0:
                t = 0.0
            elif t > 1.0:
                t = 1.0
            ex = px - (x1 + t * dx)
            ey = py - (y1 + t * dy)
        else:
            ex = px - x1
            ey = py - y1
        rr = hw + margin
        if ex * ex + ey * ey < rr * rr:
            return True
    return False


def render(size, maskable=False):
    """渲染一张 size×size 的图标"""
    if maskable:
        radius = 0.0
        glyph_scale = 0.62   # 缩进安全区，避免被系统圆形裁切
    else:
        radius = size * 0.225
        glyph_scale = 0.94

    c = size / 2.0
    hh = size / 2.0
    strokes = scale_strokes(size, glyph_scale)

    # 图形包围盒（用于快速剔除）
    minx = min(min(s[0], s[2]) - s[4] for s in strokes) - 1.5
    maxx = max(max(s[0], s[2]) + s[4] for s in strokes) + 1.5
    miny = min(min(s[1], s[3]) - s[4] for s in strokes) - 1.5
    maxy = max(max(s[1], s[3]) + s[4] for s in strokes) + 1.5

    inv = 1.0 / (size - 1) if size > 1 else 0.0
    t_r, t_g, t_b = TOP_RGB
    b_r, b_g, b_b = BOT_RGB

    rows = []
    for y in range(size):
        row = bytearray()
        pcy = y + 0.5
        t = y * inv
        bg_r = t_r + (b_r - t_r) * t
        bg_g = t_g + (b_g - t_g) * t
        bg_b = t_b + (b_b - t_b) * t

        for x in range(size):
            pcx = x + 0.5

            # ---- 背景圆角矩形覆盖率 ----
            dc = rrect_sdf(pcx, pcy, c, hh, radius)
            if dc > 0.9:
                row += TRANSPARENT
                continue
            if dc < -0.9:
                bg_a = 1.0
            else:
                hit = 0
                for ox, oy in SUP:
                    if rrect_sdf(x + ox, y + oy, c, hh, radius) < 0.0:
                        hit += 1
                if hit == 0:
                    row += TRANSPARENT
                    continue
                bg_a = hit * 0.25

            # ---- ¥ 符号覆盖率 ----
            g_a = 0.0
            if minx <= pcx <= maxx and miny <= pcy <= maxy:
                if seg_hit(pcx, pcy, strokes):
                    if (seg_hit(pcx - 0.55, pcy, strokes)
                            and seg_hit(pcx + 0.55, pcy, strokes)
                            and seg_hit(pcx, pcy - 0.55, strokes)
                            and seg_hit(pcx, pcy + 0.55, strokes)):
                        g_a = 1.0
                    else:
                        hit = 0
                        for ox, oy in SUP:
                            if seg_hit(x + ox, y + oy, strokes):
                                hit += 1
                        g_a = hit * 0.25
                elif seg_near(pcx, pcy, strokes, 1.0):
                    hit = 0
                    for ox, oy in SUP:
                        if seg_hit(x + ox, y + oy, strokes):
                            hit += 1
                    g_a = hit * 0.25

            # ---- 合成 ----
            r = bg_r + (255.0 - bg_r) * g_a
            g = bg_g + (255.0 - bg_g) * g_a
            b = bg_b + (255.0 - bg_b) * g_a

            row += bytes((int(r + 0.5), int(g + 0.5), int(b + 0.5),
                          int(bg_a * 255.0 + 0.5)))
        rows.append(bytes(row))
    return rows


def main():
    outdir = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(outdir, exist_ok=True)

    jobs = [
        ('icon-192.png', 192, False),
        ('icon-512.png', 512, False),
        ('icon-512-maskable.png', 512, True),
        ('favicon.png', 64, False),
    ]
    for name, size, maskable in jobs:
        path = os.path.join(outdir, name)
        write_png(path, size, size, render(size, maskable))
        print('  ok  %-24s %dx%d  %d bytes' % (name, size, size, os.path.getsize(path)))


if __name__ == '__main__':
    main()
