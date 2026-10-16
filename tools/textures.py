#!/usr/bin/env python3
"""Generates BloomCraft's item and block textures (16x16 pixel art) plus their models and item definitions.

    python3 tools/textures.py [--preview preview.png]

Everything is drawn from code so it can be tweaked and regenerated: shapes are filled, shaded from the top left,
outlined and given a highlight. Output goes to src/main/resources/assets/bloomcraft/.
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "bloomcraft")
TEX_ITEM = os.path.join(ROOT, "textures", "item")
TEX_BLOCK = os.path.join(ROOT, "textures", "block")
MODELS_ITEM = os.path.join(ROOT, "models", "item")
MODELS_BLOCK = os.path.join(ROOT, "models", "block")
ITEMS = os.path.join(ROOT, "items")
BLOCKSTATES = os.path.join(ROOT, "blockstates")

S = 16
PREVIEW = []  # (name, image)


# ----------------------------------------------------------------------------------------------------------------
# colour helpers
# ----------------------------------------------------------------------------------------------------------------

def rgb(hex_value):
    return ((hex_value >> 16) & 255, (hex_value >> 8) & 255, hex_value & 255, 255)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def shade(c, f):
    """f > 1 lightens towards white, f < 1 darkens towards black."""
    if f >= 1:
        return mix(c, (255, 255, 255, 255), min(1.0, f - 1))
    return mix((0, 0, 0, 255), c, max(0.0, f))


def outline_of(c):
    return shade(c, 0.32)


# ----------------------------------------------------------------------------------------------------------------
# shapes as masks
# ----------------------------------------------------------------------------------------------------------------

def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def mask_from(draw_fn):
    m = Image.new("L", (S, S), 0)
    draw_fn(ImageDraw.Draw(m))
    return m


def poly(points):
    return mask_from(lambda d: d.polygon(points, fill=255))


def ellipse(box):
    return mask_from(lambda d: d.ellipse(box, fill=255))


def rect(box):
    return mask_from(lambda d: d.rectangle(box, fill=255))


def union(*masks):
    out = Image.new("L", (S, S), 0)
    for m in masks:
        out.paste(255, (0, 0), m)
    return out


def subtract(a, b):
    out = a.copy()
    out.paste(0, (0, 0), b)
    return out


def pixels(mask):
    return [(x, y) for y in range(S) for x in range(S) if mask.getpixel((x, y)) > 127]


def bounds(mask):
    pts = pixels(mask)
    if not pts:
        return 0, 0, 0, 0
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)


def fill_shaded(img, mask, base, light=1.35, dark=0.62, highlight=True):
    """Fills the mask with a top-left lit gradient and a dark outline inside its edge."""
    x0, y0, x1, y1 = bounds(mask)
    span = max(1, (x1 - x0) + (y1 - y0))
    for (x, y) in pixels(mask):
        t = ((x - x0) + (y - y0)) / span  # 0 at the top left, 1 at the bottom right
        f = light + (dark - light) * t
        img.putpixel((x, y), shade(base, f))
    # dark rim on the bottom/right edges, light rim on the top/left
    for (x, y) in pixels(mask):
        right = x + 1 >= S or mask.getpixel((x + 1, y)) < 128
        below = y + 1 >= S or mask.getpixel((x, y + 1)) < 128
        left = x == 0 or mask.getpixel((x - 1, y)) < 128
        above = y == 0 or mask.getpixel((x, y - 1)) < 128
        if right or below:
            img.putpixel((x, y), shade(base, 0.5))
        elif (left or above) and highlight:
            img.putpixel((x, y), shade(base, 1.5))


def outline(img, mask, color):
    """Draws a 1-pixel outline just outside the mask."""
    outside = []
    for y in range(S):
        for x in range(S):
            if mask.getpixel((x, y)) > 127:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and mask.getpixel((nx, ny)) > 127:
                    outside.append((x, y))
                    break
    for p in outside:
        if img.getpixel(p)[3] == 0:
            img.putpixel(p, color)


def put(img, points, color):
    for p in points:
        if 0 <= p[0] < S and 0 <= p[1] < S:
            img.putpixel(p, color)


def glow(img, center, color, radius=2.5, strength=0.55):
    """A soft halo behind everything else (only on empty pixels)."""
    cx, cy = center
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < radius and img.getpixel((x, y))[3] == 0:
                a = int(255 * strength * (1 - d / radius))
                if a > 20:
                    img.putpixel((x, y), color[:3] + (a,))


# ----------------------------------------------------------------------------------------------------------------
# output
# ----------------------------------------------------------------------------------------------------------------

def save_item_texture(name, img):
    os.makedirs(TEX_ITEM, exist_ok=True)
    img.save(os.path.join(TEX_ITEM, name + ".png"))
    PREVIEW.append((name, img))


def save_block_texture(name, img):
    os.makedirs(TEX_BLOCK, exist_ok=True)
    img.save(os.path.join(TEX_BLOCK, name + ".png"))
    PREVIEW.append((name, img))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def item_model(name, texture=None, parent="minecraft:item/generated"):
    write_json(os.path.join(MODELS_ITEM, name + ".json"),
               {"parent": parent, "textures": {"layer0": "bloomcraft:item/" + (texture or name)}})
    return "bloomcraft:item/" + name


def plain(model):
    return {"type": "minecraft:model", "model": model}


def select_by_key(item, cases, fallback):
    """Item definition choosing a model by the item's custom model data string (BossItems.setModel)."""
    write_json(os.path.join(ITEMS, item + ".json"), {"model": {
        "type": "minecraft:select",
        "property": "minecraft:custom_model_data",
        "index": 0,
        "cases": [{"when": key, "model": plain(model)} for key, model in cases],
        "fallback": plain(fallback),
    }})


