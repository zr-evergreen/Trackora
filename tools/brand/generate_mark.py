#!/usr/bin/env python3
"""Generates Trackora's launcher icon, themed icon, splash mark and
notification icon from one description of the mark.

The mark: three upright marks — a job's three stages — and a saffron thread
rising through them, over the first, under the second, over the third. A
tally from the paper notebook the app replaces, woven like the cloth of the
workshops it is made for.

All geometry lives here, in the 108-unit adaptive-icon canvas, and every
asset is written from it, so they cannot drift apart. Run from anywhere:

    python3 tools/brand/generate_mark.py            # drawables and legacy PNGs
    python3 tools/brand/generate_mark.py --no-png   # drawables only

The legacy PNGs (Android 7.x) are rendered with headless Chrome.
"""
import argparse
import math
import os
import re
import subprocess
import tempfile

def f(v):
    s = f"{v:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

# ---- Geometry --------------------------------------------------------------

SCALE = 0.86            # fits the whole mark, caps included, in the 33-unit safe radius
C = 54.0

def sc(x, y):
    return (C + (x - C) * SCALE, C + (y - C) * SCALE)

BAR_X = [41.0, 54.0, 67.0]
BAR_TOP, BAR_BOTTOM = 36.0, 72.0
BAR_W = 9.5
THREAD = [(28.0, 70.0), (45.0, 65.0), (63.0, 55.0), (80.0, 35.0)]   # cubic Bézier
THREAD_W = 9.0
EDGE = 2.4              # the ink edge that separates a crossing from what lies beneath
UNDER = {1}             # the thread passes under the second mark

bar_w = BAR_W * SCALE
thread_w = THREAD_W * SCALE
edge = EDGE * SCALE
bars = []
for x in BAR_X:
    (cx, top) = sc(x, BAR_TOP)
    (_, bottom) = sc(x, BAR_BOTTOM)
    bars.append((cx, top, bottom))
thread = [sc(*p) for p in THREAD]

def bar_path(cx, top, bottom, r):
    """A vertical capsule, clockwise (in y-down screen space)."""
    return (f"M{f(cx - r)},{f(top)} A{f(r)},{f(r)} 0 0 1 {f(cx + r)},{f(top)} "
            f"L{f(cx + r)},{f(bottom)} A{f(r)},{f(r)} 0 0 1 {f(cx - r)},{f(bottom)} Z")

def bar_path_ccw(cx, top, bottom, r):
    """The same capsule, counter-clockwise, to cut a hole under the non-zero rule."""
    return (f"M{f(cx + r)},{f(top)} A{f(r)},{f(r)} 0 0 0 {f(cx - r)},{f(top)} "
            f"L{f(cx - r)},{f(bottom)} A{f(r)},{f(r)} 0 0 0 {f(cx + r)},{f(bottom)} Z")

def thread_path():
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = thread
    return f"M{f(x0)},{f(y0)} C{f(x1)},{f(y1)} {f(x2)},{f(y2)} {f(x3)},{f(y3)}"

def bez(t):
    (x0, y0), (x1, y1), (x2, y2), (x3, y3) = thread
    u = 1 - t
    x = u**3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * x3
    y = u**3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * y3
    dx = 3 * u * u * (x1 - x0) + 6 * u * t * (x2 - x1) + 3 * t * t * (x3 - x2)
    dy = 3 * u * u * (y1 - y0) + 6 * u * t * (y2 - y1) + 3 * t * t * (y3 - y2)
    n = math.hypot(dx, dy)
    return x, y, dx / n, dy / n

def band_polygon(half, steps=48):
    """The thread widened to `half` on each side, as a closed polygon.
    Returned counter-clockwise so that, inside a clockwise frame, it cuts a hole."""
    left, right = [], []
    for i in range(steps + 1):
        x, y, tx, ty = bez(i / steps)
        nx, ny = -ty, tx
        left.append((x + nx * half, y + ny * half))
        right.append((x - nx * half, y - ny * half))
    # Round each end with a half-circle of points: at the far end from the
    # left edge round the tip to the right edge, at the near end from the
    # right edge round the back to the left edge.
    def cap(t, start_angle):
        x, y, tx, ty = bez(t)
        base = math.atan2(ty, tx)
        return [(x + math.cos(base + start_angle - math.pi * k / 12) * half,
                 y + math.sin(base + start_angle - math.pi * k / 12) * half) for k in range(1, 12)]
    poly = left + cap(1, math.pi / 2) + right[::-1] + cap(0, -math.pi / 2)
    area = sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1])) / 2
    # In y-down space a positive shoelace area is clockwise on screen.
    if area > 0:
        poly = poly[::-1]
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in poly) + " Z"

