#!/usr/bin/env python3
"""
Generates the two GUI sheets MCA: Quests ships.

The mod had no textures at all until this script: every panel, bar and scrollbar was a solid
``fill()`` rectangle. The art here is generated rather than painted so that it is reviewable as
source, regenerable, and pixel-identical on every machine -- a diff of this file says what changed,
which a diff of a PNG cannot.

Run it from the repository root::

    python tools/gen_gui_textures.py

It writes ``src/main/resources/assets/mcaquests/textures/gui/{panel,icons}.png`` and the machine
readable ``tools/gui_layout.json``. The PNGs are committed and are what ships; the manifest stays here
rather than beside them because the game never reads it, and a jar should not carry ten kilobytes of
something only a test looks at. The build never runs this script. ``GuiTexturesExistTest`` compares
the committed manifest against the sprite constants in ``GuiTextures.java``, so the atlas and the code
that reads it cannot drift apart.

Both sheets are 256x256 on purpose: ``GuiGraphics.blitRepeating``'s common overload assumes a
256x256 texture, and the middle band of every nine-sliced sprite below is a flat colour so that
tiling is invisible at any size.

Design register: vanilla's own container grey -- the #C6C6C6 face, #FFFFFF/#555555 bevel and
#8B8B8B inset that every chest, furnace and inventory in the game is drawn from. The sheets were
first cut in dark oak and leather, on the argument that every text colour the mod used was light and
a grey panel would have made all of it unreadable. That argument was backwards: it kept the palette
and retinted the game. The text colours are now derived from the panel instead (see Palette.java),
which is the only way the interface reads as part of Minecraft rather than beside it.

One surface stays dark: ``hud``. It is a translucent plate drawn over the live world, where a light
grey slab is unreadable against snow and sky, so it keeps its dark face and light text.
"""

import json
import pathlib

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "mcaquests" / "textures" / "gui"
MANIFEST = pathlib.Path(__file__).resolve().parent / "gui_layout.json"

SHEET = 256

# --- palette ----------------------------------------------------------------------------------
# Every colour the sheets use, named once. Palette.java mirrors the text colours; these are the
# surfaces those colours sit on.
CLEAR = (0, 0, 0, 0)
BORDER = (0x00, 0x00, 0x00, 0xFF)

# The window frame and every raised surface on it: vanilla's container face and bevel, exactly.
FRAME_HI = (0xFF, 0xFF, 0xFF, 0xFF)
FRAME_FACE = (0xC6, 0xC6, 0xC6, 0xFF)
FRAME_LO = (0x55, 0x55, 0x55, 0xFF)

# The content well is *inset*, so its bevel runs the other way: dark at the top-left, white at the
# bottom-right. Same grey vanilla uses behind a row of slots.
# `bevel(inset=True)` draws `lo` along the top-left and `hi` along the bottom-right, which is what
# an inset surface wants: the shadow above, the light below. So LO is the dark one here.
WELL_HI = (0xFF, 0xFF, 0xFF, 0xFF)
WELL_FACE = (0x8B, 0x8B, 0x8B, 0xFF)
WELL_LO = (0x37, 0x37, 0x37, 0xFF)

# Cards sit raised on the well, so they take the frame's face and read as panels of their own.
CARD_FACE = (0xC6, 0xC6, 0xC6, 0xFF)
CARD_HI = (0xFF, 0xFF, 0xFF, 0xFF)
CARD_LO = (0x55, 0x55, 0x55, 0xFF)
# Ready and hovered differ in *edge*, never in face, so a card never changes weight under the cursor.
CARD_READY = (0x3F, 0x8A, 0x3F, 0xFF)
CARD_HOVER = (0x2C, 0x5C, 0x8A, 0xFF)

# Buttons stay *dark*, which is the vanilla answer and not a contradiction of the grey panel: a
# vanilla container is a light grey slab with vanilla's own dark widgets sitting on it, and its
# button labels are white with a shadow. Making the buttons light too would have been more grey and
# less like Minecraft, and would have left no contrast between a control and the card behind it.
BTN_HI = (0x9E, 0x9E, 0x9E, 0xFF)
BTN_FACE = (0x6E, 0x6E, 0x6E, 0xFF)
BTN_LO = (0x38, 0x38, 0x38, 0xFF)
BTN_HOVER_FACE = (0x8A, 0x8A, 0x8A, 0xFF)
BTN_OFF_FACE = (0x4E, 0x4E, 0x4E, 0xFF)
BTN_OFF_HI = (0x6A, 0x6A, 0x6A, 0xFF)

# A tab that is not selected, and the pill behind a difficulty label: one step down from the panel
# face, still light, so both read as recessed rather than as another widget.
BADGE_FACE = (0xA8, 0xA8, 0xA8, 0xFF)

SLOT_FACE = (0x8B, 0x8B, 0x8B, 0xFF)
BAR_TRACK = (0x54, 0x54, 0x54, 0xFF)
# Bar fills are darkened from their pre-1.5.0 values: a #5CC85C green that read well on near-black
# is barely a shade away from #C6C6C6, and a progress bar you cannot see is not a progress bar.
BAR_GREEN = (0x3F, 0x8A, 0x3F, 0xFF)
BAR_BLUE = (0x3D, 0x6E, 0x9E, 0xFF)
BAR_AMBER = (0xB9, 0x8A, 0x18, 0xFF)