def single(item, model):
    write_json(os.path.join(ITEMS, item + ".json"), {"model": plain(model)})


# ----------------------------------------------------------------------------------------------------------------
# Boss Souls: a wisp of rank-coloured flame
# ----------------------------------------------------------------------------------------------------------------

RANKS = {
    "gray": 0xB8B8B8, "green": 0x55E655, "red": 0xFF5050,
    "purple": 0xC77DFF, "gold": 0xFFC125, "ascendant": 0xBFE6FF,
}


def soul(rank, hex_value):
    img = new()
    base = rgb(hex_value)
    body = union(ellipse((4, 6, 11, 14)), poly([(5, 8), (7, 2), (8, 5), (10, 1), (11, 8)]))
    glow(img, (8, 9.5), base, radius=7.5, strength=0.35)
    outline(img, body, outline_of(base))
    fill_shaded(img, body, base, light=1.25, dark=0.7, highlight=False)
    core = ellipse((6, 9, 9, 13))
    for p in pixels(core):
        img.putpixel(p, shade(base, 1.6))
    put(img, [(7, 10), (7, 11)], (255, 255, 255, 255))
    # eyes: a soul looks back
    put(img, [(6, 9), (9, 9)], shade(base, 0.25))
    put(img, [(3, 4), (12, 3), (13, 10)], shade(base, 1.7)[:3] + (200,))
    name = "boss_soul_" + rank
    save_item_texture(name, img)
    return item_model(name)


# ----------------------------------------------------------------------------------------------------------------
# Boss Totems: a small carved idol with glowing eyes
# ----------------------------------------------------------------------------------------------------------------

TOTEM_BODY = {"gray": 0x7A7A7A, "green": 0x3E7A4A, "red": 0x8A3A2A, "purple": 0x4A2A6A, "gold": 0xC89A2A}


def totem(rank, hex_value):
    img = new()
    body_c = rgb(TOTEM_BODY[rank])
    eye = rgb(hex_value)
    body = union(rect((5, 4, 10, 13)), rect((4, 6, 11, 11)), rect((6, 14, 9, 14)))
    horns = union(poly([(4, 4), (3, 1), (6, 3)]), poly([(11, 4), (12, 1), (9, 3)]))
    shape = union(body, horns)
    outline(img, shape, outline_of(body_c))
    fill_shaded(img, shape, body_c)
    # carved face
    put(img, [(6, 7), (9, 7)], eye)
    put(img, [(6, 6), (9, 6)], shade(eye, 1.5))
    put(img, [(7, 10), (8, 10)], shade(body_c, 0.35))
    put(img, [(6, 11), (9, 11)], shade(body_c, 0.45))
    # a gem on the chest
    put(img, [(7, 12), (8, 12)], eye)
    glow(img, (8, 7), eye, radius=6.5, strength=0.28)
    name = "boss_totem_" + rank
    save_item_texture(name, img)
    return item_model(name)


