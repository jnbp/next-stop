#!/usr/bin/env python3
"""Render the Google Play store graphics from the app's own sources.

Outputs (fastlane supply layout), for every locale under fastlane/metadata/android/:
  images/icon.png            512 x 512 RGBA, the launcher icon (adaptive layers, visible 72dp area)
  images/featureGraphic.png  1024 x 500 RGB (no alpha), rendered from images/featureGraphic.svg

The launcher icon is read from app/src/main/res/mipmap-anydpi/ic_launcher.xml: its background
and foreground VectorDrawables are converted to SVG by a small converter below, stacked on the
108dp adaptive canvas and cropped to the centred 72dp region a launcher shows. Play applies its
own rounded mask, so the PNG is a full-bleed square without corners or shadow.

The feature graphic text is set in Google Sans Flex from app/src/main/res/font (static
instances, family name "Google Sans Flex 18pt"); system fonts are skipped so nothing is
substituted silently.

Needs: pip install resvg-py pillow
Run from anywhere:  python3 tools/store_art.py  [--repo PATH] [--keep-svg]
"""
from __future__ import annotations

import argparse
import io
import math
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import resvg_py
from PIL import Image

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"

ICON_SIZE = 512
FEATURE_W, FEATURE_H = 1024, 500
FEATURE_BG = "#1B4FD8"  # flatten colour; the SVG paints its own opaque background anyway
FONT_FAMILY = "Google Sans Flex 18pt"


# ---------------------------------------------------------------- VectorDrawable -> SVG

def _color(value: str | None):
    """Android colour (#RGB, #ARGB, #RRGGBB, #AARRGGBB) -> (svg colour, alpha)."""
    if not value:
        return None, 1.0
    if not value.startswith("#"):
        raise ValueError(f"colour reference not supported: {value!r} (resolve it to a literal first)")
    h = value[1:]
    if len(h) in (3, 4):
        h = "".join(c * 2 for c in h)
    if len(h) == 8:
        return "#" + h[2:], int(h[:2], 16) / 255.0
    if len(h) == 6:
        return "#" + h, 1.0
    raise ValueError(f"bad colour {value!r}")


def _f(el, name, default):
    v = el.get(A + name)
    return float(v) if v is not None else default


def _fmt(x: float) -> str:
    return f"{x:.6g}"


