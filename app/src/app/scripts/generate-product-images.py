#!/usr/bin/env python3
"""Genera las ilustraciones de producto (public/products/<id>.svg).

Cada producto del catálogo tiene un dibujo que representa lo que dice su título (una bolsa de croquetas, una pipeta,
un rascador, etc.), así la imagen siempre tiene sentido con el nombre. Para cambiar un dibujo se edita este archivo
y se vuelve a ejecutar:  python scripts/generate-product-images.py
"""
import os
import sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "public", "products")
W, H = 600, 450

INK = "#3b2a1a"      # contorno oscuro cálido
WHITE = "#ffffff"
FONT = 'font-family="Segoe UI, Helvetica, Arial, sans-serif"'


def shade(hex_color, factor):
    """Oscurece (factor < 1) o aclara (factor > 1) un color #rrggbb."""
    h = hex_color.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    if factor <= 1:
        r, g, b = (int(v * factor) for v in (r, g, b))
    else:
        r, g, b = (min(255, int(v + (255 - v) * (factor - 1))) for v in (r, g, b))
    return "#%02x%02x%02x" % (r, g, b)


def esc(text):
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ----------------------------------------------------------------------------- primitivas

def shadow(cx=300, cy=392, rx=150, ry=14):
    return f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="rgba(60,40,20,0.16)"/>'


def text(x, y, s, size=24, fill=INK, weight=800, anchor="middle", spacing=0):
    return (f'<text x="{x}" y="{y}" {FONT} font-size="{size}" font-weight="{weight}" fill="{fill}" '
            f'text-anchor="{anchor}" letter-spacing="{spacing}">{esc(s)}</text>')


def paw(cx, cy, s=1.0, fill=INK):
    return (f'<g fill="{fill}" transform="translate({cx} {cy}) scale({s})">'
            '<ellipse cx="0" cy="8" rx="15" ry="12"/><ellipse cx="-17" cy="-8" rx="6.5" ry="8.5"/>'
            '<ellipse cx="-6" cy="-18" rx="6.5" ry="8.5"/><ellipse cx="6" cy="-18" rx="6.5" ry="8.5"/>'
            '<ellipse cx="17" cy="-8" rx="6.5" ry="8.5"/></g>')


def fish(cx, cy, s=1.0, fill=INK):
    return (f'<g fill="{fill}" transform="translate({cx} {cy}) scale({s})">'
            '<ellipse cx="0" cy="0" rx="24" ry="13"/><path d="M20 0 L38 -13 L38 13 Z"/>'
            f'<circle cx="-12" cy="-3" r="2.6" fill="{WHITE}"/></g>')


def drumstick(cx, cy, s=1.0, fill=INK):
    return (f'<g fill="{fill}" transform="translate({cx} {cy}) scale({s}) rotate(-35)">'
            '<ellipse cx="-8" cy="0" rx="20" ry="15"/><rect x="8" y="-4" width="26" height="8" rx="4"/>'
            '<circle cx="35" cy="-5" r="6"/><circle cx="35" cy="5" r="6"/></g>')


def bone_shape(cx, cy, w=120, h=30, fill=WHITE, stroke=INK, rot=0, sw=0):
    r = h * 0.62
    half = w / 2 - r * 0.6
    stroke_attr = f' stroke="{stroke}" stroke-width="{sw}"' if sw else ""
    return (f'<g transform="translate({cx} {cy}) rotate({rot})" fill="{fill}"{stroke_attr}>'
            f'<rect x="{-half}" y="{-h / 2}" width="{half * 2}" height="{h}" rx="{h / 2.4}"/>'
            f'<circle cx="{-half - r * 0.25}" cy="{-h * 0.38}" r="{r}"/><circle cx="{-half - r * 0.25}" cy="{h * 0.38}" r="{r}"/>'
            f'<circle cx="{half + r * 0.25}" cy="{-h * 0.38}" r="{r}"/><circle cx="{half + r * 0.25}" cy="{h * 0.38}" r="{r}"/></g>')


def tooth(cx, cy, s=1.0, fill=WHITE, stroke=INK):
    return (f'<g transform="translate({cx} {cy}) scale({s})" fill="{fill}" stroke="{stroke}" stroke-width="3" stroke-linejoin="round">'
            '<path d="M-16 -18 C-24 -18 -26 -4 -20 8 C-16 18 -14 26 -9 26 C-4 26 -5 12 0 12 C5 12 4 26 9 26 '
            'C14 26 16 18 20 8 C26 -4 24 -18 16 -18 C8 -18 6 -12 0 -12 C-6 -12 -8 -18 -16 -18 Z"/></g>')


def leaf(cx, cy, s=1.0, fill="#4caf50"):
    return (f'<g transform="translate({cx} {cy}) scale({s}) rotate(-30)"><path d="M0 -22 C20 -12 20 12 0 22 C-20 12 -20 -12 0 -22 Z" fill="{fill}"/>'
            f'<path d="M0 -16 L0 22" stroke="{WHITE}" stroke-width="2.5" fill="none"/></g>')


def droplet(cx, cy, s=1.0, fill="#42a5f5"):
    return (f'<g transform="translate({cx} {cy}) scale({s})"><path d="M0 -22 C16 -2 20 6 20 12 A20 20 0 0 1 -20 12 C-20 6 -16 -2 0 -22 Z" fill="{fill}"/></g>')


def flea(cx, cy, s=1.0, fill=INK):
    return (f'<g transform="translate({cx} {cy}) scale({s})" fill="{fill}"><ellipse cx="0" cy="0" rx="9" ry="12"/>'
            '<g stroke="' + fill + '" stroke-width="2.4" fill="none" stroke-linecap="round">'
            '<path d="M-8 -4 L-17 -10 M-8 2 L-18 4 M-7 8 L-15 15 M8 -4 L17 -10 M8 2 L18 4 M7 8 L15 15"/></g></g>')


def slash(cx, cy, r=26, color="#e53935"):
    return (f'<g stroke="{color}" stroke-width="6" fill="none" stroke-linecap="round"><circle cx="{cx}" cy="{cy}" r="{r}"/>'
            f'<path d="M{cx - r * 0.7} {cy + r * 0.7} L{cx + r * 0.7} {cy - r * 0.7}"/></g>')