# ----------------------------------------------------------------------------------------------------------------
# Relics: an amulet with a gem whose shape and colour fit the relic
# ----------------------------------------------------------------------------------------------------------------

METALS = {"gold": 0xE8B83A, "silver": 0xC8D0D8, "bronze": 0xC07A3A, "iron": 0x9AA0A8}

GEMS = {
    "round": lambda: ellipse((4, 6, 11, 13)),
    "oval": lambda: ellipse((5, 5, 10, 14)),
    "heart": lambda: union(ellipse((4, 6, 8, 10)), ellipse((7, 6, 11, 10)), poly([(4, 9), (11, 9), (8, 14), (7, 14)])),
    "drop": lambda: union(ellipse((4, 8, 11, 14)), poly([(5, 10), (8, 4), (10, 10)])),
    "diamond": lambda: poly([(8, 4), (12, 9), (8, 14), (3, 9)]),
    "square": lambda: rect((5, 6, 10, 12)),
    "hexagon": lambda: poly([(6, 5), (10, 5), (12, 9), (10, 13), (6, 13), (4, 9)]),
    "star": lambda: poly([(8, 4), (9, 7), (12, 7), (10, 9), (11, 13), (8, 11), (5, 13), (6, 9), (4, 7), (7, 7)]),
    "crescent": lambda: subtract(ellipse((4, 5, 12, 13)), ellipse((7, 4, 14, 11))),
    "shield": lambda: poly([(4, 5), (12, 5), (12, 9), (8, 14), (4, 9)]),
    "crown": lambda: poly([(3, 7), (5, 10), (6, 6), (8, 10), (10, 6), (11, 10), (13, 7), (12, 13), (4, 13)]),
    "coin": lambda: ellipse((4, 6, 11, 13)),
}

# relic id -> (gem shape, gem colour, metal, detail)
RELICS = {
    "blood-pact": ("heart", 0xB0102A, "gold", None),
    "soul-harvest": ("drop", 0x4FE8FF, "silver", "soul"),
    "second-dawn": ("round", 0xFFB040, "gold", "rays"),
    "bramble-heart": ("heart", 0x3E9A3A, "bronze", "thorns"),
    "emberheart": ("drop", 0xFF7A20, "gold", "flame"),
    "frostbite": ("diamond", 0x9AE4FF, "silver", "frost"),
    "stormcaller": ("diamond", 0x6A4ADA, "silver", "bolt"),
    "windstep": ("oval", 0xC8FFF0, "silver", "swirl"),
    "hunters-mark": ("round", 0xE03030, "bronze", "cross"),
    "echo-strike": ("hexagon", 0x0FA0A0, "iron", "rings"),
    "owls-sight": ("round", 0xF0B020, "gold", "eye"),
    "lodestone": ("square", 0x8A8A9A, "iron", "magnet"),
    "aegis": ("shield", 0x4A78C8, "gold", None),
    "featherweight": ("oval", 0xF4F4F4, "silver", "feather"),
    "prospector": ("diamond", 0x30D070, "gold", None),
    "iron-will": ("square", 0xB8BEC6, "iron", None),
    "blood-mending": ("round", 0xD02040, "silver", "plus"),
    "skybound": ("oval", 0x7AC8FF, "gold", "swirl"),
    "ember-ward": ("hexagon", 0xF05A20, "iron", "flame"),
    "tidebound": ("drop", 0x1A5AC8, "silver", None),
    "ocean-grace": ("round", 0x40D8C8, "silver", "rings"),
    "villager-favor": ("diamond", 0x2AC85A, "bronze", None),
    "miners-favor": ("square", 0x3050C8, "iron", "sparkle"),
    "nightstalker": ("crescent", 0x7A4AC8, "silver", None),
    "sunblessed": ("star", 0xFFD030, "gold", None),
    "greed": ("coin", 0xF0C030, "gold", "coin"),
    "heavy-crown": ("crown", 0xE8B83A, "gold", "crown"),
}