class VectorToSvg:
    def __init__(self, prefix: str):
        self.prefix = prefix
        self.defs: list[str] = []
        self.n = 0

    def _id(self, kind):
        self.n += 1
        return f"{self.prefix}-{kind}{self.n}"

    def _gradient(self, path_el, attr):
        """aapt:attr name="android:fillColor"/"android:strokeColor" holding a <gradient>."""
        for aattr in path_el.findall(AAPT + "attr"):
            if aattr.get("name") != "android:" + attr:
                continue
            g = aattr.find("gradient")
            if g is None:
                continue
            gid = self._id("g")
            gtype = g.get(A + "type", "linear")
            stops = []
            items = g.findall("item")
            if items:
                for it in items:
                    c, a = _color(it.get(A + "color"))
                    stops.append((float(it.get(A + "offset", 0)), c, a))
            else:
                for off, key in ((0.0, "startColor"), (0.5, "centerColor"), (1.0, "endColor")):
                    if g.get(A + key):
                        c, a = _color(g.get(A + key))
                        stops.append((off, c, a))
            stop_xml = "".join(
                f'<stop offset="{_fmt(o)}" stop-color="{c}" stop-opacity="{_fmt(a)}"/>' for o, c, a in stops)
            spread = {"clamp": "pad", "repeat": "repeat", "mirror": "reflect"}[g.get(A + "tileMode", "clamp")]
            if gtype == "linear":
                self.defs.append(
                    f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" spreadMethod="{spread}" '
                    f'x1="{_fmt(_f(g, "startX", 0))}" y1="{_fmt(_f(g, "startY", 0))}" '
                    f'x2="{_fmt(_f(g, "endX", 0))}" y2="{_fmt(_f(g, "endY", 0))}">{stop_xml}</linearGradient>')
            elif gtype == "radial":
                self.defs.append(
                    f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" spreadMethod="{spread}" '
                    f'cx="{_fmt(_f(g, "centerX", 0))}" cy="{_fmt(_f(g, "centerY", 0))}" '
                    f'r="{_fmt(_f(g, "gradientRadius", 0))}">{stop_xml}</radialGradient>')
            else:
                raise ValueError("sweep gradients are not supported")
            return f"url(#{gid})", 1.0
        return None

    def _path(self, el) -> str:
        d = el.get(A + "pathData")
        attrs = [f'd="{d}"']
        fill = self._gradient(el, "fillColor") or _color(el.get(A + "fillColor"))
        if fill[0]:
            attrs.append(f'fill="{fill[0]}"')
            op = fill[1] * _f(el, "fillAlpha", 1.0)
            if op != 1.0:
                attrs.append(f'fill-opacity="{_fmt(op)}"')
            if el.get(A + "fillType", "nonZero") == "evenOdd":
                attrs.append('fill-rule="evenodd"')
        else:
            attrs.append('fill="none"')
        stroke = self._gradient(el, "strokeColor") or _color(el.get(A + "strokeColor"))
        sw = _f(el, "strokeWidth", 0.0)
        if stroke[0] and sw > 0:
            attrs.append(f'stroke="{stroke[0]}" stroke-width="{_fmt(sw)}"')
            op = stroke[1] * _f(el, "strokeAlpha", 1.0)
            if op != 1.0:
                attrs.append(f'stroke-opacity="{_fmt(op)}"')
            attrs.append(f'stroke-linecap="{el.get(A + "strokeLineCap", "butt")}"')
            attrs.append(f'stroke-linejoin="{el.get(A + "strokeLineJoin", "miter")}"')
            attrs.append(f'stroke-miterlimit="{_fmt(_f(el, "strokeMiterLimit", 4.0))}"')
        if el.get(A + "trimPathStart") or el.get(A + "trimPathEnd") or el.get(A + "trimPathOffset"):
            raise ValueError("trimPath* is not supported")
        return "<path " + " ".join(attrs) + "/>"

    def _children(self, el) -> str:
        out = []
        clip = None
        for child in el:
            tag = child.tag
            if tag == "path":
                out.append(self._path(child))
            elif tag == "group":
                out.append(self._group(child))
            elif tag == "clip-path":
                # A clip-path clips the siblings that follow it inside the same group.
                clip = self._id("c")
                self.defs.append(f'<clipPath id="{clip}"><path d="{child.get(A + "pathData")}"/></clipPath>')
                out.append(f'<g clip-path="url(#{clip})">')
            elif tag == AAPT + "attr":
                continue
            else:
                raise ValueError(f"unsupported element <{tag}>")
        if clip:
            out.append("</g>" * sum(1 for c in el if c.tag == "clip-path"))
        return "".join(out)

    def _group(self, el) -> str:
        px, py = _f(el, "pivotX", 0), _f(el, "pivotY", 0)
        tx, ty = _f(el, "translateX", 0), _f(el, "translateY", 0)
        sx, sy = _f(el, "scaleX", 1), _f(el, "scaleY", 1)
        rot = _f(el, "rotation", 0)
        # Android: M = T(translate + pivot) * R(rotation) * S(scale) * T(-pivot)
        tr = (f'translate({_fmt(tx + px)} {_fmt(ty + py)}) rotate({_fmt(rot)}) '
              f'scale({_fmt(sx)} {_fmt(sy)}) translate({_fmt(-px)} {_fmt(-py)})')
        return f'<g transform="{tr}">{self._children(el)}</g>'

    def convert(self, path: Path):
        """Returns (viewportW, viewportH, widthDp, heightDp, inner svg markup)."""
        root = ET.parse(path).getroot()
        if root.tag != "vector":
            raise ValueError(f"{path}: expected <vector>, got <{root.tag}>")
        vw, vh = _f(root, "viewportWidth", 0), _f(root, "viewportHeight", 0)
        w = float(re.sub(r"dp$", "", root.get(A + "width")))
        h = float(re.sub(r"dp$", "", root.get(A + "height")))
        body = self._children(root)
        alpha = _f(root, "alpha", 1.0)
        if alpha != 1.0:
            body = f'<g opacity="{_fmt(alpha)}">{body}</g>'
        if root.get(A + "tint"):
            raise ValueError("android:tint on the vector is not supported")
        return vw, vh, w, h, body