def icon(kind, cx, cy, s=1.0, fill=INK):
    if kind == "paw":
        return paw(cx, cy, s, fill)
    if kind == "fish":
        return fish(cx, cy, s, fill)
    if kind == "chicken":
        return drumstick(cx, cy, s, fill)
    if kind == "bone":
        return bone_shape(cx, cy, 64 * s, 16 * s, fill=fill)
    if kind == "tooth":
        return tooth(cx, cy, s * 0.9, WHITE if fill == INK else fill, INK)
    if kind == "leaf":
        return leaf(cx, cy, s)
    if kind == "drop":
        return droplet(cx, cy, s)
    if kind == "flea":
        return slash(cx, cy, 30 * s) + flea(cx, cy, s * 1.1, fill)
    if kind == "heart":
        return (f'<g transform="translate({cx} {cy}) scale({s})" fill="#e53935"><path d="M0 22 C-34 -2 -24 -26 -9 -24 '
                'C-3 -23 0 -17 0 -15 C0 -17 3 -23 9 -24 C24 -26 34 -2 0 22 Z"/></g>')
    if kind == "omega":
        return (f'<g transform="translate({cx} {cy}) scale({s})">' + fish(0, -8, 0.8, "#ffb300") +
                f'<text x="0" y="30" {FONT} font-size="18" font-weight="800" fill="{fill}" text-anchor="middle">Ω3</text></g>')
    return ""


# ----------------------------------------------------------------------------- objetos

def obj_bag(p):
    c, d = p["color"], shade(p["color"], 0.78)
    s = shadow(rx=130)
    s += f'<path d="M185 112 C185 100 200 92 215 92 L385 92 C400 92 415 100 415 112 L420 372 C420 384 410 392 398 392 L202 392 C190 392 180 384 180 372 Z" fill="{c}"/>'
    s += f'<path d="M190 100 L410 100 L412 128 L188 128 Z" fill="{d}"/>'
    s += '<path d="M196 100 L204 128 M216 100 L222 128 M236 100 L240 128 M256 100 L258 128 M276 100 L276 128 M296 100 L296 128 M316 100 L314 128 M336 100 L332 128 M356 100 L350 128 M376 100 L368 128 M396 100 L388 128" stroke="rgba(0,0,0,0.12)" stroke-width="3"/>'
    s += f'<rect x="206" y="168" width="188" height="186" rx="22" fill="{WHITE}"/>'
    s += icon(p["icon"], 300, 232, 1.9, c)
    s += text(300, 304, p["brand"].upper(), 24, INK, 800, spacing=2)
    s += text(300, 336, p["sub"], 19, "#6b7280", 700)
    s += f'<rect x="180" y="150" width="14" height="236" rx="7" fill="rgba(255,255,255,0.18)"/>'
    return s


def obj_cans(p):
    c, d = p["color"], shade(p["color"], 0.8)

    def can(cx, cy, w=118, h=150, label=True):
        rx = w / 2
        out = f'<rect x="{cx - rx}" y="{cy - h / 2}" width="{w}" height="{h}" rx="6" fill="#cfd8dc"/>'
        out += f'<rect x="{cx - rx}" y="{cy - h / 2 + 22}" width="{w}" height="{h - 44}" fill="{c}"/>'
        out += f'<ellipse cx="{cx}" cy="{cy - h / 2}" rx="{rx}" ry="13" fill="#eceff1" stroke="#b0bec5" stroke-width="3"/>'
        out += f'<ellipse cx="{cx}" cy="{cy + h / 2}" rx="{rx}" ry="13" fill="#b0bec5"/>'
        out += f'<rect x="{cx - rx + 10}" y="{cy - h / 2 + 34}" width="{w - 20}" height="{h - 68}" rx="14" fill="{WHITE}"/>'
        if label:
            out += icon(p["icon"], cx, cy - 6, 1.1, c)
            out += text(cx, cy + 30, p["brand"].upper(), 12, INK, 800, spacing=1)
        return out

    s = shadow(rx=170)
    s += can(228, 300, 106, 132, False)
    s += can(372, 292, 118, 146, True)
    s += can(300, 322, 126, 150, True)
    return s


def obj_bottle(p):
    c, d = p["color"], shade(p["color"], 0.78)
    style = p.get("style", "cap")
    s = shadow(rx=100)
    if style == "jar":
        s += f'<rect x="196" y="150" width="208" height="232" rx="34" fill="{c}"/>'
        s += f'<rect x="186" y="118" width="228" height="48" rx="14" fill="{d}"/>'
        s += f'<rect x="186" y="112" width="228" height="14" rx="7" fill="{shade(c, 0.6)}"/>'
        s += f'<rect x="214" y="190" width="172" height="164" rx="20" fill="{WHITE}"/>'
        s += icon(p["icon"], 300, 246, 1.7, c)
        s += text(300, 322, p["brand"].upper(), 21, INK, 800, spacing=1.5)
        return s
    body_y = 170 if style in ("pump", "dropper") else 150
    s += f'<rect x="226" y="{body_y}" width="148" height="{392 - body_y}" rx="30" fill="{c}"/>'
    s += f'<rect x="258" y="{body_y - 30}" width="84" height="46" rx="14" fill="{shade(c, 0.9)}"/>'
    if style == "pump":
        s += f'<rect x="288" y="108" width="24" height="46" rx="8" fill="{d}"/>'
        s += f'<path d="M270 100 L350 100 L350 116 L290 116 L290 108 L270 108 Z" fill="{d}"/>'
        s += f'<rect x="270" y="96" width="90" height="16" rx="8" fill="{d}"/>'
    elif style == "dropper":
        s += f'<rect x="276" y="86" width="48" height="84" rx="18" fill="#37474f"/>'
        s += f'<rect x="266" y="150" width="68" height="26" rx="8" fill="#546e7a"/>'
    else:
        s += f'<rect x="262" y="112" width="76" height="52" rx="14" fill="{d}"/>'
        s += f'<g stroke="rgba(0,0,0,0.2)" stroke-width="3"><path d="M274 118 V158 M288 118 V158 M302 118 V158 M316 118 V158 M330 118 V158"/></g>'
    s += f'<rect x="242" y="{body_y + 44}" width="116" height="{392 - body_y - 76}" rx="16" fill="{WHITE}"/>'
    cy = body_y + 44 + (392 - body_y - 76) / 2 - 16
    s += icon(p["icon"], 300, cy, 1.15, c)
    s += text(300, cy + 52, p["brand"].upper(), 15, INK, 800, spacing=1)
    s += f'<rect x="234" y="{body_y + 10}" width="10" height="{392 - body_y - 40}" rx="5" fill="rgba(255,255,255,0.22)"/>'
    return s


