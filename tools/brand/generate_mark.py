#!/usr/bin/env python3
"""Generates Trackora's launcher icon, themed icon, notification icon and
splash from one description of the mark.

The mark, the Trackora seal: a disc, one whole job, cut by the T of Trackora
into three parts, the three stages a job goes through. Where the T's bar
crosses the stem it steps up a level: the work moving up a stage.

All geometry lives here and every asset is written from it, so they cannot
drift apart. Run from anywhere, with nothing but Python 3:

    python3 tools/brand/generate_mark.py            # drawables and legacy PNGs
    python3 tools/brand/generate_mark.py --no-png   # drawables only

The legacy PNGs (Android 7.x) are rasterised here too, from the same outlines
the vector drawables are drawn with.
"""
import argparse
import math
import os
import re
import struct
import zlib


def f(v):
    s = f"{v:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

# ---- Geometry --------------------------------------------------------------
#
# In units of the seal's radius, centred on the origin, y down as on screen.
# The T is a gutter cut out of the disc:
#  - the bar's cut is a little narrower than the stem's, because a horizontal
#    gap looks heavier than a vertical one of the same width;
#  - both stay open at 16 px, where the stem's cut is still 1.7 px wide;
#  - the bar steps up across the stem by exactly the stem's cut, so one 45°
#    line joins the two lower parts' top corners, and the top part's edge runs
#    parallel to it one cut away. Every edge is level, upright or at 45°, and
#    those stay crisp on the pixel grid at sizes where a tilted bar blurs.

BAR_CUT = 0.21                  # the bar's gutter, top to bottom
STEM_CUT = 0.24                 # the stem's gutter, side to side
BAR = -0.30                     # the bar's centre line where it crosses the stem
LEFT = BAR + STEM_CUT / 2       # the bar's centre line left of the stem …
RIGHT = BAR - STEM_CUT / 2      # … and right of it, one stem cut higher
# Where the top part's edge turns up through the step: set back from the stem
# so that the gutter keeps its width on the diagonal too.
SET_BACK = BAR_CUT * (math.sqrt(2) - 1)
STEP_FROM, STEP_TO = -STEM_CUT / 2 - SET_BACK, STEM_CUT / 2 - SET_BACK

FAR = 1.5                       # anywhere outside the disc
# Each part is the disc clipped to one of these regions, all clockwise on screen.
REGIONS = {
    "top": [(-FAR, -FAR), (FAR, -FAR), (FAR, RIGHT - BAR_CUT / 2), (STEP_TO, RIGHT - BAR_CUT / 2),
            (STEP_FROM, LEFT - BAR_CUT / 2), (-FAR, LEFT - BAR_CUT / 2)],
    "lower_left": [(-FAR, LEFT + BAR_CUT / 2), (-STEM_CUT / 2, LEFT + BAR_CUT / 2),
                   (-STEM_CUT / 2, FAR), (-FAR, FAR)],
    "lower_right": [(STEM_CUT / 2, RIGHT + BAR_CUT / 2), (FAR, RIGHT + BAR_CUT / 2),
                    (FAR, FAR), (STEM_CUT / 2, FAR)],
}

def _crossings(p, q):
    """Where the segment p→q crosses the unit circle, as (t, entering) pairs."""
    dx, dy = q[0] - p[0], q[1] - p[1]
    a = dx * dx + dy * dy
    b = 2 * (p[0] * dx + p[1] * dy)
    c = p[0] ** 2 + p[1] ** 2 - 1
    disc = b * b - 4 * a * c
    if disc <= 0:
        return []
    root = math.sqrt(disc)
    return [(t, entering) for t, entering in (((-b - root) / (2 * a), True), ((-b + root) / (2 * a), False))
            if 0 < t < 1]