SCROLL_TRACK = (0x54, 0x54, 0x54, 0xFF)
SCROLL_THUMB = (0xC6, 0xC6, 0xC6, 0xFF)
SCROLL_THUMB_HI = (0xDB, 0xDB, 0xDB, 0xFF)
SCROLL_THUMB_ON = (0xF0, 0xF0, 0xF0, 0xFF)

# The one dark surface. See the module note: it is a plate over the world, not a panel in a window.
HUD_FACE = (0x18, 0x18, 0x18, 0xD8)
HUD_HI = (0x4A, 0x4A, 0x4A, 0xE8)
HUD_LO = (0x08, 0x08, 0x08, 0xE8)


def lighten(colour, amount):
    return tuple(min(255, v + amount) for v in colour[:3]) + (colour[3],)


def darken(colour, amount):
    return tuple(max(0, v - amount) for v in colour[:3]) + (colour[3],)


# --- sprite atlas -----------------------------------------------------------------------------
# name -> (u, v, w, h, slice_x, slice_y). The two insets are independent because the sprites are:
# a divider is 48x3 and only ever tiles sideways, a scrollbar track is 6x32 and only ever tiles down,
# and a single figure could describe neither without eating the whole sprite. Each inset must leave a
# middle band to tile -- 2 * inset <= the size on that axis -- which GuiTexturesExistTest checks.
# Nothing here may overlap; assert_no_overlap proves that.
PANEL = {
    "window":                  (0, 0, 48, 48, 8, 8),
    "well":                    (48, 0, 48, 48, 6, 6),
    "card":                    (96, 0, 48, 48, 5, 5),
    "card_ready":              (144, 0, 48, 48, 5, 5),
    "card_hover":              (192, 0, 48, 48, 5, 5),

    "hud":                     (0, 48, 48, 48, 6, 6),
    "band_header":             (48, 48, 48, 24, 6, 6),
    "band_footer":             (48, 72, 48, 24, 6, 6),
    "tab_selected":            (96, 48, 32, 24, 6, 6),
    "tab_unselected":          (128, 48, 32, 24, 6, 6),
    "tab_hover":               (160, 48, 32, 24, 6, 6),
    "badge":                   (192, 48, 32, 16, 5, 5),
    "slot":                    (224, 48, 18, 18, 0, 0),
    "divider":                 (192, 66, 48, 3, 2, 1),

    "button":                  (0, 96, 80, 20, 6, 6),
    "button_hover":            (80, 96, 80, 20, 6, 6),
    "button_disabled":         (160, 96, 80, 20, 6, 6),
    "button_compact":          (0, 116, 64, 12, 4, 4),
    "button_compact_hover":    (64, 116, 64, 12, 4, 4),
    "button_compact_disabled": (128, 116, 64, 12, 4, 4),

    "bar_track":               (0, 128, 64, 6, 3, 2),
    "bar_green":               (0, 134, 64, 6, 3, 2),
    "bar_blue":                (0, 140, 64, 6, 3, 2),
    "bar_amber":               (0, 146, 64, 6, 3, 2),

    "scroll_track":            (64, 128, 6, 32, 2, 3),
    "scroll_thumb":            (70, 128, 6, 32, 2, 3),
    "scroll_thumb_hover":      (76, 128, 6, 32, 2, 3),
    "scroll_thumb_drag":       (82, 128, 6, 32, 2, 3),
}


def assert_no_overlap(atlas, label):
    """A sprite that quietly overlaps its neighbour is a bug you only ever see in game."""
    claimed = {}
    for name, (u, v, w, h, _sx, _sy) in atlas.items():
        if u < 0 or v < 0 or u + w > SHEET or v + h > SHEET:
            raise SystemExit(f"{label}: sprite '{name}' falls outside the {SHEET}x{SHEET} sheet")
        for y in range(v, v + h):
            for x in range(u, u + w):
                other = claimed.get((x, y))
                if other is not None:
                    raise SystemExit(f"{label}: sprite '{name}' overlaps '{other}' at ({x},{y})")
                claimed[(x, y)] = name


# --- drawing helpers --------------------------------------------------------------------------

def bevel(d, box, face, hi, lo, border=None, inset=False):
    """A vanilla-style bevelled box. `box` is inclusive on both corners."""
    x0, y0, x1, y1 = box
    if border is not None:
        d.rectangle([x0, y0, x1, y1], fill=border)
        x0, y0, x1, y1 = x0 + 1, y0 + 1, x1 - 1, y1 - 1
    d.rectangle([x0, y0, x1, y1], fill=face)
    top_left, bottom_right = (lo, hi) if inset else (hi, lo)
    d.line([(x0, y0), (x1, y0)], fill=top_left)
    d.line([(x0, y0), (x0, y1)], fill=top_left)
    d.line([(x0, y1), (x1, y1)], fill=bottom_right)
    d.line([(x1, y0), (x1, y1)], fill=bottom_right)