def obj_tube(p):
    c, d = p["color"], shade(p["color"], 0.75)
    s = shadow(rx=170)
    s += '<g transform="rotate(-18 300 250)">'
    s += f'<path d="M120 214 L420 206 C440 206 452 226 452 250 C452 274 440 294 420 294 L120 286 C106 286 100 270 100 250 C100 230 106 214 120 214 Z" fill="{c}"/>'
    s += f'<path d="M452 232 L500 232 L500 268 L452 268 Z" fill="{d}"/><rect x="496" y="226" width="34" height="48" rx="10" fill="{shade(c, 0.6)}"/>'
    s += f'<path d="M104 222 L120 214 L122 286 L106 280 Z" fill="{d}"/>'
    s += f'<rect x="170" y="222" width="230" height="56" rx="12" fill="{WHITE}"/>'
    s += '</g>'
    s += icon(p["icon"], 282, 238, 1.25, c)
    s += f'<g transform="rotate(-18 300 250)">{text(350, 258, p["brand"].upper(), 17, INK, 800, spacing=1)}</g>'
    # cepillo de dientes
    s += (f'<g transform="translate(300 346) rotate(-12)"><rect x="-110" y="-7" width="150" height="14" rx="7" fill="#90caf9"/>'
          f'<rect x="30" y="-12" width="52" height="24" rx="6" fill="#42a5f5"/><g fill="{WHITE}"><rect x="36" y="-22" width="6" height="12" rx="2"/>'
          '<rect x="46" y="-22" width="6" height="12" rx="2"/><rect x="56" y="-22" width="6" height="12" rx="2"/><rect x="66" y="-22" width="6" height="12" rx="2"/></g></g>')
    return s


def obj_wipes(p):
    c, d = p["color"], shade(p["color"], 0.8)
    s = shadow(rx=150)
    s += f'<rect x="176" y="168" width="248" height="220" rx="26" fill="{c}"/>'
    s += f'<rect x="176" y="168" width="248" height="46" rx="20" fill="{d}"/>'
    s += f'<rect x="262" y="142" width="76" height="40" rx="14" fill="{WHITE}" stroke="{d}" stroke-width="4"/>'
    s += f'<path d="M278 142 C278 108 300 96 322 108 C340 118 336 142 336 142 Z" fill="{WHITE}" stroke="#cfd8dc" stroke-width="3"/>'
    s += f'<rect x="200" y="236" width="200" height="130" rx="18" fill="{WHITE}"/>'
    s += icon(p["icon"], 300, 288, 1.6, c)
    s += text(300, 346, p["brand"].upper(), 19, INK, 800, spacing=1.5)
    return s


def obj_diapers(p):
    c, d = p["color"], shade(p["color"], 0.8)
    s = shadow(rx=150)
    s += f'<rect x="180" y="150" width="240" height="238" rx="30" fill="{c}"/>'
    s += f'<path d="M180 150 C180 126 200 114 222 114 L378 114 C400 114 420 126 420 150 Z" fill="{d}"/>'
    s += f'<rect x="204" y="206" width="192" height="156" rx="20" fill="{WHITE}"/>'
    # pañal
    s += (f'<g transform="translate(300 270)"><path d="M-54 -22 L54 -22 C54 14 36 42 12 42 L-12 42 C-36 42 -54 14 -54 -22 Z" fill="#fff" stroke="{c}" stroke-width="5"/>'
          f'<rect x="-54" y="-30" width="108" height="14" rx="6" fill="{c}"/><circle cx="-34" cy="-23" r="5" fill="{WHITE}"/><circle cx="34" cy="-23" r="5" fill="{WHITE}"/></g>')
    s += paw(300, 262, 0.7, c)
    s += text(300, 344, p["brand"].upper(), 20, INK, 800, spacing=1.5)
    return s


def obj_towel(p):
    c, d = p["color"], shade(p["color"], 0.82)
    s = shadow(rx=170)
    s += f'<path d="M150 150 L450 150 L470 380 L130 380 Z" fill="{c}"/>'
    s += f'<path d="M150 150 L450 150 L456 196 L144 196 Z" fill="{d}"/>'
    s += f'<g fill="{d}">' + "".join(f'<rect x="{138 + i * 22}" y="{346}" width="14" height="46" rx="6" transform="rotate({(-1) ** i * 2} {145 + i * 22} 370)"/>' for i in range(14)) + '</g>'
    s += f'<path d="M180 220 L420 220 M176 262 L424 262 M172 304 L428 304" stroke="rgba(255,255,255,0.45)" stroke-width="10" stroke-linecap="round"/>'
    s += paw(300, 262, 1.5, WHITE)
    return s


def obj_bowl(p):
    c = p["color"]
    steel = p.get("steel", False)
    base = "#b0bec5" if steel else c
    rim = "#eceff1" if steel else shade(c, 1.35)
    food = "#8d5524"

    def bowl(cx, cy, rx=122, ry=34, depth=84):
        out = f'<path d="M{cx - rx} {cy} C{cx - rx + 8} {cy + depth} {cx - rx + 40} {cy + depth + 14} {cx} {cy + depth + 14} C{cx + rx - 40} {cy + depth + 14} {cx + rx - 8} {cy + depth} {cx + rx} {cy} Z" fill="{base}"/>'
        if steel:
            out += f'<path d="M{cx - rx + 22} {cy + 20} C{cx - rx + 30} {cy + depth - 10} {cx - rx + 50} {cy + depth} {cx - rx + 70} {cy + depth + 8}" stroke="rgba(255,255,255,0.7)" stroke-width="8" fill="none" stroke-linecap="round"/>'
        out += f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{rim}"/>'
        out += f'<ellipse cx="{cx}" cy="{cy + 4}" rx="{rx - 14}" ry="{ry - 8}" fill="{shade(base, 0.7)}"/>'
        out += f'<g fill="{food}">' + "".join(f'<circle cx="{cx - 60 + i * 17}" cy="{cy + 2 - (i % 3) * 5}" r="9"/>' for i in range(8)) + '</g>'
        return out

    s = shadow(rx=190)
    if p.get("double"):
        s += f'<rect x="120" y="232" width="360" height="22" rx="11" fill="{shade(c, 0.7)}"/>'
        s += bowl(200, 238, 82, 24, 74) + bowl(400, 238, 82, 24, 74)
        s += f'<rect x="120" y="344" width="360" height="26" rx="13" fill="{shade(c, 0.6)}"/>'
        s += '<g fill="rgba(255,255,255,0.7)">' + "".join(f'<rect x="{140 + i * 40}" y="352" width="22" height="9" rx="4"/>' for i in range(9)) + '</g>'
    else:
        s += bowl(300, 230)
    return s