FRAME = "M0,0 L108,0 L108,108 L0,108 Z"     # clockwise on screen

# ---- Palette ---------------------------------------------------------------

INK_LIGHT = "#3A5BE0"   # the background gradient, top-left …
INK_DEEP = "#1A2A8C"    # … to bottom-right; InkIndigo (#2B4ACB) sits between
EDGE_INK = "#1C2B8A"
PAPER = "#FAF8F5"
SAFFRON = "#FFB866"     # SaffronDark in the app's palette

# ---- SVG (for the legacy PNGs) ---------------------------------------------

def svg_foreground(paper=PAPER, saffron=SAFFRON, edge_color=EDGE_INK, uid="f"):
    """Full colour. The ink edge appears only where one strand crosses another,
    like the gap in a real weave: around the thread where it lies on a mark,
    and around the middle mark where it lies on the thread."""
    r = bar_w / 2
    over = [b for i, b in enumerate(bars) if i not in UNDER]
    under = [b for i, b in enumerate(bars) if i in UNDER]
    s = f'<defs><clipPath id="{uid}-on-bars"><path d="{" ".join(bar_path(*b, r) for b in over)}"/></clipPath>'
    s += f'<clipPath id="{uid}-on-thread"><path d="{band_polygon(thread_w / 2)}"/></clipPath></defs>'
    s += "".join(f'<path d="{bar_path(*b, r)}" fill="{paper}"/>' for b in over)
    s += (f'<g clip-path="url(#{uid}-on-bars)"><path d="{thread_path()}" fill="none" stroke="{edge_color}" '
          f'stroke-width="{f(thread_w + 2 * edge)}" stroke-linecap="round"/></g>')
    s += f'<path d="{thread_path()}" fill="none" stroke="{saffron}" stroke-width="{f(thread_w)}" stroke-linecap="round"/>'
    for b in under:
        s += f'<g clip-path="url(#{uid}-on-thread)"><path d="{bar_path(*b, r + edge)}" fill="{edge_color}"/></g>'
        s += f'<path d="{bar_path(*b, r)}" fill="{paper}"/>'
    return s

def svg_background():
    return ('<defs><linearGradient id="bg" gradientUnits="userSpaceOnUse" x1="18" y1="14" x2="90" y2="98">'
            f'<stop offset="0" stop-color="{INK_LIGHT}"/><stop offset="1" stop-color="{INK_DEEP}"/></linearGradient></defs>'
            '<rect width="108" height="108" fill="url(#bg)"/>')

# ---- Android drawables ---------------------------------------------------

r = bar_w / 2
over = [b for i, b in enumerate(bars) if i not in UNDER]
under = [b for i, b in enumerate(bars) if i in UNDER]
OVER_BARS = " ".join(bar_path(*b, r) for b in over)
UNDER_BAR = " ".join(bar_path(*b, r) for b in under)
UNDER_BAR_EDGE = " ".join(bar_path(*b, r + edge) for b in under)
THREAD_D = thread_path()
THREAD_BAND = band_polygon(thread_w / 2)
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'

def normalise(xml):
    """Lays the XML out as Android Studio does: one attribute per line,
    indented four spaces past its element."""
    out, tag_indent = [], 0
    for line in xml.split("\n"):
        s = line.lstrip()
        if s.startswith("<") and not s.startswith(("<!--", "</", "<?")):
            tag_indent = len(line) - len(s)
            m = re.match(r'^<([\w:-]+)\s+(android:\w+="[^"]*")(.*)$', s)
            if m and m.group(3).strip() not in ("/>", ">"):
                out.append(" " * tag_indent + "<" + m.group(1))
                out.append(" " * (tag_indent + 4) + m.group(2) + m.group(3))
                continue
            out.append(line)
        elif s.startswith(("android:", "xmlns:")):
            out.append(" " * (tag_indent + 4) + s)
        else:
            out.append(line)
    return "\n".join(out)


HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/brand/generate_mark.py. Change the mark there and regenerate. -->
"""


# ---- Legacy launcher PNGs (Android 7.x, before adaptive icons) ---------------

DENSITIES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
# The same rounded square as the icons they replaced (corner ~18% of the side), and a circle.
LEGACY_SHAPES = {
    "ic_launcher": '<rect x="18" y="18" width="72" height="72" rx="13" ry="13"/>',
    "ic_launcher_round": '<circle cx="54" cy="54" r="36"/>',
}


def write_legacy_pngs(res_dir, chrome):
    """Renders the full icon at each density with headless Chrome, whose SVG
    renderer draws the same paths the vector drawables use."""
    with tempfile.TemporaryDirectory() as tmp:
        for density, px in DENSITIES:
            for name, shape in LEGACY_SHAPES.items():
                page = os.path.join(tmp, f"{name}-{density}.html")
                with open(page, "w") as fh:
                    fh.write(
                        "<!doctype html><html><head><style>html,body{margin:0;background:transparent}</style></head><body>"
                        f'<svg width="{px}" height="{px}" viewBox="18 18 72 72" style="display:block">'
                        f'<defs><clipPath id="m">{shape}</clipPath></defs>'
                        f'<g clip-path="url(#m)">{svg_background()}{svg_foreground()}</g></svg></body></html>'
                    )
                out = os.path.join(res_dir, f"mipmap-{density}", f"{name}.png")
                subprocess.run(
                    [chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                     "--force-device-scale-factor=1", f"--user-data-dir={os.path.join(tmp, 'profile')}",
                     "--default-background-color=00000000", f"--screenshot={out}",
                     f"--window-size={px},{px}", f"file://{page}"],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True,
                )


# ---- Writing ------------------------------------------------------------------

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
parser.add_argument("--res", default=os.path.join(ROOT, "app", "src", "main", "res"))
parser.add_argument("--chrome", default="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    help="headless Chrome, for the legacy PNGs")
parser.add_argument("--no-png", action="store_true", help="write only the vector drawables")
args = parser.parse_args()
DRAWABLE = os.path.join(args.res, "drawable")

def write(name, text):
    with open(os.path.join(DRAWABLE, name), "w") as fh:
        fh.write(normalise(text))
    print("drawable/" + name)

def woven(mark_color, thread_color, edge_color, names=False, hidden=False):
    """The mark's layers in drawing order. With names=True each part is named
    so an animated vector can address it."""
    def g(name):
        """Names a part for the animation and, when hidden, sets it to where
        the animation starts from: marks flat on their baseline, thread undrawn."""
        if not names:
            return ''
        start = ''
        if hidden:
            start = '\n        android:scaleY="0"' if name.startswith('mark') else '\n        android:trimPathEnd="0"'
        return f' android:name="{name}"{start}'
    return f"""
    <!-- The first and third marks: the thread passes over them. -->
""" + "".join(f"""    <group{g('mark_' + str(i + 1))}
        android:pivotX="{f(b[0])}"
        android:pivotY="{f(b[2] + r)}">
        <path
            android:fillColor="{mark_color}"
            android:pathData="{bar_path(*b, r)}" />
    </group>
""" for i, b in enumerate(bars) if i not in UNDER) + f"""    <!-- The ink edge around the thread, only where it lies on a mark. -->
    <group>
        <clip-path android:pathData="{OVER_BARS}" />
        <path{g('thread_edge')}
            android:pathData="{THREAD_D}"
            android:strokeColor="{edge_color}"
            android:strokeLineCap="round"
            android:strokeWidth="{f(thread_w + 2 * edge)}" />
    </group>
    <!-- The thread, rising. -->
    <path{g('thread')}
        android:pathData="{THREAD_D}"
        android:strokeColor="{thread_color}"
        android:strokeLineCap="round"
        android:strokeWidth="{f(thread_w)}" />
    <!-- The middle mark passes over the thread, with an edge only where it does. -->
    <group{g('mark_2')}
        android:pivotX="{f(bars[1][0])}"
        android:pivotY="{f(bars[1][2] + r)}">
        <group>
            <clip-path android:pathData="{THREAD_BAND}" />
            <path
                android:fillColor="{edge_color}"
                android:pathData="{UNDER_BAR_EDGE}" />
        </group>
        <path
            android:fillColor="{mark_color}"
            android:pathData="{UNDER_BAR}" />
    </group>
"""

# ---- Launcher icon ----------------------------------------------------------

write("ic_launcher_background.xml", HEADER + f"""<!--
    Ink indigo, lit from the top left: the app's primary (#2B4ACB) sits in the
    middle of the gradient, so the icon and the app read as one thing.