def box_of(atlas, name):
    u, v, w, h, _sx, _sy = atlas[name]
    return u, v, u + w - 1, v + h - 1


# --- panel.png --------------------------------------------------------------------------------

def build_panel():
    img = Image.new("RGBA", (SHEET, SHEET), CLEAR)
    d = ImageDraw.Draw(img)

    # The window frame: black outline, container-grey face, and a darker inner rule 4px in so the
    # frame reads as a frame rather than a slab. The rule stays inside the 8px slice, so it survives
    # nine-slicing at every window size.
    x0, y0, x1, y1 = box_of(PANEL, "window")
    bevel(d, (x0, y0, x1, y1), FRAME_FACE, FRAME_HI, FRAME_LO, border=BORDER)
    d.rectangle([x0 + 4, y0 + 4, x1 - 4, y1 - 4], outline=FRAME_LO)
    d.rectangle([x0 + 5, y0 + 5, x1 - 5, y1 - 5], fill=FRAME_FACE)

    # The content well: inset and a shade darker than the frame, exactly as vanilla insets the area
    # behind a row of slots, so the cards floating on it read as raised.
    bevel(d, box_of(PANEL, "well"), WELL_FACE, WELL_HI, WELL_LO, border=BORDER, inset=True)

    # Card frames. One face, three border colours: resting, ready to turn in, hovered.
    for name, edge in (("card", CARD_HI), ("card_ready", CARD_READY), ("card_hover", CARD_HOVER)):
        cx0, cy0, cx1, cy1 = box_of(PANEL, name)
        d.rectangle([cx0, cy0, cx1, cy1], fill=CARD_FACE, outline=edge)
        d.line([(cx0 + 1, cy1 - 1), (cx1 - 1, cy1 - 1)], fill=CARD_LO)
        d.line([(cx1 - 1, cy0 + 1), (cx1 - 1, cy1 - 1)], fill=CARD_LO)

    # HUD tracker background -- translucent, because it sits over the world and must not become a
    # wall. The alpha is baked into the texture, so the overlay needs no colour maths.
    bevel(d, box_of(PANEL, "hud"), HUD_FACE, HUD_HI, HUD_LO, inset=True)

    # Header and footer bands tile horizontally only. The header carries a bright rule along its
    # bottom edge to separate it from the content; the footer carries one along its top.
    hx0, hy0, hx1, hy1 = box_of(PANEL, "band_header")
    d.rectangle([hx0, hy0, hx1, hy1], fill=FRAME_FACE)
    d.line([(hx0, hy0), (hx1, hy0)], fill=FRAME_HI)
    d.line([(hx0, hy1 - 1), (hx1, hy1 - 1)], fill=FRAME_HI)
    d.line([(hx0, hy1), (hx1, hy1)], fill=FRAME_LO)

    fx0, fy0, fx1, fy1 = box_of(PANEL, "band_footer")
    d.rectangle([fx0, fy0, fx1, fy1], fill=FRAME_FACE)
    d.line([(fx0, fy0), (fx1, fy0)], fill=FRAME_LO)
    d.line([(fx0, fy0 + 1), (fx1, fy0 + 1)], fill=FRAME_HI)
    d.line([(fx0, fy1), (fx1, fy1)], fill=FRAME_LO)

    # Tabs. The selected tab has no bottom edge, so it reads as continuous with the panel below it.
    for name, face, edge in (("tab_selected", FRAME_FACE, FRAME_HI),
                             ("tab_unselected", BADGE_FACE, FRAME_HI),
                             ("tab_hover", BTN_HOVER_FACE, FRAME_HI)):
        tx0, ty0, tx1, ty1 = box_of(PANEL, name)
        d.rectangle([tx0, ty0, tx1, ty1], fill=BORDER)
        d.rectangle([tx0 + 1, ty0 + 1, tx1 - 1, ty1], fill=face)
        d.line([(tx0 + 1, ty0 + 1), (tx1 - 1, ty0 + 1)], fill=edge)
        d.line([(tx0 + 1, ty0 + 1), (tx0 + 1, ty1)], fill=edge)
        d.line([(tx1 - 1, ty0 + 1), (tx1 - 1, ty1)], fill=FRAME_LO)
        if name == "tab_selected":
            d.line([(tx0 + 1, ty1), (tx1 - 1, ty1)], fill=FRAME_FACE)

    # A small pill behind a difficulty or category label: inset, one step darker than the card it
    # sits on, so the label reads as a tag rather than as more body text.
    bevel(d, box_of(PANEL, "badge"), BADGE_FACE, WELL_HI, WELL_LO, border=BORDER, inset=True)

    # Item slot, 18x18 and inset like every vanilla slot, so a reward item sits in it as expected.
    bevel(d, box_of(PANEL, "slot"), SLOT_FACE, WELL_HI, WELL_LO, inset=True)

    # Horizontal rule between sections.
    dx0, dy0, dx1, dy1 = box_of(PANEL, "divider")
    d.line([(dx0, dy0), (dx1, dy0)], fill=CARD_LO)
    d.line([(dx0, dy0 + 1), (dx1, dy0 + 1)], fill=FRAME_LO)
    d.line([(dx0, dy1), (dx1, dy1)], fill=CARD_HI)

    # Buttons, in vanilla's own active / hovered / disabled vocabulary so a player reads them
    # without being taught.
    bevel(d, box_of(PANEL, "button"), BTN_FACE, BTN_HI, BTN_LO, border=BORDER)
    bevel(d, box_of(PANEL, "button_hover"), BTN_HOVER_FACE, BTN_HI, BTN_LO, border=BORDER)
    bevel(d, box_of(PANEL, "button_disabled"), BTN_OFF_FACE, BTN_OFF_HI, BTN_LO, border=BORDER)

    # The compact variant exists for the quest log's 60x12 Abandon button, which vanilla's own
    # 200x20 sprite renders visibly squashed. 4px slices, so it survives at 12px tall.
    bevel(d, box_of(PANEL, "button_compact"), BTN_FACE, BTN_HI, BTN_LO, border=BORDER)
    bevel(d, box_of(PANEL, "button_compact_hover"), BTN_HOVER_FACE, BTN_HI, BTN_LO, border=BORDER)
    bevel(d, box_of(PANEL, "button_compact_disabled"), BTN_OFF_FACE, BTN_OFF_HI, BTN_LO, border=BORDER)

    # Progress bars: one track, three fills. Green is objective and quest progress, blue is your own
    # share of a shared project total, amber is time running out.
    bx0, by0, bx1, by1 = box_of(PANEL, "bar_track")
    d.rectangle([bx0, by0, bx1, by1], fill=BAR_TRACK)
    d.line([(bx0, by0), (bx1, by0)], fill=BORDER)
    d.line([(bx0, by1), (bx1, by1)], fill=WELL_HI)
    for name, colour in (("bar_green", BAR_GREEN), ("bar_blue", BAR_BLUE), ("bar_amber", BAR_AMBER)):
        bx0, by0, bx1, by1 = box_of(PANEL, name)
        d.rectangle([bx0, by0, bx1, by1], fill=colour)
        d.line([(bx0, by0), (bx1, by0)], fill=lighten(colour, 40))
        d.line([(bx0, by1), (bx1, by1)], fill=darken(colour, 60))

    # Scrollbar. The thumb gains a hovered and a dragging state, because it is draggable now.
    sx0, sy0, sx1, sy1 = box_of(PANEL, "scroll_track")
    d.rectangle([sx0, sy0, sx1, sy1], fill=SCROLL_TRACK)
    d.line([(sx0, sy0), (sx0, sy1)], fill=BORDER)
    d.line([(sx1, sy0), (sx1, sy1)], fill=WELL_HI)
    for name, face in (("scroll_thumb", SCROLL_THUMB),
                       ("scroll_thumb_hover", SCROLL_THUMB_HI),
                       ("scroll_thumb_drag", SCROLL_THUMB_ON)):
        tx0, ty0, tx1, ty1 = box_of(PANEL, name)
        d.rectangle([tx0, ty0, tx1, ty1], fill=face)
        d.line([(tx0, ty0), (tx1, ty0)], fill=lighten(face, 30))
        d.line([(tx0, ty0), (tx0, ty1)], fill=lighten(face, 30))
        d.line([(tx0, ty1), (tx1, ty1)], fill=darken(face, 40))
        d.line([(tx1, ty0), (tx1, ty1)], fill=darken(face, 40))

    return img