def obj_feeder(p):
    c, d = p["color"], shade(p["color"], 0.8)
    s = shadow(rx=170)
    s += f'<path d="M210 70 L390 70 L402 250 L198 250 Z" fill="rgba(144,202,249,0.5)" stroke="{d}" stroke-width="5"/>'
    s += f'<g fill="#a1662f">' + "".join(f'<circle cx="{228 + (i % 7) * 24}" cy="{200 - (i // 7) * 24}" r="10"/>' for i in range(21)) + '</g>'
    s += f'<rect x="196" y="56" width="208" height="26" rx="13" fill="{d}"/>'
    s += f'<rect x="168" y="246" width="264" height="132" rx="26" fill="{c}"/>'
    s += f'<rect x="196" y="272" width="104" height="44" rx="12" fill="#263238"/>' + text(248, 303, "08:00", 26, "#80deea", 800)
    s += f'<circle cx="352" cy="294" r="18" fill="{WHITE}"/><circle cx="352" cy="294" r="8" fill="{d}"/>'
    s += f'<path d="M210 378 C210 340 230 330 300 330 C370 330 390 340 390 378 Z" fill="{shade(c, 1.25)}"/>'
    return s


def obj_bone(p):
    c = p["color"]
    s = shadow(rx=190)
    s += bone_shape(300, 270, 360, 86, fill=c, rot=-14)
    s += bone_shape(300, 262, 340, 20, fill=shade(c, 1.4), rot=-14)
    if p.get("label"):
        s += f'<g transform="translate(300 262) rotate(-14)">{text(0, 10, p["label"], 30, WHITE, 800, spacing=2)}</g>'
    return s


def obj_ball(p):
    c = p["color"]
    s = shadow(rx=120, cy=396)
    s += f'<circle cx="300" cy="260" r="124" fill="{c}"/>'
    s += f'<path d="M184 232 C230 262 230 330 214 372" stroke="{WHITE}" stroke-width="10" fill="none" stroke-linecap="round" opacity="0.9"/>'
    s += f'<path d="M416 232 C370 262 370 330 386 372" stroke="{WHITE}" stroke-width="10" fill="none" stroke-linecap="round" opacity="0.9"/>'
    s += f'<ellipse cx="258" cy="204" rx="34" ry="20" fill="rgba(255,255,255,0.35)" transform="rotate(-30 258 204)"/>'
    if p.get("squeak"):
        s += f'<g fill="{INK}" opacity="0.85"><path d="M432 110 L452 90 M450 130 L478 124 M420 92 L424 66" stroke="{INK}" stroke-width="7" stroke-linecap="round"/></g>'
    return s


def obj_launcher(p):
    c, d = p["color"], shade(p["color"], 0.78)
    s = shadow(rx=170)
    s += f'<path d="M120 340 L360 140 C376 126 396 130 404 146 L414 168 C420 182 414 196 402 206 L170 388 Z" fill="{c}"/>'
    s += f'<path d="M150 360 L388 164" stroke="{shade(c, 1.4)}" stroke-width="10" stroke-linecap="round"/>'
    s += f'<circle cx="446" cy="132" r="54" fill="#cddc39" stroke="{d}" stroke-width="5"/>'
    s += f'<path d="M402 120 C426 142 428 166 420 178 M490 140 C468 128 462 108 470 90" stroke="{WHITE}" stroke-width="6" fill="none" stroke-linecap="round"/>'
    s += f'<rect x="120" y="332" width="76" height="30" rx="12" fill="{d}" transform="rotate(-38 158 346)"/>'
    return s


def obj_plush(p):
    c, d = p["color"], shade(p["color"], 0.8)
    kind = p.get("kind", "bear")
    s = shadow(rx=130)
    if kind == "lamb":
        s += f'<g fill="{WHITE}" stroke="#d7ccc8" stroke-width="3">' + "".join(
            f'<circle cx="{cx}" cy="{cy}" r="{r}"/>' for cx, cy, r in [(210, 270, 52), (390, 270, 52), (250, 220, 60), (350, 220, 60), (300, 300, 66), (300, 214, 56), (216, 330, 40), (384, 330, 40)]) + '</g>'
        s += f'<ellipse cx="300" cy="150" rx="62" ry="64" fill="#f5e6d3"/><ellipse cx="238" cy="148" rx="22" ry="38" fill="#e0c3a3" transform="rotate(22 238 148)"/><ellipse cx="362" cy="148" rx="22" ry="38" fill="#e0c3a3" transform="rotate(-22 362 148)"/>'
        s += f'<circle cx="278" cy="142" r="7" fill="{INK}"/><circle cx="322" cy="142" r="7" fill="{INK}"/><ellipse cx="300" cy="166" rx="14" ry="10" fill="#c98f6a"/>'
        s += f'<rect x="220" y="350" width="34" height="38" rx="14" fill="#d7ccc8"/><rect x="346" y="350" width="34" height="38" rx="14" fill="#d7ccc8"/>'
        return s
    s += f'<ellipse cx="300" cy="300" rx="92" ry="86" fill="{c}"/>'
    s += f'<ellipse cx="300" cy="312" rx="56" ry="56" fill="{shade(c, 1.3)}"/>'
    s += f'<circle cx="216" cy="262" r="30" fill="{c}"/><circle cx="384" cy="262" r="30" fill="{c}"/>'
    s += f'<circle cx="228" cy="372" r="34" fill="{d}"/><circle cx="372" cy="372" r="34" fill="{d}"/>'
    s += f'<circle cx="300" cy="170" r="76" fill="{c}"/><circle cx="238" cy="112" r="30" fill="{c}"/><circle cx="362" cy="112" r="30" fill="{c}"/>'
    s += f'<circle cx="238" cy="112" r="16" fill="{shade(c, 1.3)}"/><circle cx="362" cy="112" r="16" fill="{shade(c, 1.3)}"/>'
    s += f'<ellipse cx="300" cy="192" rx="36" ry="28" fill="{shade(c, 1.35)}"/><ellipse cx="300" cy="182" rx="13" ry="9" fill="{INK}"/>'
    s += f'<circle cx="272" cy="156" r="8" fill="{INK}"/><circle cx="328" cy="156" r="8" fill="{INK}"/>'
    s += f'<path d="M284 204 Q300 218 316 204" stroke="{INK}" stroke-width="4" fill="none" stroke-linecap="round"/>'
    return s


def obj_mouse(p):
    c = p["color"]
    s = shadow(rx=140)
    s += f'<path d="M458 310 C520 290 540 232 500 214" stroke="{shade(c, 0.7)}" stroke-width="9" fill="none" stroke-linecap="round"/>'
    s += f'<ellipse cx="300" cy="300" rx="150" ry="86" fill="{c}"/>'
    s += f'<path d="M150 300 C150 240 220 200 280 214 C330 226 380 260 400 300 Z" fill="{shade(c, 1.15)}"/>'
    s += f'<circle cx="214" cy="226" r="46" fill="#f8bbd0" stroke="{c}" stroke-width="6"/><circle cx="288" cy="214" r="40" fill="#f8bbd0" stroke="{c}" stroke-width="6"/>'
    s += f'<circle cx="150" cy="306" r="14" fill="#e91e63"/><circle cx="178" cy="284" r="8" fill="{INK}"/>'
    s += f'<g stroke="{INK}" stroke-width="3" stroke-linecap="round"><path d="M146 312 L96 304 M148 318 L100 330 M150 296 L104 278"/></g>'
    return s