def outline(region):
    """The region's share of the unit disc, as (point, along_circle) pairs:
    along_circle means the point is reached round the circle from the one
    before it, where the region's edge has left the disc."""
    pts = []
    for i, p in enumerate(region):
        q = region[(i + 1) % len(region)]
        if p[0] ** 2 + p[1] ** 2 < 1:
            pts.append((p, False))
        for t, entering in _crossings(p, q):
            pts.append(((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])), entering))
    return pts

def path_data(part, cx, cy, r):
    """One part of the seal, of radius r centred on (cx, cy), as path data."""
    pts = outline(REGIONS[part])
    at = lambda p: f"{f(cx + p[0] * r)},{f(cy + p[1] * r)}"

    def to(prev, p, along_circle):
        if not along_circle:
            return "L" + at(p)
        sweep = (math.atan2(p[1], p[0]) - math.atan2(prev[1], prev[0])) % (2 * math.pi)
        return f"A{f(r)},{f(r)} 0 {1 if sweep > math.pi else 0} 1 {at(p)}"

    d = "M" + at(pts[0][0])
    for (prev, _), (p, along_circle) in zip(pts, pts[1:]):
        d += " " + to(prev, p, along_circle)
    if pts[0][1]:
        d += " " + to(pts[-1][0], pts[0][0], True)
    return d + " Z"

def part_at(x, y):
    """The part of the unit seal that covers (x, y), or None."""
    if x * x + y * y > 1:
        return None
    for part, region in REGIONS.items():
        inside = False
        for (x1, y1), (x2, y2) in zip(region, region[1:] + region[:1]):
            if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
                inside = not inside
        if inside:
            return part
    return None

# ---- Sizes, palette and motion ---------------------------------------------

C = 54.0                # the centre of the 108-unit adaptive-icon canvas
ICON_R = 21.0           # 58% of the 72 units a launcher shows; well inside the 33-unit safe zone
# The platform draws the splash icon at 288dp for the 108-unit canvas, so the
# seal is 128dp across: two-thirds of the 192dp circle kept clear for it.
SPLASH_R = 24.0
NOTIFICATION_R = 10.0   # in the 24dp notification icon: fills the 20dp live area

INDIGO = "#2B4ACB"      # InkIndigo, the app's primary
PAPER = "#FAF8F5"       # the app's light background

# The splash entrance on Android 12+. Each part fades in as it slides into
# place from an offset, in radius units: the lower parts rise, then the top
# part steps up along the 45° line. It travels less than one cut, so it never
# touches the others.
ENTRANCE = {            # part: (start ms, duration ms, from x, from y)
    "lower_left": (0, 300, 0.0, 0.32),
    "lower_right": (70, 300, 0.0, 0.32),
    "top": (240, 440, -0.16, 0.16),
}
ENTRANCE_MS = max(start + duration for start, duration, _, _ in ENTRANCE.values())
PART_NOTES = {
    "top": "The top part, its edge stepping up across the stem.",
    "lower_left": "The lower parts, either side of the stem.",
    "lower_right": None,
}

# ---- Android resources -------------------------------------------------------

NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/brand/generate_mark.py. Change the mark there and regenerate. -->
"""

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

def vector(size_dp, viewport, body):
    return f"""<vector {NS}
    android:width="{size_dp}dp"
    android:height="{size_dp}dp"
    android:viewportWidth="{f(viewport)}"
    android:viewportHeight="{f(viewport)}">{body}</vector>
"""

def seal(fill, cx, cy, r):
    """The three parts as plain paths."""
    out = "\n"
    for part in REGIONS:
        if PART_NOTES[part]:
            out += f"    <!-- {PART_NOTES[part]} -->\n"
        out += f"""    <path
        android:fillColor="{fill}"
        android:pathData="{path_data(part, cx, cy, r)}" />
"""
    return out

def seal_before_entrance(fill, cx, cy, r):
    """The three parts as splash_mark_animated finds them: each in a named
    group set off from its place, its path named and faded out."""
    out = "\n"
    for part, (_, _, dx, dy) in ENTRANCE.items():
        offsets = "".join(f'\n        android:{prop}="{f(v * r)}"' for prop, v in (("translateX", dx), ("translateY", dy)) if v)
        out += f"""    <group
        android:name="{part}"{offsets}>
        <path
            android:name="{part}_fill"
            android:fillAlpha="0"
            android:fillColor="{fill}"
            android:pathData="{path_data(part, cx, cy, r)}" />
    </group>