def _drawable(res: Path, ref: str) -> Path:
    m = re.fullmatch(r"@drawable/(\w+)", ref or "")
    if not m:
        raise ValueError(f"unsupported adaptive-icon layer reference {ref!r}")
    p = res / "drawable" / f"{m.group(1)}.xml"
    if not p.exists():
        raise FileNotFoundError(p)
    return p


def adaptive_icon_svg(res: Path, size: int = ICON_SIZE) -> str:
    """Background + foreground on the 108dp canvas, cropped to the centred 72dp visible area."""
    root = ET.parse(res / "mipmap-anydpi" / "ic_launcher.xml").getroot()
    defs, layers = [], []
    for layer in ("background", "foreground"):
        el = root.find(layer)
        conv = VectorToSvg(layer)
        vw, vh, w, h, body = conv.convert(_drawable(res, el.get(A + "drawable")))
        if (w, h) != (108, 108):
            raise ValueError(f"{layer} is {w}x{h}dp; adaptive layers are 108x108dp")
        defs += conv.defs
        # Each layer fills the 108dp canvas; its own viewport maps onto it.
        layers.append(f'<svg x="0" y="0" width="108" height="108" viewBox="0 0 {_fmt(vw)} {_fmt(vh)}" '
                      f'overflow="hidden">{body}</svg>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
            f'<defs>{"".join(defs)}</defs>{"".join(layers)}</svg>')


# ---------------------------------------------------------------- rendering

def render(svg: str, fonts: list[Path], width: int, height: int) -> Image.Image:
    png = resvg_py.svg_to_bytes(svg_string=svg, width=width, height=height, skip_system_fonts=True,
                                font_files=[str(f) for f in fonts], font_family=FONT_FAMILY,
                                sans_serif_family=FONT_FAMILY)
    img = Image.open(io.BytesIO(bytes(png)))
    img.load()
    if img.size != (width, height):
        raise RuntimeError(f"rendered {img.size}, expected {(width, height)}")
    return img


def feature_svg(src: Path) -> str:
    svg = src.read_text(encoding="utf-8")
    # The SVG names the family as the web font does ("Google Sans Flex"); the bundled static TTFs
    # call themselves "Google Sans Flex 18pt". Put that name first so the real font is used.
    new = svg.replace("font-family:'Google Sans Flex'", f"font-family:'{FONT_FAMILY}','Google Sans Flex'")
    if new == svg:
        raise RuntimeError(f"{src}: font-family rule not found; check the font setup")
    return new


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--repo", type=Path, default=Path(__file__).resolve().parent.parent)
    ap.add_argument("--keep-svg", action="store_true", help="also write the intermediate icon.svg (not committed)")
    args = ap.parse_args()
    repo = args.repo
    res = repo / "app" / "src" / "main" / "res"
    fonts = sorted((res / "font").glob("google_sans_flex_*.ttf"))
    if not fonts:
        raise SystemExit("no Google Sans Flex fonts in app/src/main/res/font")

    icon_svg = adaptive_icon_svg(res)
    icon = render(icon_svg, fonts, ICON_SIZE, ICON_SIZE).convert("RGBA")

    meta = repo / "fastlane" / "metadata" / "android"
    for locale in sorted(p for p in meta.iterdir() if (p / "images").is_dir()):
        images = locale / "images"
        out = images / "icon.png"
        icon.save(out, optimize=True)
        if args.keep_svg:
            (images / "icon.svg").write_text(icon_svg, encoding="utf-8")
        print(f"{out.relative_to(repo)}  {icon.size[0]}x{icon.size[1]} {icon.mode}  {out.stat().st_size} B")
        if out.stat().st_size > 1024 * 1024:
            raise SystemExit("icon.png is over 1 MB")

        src = images / "featureGraphic.svg"
        if src.exists():
            img = render(feature_svg(src), fonts, FEATURE_W, FEATURE_H)
            flat = Image.new("RGB", img.size, FEATURE_BG)
            flat.paste(img, mask=img.convert("RGBA").split()[3])
            out = images / "featureGraphic.png"
            flat.save(out, optimize=True)
            print(f"{out.relative_to(repo)}  {flat.size[0]}x{flat.size[1]} {flat.mode}  {out.stat().st_size} B")
            if out.stat().st_size > 15 * 1024 * 1024:
                raise SystemExit("featureGraphic.png is over 15 MB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