def obj_wand(p):
    c = p["color"]
    s = shadow(rx=140)
    s += f'<path d="M150 380 L420 120" stroke="{c}" stroke-width="16" stroke-linecap="round"/>'
    s += f'<path d="M150 380 L190 342" stroke="{shade(c, 0.6)}" stroke-width="22" stroke-linecap="round"/>'
    s += f'<path d="M420 120 C440 150 430 200 410 240" stroke="#8d6e63" stroke-width="4" fill="none"/>'
    s += (f'<g transform="translate(410 250)"><path d="M0 0 C-34 -4 -50 24 -44 56 C-20 50 -6 30 0 0 Z" fill="#e53935"/><path d="M0 0 C34 -4 50 24 44 56 C20 50 6 30 0 0 Z" fill="#ff9800"/>'
          f'<path d="M0 0 C-8 24 -6 60 0 92 C6 60 8 24 0 0 Z" fill="#fdd835"/><path d="M0 0 C-20 14 -26 50 -22 80 C-6 54 0 30 0 0 Z" fill="#42a5f5"/>'
          f'<path d="M0 0 C20 14 26 50 22 80 C6 54 0 30 0 0 Z" fill="#66bb6a"/></g>')
    s += f'<circle cx="420" cy="120" r="12" fill="{shade(c, 0.6)}"/>'
    return s


def obj_ring(p):
    c, d = p["color"], shade(p["color"], 0.8)
    s = shadow(rx=150)
    s += f'<circle cx="300" cy="250" r="118" fill="none" stroke="{d}" stroke-width="64"/>'
    s += f'<circle cx="300" cy="250" r="118" fill="none" stroke="{c}" stroke-width="52"/>'
    s += f'<g fill="{shade(c, 1.35)}">' + "".join(
        f'<rect x="{300 + 118 * __import__("math").cos(i * 0.785) - 7}" y="{250 + 118 * __import__("math").sin(i * 0.785) - 17}" width="14" height="34" rx="7" transform="rotate({i * 45 + 90} {300 + 118 * __import__("math").cos(i * 0.785)} {250 + 118 * __import__("math").sin(i * 0.785)})"/>'
        for i in range(8)) + '</g>'
    s += f'<path d="M214 190 C240 150 290 138 330 148" stroke="rgba(255,255,255,0.5)" stroke-width="10" fill="none" stroke-linecap="round"/>'
    return s


def obj_rope(p):
    c = p["color"]
    s = shadow(rx=170)
    s += f'<path d="M150 300 L450 220" stroke="#d7b98a" stroke-width="38" stroke-linecap="round"/>'
    s += '<g stroke="#a5825a" stroke-width="7" stroke-linecap="round">' + "".join(
        f'<path d="M{170 + i * 38} {292 - i * 10} L{186 + i * 38} {266 - i * 10}"/>' for i in range(7)) + '</g>'
    for cx, cy in [(150, 300), (450, 220)]:
        s += f'<circle cx="{cx - 14}" cy="{cy - 26}" r="34" fill="{c}"/><circle cx="{cx - 14}" cy="{cy + 28}" r="34" fill="{c}"/><circle cx="{cx + 10}" cy="{cy}" r="34" fill="{c}"/>'
    return s


def obj_collar(p):
    c, d = p["color"], shade(p["color"], 0.75)
    s = shadow(rx=170)
    s += f'<ellipse cx="300" cy="238" rx="168" ry="130" fill="none" stroke="{d}" stroke-width="50"/>'
    s += f'<ellipse cx="300" cy="238" rx="168" ry="130" fill="none" stroke="{c}" stroke-width="38"/>'
    s += f'<path d="M168 150 C220 112 380 112 432 150" stroke="rgba(255,255,255,0.35)" stroke-width="8" fill="none" stroke-linecap="round"/>'
    s += f'<g stroke="{shade(c, 1.4)}" stroke-width="5" stroke-dasharray="14 12" fill="none"><ellipse cx="300" cy="238" rx="168" ry="130"/></g>'
    s += f'<rect x="270" y="338" width="60" height="38" rx="8" fill="#cfd8dc" stroke="#90a4ae" stroke-width="4"/><rect x="288" y="326" width="24" height="22" rx="5" fill="#90a4ae"/>'
    s += f'<circle cx="300" cy="404" r="30" fill="#ffd54f" stroke="#f9a825" stroke-width="5"/>' + paw(300, 404, 0.55, "#f57f17")
    return s


def obj_harness(p):
    c, d = p["color"], shade(p["color"], 0.78)
    s = shadow(rx=150)
    s += f'<ellipse cx="300" cy="190" rx="90" ry="66" fill="none" stroke="{c}" stroke-width="30"/>'
    s += f'<path d="M232 232 L210 350 M368 232 L390 350" stroke="{c}" stroke-width="30" stroke-linecap="round"/>'
    s += f'<path d="M210 350 C230 388 370 388 390 350" stroke="{c}" stroke-width="30" fill="none" stroke-linecap="round"/>'
    s += f'<path d="M300 124 L300 352" stroke="{d}" stroke-width="22"/>'
    s += f'<rect x="270" y="86" width="60" height="30" rx="10" fill="#b0bec5" stroke="#78909c" stroke-width="4"/>'
    s += f'<rect x="276" y="246" width="48" height="38" rx="8" fill="#b0bec5" stroke="#78909c" stroke-width="4"/>'
    s += '<g stroke="rgba(255,255,255,0.45)" stroke-width="5" stroke-dasharray="12 10"><ellipse cx="300" cy="190" rx="90" ry="66" fill="none"/></g>'
    return s


def obj_leash(p):
    c, d = p["color"], shade(p["color"], 0.78)
    s = shadow(rx=180)
    s += f'<path d="M170 170 C170 120 250 120 250 170 C250 214 200 224 190 262 C170 330 330 340 400 300 C450 272 470 330 440 360" stroke="{d}" stroke-width="28" fill="none" stroke-linecap="round"/>'
    s += f'<path d="M170 170 C170 120 250 120 250 170 C250 214 200 224 190 262 C170 330 330 340 400 300 C450 272 470 330 440 360" stroke="{c}" stroke-width="20" fill="none" stroke-linecap="round"/>'
    if p.get("reflective"):
        s += '<path d="M170 170 C170 120 250 120 250 170 C250 214 200 224 190 262 C170 330 330 340 400 300 C450 272 470 330 440 360" stroke="#e0e0e0" stroke-width="8" stroke-dasharray="22 16" fill="none"/>'
    s += f'<rect x="424" y="354" width="44" height="44" rx="10" fill="#b0bec5" stroke="#78909c" stroke-width="5"/><circle cx="446" cy="376" r="10" fill="{WHITE}"/>'
    if p.get("rubber"):
        s += f'<ellipse cx="210" cy="148" rx="54" ry="58" fill="none" stroke="{shade(c, 0.5)}" stroke-width="22"/>'
    return s


