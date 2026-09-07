# MCA: Quests × Map Atlases

**[Map Atlases](https://www.curseforge.com/minecraft/mc-mods/map-atlases)** turns vanilla maps into a
browsable, collectable atlas with its own fullscreen screen and minimap. This integration puts quest
destinations on that atlas — on the fullscreen screen, on its minimap, and on the map a held atlas
shows — the same way MCA: Quests already does for JourneyMap and Xaero's Minimap.

This is a client-side integration; MCA: Quests' own "follow this quest" request is the only packet
it sends to the server. **Show in atlas**, when no atlas is currently open, also sends Map Atlases'
own request to open one (its `C2S2COpenAtlasScreenPacket`, over Moonlight's `NetworkHelper`) — a
request to that mod's server side, not to MCA: Quests'. It reaches Map Atlases through reflection,
never by compiling against it, and is checked against the exact release it was built for — see
[Version support](#version-support) below.

Everything here is **optional**. Without Map Atlases installed, or with an unsupported version, quest
guidance still works exactly as it does today: the HUD tracker, the in-world marker, JourneyMap and
Xaero's Minimap are all unaffected.

---

## Install

1. Install **MCA Reborn**, **MCA: Quests**, and **Map Atlases** on the client. Map Atlases' own
   dependencies (Moonlight and, for its personal-pin feature, Moonlight's pin store) must be present
   for that feature; nothing else here requires them.
2. No server-side install and no configuration is required to see the automatic overlays.

Confirm it took with `/mcaquestsclient waypoints status`. Look for the `map_atlases` backend and read
its status line — it names the installed Map Atlases version, how many destinations it has accepted,
and how many are currently visible on the fullscreen map and the minimap.

---

## How it works

### Automatic overlays

One marker per active quest that currently has somewhere to send you, drawn:

- on the atlas **fullscreen screen**, for the dimension and layer you are currently viewing;
- on the atlas **minimap** HUD, in the same position, scale and rotation as everything else on it;
- on the map a **held atlas** shows in first person, if that item is currently displaying the exact
  map the marker belongs on.

Each is controlled independently (`mapAtlasesWorldMap`, `mapAtlasesMinimap`, `mapAtlasesInHand`) under
the master `mapAtlasesWaypoints` switch, which itself only matters when the mod-wide `mapWaypoints` is
on. The quest you are following also gets a small rim indicator at the minimap's edge when it is
covered by the active atlas but currently off-screen (`mapAtlasesPrimaryEdgeArrow`); it disappears once
the marker is visible or you are within arrival range, and it steps aside from the atlas's own native
pin arrows and cardinal labels rather than covering them.

These markers are temporary. They are never written into a map item, into vanilla map data, or into
Map Atlases' own personal-pin store — removing MCA: Quests or Map Atlases leaves nothing behind.

### Quest destinations and marker clicks

Opening an atlas adds a **Quest destinations** button in its top-left corner: a paged, keyboard- and
narrator-accessible list of every currently eligible target, plus a **Focus followed quest** button
that pans the atlas to your active quest without teleporting, creating a map, or changing quest
progress. Clicking a drawn marker on the fullscreen atlas screen opens the same list filtered to that
spot, or goes straight to the destination's own action menu when only one quest is there — the native
minimap HUD has no click handling, so this only works on the fullscreen screen.

### Show in atlas and Save pin

The quest log's map button, and every marker's action menu, offer up to two explicit actions per
installed map backend:

- **Show in atlas** — opens (or focuses, if already open) an atlas on the destination's map and
  centres it there. Controlled by `mapAtlasesNavigation`.
- **Save pin in Map Atlases** — deliberately copies the destination into Map Atlases' own personal-pin
  store. It survives the quest ending or MCA: Quests being removed, does not follow a moving villager,
  and follows Map Atlases' own persistence. Controlled by `mapAtlasesPins`. Unlike other backends, this
  button is always offered for Map Atlases; when its native pin feature (which depends on Moonlight)
  isn't actually available, the button stays but is greyed out with a reason tooltip instead of being
  withheld.

When more than one backend can take the pin — say, both JourneyMap and Map Atlases — each is listed
separately with its own button and its own result message, plus a "save in all listed available maps"
option; a single click no longer silently sends the pin to every backend that happens to share the same
durability. When an action is unavailable, the reason is spelled out (no active atlas, the location
isn't on any map in it, the native pin feature is off, a native tool is mid-use, and so on) rather than
the button just failing to do anything.

---

## Coverage, dimensions and slices

A destination is only ever drawn where the atlas already has a map for it — this integration never
paints unexplored terrain, requests remote data, or creates or consumes a map to make a marker appear.
Two coverage policies are available (`mapAtlasesCoverage`):

- **`COVERED_MAPS`** (default) — draw it as soon as the selected atlas has any map sheet covering that
  spot, explored or not. A quest marker can point at an undiscovered part of an existing map sheet
  without revealing anything about the terrain there.
- **`EXPLORED_PIXELS`** — additionally require that exact spot to already be explored. This is only
  meaningful on ordinary vanilla-type map sheets; maze, ore-maze and other map types fall back to
  withheld rather than guessing at pixel semantics they don't share.

Quest markers respect Map Atlases' own marker policy per map type — a map type that hides markers
shows "Quest markers are hidden on this map type" instead of an unverified drawing.

Destinations always carry their real dimension. A target in another dimension only ever appears on that
dimension's own tab or map, never with a walking arrow through the one you're standing in, and unmapped
or unrecognised dimensions get a text explanation rather than an Overworld fallback.

Map Atlases can slice a dimension into vertical layers. `mapAtlasesSlicePolicy` controls what happens
when a target's height isn't certain relative to the selected layer:

- **`SHOW_WITH_HEIGHT_HINT`** (default) — always draw it, with an above/below hint character when the
  target and the selected layer disagree, and a plain "uncertain" hint when the quest's own vertical
  provenance isn't reliable (an approximate or last-known destination, for instance).
- **`STRICT_SLICE`** — withhold a target whose height isn't reliable or falls outside
  `mapAtlasesSliceTolerance` blocks of the selected layer, and say why, rather than implying it's on a
  floor it may not be on.

---

## Configuration summary

Every key above lives under `[client]` in `mcaquests-client.toml`, alongside `mapAtlasesLabels` (marker
label density), `mapAtlasesMarkerScale`, and `mapAtlasesWorldMapBudget` / `mapAtlasesMinimapBudget`
(how many quest glyphs the fullscreen map and minimap will draw before the rest fall back to the **Quest
destinations** list). See **[CONFIG.md](CONFIG.md#map-atlases)** for the full table, defaults and
ranges.

---

## Diagnostics

`/mcaquestsclient waypoints status` and `probe` cover Map Atlases the same way they already cover
JourneyMap and Xaero: what bound, the installed version, and (for Map Atlases specifically) how many
destinations are currently accepted versus visible on each surface, whether native pins and navigation
are actually available, and the last failure recorded, if any.

---

## Version support

The rendering hooks (`compat/mapatlases/AtlasHookManifest`) are checked against one exact, verified
release build. If a different Map Atlases build is installed, this integration disables itself with a
log line and a reason visible through `/mcaquestsclient waypoints status` — JourneyMap, Xaero's
Minimap, the HUD tracker and the in-world marker are entirely unaffected. Broader version support is a
matter of testing further releases, not a promise this document can make ahead of it.

---

## FAQ

**Do I need Map Atlases?** No. Without it, or with an unsupported build of it, MCA: Quests behaves
exactly as it did before this integration.

**Does the server need Map Atlases?** No. Everything here is client-side; a server never needs it
installed, and other players on the same server can run without it.

**Why don't I see any quest markers on my atlas?** Run `/mcaquestsclient waypoints status` first —
check that `mapAtlasesWaypoints` and the master `mapWaypoints` are both on, that the installed Map
Atlases build is recognised, and that the atlas you're looking at actually has a map covering that
destination.

**Why is "Save pin in Map Atlases" greyed out?** Map Atlases' personal-pin feature depends on Moonlight
and its own pin setting; if either is missing or disabled, the button explains that instead of doing
nothing.

**Will this duplicate a waypoint I already have on Xaero's Minimap?** No — each backend is independent
and keeps its own markers; installing or removing one never changes what another does.

---

See [CONFIG.md](CONFIG.md#map-atlases) for every option and its default and range.