# --- icons.png --------------------------------------------------------------------------------
# A 16x16 grid of 16x16 glyphs. Only the rows below are drawn; the rest of the sheet is transparent
# and is room to grow without moving anything that already ships.

# Every glyph is drawn with a dark OUTL outline, which is what lets the same sheet sit on the
# #C6C6C6 card face and on the dark HUD plate without a second set of art. The neutral tones below
# were warmed cream and warm-black before 1.5.0; they are grey now for the same reason the panel is.
OUTL = (0x1A, 0x1A, 0x1A, 0xFF)
PAPER = (0xEC, 0xEC, 0xEA, 0xFF)
PAPER_SH = (0xB4, 0xB4, 0xB2, 0xFF)
GOLD = (0xFF, 0xC8, 0x4A, 0xFF)
GOLD_SH = (0xC8, 0x93, 0x28, 0xFF)
GREEN = (0x6C, 0xC8, 0x5C, 0xFF)
GREEN_SH = (0x3E, 0x8C, 0x37, 0xFF)
RED = (0xE0, 0x5A, 0x4A, 0xFF)
RED_SH = (0x9C, 0x33, 0x2C, 0xFF)
BLUE = (0x6F, 0xA8, 0xDC, 0xFF)
BLUE_SH = (0x3C, 0x6E, 0x9E, 0xFF)
STEEL = (0xB4, 0xBA, 0xC4, 0xFF)
STEEL_SH = (0x6A, 0x72, 0x7E, 0xFF)
WOOD = (0x9A, 0x6E, 0x42, 0xFF)
WOOD_SH = (0x6A, 0x48, 0x2A, 0xFF)
STONE = (0x8A, 0x84, 0x7C, 0xFF)
STONE_SH = (0x55, 0x50, 0x4A, 0xFF)
PURPLE = (0xC8, 0xA2, 0xFF, 0xFF)
PURPLE_SH = (0x86, 0x63, 0xB8, 0xFF)
HEART = (0xE0, 0x4A, 0x5A, 0xFF)
HEART_SH = (0x8E, 0x26, 0x32, 0xFF)
HOLLOW = (0x3A, 0x3A, 0x3A, 0xFF)