def obj_brush(p):
    c, d = p["color"], shade(p["color"], 0.75)
    s = shadow(rx=170)
    s += f'<g transform="rotate(-28 300 250)"><rect x="130" y="226" width="150" height="46" rx="23" fill="{c}"/>'
    s += f'<rect x="268" y="188" width="190" height="124" rx="26" fill="{d}"/><rect x="278" y="198" width="170" height="104" rx="20" fill="#ffe0b2"/>'
    s += '<g fill="#90a4ae">' + "".join(f'<circle cx="{298 + col * 28}" cy="{216 + row * 28}" r="6"/>' for col in range(6) for row in range(4)) + '</g></g>'
    return s


def obj_glove(p):
    c, d = p["color"], shade(p["color"], 0.8)
    s = shadow(rx=150)
    s += f'<path d="M190 380 C150 330 150 250 176 200 L196 120 C200 100 228 100 230 124 L238 190 L250 80 C252 58 284 58 286 80 L290 180 L314 94 C320 74 350 80 346 102 L330 196 L372 134 C384 116 410 130 402 150 L372 232 C420 250 440 320 420 380 Z" fill="{c}"/>'
    s += f'<rect x="188" y="344" width="232" height="42" rx="14" fill="{d}"/>'
    s += '<g fill="rgba(255,255,255,0.75)">' + "".join(f'<circle cx="{222 + (i % 6) * 32}" cy="{250 + (i // 6) * 32}" r="6"/>' for i in range(18)) + '</g>'
    return s


def obj_bed(p):
    c, d = p["color"], shade(p["color"], 0.78)
    s = shadow(rx=210, cy=398)
    s += f'<path d="M84 270 L84 330 C84 372 190 392 300 392 C410 392 516 372 516 330 L516 270 Z" fill="{d}"/>'
    s += f'<ellipse cx="300" cy="270" rx="216" ry="92" fill="{shade(c, 1.1)}"/>'
    s += f'<ellipse cx="300" cy="276" rx="150" ry="58" fill="{shade(c, 1.45)}"/>'
    s += f'<ellipse cx="300" cy="284" rx="132" ry="46" fill="{shade(c, 1.3)}"/>'
    s += f'<path d="M110 300 C130 346 200 366 300 366 C400 366 470 346 490 300" fill="none" stroke="rgba(255,255,255,0.28)" stroke-width="9" stroke-linecap="round"/>'
    s += paw(300, 278, 1.2, shade(c, 0.95))
    return s


def obj_house(p):
    c, d = p["color"], shade(p["color"], 0.75)
    kind = p.get("kind", "dome")
    s = shadow(rx=200)
    if kind == "wood":
        s += f'<rect x="150" y="190" width="300" height="196" fill="{c}"/>'
        s += '<g stroke="rgba(0,0,0,0.18)" stroke-width="4">' + "".join(f'<path d="M150 {222 + i * 33} L450 {222 + i * 33}"/>' for i in range(5)) + '</g>'
        s += f'<path d="M120 200 L300 80 L480 200 Z" fill="{d}"/><path d="M300 80 L480 200 L450 200 L300 100 Z" fill="rgba(255,255,255,0.12)"/>'
        s += f'<path d="M250 386 L250 290 C250 250 350 250 350 290 L350 386 Z" fill="#3e2723"/>'
        s += f'<circle cx="300" cy="248" r="14" fill="#ffd54f"/>'
        s += f'<rect x="276" y="106" width="48" height="40" rx="6" fill="{shade(c, 1.2)}" stroke="{d}" stroke-width="4"/>'
        return s
    s += f'<path d="M130 386 C130 190 230 110 300 110 C370 110 470 190 470 386 Z" fill="{c}"/>'
    s += f'<path d="M130 386 C130 190 230 110 300 110" stroke="rgba(255,255,255,0.25)" stroke-width="12" fill="none" stroke-linecap="round" transform="translate(12 8)"/>'
    s += f'<path d="M232 386 L232 300 C232 244 368 244 368 300 L368 386 Z" fill="{shade(c, 0.4)}"/>'
    s += f'<ellipse cx="300" cy="386" rx="170" ry="14" fill="{d}"/>'
    s += f'<path d="M210 150 L190 100 L240 126 M390 150 L410 100 L360 126" fill="{c}" stroke="{c}" stroke-width="14" stroke-linejoin="round"/>'
    return s


def obj_scratcher(p):
    c, d = p["color"], shade(p["color"], 0.78)
    kind = p.get("kind", "post")
    s = shadow(rx=170)
    if kind == "tower":
        s += f'<rect x="160" y="352" width="280" height="34" rx="12" fill="{d}"/>'
        s += f'<rect x="250" y="120" width="48" height="236" fill="#d7b98a"/>'
        s += '<g stroke="#a5825a" stroke-width="5">' + "".join(f'<path d="M250 {136 + i * 16} L298 {128 + i * 16}"/>' for i in range(14)) + '</g>'
        s += f'<rect x="170" y="94" width="150" height="30" rx="14" fill="{c}"/>'
        s += f'<rect x="318" y="196" width="150" height="26" rx="12" fill="{c}"/><rect x="338" y="222" width="30" height="130" fill="#d7b98a"/>'
        s += f'<circle cx="410" cy="160" r="20" fill="#e53935"/><path d="M410 180 C410 214 400 226 392 234" stroke="#e53935" stroke-width="4" fill="none"/>'
        return s
    s += f'<rect x="170" y="338" width="260" height="46" rx="14" fill="{c}"/>'
    s += f'<rect x="262" y="120" width="76" height="224" fill="#d7b98a"/>'
    s += '<g stroke="#a5825a" stroke-width="5">' + "".join(f'<path d="M262 {132 + i * 16} L338 {122 + i * 16}"/>' for i in range(14)) + '</g>'
    s += f'<ellipse cx="300" cy="118" rx="106" ry="30" fill="{c}"/><ellipse cx="300" cy="108" rx="106" ry="28" fill="{shade(c, 1.25)}"/>'
    s += f'<circle cx="402" cy="260" r="22" fill="#e53935"/><path d="M382 252 C350 250 338 238 338 226" stroke="#e53935" stroke-width="4" fill="none"/>'
    return s