-->
<vector {NS}
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="90"
                android:endY="98"
                android:startX="18"
                android:startY="14"
                android:type="linear">
                <item android:color="#FF{INK_LIGHT[1:]}" android:offset="0" />
                <item android:color="#FF{INK_DEEP[1:]}" android:offset="1" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
""")

write("ic_launcher_foreground.xml", HEADER + f"""<!--
    The Trackora mark: three upright marks — a job's three stages — and a
    saffron thread rising through them, over the first, under the second and
    over the third. A tally from the paper notebook the app replaces, woven
    like the cloth of the workshops it is made for.

    Everything sits inside the adaptive icon's 66dp safe zone, so no launcher
    mask cuts into it.
-->
<vector {NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">{woven('#FF' + PAPER[1:], '#FF' + SAFFRON[1:], '#FF' + EDGE_INK[1:])}</vector>
""")

def frame_minus(hole):
    return f"{FRAME} {hole}"

b2 = bars[1]
MONO = f"""
    <!-- Marks one and three, with a gap where the thread crosses them. -->
    <group>
        <clip-path android:pathData="{frame_minus(band_polygon(thread_w / 2 + edge))}" />
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="{OVER_BARS}" />
    </group>
    <!-- The thread, with a gap where the middle mark crosses it. -->
    <group>
        <clip-path android:pathData="{frame_minus(bar_path_ccw(b2[0], b2[1] - edge, b2[2] + edge, r + edge))}" />
        <path
            android:pathData="{THREAD_D}"
            android:strokeColor="#FFFFFFFF"
            android:strokeLineCap="round"
            android:strokeWidth="{f(thread_w)}" />
    </group>
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{UNDER_BAR}" />
"""

write("ic_launcher_monochrome.xml", HEADER + f"""<!--
    The mark in one colour, for themed icons (Android 13+), which tint it
    themselves. With no second colour to tell the strands apart, the weave is
    drawn as real gaps: each clip path is the whole canvas with the crossing
    strand's outline wound the other way, which the non-zero rule turns into a
    hole.
-->
<vector {NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">{MONO}</vector>
""")

# ---- Notification icon ------------------------------------------------------

# Crop the canvas to the mark with a little room, so it fills the status bar slot.
x0 = min(thread[0][0] - thread_w / 2, bars[0][0] - r) - 2
x1 = max(thread[3][0] + thread_w / 2, bars[2][0] + r) + 2
y0 = min(thread[3][1] - thread_w / 2, bars[0][1] - r) - 2
y1 = max(thread[0][1] + thread_w / 2, bars[0][2] + r) + 2
side = max(x1 - x0, y1 - y0)
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
write("ic_notification.xml", HEADER + f"""<!--
    Status-bar icon for the daily reminder: the Trackora mark as a single-colour
    silhouette, the same shape as the themed launcher icon, cropped so it fills
    the slot. The system tints status-bar icons itself, so only the shape
    matters.
-->
<vector {NS}
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="{f(side)}"
    android:viewportHeight="{f(side)}">
    <group
        android:translateX="{f(side / 2 - cx)}"
        android:translateY="{f(side / 2 - cy)}">{MONO.replace(chr(10), chr(10) + '    ')}</group>
</vector>
""")

# ---- Splash -----------------------------------------------------------------

SPLASH_DOC = """<!--
    The mark on the splash screen: the launcher icon's mark without its tile,
    on the app's own background, so the splash hands over to the first screen
    without a change of colour. Its colours follow the theme through
    @color/splash_*.
-->"""
write("splash_mark.xml", HEADER + SPLASH_DOC + f"""
<vector {NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">{woven('@color/splash_mark', '@color/splash_thread', '@color/splash_background')}</vector>
""")

# The animated version's starting frame (Android 12+): nothing drawn yet.
write("splash_mark_start.xml", HEADER + f"""<!--
    The splash mark before its animation runs: every part named, the marks
    flat on their baseline and the thread not yet drawn. splash_mark_animated
    raises the marks one after another and draws the thread through them.
-->
<vector {NS}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">{woven('@color/splash_mark', '@color/splash_thread', '@color/splash_background', names=True, hidden=True)}</vector>
""")

if not args.no_png:
    write_legacy_pngs(args.res, args.chrome)
    print("mipmap-*/ic_launcher.png, ic_launcher_round.png")