def relic(rid, spec):
    shape, gem_hex, metal_name, detail = spec
    img = new()
    gem = rgb(gem_hex)
    metal = rgb(METALS[metal_name])
    gem_mask = GEMS[shape]()
    # chain loop at the top and the setting around the gem
    loop = subtract(ellipse((6, 0, 9, 4)), rect((7, 1, 8, 2)))
    setting = union(*[mask_from(lambda d, dx=dx, dy=dy: d.bitmap((dx, dy), gem_mask, fill=255))
                      for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))])
    setting = subtract(setting, gem_mask)
    fill_shaded(img, loop, metal, light=1.3, dark=0.7)
    outline(img, union(setting, gem_mask, loop), outline_of(metal))
    for p in pixels(setting):
        img.putpixel(p, shade(metal, 0.9))
    for p in pixels(setting):
        x, y = p
        if (x + y) % 3 == 0:
            img.putpixel(p, shade(metal, 1.3))
    fill_shaded(img, gem_mask, gem, light=1.45, dark=0.55)
    x0, y0, x1, y1 = bounds(gem_mask)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    light = shade(gem, 1.8)
    darkc = shade(gem, 0.3)
    if detail == "rays":
        put(img, [(cx, y0 - 2), (x0 - 2, cy), (x1 + 2, cy), (x0 - 1, y0 - 1), (x1 + 1, y0 - 1)], shade(gem, 1.5))
    elif detail == "thorns":
        put(img, [(x0, y0 + 1), (x1, y0 + 1), (cx, y1 - 1), (cx - 1, cy)], rgb(0x1E4A1A))
    elif detail == "flame":
        put(img, [(cx, cy - 1), (cx, cy), (cx - 1, cy + 1), (cx, cy + 1), (cx + 1, cy + 1)], rgb(0xFFF0A0))
    elif detail == "frost":
        put(img, [(cx, cy - 2), (cx, cy + 2), (cx - 2, cy), (cx + 2, cy), (cx, cy)], (255, 255, 255, 255))
    elif detail == "bolt":
        put(img, [(cx + 1, cy - 3), (cx, cy - 2), (cx, cy - 1), (cx - 1, cy), (cx, cy), (cx + 1, cy), (cx, cy + 1),
                  (cx - 1, cy + 2)], rgb(0xFFF060))
    elif detail == "swirl":
        put(img, [(cx - 1, cy - 1), (cx, cy - 2), (cx + 1, cy - 1), (cx + 1, cy), (cx, cy + 1), (cx - 1, cy + 1)], light)
    elif detail == "cross":
        put(img, [(cx, cy - 2), (cx, cy - 1), (cx, cy + 1), (cx, cy + 2), (cx - 2, cy), (cx - 1, cy), (cx + 1, cy),
                  (cx + 2, cy)], rgb(0x2A0A0A))
    elif detail == "rings":
        ring = subtract(ellipse((cx - 2, cy - 2, cx + 2, cy + 2)), ellipse((cx - 1, cy - 1, cx + 1, cy + 1)))
        put(img, pixels(ring), light)
    elif detail == "eye":
        put(img, [(cx, cy), (cx + 1, cy), (cx, cy + 1), (cx + 1, cy + 1)], rgb(0x101010))
        put(img, [(cx, cy)], (255, 255, 255, 255))
    elif detail == "magnet":
        put(img, [(x0 + 1, y0 + 1), (x0 + 1, y0 + 2), (x1 - 1, y0 + 1), (x1 - 1, y0 + 2)], rgb(0xD03030))
        put(img, [(x0 + 1, y1 - 1), (x1 - 1, y1 - 1)], rgb(0x3050D0))
    elif detail == "feather":
        put(img, [(cx + 1, y0 + 1), (cx, cy - 1), (cx, cy), (cx - 1, cy + 1), (cx - 1, y1 - 1)], rgb(0xB0B0B0))
    elif detail == "plus":
        put(img, [(cx, cy - 1), (cx, cy), (cx, cy + 1), (cx - 1, cy), (cx + 1, cy)], (255, 255, 255, 255))
    elif detail == "sparkle":
        put(img, [(x0 + 1, y0 + 1), (x1 - 1, cy), (cx - 1, y1 - 1)], rgb(0xFFE070))
    elif detail == "coin":
        put(img, [(cx, cy - 1), (cx, cy), (cx, cy + 1), (cx - 1, cy - 1), (cx + 1, cy + 1)], shade(gem, 0.5))
    elif detail == "crown":
        put(img, [(cx, y1 - 2), (cx - 3, y1 - 2), (cx + 3, y1 - 2)], rgb(0xD02040))
    elif detail == "soul":
        put(img, [(cx - 1, cy), (cx + 1, cy)], darkc)
    # the gem's sparkle
    put(img, [(x0 + 1, y0 + 1)], light)
    name = "relic_" + rid.replace("-", "_")
    save_item_texture(name, img)
    return item_model(name)