def obj_litter(p):
    c, d = p["color"], shade(p["color"], 0.8)
    s = shadow(rx=170)
    s += f'<path d="M150 232 L450 232 L462 388 L138 388 Z" fill="{c}"/>'
    s += f'<path d="M150 232 L450 232 L446 190 L154 190 Z" fill="{d}"/>'
    s += f'<g fill="#e0d6c8">' + "".join(f'<circle cx="{170 + (i % 8) * 36 + (i // 8 % 2) * 14}" cy="{188 - (i // 8) * 8 - (i % 3) * 4}" r="11"/>' for i in range(16)) + '</g>'
    s += f'<rect x="196" y="262" width="208" height="102" rx="18" fill="{WHITE}"/>'
    s += (f'<g transform="translate(300 302)"><ellipse cx="0" cy="12" rx="26" ry="20" fill="{INK}"/><path d="M-22 0 L-26 -24 L-8 -12 Z M22 0 L26 -24 L8 -12 Z" fill="{INK}"/></g>')
    s += text(300, 350, p["brand"].upper() + "  " + p["sub"], 17, INK, 800, spacing=1)
    return s


def obj_pipette(p):
    c, d = p["color"], shade(p["color"], 0.75)
    s = shadow(rx=170)
    for i, (cx, cy, rot) in enumerate([(220, 248, -14), (300, 238, 0), (380, 248, 14)]):
        s += (f'<g transform="translate({cx} {cy}) rotate({rot})"><path d="M-26 -120 L26 -120 L34 130 C34 146 -34 146 -34 130 Z" fill="{WHITE}" stroke="{c}" stroke-width="5"/>'
              f'<rect x="-16" y="-150" width="32" height="38" rx="8" fill="{d}"/><rect x="-26" y="-60" width="52" height="150" rx="10" fill="{c}" opacity="0.18"/>'
              f'<path d="M-18 -4 C-18 -22 18 -22 18 -4 L18 30 C18 52 -18 52 -18 30 Z" fill="{c}"/></g>')
    s += flea(300, 276, 1.1, WHITE)
    s += text(300, 424, p["brand"].upper(), 20, INK, 800, spacing=2)
    return s


def obj_carrier(p):
    c, d = p["color"], shade(p["color"], 0.75)
    s = shadow(rx=190)
    s += f'<path d="M215 154 C215 96 385 96 385 154" stroke="{d}" stroke-width="22" fill="none" stroke-linecap="round"/>'
    s += f'<rect x="120" y="150" width="360" height="236" rx="34" fill="{c}"/>'
    s += f'<rect x="120" y="150" width="360" height="52" rx="26" fill="{d}"/>'
    s += f'<rect x="170" y="226" width="200" height="132" rx="20" fill="#263238"/>'
    s += '<g stroke="#90a4ae" stroke-width="6">' + "".join(f'<path d="M{190 + i * 26} 232 L{190 + i * 26} 352"/>' for i in range(7)) + '</g>'
    s += f'<rect x="386" y="232" width="62" height="120" rx="14" fill="{shade(c, 1.2)}"/>' + '<g fill="rgba(0,0,0,0.25)">' + "".join(f'<rect x="398" y="{248 + i * 20}" width="38" height="8" rx="4"/>' for i in range(5)) + '</g>'
    s += paw(224, 160, 0.7, WHITE)
    return s


def obj_cat_dog_head_toy(p):  # no usado, reservado
    return ""


def obj_anti_bark(p):
    c, d = p["color"], shade(p["color"], 0.75)
    s = shadow(rx=170)
    s += f'<ellipse cx="300" cy="248" rx="160" ry="118" fill="none" stroke="{d}" stroke-width="44"/>'
    s += f'<ellipse cx="300" cy="248" rx="160" ry="118" fill="none" stroke="{c}" stroke-width="32"/>'
    s += f'<rect x="240" y="326" width="120" height="70" rx="18" fill="#37474f"/>'
    s += f'<g fill="{WHITE}"><path d="M262 360 L276 360 L292 346 L292 376 L276 362 L262 362 Z"/><path d="M302 352 C314 358 314 364 302 370" stroke="{WHITE}" stroke-width="4" fill="none"/></g>'
    s += slash(300, 361, 30, "#ef5350")
    s += f'<circle cx="324" cy="388" r="6" fill="#66bb6a"/>'
    return s


def obj_treats(p):
    c, d = p["color"], shade(p["color"], 0.78)
    s = shadow(rx=140)
    s += f'<path d="M190 130 L410 130 L424 380 L176 380 Z" fill="{c}"/>'
    s += f'<path d="M190 130 L410 130 L412 160 L188 160 Z" fill="{d}"/>'
    s += f'<path d="M190 130 L200 112 L210 130 L220 112 L230 130 L240 112 L250 130 L260 112 L270 130 L280 112 L290 130 L300 112 L310 130 L320 112 L330 130 L340 112 L350 130 L360 112 L370 130 L380 112 L390 130 L400 112 L410 130" fill="{d}"/>'
    s += f'<ellipse cx="300" cy="128" rx="110" ry="9" fill="rgba(0,0,0,0.18)"/>'
    s += text(300, 196, p["sub"], 18, WHITE, 700)
    s += f'<circle cx="300" cy="276" r="70" fill="{WHITE}"/>'
    s += icon(p["icon"], 300, 270, 1.7, c)
    s += text(300, 372, p["brand"].upper(), 22, WHITE, 800, spacing=2)
    return s


OBJECTS = {
    "bag": obj_bag, "cans": obj_cans, "bottle": obj_bottle, "tube": obj_tube, "wipes": obj_wipes, "diapers": obj_diapers,
    "towel": obj_towel, "bowl": obj_bowl, "feeder": obj_feeder, "bone": obj_bone, "ball": obj_ball, "launcher": obj_launcher,
    "plush": obj_plush, "mouse": obj_mouse, "wand": obj_wand, "ring": obj_ring, "rope": obj_rope, "collar": obj_collar,
    "harness": obj_harness, "leash": obj_leash, "brush": obj_brush, "glove": obj_glove, "bed": obj_bed, "house": obj_house,
    "scratcher": obj_scratcher, "litter": obj_litter, "pipette": obj_pipette, "carrier": obj_carrier, "antibark": obj_anti_bark,
    "treats": obj_treats,
}

# Fondo suave por categoría (centro, borde)
BG = {
    "alimentos": ("#fff1dc", "#ffd9a8"), "juguetes": ("#ffe3ea", "#ffc2d1"), "accesorios": ("#efe3ff", "#d9c2ff"),
    "higiene": ("#dcf7ea", "#aeeccf"), "salud": ("#dcecff", "#b3d4fb"),
}

