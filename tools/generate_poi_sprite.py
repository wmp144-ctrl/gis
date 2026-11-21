#!/usr/bin/env python3
"""Render the offline POI sprite from the single category table.

Source of truth: ``app/src/main/assets/poi-icons.json``

    [{"key": "fuel", "codepoint": 58694, "tint": "#F0A020"}, ...]

Two consumers derive their icons from that one file and nothing else:

* the app, through ``MapSymbolIcons``, which draws the same plates at runtime as a
  fallback for styles that cannot load the sprite;
* this script, which packs them into ``app/src/main/assets/sprite/atlas.png`` and
  ``atlas.json`` — the atlas the style declares with
  ``"sprite": "asset://sprite/atlas"``.

So adding, recolouring or reordering a category means editing the JSON and running
this script; the runtime plates follow automatically, and
``MapLabelLayerTest.iconTableAndSpriteStayInSync`` fails if the sprite is stale.

Usage
-----
    python3 tools/generate_poi_sprite.py

Requirements: Pillow, plus the bundled icon font
``app/src/main/assets/fonts/MaterialIcons-Regular.ttf``.

Layout (mirrored by ``MapSymbolIcons`` so both renderings match): 64 px plates on a
68 px cell grid with 2 px of transparent padding, 8 cells per row, a 38 px glyph
centred on a white rounded plate with a hairline border.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
TABLE = ASSETS / "poi-icons.json"
FONT = ASSETS / "fonts" / "MaterialIcons-Regular.ttf"
SPRITE_DIR = ASSETS / "sprite"

SIZE = 64          # plate size in px
PAD = 2            # transparent gutter around each plate
CELL = SIZE + PAD * 2
COLS = 8
GLYPH_PX = 38
INSET = 3
RADIUS = 15
PLATE_ALPHA = 236
BORDER_ALPHA = 38
BORDER_WIDTH = 2


def parse_tint(value: str) -> tuple[int, int, int, int]:
    text = value.strip().lstrip("#")
    if len(text) != 6:
        raise ValueError(f"tint must be #RRGGBB, got {value!r}")
    return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16), 255)


def main() -> int:
    entries = json.loads(TABLE.read_text(encoding="utf-8"))
    keys = [entry["key"] for entry in entries]
    if len(keys) != len(set(keys)):
        raise SystemExit("poi-icons.json contains duplicate keys")

    font = ImageFont.truetype(str(FONT), GLYPH_PX)
    rows = (len(entries) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * CELL, rows * CELL), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)
    meta: dict[str, dict[str, int]] = {}

    for index, entry in enumerate(entries):
        key, codepoint = entry["key"], int(entry["codepoint"])
        origin_x = (index % COLS) * CELL + PAD
        origin_y = (index // COLS) * CELL + PAD

        draw.rounded_rectangle(
            [origin_x + INSET, origin_y + INSET, origin_x + SIZE - INSET, origin_y + SIZE - INSET],
            radius=RADIUS,
            fill=(255, 255, 255, PLATE_ALPHA),
            outline=(0, 0, 0, BORDER_ALPHA),
            width=BORDER_WIDTH,
        )
        draw.text(
            (origin_x + SIZE / 2, origin_y + SIZE / 2),
            chr(codepoint),
            font=font,
            fill=parse_tint(entry["tint"]),
            anchor="mm",
        )
        meta[f"atlas-{key}"] = {
            "x": origin_x,
            "y": origin_y,
            "width": SIZE,
            "height": SIZE,
            "pixelRatio": 1,
        }

    SPRITE_DIR.mkdir(parents=True, exist_ok=True)
    atlas.save(SPRITE_DIR / "atlas.png", optimize=True)
    (SPRITE_DIR / "atlas.json").write_text(
        json.dumps(meta, separators=(",", ":")), encoding="utf-8"
    )

    print(
        f"wrote {len(meta)} icons to {SPRITE_DIR.relative_to(ROOT)} "
        f"({atlas.size[0]}x{atlas.size[1]} px)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