def cell(d, ox, oy):
    """Local-coordinate drawing primitives for one 16x16 glyph cell."""

    def px(x, y, c):
        d.point((ox + x, oy + y), fill=c)

    def rc(x0, y0, x1, y1, c):
        d.rectangle([ox + x0, oy + y0, ox + x1, oy + y1], fill=c)

    def ro(x0, y0, x1, y1, c):
        d.rectangle([ox + x0, oy + y0, ox + x1, oy + y1], outline=c)

    def ln(pts, c):
        d.line([(ox + x, oy + y) for x, y in pts], fill=c)

    def pg(pts, c, outline=None):
        d.polygon([(ox + x, oy + y) for x, y in pts], fill=c, outline=outline)

    def el(x0, y0, x1, y1, c, outline=None):
        d.ellipse([ox + x0, oy + y0, ox + x1, oy + y1], fill=c, outline=outline)

    return px, rc, ro, ln, pg, el


def stamp(d, ox, oy, rows, palette):
    """Draws a glyph from an ASCII grid. Used where the silhouette has to be exact."""
    for y, row in enumerate(rows):
        if len(row) != 16:
            raise SystemExit(f"glyph row {y} is {len(row)} cells wide, expected 16")
        for x, ch in enumerate(row):
            colour = palette.get(ch)
            if colour is not None:
                d.point((ox + x, oy + y), fill=colour)


HEART_MASK = [
    "................",
    "................",
    "...XX....XX.....",
    "..XhhXXXXhhX....",
    ".XhhhhhhhhhhX...",
    ".XhhhhhhhhhhX...",
    ".XhhhhhhhhhhX...",
    ".XhhhhhhhhhhX...",
    "..XhhhhhhhhX....",
    "..XhhhhhhhhX....",
    "...XhhhhhhX.....",
    "....XhhhhX......",
    ".....XhhX.......",
    "......XX........",
    "................",
    "................",
]


def _heart(d, ox, oy, mode):
    stamp(d, ox, oy, HEART_MASK, {"X": HEART_SH, "h": HOLLOW if mode == "empty" else HEART})
    if mode == "half":
        # Right half hollowed out, the way vanilla's own half heart reads.
        for y, row in enumerate(HEART_MASK):
            for x, ch in enumerate(row):
                if ch == "h" and x >= 8:
                    d.point((ox + x, oy + y), fill=HOLLOW)


def i_heart_full(d, ox, oy):
    _heart(d, ox, oy, "full")


def i_heart_half(d, ox, oy):
    _heart(d, ox, oy, "half")


def i_heart_empty(d, ox, oy):
    _heart(d, ox, oy, "empty")


def i_quest(d, ox, oy):
    _px, rc, ro, ln, _pg, el = cell(d, ox, oy)
    rc(3, 2, 12, 13, PAPER)
    ro(3, 2, 12, 13, OUTL)
    ln([(5, 5), (10, 5)], PAPER_SH)
    ln([(5, 7), (10, 7)], PAPER_SH)
    ln([(5, 9), (8, 9)], PAPER_SH)
    el(8, 9, 12, 13, GOLD, OUTL)


def i_chain(d, ox, oy):
    _px, _rc, _ro, _ln, _pg, el = cell(d, ox, oy)
    el(1, 4, 8, 11, None, GOLD_SH)
    el(2, 5, 7, 10, None, GOLD)
    el(7, 4, 14, 11, None, GOLD_SH)
    el(8, 5, 13, 10, None, GOLD)


def i_project(d, ox, oy):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    rc(2, 2, 10, 5, STEEL)
    ro(2, 2, 10, 5, OUTL)
    rc(2, 4, 10, 5, STEEL_SH)
    rc(6, 5, 8, 13, WOOD)
    ro(6, 5, 8, 13, WOOD_SH)


def i_situation(d, ox, oy):
    _px, rc, _ro, _ln, pg, _el = cell(d, ox, oy)
    pg([(8, 1), (14, 13), (2, 13)], GOLD, OUTL)
    rc(7, 5, 8, 9, OUTL)
    rc(7, 10, 8, 11, OUTL)


def i_reputation(d, ox, oy):
    _px, _rc, _ro, ln, pg, _el = cell(d, ox, oy)
    pg([(8, 1), (14, 4), (14, 9), (8, 14), (2, 9), (2, 4)], BLUE, OUTL)
    pg([(8, 2), (8, 13), (3, 9), (3, 5)], BLUE_SH)
    ln([(8, 4), (8, 11)], PAPER)