"""
    return out

def animator(prop, start, duration, value_from, indent):
    pad = " " * indent
    return f"""{pad}<objectAnimator
{pad}    android:duration="{duration}"
{pad}    android:interpolator="@android:interpolator/fast_out_slow_in"
{pad}    android:propertyName="{prop}"
{pad}    android:startOffset="{start}"
{pad}    android:valueFrom="{f(value_from)}"
{pad}    android:valueTo="{'1' if prop == 'fillAlpha' else '0'}"
{pad}    android:valueType="floatType" />
"""

def entrance(r):
    out = ""
    for part, (start, duration, dx, dy) in ENTRANCE.items():
        moves = [("translateX", dx * r), ("translateY", dy * r)]
        moves = [animator(prop, start, duration, v, 16) for prop, v in moves if v]
        out += f"""    <target android:name="{part}">
        <aapt:attr name="android:animation">
            <set>
{"".join(moves)}            </set>
        </aapt:attr>
    </target>
    <target android:name="{part}_fill">
        <aapt:attr name="android:animation">
{animator("fillAlpha", start, duration, 0, 12)}        </aapt:attr>
    </target>
"""
    return out

def write_resources(res):
    def write(name, text):
        with open(os.path.join(res, name), "w") as fh:
            fh.write(normalise(HEADER + text))
        print(name)

    argb = lambda colour: "#FF" + colour[1:]

    write("drawable/ic_launcher_background.xml", f"""<!--
    Flat ink indigo, the app's primary, so the icon and the app read as one
    thing. No gradient: the seal is a silhouette and needs nothing behind it.
-->
""" + vector(108, 108, f"""
    <path
        android:fillColor="{argb(INDIGO)}"
        android:pathData="M0,0h108v108h-108z" />
"""))

    write("drawable/ic_launcher_foreground.xml", f"""<!--
    The Trackora seal: a disc, one whole job, cut by the T of Trackora into
    three parts, the three stages a job goes through. Where the T's bar
    crosses the stem it steps up a level: the work moving up a stage.

    The seal is {f(2 * ICON_R)} units across, well inside the adaptive icon's 66-unit
    safe zone, so no launcher mask cuts into it.
-->
""" + vector(108, 108, seal(argb(PAPER), C, C, ICON_R)))

    write("drawable/ic_launcher_monochrome.xml", """<!--
    The seal in one colour, for themed icons (Android 13+), which tint it
    themselves. Its cuts are real gaps, so it needs no second colour to read.
-->
""" + vector(108, 108, seal("#FFFFFFFF", C, C, ICON_R)))

    write("drawable/ic_notification.xml", """<!--
    The status-bar icon for the daily reminder. Android draws small icons from
    their alpha channel alone, in a colour of its own choosing, so this is the
    seal as a white silhouette with its cuts as real gaps. The full-colour
    launcher icon would show as a plain white square.

    The seal fills the 20dp live area inside the icon's 2dp padding, and its
    cuts stay at least 2dp wide.
-->
""" + vector(24, 24, seal("#FFFFFFFF", 12, 12, NOTIFICATION_R)))

    write("drawable/splash_mark.xml", """<!--
    The seal on the splash screen, on the app's own background, so the splash
    hands over to the first screen without a change of colour. Its colour
    follows the theme through @color/splash_mark. Android 7 to 11 show it
    still; Android 12 and later play splash_mark_animated.
-->
""" + vector(108, 108, seal("@color/splash_mark", C, C, SPLASH_R)))

    write("drawable/splash_mark_start.xml", """<!--
    The splash seal before its entrance: each part named, faded out and set
    off from its place. splash_mark_animated brings them in.