def relic_fallback():
    return relic("unknown", ("round", 0xE08CFF, "silver", None))


# ----------------------------------------------------------------------------------------------------------------
# Empowerment Runes: a stone tablet with a glowing glyph
# ----------------------------------------------------------------------------------------------------------------

GLYPHS = {
    "loot": (0xF0C030, [(6, 6), (7, 5), (8, 5), (9, 6), (6, 7), (9, 7), (6, 8), (7, 9), (8, 9), (9, 8), (7, 7), (8, 7)]),
    "fortune": (0x40E0A0, [(7, 4), (8, 5), (9, 6), (8, 7), (7, 8), (6, 7), (5, 6), (6, 5), (7, 6), (7, 10), (7, 11)]),
    "reach": (0x80C0FF, [(5, 10), (6, 9), (7, 8), (8, 7), (9, 6), (10, 5), (9, 5), (10, 6), (8, 5), (10, 7)]),
    "attack-damage": (0xFF5050, [(10, 4), (9, 5), (8, 6), (7, 7), (6, 8), (5, 9), (6, 10), (4, 9), (5, 11), (9, 4)]),
    "attack-speed": (0xFFE060, [(9, 4), (8, 5), (7, 6), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10), (8, 6)]),
    "max-health": (0xFF4070, [(5, 6), (6, 5), (7, 6), (8, 6), (9, 5), (10, 6), (10, 7), (9, 8), (8, 9), (7, 9),
                              (6, 8), (5, 7), (7, 10), (8, 10), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8)]),
    "armor": (0x80B0FF, [(5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (5, 6), (10, 6), (5, 7), (10, 7), (6, 8),
                         (9, 8), (7, 9), (8, 9), (7, 10), (8, 10)]),
    "armor-toughness": (0xB080FF, [(5, 5), (10, 5), (5, 6), (6, 6), (9, 6), (10, 6), (6, 7), (7, 7), (8, 7), (9, 7),
                                   (7, 8), (8, 8), (7, 9), (8, 9)]),
    "knockback-resistance": (0xC0C0C0, [(5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (6, 8), (7, 8), (8, 8),
                                        (9, 8), (7, 7), (8, 7), (7, 6), (8, 6), (7, 5), (8, 5)]),
    "movement-speed": (0x60F0F0, [(5, 6), (6, 6), (7, 6), (9, 6), (10, 7), (9, 8), (5, 8), (6, 8), (7, 8),
                                  (4, 10), (5, 10), (6, 10), (8, 10)]),
}


def rune(stat, spec):
    color_hex, glyph = spec
    img = new()
    stone = rgb(0x5A6070)
    tablet = union(rect((4, 2, 11, 13)), rect((3, 3, 12, 12)))
    outline(img, tablet, rgb(0x1E2028))
    fill_shaded(img, tablet, stone, light=1.25, dark=0.6)
    # chipped corners and cracks
    put(img, [(4, 12), (11, 3)], shade(stone, 0.45))
    color = rgb(color_hex)
    glow_layer = new()
    for (x, y) in glyph:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y + dy + 1)
            if 0 <= p[0] < S and 0 <= p[1] < S:
                glow_layer.putpixel(p, color[:3] + (90,))
    img.alpha_composite(glow_layer)
    put(img, [(x, y + 1) for (x, y) in glyph], shade(color, 1.25))
    name = "rune_" + stat.replace("-", "_")
    save_item_texture(name, img)
    return item_model(name)


# ----------------------------------------------------------------------------------------------------------------
# single items
# ----------------------------------------------------------------------------------------------------------------