def i_title(d, ox, oy):
    _px, rc, ro, _ln, pg, _el = cell(d, ox, oy)
    rc(3, 1, 12, 10, PURPLE)
    ro(3, 1, 12, 10, OUTL)
    rc(3, 8, 12, 10, PURPLE_SH)
    pg([(3, 10), (8, 14), (12, 10)], PURPLE_SH, OUTL)


def i_village(d, ox, oy):
    _px, rc, ro, _ln, pg, _el = cell(d, ox, oy)
    pg([(8, 1), (14, 7), (2, 7)], RED, OUTL)
    rc(3, 7, 12, 13, WOOD)
    ro(3, 7, 12, 13, OUTL)
    rc(7, 9, 9, 13, WOOD_SH)


def i_family(d, ox, oy):
    _px, rc, _ro, _ln, _pg, el = cell(d, ox, oy)
    el(2, 2, 6, 6, PAPER, OUTL)
    rc(2, 7, 6, 13, BLUE)
    el(9, 4, 13, 8, PAPER, OUTL)
    rc(9, 9, 13, 13, GREEN)


def i_clock(d, ox, oy):
    _px, _rc, _ro, ln, _pg, el = cell(d, ox, oy)
    el(1, 1, 14, 14, PAPER, OUTL)
    ln([(8, 8), (8, 4)], OUTL)
    ln([(8, 8), (11, 10)], OUTL)


def i_compass(d, ox, oy):
    _px, _rc, _ro, _ln, pg, el = cell(d, ox, oy)
    el(1, 1, 14, 14, STEEL, OUTL)
    pg([(8, 3), (10, 8), (8, 12), (6, 8)], RED, OUTL)
    pg([(8, 8), (10, 8), (8, 12), (6, 8)], PAPER)


def i_distance(d, ox, oy):
    px, _rc, _ro, ln, pg, _el = cell(d, ox, oy)
    for x in (2, 5, 8):
        ln([(x, 7), (x + 1, 7)], STEEL_SH)
        px(x, 8, STEEL_SH)
    pg([(10, 4), (14, 8), (10, 12)], GOLD, OUTL)


def i_danger(d, ox, oy):
    _px, rc, ro, _ln, _pg, el = cell(d, ox, oy)
    el(3, 2, 12, 10, PAPER, OUTL)
    rc(5, 5, 6, 7, OUTL)
    rc(9, 5, 10, 7, OUTL)
    rc(5, 10, 10, 13, PAPER)
    ro(5, 10, 10, 13, OUTL)
    rc(7, 11, 8, 13, OUTL)


def i_check(d, ox, oy):
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    for dy in (0, 1, 2):
        ln([(2, 7 + dy), (6, 11 + dy), (13, 4 + dy)], GREEN_SH if dy == 2 else GREEN)


def i_cross(d, ox, oy):
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    for dx in (0, 1, 2):
        ln([(2 + dx, 3), (11 + dx, 12)], RED_SH if dx == 2 else RED)
        ln([(11 + dx, 3), (2 + dx, 12)], RED_SH if dx == 2 else RED)


def i_pause(d, ox, oy):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    for x0 in (3, 9):
        rc(x0, 3, x0 + 3, 12, GOLD)
        ro(x0, 3, x0 + 3, 12, OUTL)


def i_dot(d, ox, oy):
    _px, _rc, _ro, _ln, _pg, el = cell(d, ox, oy)
    el(5, 5, 10, 10, STEEL, OUTL)


def _obj_box(d, ox, oy, fill):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    rc(2, 2, 13, 13, HOLLOW)
    ro(2, 2, 13, 13, OUTL)
    if fill is not None:
        ro(3, 3, 12, 12, fill)


def i_obj_pending(d, ox, oy):
    _obj_box(d, ox, oy, STONE_SH)


def i_obj_done(d, ox, oy):
    _obj_box(d, ox, oy, GREEN_SH)
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    for dy in (0, 1):
        ln([(4, 8 + dy), (7, 11 + dy), (12, 5 + dy)], GREEN)


def i_obj_failed(d, ox, oy):
    _obj_box(d, ox, oy, RED_SH)
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    ln([(5, 5), (10, 10)], RED)
    ln([(10, 5), (5, 10)], RED)


def i_obj_suspended(d, ox, oy):
    _obj_box(d, ox, oy, GOLD_SH)
    _px, rc, _ro, _ln, _pg, _el = cell(d, ox, oy)
    rc(5, 7, 10, 8, GOLD)


def _pips(d, ox, oy, lit):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    for i in range(3):
        x0 = 2 + i * 5
        rc(x0, 5, x0 + 3, 10, GOLD if i < lit else HOLLOW)
        ro(x0, 5, x0 + 3, 10, OUTL)


def i_pip_easy(d, ox, oy):
    _pips(d, ox, oy, 1)


def i_pip_medium(d, ox, oy):
    _pips(d, ox, oy, 2)


def i_pip_hard(d, ox, oy):
    _pips(d, ox, oy, 3)