-->
""" + vector(108, 108, seal_before_entrance("@color/splash_mark", C, C, SPLASH_R)))

    write("drawable/splash_mark_animated.xml", f"""<!--
    The splash seal's entrance on Android 12 and later. Each part fades in as
    it slides into place: the lower parts rise, then the top part steps up
    along the 45° line, travelling less than one cut, so it never touches the
    others. {ENTRANCE_MS}ms in all, and cut short if the app is ready sooner: the
    splash never waits for it.
-->
<animated-vector {NS}
    xmlns:aapt="http://schemas.android.com/aapt"
    android:drawable="@drawable/splash_mark_start">
{entrance(SPLASH_R)}</animated-vector>
""")

    write("values/splash.xml", f"""<resources>
    <!-- How long splash_mark_animated runs, for the Android 12+ splash theme. -->
    <integer name="splash_entrance_duration">{ENTRANCE_MS}</integer>
</resources>
""")

# ---- Legacy launcher PNGs (Android 7.x, before adaptive icons) ---------------

DENSITIES = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
# The middle 72 units of the canvas, the part a launcher shows, cut to the
# same rounded square (corner ~18% of the side) and circle as before.
CORNER = 13.0

def rounded_square(x, y):
    dx, dy = abs(x - C), abs(y - C)
    ex, ey = max(dx - (36 - CORNER), 0), max(dy - (36 - CORNER), 0)
    return dx <= 36 and dy <= 36 and ex * ex + ey * ey <= CORNER * CORNER

def circle(x, y):
    return (x - C) ** 2 + (y - C) ** 2 <= 36 * 36

LEGACY_SHAPES = {"ic_launcher": rounded_square, "ic_launcher_round": circle}

def _rgb(colour):
    return tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5))

def rasterise(colour_at, x0, y0, span, px):
    """Rows of RGBA pixels covering the square (x0, y0)–(x0 + span, y0 + span),
    with colour_at giving an RGB triple, or None for clear, at any point.
    Each pixel takes 16 samples, and 256 where an edge runs through it."""
    unit = span / px

    def samples(i, j, n):
        return [colour_at(x0 + (i + (a + 0.5) / n) * unit, y0 + (j + (b + 0.5) / n) * unit)
                for b in range(n) for a in range(n)]

    rows = []
    for j in range(px):
        row = bytearray()
        for i in range(px):
            s = samples(i, j, 4)
            if len(set(s)) > 1:
                s = samples(i, j, 16)
            hits = [c for c in s if c]
            if hits:
                row += bytes(round(sum(c[k] for c in hits) / len(hits)) for k in range(3))
                row.append(round(255 * len(hits) / len(s)))
            else:
                row += b"\0\0\0\0"
        rows.append(bytes(row))
    return rows

def png(rows):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    w, h = len(rows[0]) // 4, len(rows)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(b"".join(b"\0" + r for r in rows), 9)) + chunk(b"IEND", b""))

def launcher_colour(shape):
    """The full icon, cut to a legacy shape: the paper seal on indigo."""
    ground, mark = _rgb(INDIGO), _rgb(PAPER)
    return lambda x, y: (mark if part_at((x - C) / ICON_R, (y - C) / ICON_R) else ground) if shape(x, y) else None

def write_legacy_pngs(res):
    for density, px in DENSITIES:
        for name, shape in LEGACY_SHAPES.items():
            with open(os.path.join(res, f"mipmap-{density}", f"{name}.png"), "wb") as fh:
                fh.write(png(rasterise(launcher_colour(shape), 18, 18, 72, px)))
    print("mipmap-*/ic_launcher.png, ic_launcher_round.png")

# ---- Writing ------------------------------------------------------------------

def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--res", default=os.path.join(root, "app", "src", "main", "res"))
    parser.add_argument("--no-png", action="store_true", help="write only the vector drawables")
    args = parser.parse_args()
    write_resources(args.res)
    if not args.no_png:
        write_legacy_pngs(args.res)

if __name__ == "__main__":
    main()