def catalyst():
    img = new()
    gold = rgb(METALS["gold"])
    orb_c = rgb(0xE060FF)
    ring = subtract(ellipse((2, 4, 13, 13)), ellipse((4, 6, 11, 11)))
    orb = ellipse((5, 4, 10, 10))
    glow(img, (7.5, 7.5), orb_c, radius=8, strength=0.3)
    outline(img, union(ring, orb), outline_of(gold))
    fill_shaded(img, ring, gold)
    fill_shaded(img, orb, orb_c, light=1.6, dark=0.6)
    put(img, [(6, 5), (6, 6)], (255, 255, 255, 255))
    put(img, [(1, 2), (14, 3), (13, 13), (2, 14)], rgb(0xFFE0FF))
    save_item_texture("relic_catalyst", img)
    return item_model("relic_catalyst")


def challenge():
    img = new()
    paper = rgb(0xE8D4A8)
    scroll = union(rect((3, 3, 12, 12)), rect((2, 2, 13, 3)), rect((2, 12, 13, 13)))
    outline(img, scroll, rgb(0x5A4020))
    fill_shaded(img, scroll, paper, light=1.15, dark=0.75)
    put(img, [(x, 2) for x in range(2, 14)] + [(x, 13) for x in range(2, 14)], rgb(0xB89A68))
    # writing
    for y in (5, 7, 9):
        put(img, [(x, y) for x in range(4, 11 if y != 9 else 8)], rgb(0x6A4A30))
    # wax seal with a skull mark
    seal = ellipse((8, 8, 13, 13))
    outline(img, seal, rgb(0x3A0808))
    fill_shaded(img, seal, rgb(0xB01818), light=1.3, dark=0.6)
    put(img, [(10, 10), (11, 10), (10, 11), (11, 11)], rgb(0x400808))
    put(img, [(10, 10), (11, 10)], rgb(0xF0D0D0))
    save_item_texture("nemesis_challenge", img)
    return item_model("nemesis_challenge")


def statue():
    img = new()
    stone = rgb(0x9A9AA4)
    plinth = union(rect((3, 12, 12, 14)), rect((4, 11, 11, 11)))
    figure = union(ellipse((6, 2, 9, 5)), rect((6, 6, 9, 10)), rect((5, 6, 10, 8)))
    outline(img, union(plinth, figure), rgb(0x2A2A30))
    fill_shaded(img, plinth, rgb(0x6A6A74))
    fill_shaded(img, figure, stone)
    put(img, [(6, 3), (8, 3)], rgb(0xFF4040))
    save_item_texture("nemesis_statue", img)
    return item_model("nemesis_statue")


def compass_frames():
    """32 frames like the vanilla compass: frame n's needle points (n - 16) * 11.25 degrees clockwise from up."""
    models = []
    for n in range(32):
        img = new()
        rim = rgb(0x6A1A2A)
        face = rgb(0xEDE3CC)
        body = ellipse((1, 1, 14, 14))
        inner = ellipse((3, 3, 12, 12))
        outline(img, body, rgb(0x2A0810))
        fill_shaded(img, body, rim, light=1.3, dark=0.6)
        fill_shaded(img, inner, face, light=1.08, dark=0.85, highlight=False)
        # tick marks
        put(img, [(7, 3), (8, 3), (3, 7), (3, 8), (12, 7), (12, 8), (7, 12), (8, 12)], rgb(0x8A7A60))
        angle = math.radians(((n - 16) % 32) * 11.25)
        cx, cy = 7.5, 7.5
        dx, dy = math.sin(angle), -math.cos(angle)
        d = ImageDraw.Draw(img)
        d.line([(cx, cy), (cx - dx * 3.2, cy - dy * 3.2)], fill=rgb(0x3A3A44), width=1)
        d.line([(cx, cy), (cx + dx * 4.6, cy + dy * 4.6)], fill=rgb(0xE01818), width=1)
        tip = (int(math.floor(cx + dx * 4.6)), int(math.floor(cy + dy * 4.6)))
        put(img, [tip], rgb(0xFF6060))
        put(img, [(int(cx), int(cy))], rgb(0x2A1A1A))
        name = "hunters_compass_%02d" % n
        img.save(os.path.join(TEX_ITEM, name + ".png"))
        if n in (0, 8, 16, 24):
            PREVIEW.append((name, img))
        models.append(item_model(name))
    return models