def i_star(d, ox, oy):
    _px, _rc, _ro, _ln, pg, _el = cell(d, ox, oy)
    pg([(8, 1), (10, 6), (15, 6), (11, 9), (13, 14), (8, 11), (3, 14), (5, 9), (1, 6), (6, 6)],
       GOLD, OUTL)


def i_coin(d, ox, oy):
    _px, _rc, _ro, _ln, _pg, el = cell(d, ox, oy)
    el(2, 2, 13, 13, GOLD, OUTL)
    el(5, 5, 10, 10, GOLD_SH)


def i_xp(d, ox, oy):
    _px, _rc, _ro, _ln, pg, _el = cell(d, ox, oy)
    pg([(8, 1), (14, 6), (11, 14), (5, 14), (2, 6)], GREEN, OUTL)
    pg([(8, 4), (11, 7), (9, 12), (7, 12)], GREEN_SH)


def i_gift(d, ox, oy):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    rc(2, 5, 13, 7, GOLD)
    ro(2, 5, 13, 7, OUTL)
    rc(3, 7, 12, 13, RED)
    ro(3, 7, 12, 13, OUTL)
    rc(7, 5, 8, 13, GOLD_SH)


def i_scroll(d, ox, oy):
    _px, rc, ro, ln, _pg, _el = cell(d, ox, oy)
    rc(3, 3, 12, 12, PAPER)
    ro(3, 3, 12, 12, OUTL)
    for y in (5, 7, 9):
        ln([(5, y), (10, y)], PAPER_SH)
    rc(2, 2, 13, 3, WOOD)
    rc(2, 12, 13, 13, WOOD)


def i_book(d, ox, oy):
    _px, rc, ro, ln, _pg, _el = cell(d, ox, oy)
    rc(2, 2, 13, 13, WOOD_SH)
    ro(2, 2, 13, 13, OUTL)
    rc(4, 3, 12, 12, PAPER)
    ln([(8, 3), (8, 12)], PAPER_SH)


def i_prof_farmer(d, ox, oy):
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    ln([(8, 3), (8, 14)], WOOD_SH)
    for y in (4, 6, 8):
        ln([(8, y), (4, y - 1)], GOLD)
        ln([(8, y), (12, y - 1)], GOLD_SH)


def i_prof_fisherman(d, ox, oy):
    _px, _rc, _ro, _ln, pg, el = cell(d, ox, oy)
    el(2, 5, 11, 11, BLUE, OUTL)
    pg([(11, 5), (14, 3), (14, 13), (11, 11)], BLUE_SH, OUTL)
    el(4, 7, 5, 8, OUTL)


def i_prof_shepherd(d, ox, oy):
    _px, _rc, _ro, ln, _pg, el = cell(d, ox, oy)
    ln([(4, 13), (11, 3)], STEEL)
    ln([(11, 13), (4, 3)], STEEL_SH)
    el(3, 11, 6, 14, None, OUTL)
    el(9, 11, 12, 14, None, OUTL)


def i_prof_fletcher(d, ox, oy):
    _px, _rc, _ro, ln, pg, _el = cell(d, ox, oy)
    ln([(3, 13), (12, 4)], WOOD)
    pg([(11, 2), (14, 5), (10, 6)], STEEL, OUTL)
    ln([(3, 13), (3, 10)], PAPER)
    ln([(3, 13), (6, 13)], PAPER)


def i_prof_librarian(d, ox, oy):
    i_book(d, ox, oy)
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    ln([(5, 5), (7, 5)], STONE_SH)
    ln([(9, 5), (11, 5)], STONE_SH)


def i_prof_cartographer(d, ox, oy):
    _px, rc, ro, ln, _pg, el = cell(d, ox, oy)
    rc(2, 3, 13, 12, PAPER)
    ro(2, 3, 13, 12, OUTL)
    ln([(4, 10), (7, 6), (11, 9)], WOOD_SH)
    el(9, 4, 12, 7, RED, OUTL)


def i_prof_cleric(d, ox, oy):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    rc(6, 2, 9, 4, STONE)
    ro(6, 2, 9, 4, OUTL)
    rc(4, 5, 11, 13, PURPLE)
    ro(4, 5, 11, 13, OUTL)
    rc(5, 9, 10, 12, PURPLE_SH)


def i_prof_armorer(d, ox, oy):
    _px, rc, ro, _ln, pg, _el = cell(d, ox, oy)
    pg([(8, 2), (13, 5), (13, 10), (3, 10), (3, 5)], STEEL, OUTL)
    rc(5, 7, 10, 9, OUTL)
    rc(3, 10, 12, 13, STEEL_SH)
    ro(3, 10, 12, 13, OUTL)


def i_prof_weaponsmith(d, ox, oy):
    _px, rc, ro, ln, pg, _el = cell(d, ox, oy)
    pg([(8, 1), (10, 4), (10, 10), (6, 10), (6, 4)], STEEL, OUTL)
    rc(4, 10, 11, 11, GOLD)
    ro(4, 10, 11, 11, OUTL)
    ln([(8, 12), (8, 14)], WOOD_SH)