# id -> (objeto, categoría, parámetros)
PRODUCTS = {
    "p1": ("bag", "alimentos", dict(color="#e8772e", icon="paw", brand="SuperDog", sub="Adultos · 10 kg")),
    "p2": ("ball", "juguetes", dict(color="#f06292", squeak=True)),
    "p3": ("collar", "accesorios", dict(color="#3f8fd9")),
    "p4": ("litter", "alimentos", dict(color="#7e57c2", brand="GatoClean", sub="8 L")),
    "p5": ("treats", "salud", dict(color="#26a69a", icon="tooth", brand="BiteCare", sub="Dental · Mini")),
    "p6": ("brush", "higiene", dict(color="#ef6c00")),
    "p7": ("scratcher", "juguetes", dict(color="#8e6bd1", kind="post")),
    "p8": ("rope", "juguetes", dict(color="#e53935")),
    "p9": ("tube", "salud", dict(color="#26a69a", icon="paw", brand="DogSmile")),
    "p10": ("bed", "accesorios", dict(color="#8d6ec9")),
    "p11": ("cans", "alimentos", dict(color="#ef6c8a", icon="fish", brand="CatBites")),
    "p12": ("plush", "juguetes", dict(color="#c98a4b", kind="bear")),
    "p13": ("harness", "accesorios", dict(color="#3f8fd9")),
    "p14": ("bottle", "higiene", dict(color="#26a69a", icon="flea", brand="FreshPaw", style="pump")),
    "p15": ("bottle", "salud", dict(color="#f59e0b", icon="heart", brand="VitalPet", style="jar")),
    "p16": ("bowl", "accesorios", dict(color="#5c6bc0", double=True)),
    "p17": ("house", "accesorios", dict(color="#d9778f", kind="dome")),
    "p18": ("bone", "juguetes", dict(color="#f9a03f", label="XXL")),
    "p19": ("wipes", "higiene", dict(color="#29b6f6", icon="drop", brand="CleanPaw")),
    "p20": ("bottle", "salud", dict(color="#5c6bc0", icon="bone", brand="JointCare", style="jar")),
    "p21": ("feeder", "accesorios", dict(color="#5c6bc0")),
    "p22": ("wand", "juguetes", dict(color="#8e6bd1")),
    "p23": ("treats", "alimentos", dict(color="#e8772e", icon="chicken", brand="BarkBites", sub="Pollo natural")),
    "p24": ("bottle", "salud", dict(color="#26a69a", icon="drop", brand="EarSafe", style="dropper")),
    "p25": ("glove", "higiene", dict(color="#ef6c00")),
    "p26": ("leash", "accesorios", dict(color="#e53935")),
    "p27": ("diapers", "higiene", dict(color="#29b6f6", brand="PuppyCare")),
    "p28": ("mouse", "juguetes", dict(color="#9e9e9e")),
    "p29": ("tube", "salud", dict(color="#ec6f9a", icon="fish", brand="FelineSmile")),
    "p30": ("wipes", "higiene", dict(color="#7cb342", icon="paw", brand="PetWipe")),
    "p31": ("plush", "juguetes", dict(color="#f5e6d3", kind="lamb")),
    "p32": ("bag", "alimentos", dict(color="#ec6f9a", icon="fish", brand="KittyBest", sub="Gatos · Premium")),
    "p33": ("leash", "accesorios", dict(color="#fb8c00", reflective=True)),
    "p34": ("bottle", "salud", dict(color="#66bb6a", icon="leaf", brand="CalmPet", style="dropper")),
    "p35": ("bottle", "higiene", dict(color="#43a047", icon="leaf", brand="GentlePets", style="pump")),
    "p36": ("pipette", "salud", dict(color="#26a69a", brand="FleaStop")),
    "p37": ("bowl", "accesorios", dict(color="#90a4ae", steel=True)),
    "p38": ("launcher", "juguetes", dict(color="#3f8fd9")),
    "p39": ("treats", "alimentos", dict(color="#29b6f6", icon="fish", brand="SeaBites", sub="Pescado · Gatos")),
    "p40": ("towel", "higiene", dict(color="#4fc3f7")),
    "p41": ("house", "accesorios", dict(color="#c68642", kind="wood")),
    "p42": ("leash", "accesorios", dict(color="#7e57c2", rubber=True)),
    "p43": ("treats", "alimentos", dict(color="#8d6e63", icon="chicken", brand="PuppyCrunch", sub="Cachorros")),
    "p44": ("scratcher", "juguetes", dict(color="#26a69a", kind="tower")),
    "p45": ("antibark", "salud", dict(color="#78909c")),
    "p46": ("diapers", "higiene", dict(color="#ec6f9a", brand="CleanCat")),
    "p47": ("ring", "juguetes", dict(color="#26c6da")),
    "p48": ("bottle", "salud", dict(color="#ffb300", icon="omega", brand="OmegaPet", style="jar")),
    "p49": ("carrier", "accesorios", dict(color="#5c6bc0")),
    "p50": ("cans", "alimentos", dict(color="#8d6e63", icon="bone", brand="SeniorBites")),
}


def render(pid):
    obj, category, params = PRODUCTS[pid]
    inner, outer = BG[category]
    body = OBJECTS[obj](params)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img">'
            f'<defs><radialGradient id="bg" cx="50%" cy="45%" r="75%"><stop offset="0" stop-color="{inner}"/><stop offset="1" stop-color="{outer}"/></radialGradient></defs>'
            f'<rect width="{W}" height="{H}" fill="url(#bg)"/>'
            f'<circle cx="86" cy="70" r="46" fill="rgba(255,255,255,0.35)"/><circle cx="530" cy="392" r="62" fill="rgba(255,255,255,0.28)"/>'
            f'{body}</svg>\n')


def render_placeholder():
    """Dibujo genérico para productos sin imagen (o con una imagen que no carga)."""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}" role="img">'
            '<defs><radialGradient id="bg" cx="50%" cy="45%" r="75%"><stop offset="0" stop-color="#f4f0ec"/><stop offset="1" stop-color="#e2d9cf"/></radialGradient></defs>'
            f'<rect width="{W}" height="{H}" fill="url(#bg)"/>'
            + paw(300, 215, 4.2, "#c9b8a6") + text(300, 372, "SIN IMAGEN", 22, "#b3a291", 800, spacing=3) + '</svg>' + chr(10))


def main():
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, "placeholder.svg"), "w", encoding="utf-8", newline=chr(10)) as f:
        f.write(render_placeholder())
    only = sys.argv[1:] or list(PRODUCTS)
    for pid in only:
        with open(os.path.join(OUT, pid + ".svg"), "w", encoding="utf-8", newline="\n") as f:
            f.write(render(pid))
    print(f"{len(only)} ilustraciones en {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