def compass_definition(models):
    entries = []
    for k in range(32):
        frame = (16 + k) % 32
        entries.append({"threshold": 0.0 if k == 0 else k - 0.5, "model": plain(models[frame])})
    entries.append({"threshold": 31.5, "model": plain(models[16])})
    spinning = {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "none",
                "scale": 32.0, "entries": entries}
    write_json(os.path.join(ITEMS, "hunters_compass.json"), {"model": {
        "type": "minecraft:condition",
        "property": "minecraft:has_component",
        "component": "minecraft:lodestone_tracker",
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone",
                    "scale": 32.0, "entries": entries},
        "on_false": spinning,
    }})


# ----------------------------------------------------------------------------------------------------------------
# the waystone block
# ----------------------------------------------------------------------------------------------------------------

def waystone():
    side = new()
    stone = rgb(0x4A5060)
    for y in range(S):
        for x in range(S):
            n = ((x * 7 + y * 13) ^ (x * y)) % 7
            side.putpixel((x, y), shade(stone, 0.85 + n * 0.05))
    # bricks
    for y in (0, 5, 10, 15):
        put(side, [(x, y) for x in range(S)], shade(stone, 0.55))
    for y0, xs in ((0, (4, 12)), (5, (0, 8)), (10, (4, 12))):
        for x in xs:
            put(side, [(x, y) for y in range(y0, y0 + 5)], shade(stone, 0.55))
    # glowing rune channel down the middle
    rune_c = rgb(0x40F0E0)
    channel = [(7, 2), (8, 3), (7, 4), (8, 6), (7, 7), (8, 8), (7, 9), (8, 11), (7, 12), (8, 13)]
    put(side, channel, rune_c)
    put(side, [(x + 1 if x == 7 else x - 1, y) for (x, y) in channel], shade(rune_c, 0.6))
    save_block_texture("waystone_side", side)

    top = new()
    for y in range(S):
        for x in range(S):
            n = ((x * 5 + y * 11) ^ (x + y)) % 6
            top.putpixel((x, y), shade(stone, 0.85 + n * 0.05))
    border = subtract(rect((0, 0, 15, 15)), rect((1, 1, 14, 14)))
    put(top, pixels(border), shade(stone, 0.5))
    ring = subtract(ellipse((3, 3, 12, 12)), ellipse((5, 5, 10, 10)))
    put(top, pixels(ring), shade(rune_c, 0.55))
    gem = poly([(8, 5), (11, 8), (8, 11), (5, 8)])
    fill_shaded(top, gem, rune_c, light=1.5, dark=0.7)
    save_block_texture("waystone_top", top)

    write_json(os.path.join(MODELS_BLOCK, "waystone.json"), {
        "parent": "minecraft:block/cube_column",
        "textures": {"end": "bloomcraft:block/waystone_top", "side": "bloomcraft:block/waystone_side"},
    })
    write_json(os.path.join(BLOCKSTATES, "waystone.json"), {"variants": {"": {"model": "bloomcraft:block/waystone"}}})
    single("waystone", "bloomcraft:block/waystone")


# ----------------------------------------------------------------------------------------------------------------

# ----------------------------------------------------------------------------------------------------------------
# Pets: the Pet Toy (a squeaky rubber bone), the Fetch Stick (a stick with a bow), the plain Pet Bloom
# ----------------------------------------------------------------------------------------------------------------

def pet_toy():
    img = new()
    pink = rgb(0xF58AC0)
    bar = poly([(4, 10), (10, 4), (11, 5), (5, 11)])
    knobs = union(ellipse((1, 9, 4, 12)), ellipse((4, 12, 7, 15)), ellipse((9, 1, 12, 4)), ellipse((12, 4, 15, 7)))
    bone = union(bar, knobs)
    outline(img, bone, rgb(0x7A2050))
    fill_shaded(img, bone, pink, light=1.25, dark=0.7)
    # a little heart in the middle
    put(img, [(10, 2), (13, 5)], (255, 230, 245, 255))
    save_item_texture("pet_toy", img)
    return item_model("pet_toy")