def i_prof_toolsmith(d, ox, oy):
    _px, _rc, _ro, ln, pg, _el = cell(d, ox, oy)
    pg([(2, 5), (8, 2), (14, 5), (8, 6)], STEEL, OUTL)
    ln([(8, 6), (8, 14)], WOOD)
    ln([(9, 6), (9, 14)], WOOD_SH)


def i_prof_butcher(d, ox, oy):
    _px, _rc, _ro, _ln, pg, el = cell(d, ox, oy)
    el(3, 3, 12, 11, RED, OUTL)
    el(5, 5, 10, 9, RED_SH)
    pg([(6, 11), (9, 11), (9, 14), (6, 14)], PAPER, OUTL)


def i_prof_leatherworker(d, ox, oy):
    _px, _rc, _ro, _ln, pg, _el = cell(d, ox, oy)
    pg([(3, 3), (8, 2), (13, 3), (12, 9), (8, 14), (4, 9)], WOOD, OUTL)
    pg([(6, 5), (10, 5), (9, 9), (7, 9)], WOOD_SH)


def i_prof_mason(d, ox, oy):
    _px, rc, ro, _ln, _pg, _el = cell(d, ox, oy)
    for row, y in enumerate((3, 7, 11)):
        offset = 0 if row % 2 == 0 else 3
        for x0 in range(-3 + offset, 14, 6):
            rc(max(2, x0), y, min(13, x0 + 5), y + 3, STONE)
            ro(max(2, x0), y, min(13, x0 + 5), y + 3, STONE_SH)


def i_prof_guard(d, ox, oy):
    i_reputation(d, ox, oy)
    _px, _rc, _ro, ln, _pg, _el = cell(d, ox, oy)
    ln([(8, 3), (8, 12)], STEEL)


def i_prof_archer(d, ox, oy):
    _px, _rc, _ro, ln, _pg, el = cell(d, ox, oy)
    el(2, 1, 13, 14, None, WOOD)
    el(3, 2, 12, 13, None, WOOD_SH)
    ln([(11, 2), (11, 13)], PAPER)
    ln([(4, 8), (13, 8)], STEEL)


def i_prof_villager(d, ox, oy):
    _px, rc, ro, _ln, _pg, el = cell(d, ox, oy)
    el(4, 1, 11, 9, PAPER_SH, OUTL)
    rc(6, 5, 9, 8, PAPER)
    rc(3, 9, 12, 14, GREEN_SH)
    ro(3, 9, 12, 14, OUTL)


ICON_GRID = [
    ["quest", "chain", "project", "situation", "reputation", "title", "village", "family",
     "clock", "compass", "distance", "danger", "check", "cross", "pause", "dot"],
    ["heart_full", "heart_half", "heart_empty", "obj_pending", "obj_done", "obj_failed",
     "obj_suspended", "pip_easy", "pip_medium", "pip_hard", "star", "coin", "xp", "gift",
     "scroll", "book"],
    ["prof_farmer", "prof_fisherman", "prof_shepherd", "prof_fletcher", "prof_librarian",
     "prof_cartographer", "prof_cleric", "prof_armorer", "prof_weaponsmith", "prof_toolsmith",
     "prof_butcher", "prof_leatherworker", "prof_mason", "prof_guard", "prof_archer",
     "prof_villager"],
]

ICONS = {
    name: (col * 16, row * 16, 16, 16, 0, 0)
    for row, names in enumerate(ICON_GRID)
    for col, name in enumerate(names)
}


def build_icons():
    img = Image.new("RGBA", (SHEET, SHEET), CLEAR)
    d = ImageDraw.Draw(img)
    for name, (u, v, _w, _h, _sx, _sy) in ICONS.items():
        painter = globals().get("i_" + name)
        if painter is None:
            raise SystemExit(f"no painter i_{name} for icon '{name}'")
        painter(d, u, v)
    return img


# --- entry point ------------------------------------------------------------------------------

def main():
    assert_no_overlap(PANEL, "panel.png")
    assert_no_overlap(ICONS, "icons.png")

    OUT.mkdir(parents=True, exist_ok=True)
    build_panel().save(OUT / "panel.png")
    build_icons().save(OUT / "icons.png")

    # The atlas as data. GuiTexturesExistTest reads this and asserts the Java constants agree, so
    # a UV can never be edited here without the code that blits it being updated too.
    layout = {
        "sheet": SHEET,
        "panel": {n: {"u": u, "v": v, "w": w, "h": h, "sliceX": sx, "sliceY": sy}
                  for n, (u, v, w, h, sx, sy) in sorted(PANEL.items())},
        "icons": {n: {"u": u, "v": v, "w": w, "h": h, "sliceX": sx, "sliceY": sy}
                  for n, (u, v, w, h, sx, sy) in sorted(ICONS.items())},
    }
    MANIFEST.write_text(json.dumps(layout, indent=2) + "\n", encoding="utf-8")

    print(f"wrote panel.png ({len(PANEL)} sprites) and icons.png ({len(ICONS)} icons) to {OUT}, "
          f"and the atlas manifest to {MANIFEST}")


if __name__ == "__main__":
    main()