def fetch_stick():
    img = new()
    wood = rgb(0x8A5A2A)
    stick = poly([(2, 13), (12, 3), (13, 4), (3, 14)])
    twig = poly([(8, 7), (9, 4), (10, 4), (9, 7)])
    shape = union(stick, twig)
    outline(img, shape, rgb(0x3A2410))
    fill_shaded(img, shape, wood, light=1.3, dark=0.65)
    # a red bow near the top
    bow = union(poly([(10, 3), (13, 1), (14, 3)]), poly([(12, 5), (15, 5), (13, 7)]), ellipse((11, 3, 13, 5)))
    outline(img, bow, rgb(0x500808))
    fill_shaded(img, bow, rgb(0xE02828), light=1.3, dark=0.7)
    save_item_texture("fetch_stick", img)
    return item_model("fetch_stick", parent="minecraft:item/handheld")


def pet_bloom():
    img = new()
    petal = rgb(0xF5A9D0)
    petals = union(*[ellipse((int(7.5 + math.cos(math.radians(a)) * 3.6) - 2, int(6.5 + math.sin(math.radians(a)) * 3.6) - 2,
                               int(7.5 + math.cos(math.radians(a)) * 3.6) + 2, int(6.5 + math.sin(math.radians(a)) * 3.6) + 2))
                     for a in range(0, 360, 72)])
    stem = rect((7, 10, 8, 15))
    leaf = poly([(8, 13), (11, 11), (12, 12), (9, 14)])
    glow(img, (7.5, 6.5), rgb(0xFFD0EA), radius=7.5, strength=0.35)
    outline(img, union(petals, stem, leaf), rgb(0x6A2048))
    fill_shaded(img, union(stem, leaf), rgb(0x4A9A3A))
    fill_shaded(img, petals, petal, light=1.25, dark=0.75)
    heart = ellipse((6, 5, 9, 8))
    fill_shaded(img, heart, rgb(0xFFD84A), light=1.3, dark=0.8, highlight=False)
    save_item_texture("pet_bloom", img)
    return item_model("pet_bloom")


def icon():
    """The mod icon (64x64): a bloom with a skull at its heart."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for i in range(6):
        a = math.radians(i * 60)
        cx, cy = 32 + math.cos(a) * 15, 32 + math.sin(a) * 15
        d.ellipse((cx - 11, cy - 11, cx + 11, cy + 11), fill=(232, 120, 170, 255), outline=(120, 30, 70, 255), width=2)
    d.ellipse((18, 18, 46, 46), fill=(245, 236, 210, 255), outline=(90, 70, 50, 255), width=2)
    d.ellipse((23, 27, 30, 34), fill=(40, 20, 20, 255))
    d.ellipse((34, 27, 41, 34), fill=(40, 20, 20, 255))
    d.polygon([(32, 35), (29, 40), (35, 40)], fill=(40, 20, 20, 255))
    for x in (26, 30, 34, 38):
        d.rectangle((x, 41, x + 2, 44), fill=(40, 20, 20, 255))
    img.save(os.path.join(ROOT, "icon.png"))


def main():
    os.makedirs(TEX_ITEM, exist_ok=True)
    souls = [(rank, soul(rank, c)) for rank, c in RANKS.items()]
    select_by_key("boss_soul", souls, souls[0][1])
    totems = [(rank, totem(rank, RANKS[rank])) for rank in TOTEM_BODY]
    select_by_key("boss_totem", totems, totems[1][1])
    relics = [(rid, relic(rid, spec)) for rid, spec in RELICS.items()]
    select_by_key("relic", relics, relic_fallback())
    runes = [(stat, rune(stat, spec)) for stat, spec in GLYPHS.items()]
    select_by_key("empowerment_rune", runes, runes[3][1])
    single("relic_catalyst", catalyst())
    single("nemesis_challenge", challenge())
    single("nemesis_statue", statue())
    compass_definition(compass_frames())
    waystone()
    single("pet_toy", pet_toy())
    single("fetch_stick", fetch_stick())
    single("pet_bloom", pet_bloom())
    icon()
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        cols = 12
        scale = 6
        rows = (len(PREVIEW) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * (S * scale + 4), rows * (S * scale + 4)), (60, 60, 70, 255))
        for i, (_, im) in enumerate(PREVIEW):
            x = (i % cols) * (S * scale + 4) + 2
            y = (i // cols) * (S * scale + 4) + 2
            sheet.alpha_composite(im.resize((S * scale, S * scale), Image.NEAREST), (x, y))
        sheet.save(out)
    print(f"{len(PREVIEW)} textures written")


if __name__ == "__main__":
    main()
