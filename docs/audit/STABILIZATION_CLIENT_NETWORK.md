# Client, network, and command stabilization audit

Scope: Forge 1.20.1 client/network/commands and client mixins, plus the equivalent NeoForge 1.21.1 code. Shared quest/project/guidance wire-entry decoders were included by agreement with the engine reviewer. Existing APIs, packet field order, packet IDs, and protocol versions are unchanged.

## Substantiated issues fixed

- Forge registered every message without a direction. A client could send a server-to-client packet into its server decoder, and integrated-server handlers could reach the host client. All 21 registrations now declare their actual direction. The installed Forge `IndexedMessageCodec` source confirms direction validation precedes decoding. NeoForge's existing directional payload registration was already correct.
- Collection lengths from the wire allocated an attacker-declared `ArrayList` before reading any elements. Shared decoding rejects negative/impossible lengths before allocation and grows from at most 1024 initial entries. It imposes no new item-count cap: valid 1000-quest configurations and larger collections retain their existing wire compatibility.
- Unbounded client request rates could repeat expensive menu, journal, standing and state work. A per-player token budget permits a 20-request burst and refills 10 requests per second. Forge applies it before enqueueing server work; NeoForge applies it on its existing main-thread payload dispatcher. Exhausted requests are ignored, and budgets are weakly held by player instance.
- The FTB editor's documented truncation prefix was not respected when later smaller entries still fit. A string over the wire's character limit could also survive the byte guard and fail encoding. The builder now stops at a single prefix across its seven lists and drops overlong strings before encode.
- Journal text wrapped during rendering but retained one-line row heights. Long translations, titles and archive entries could overlap following rows and controls. The same measured row height now determines layout, bars, dividers and rendering.
- Quest-log coordinate and map-pin controls captured the destination at screen construction. They now resolve current guidance on click. Layout signatures include actual row height, so moving/renamed targets and changed wrapping cannot detach controls from their rows.
- Replies to a quest-menu decision recreated the screen and lost scroll position. Same-villager replies refresh the existing screen; delayed replies cannot replace the journal, log, project screen or a disconnected world.
- HUD-toggle and cycle-tracked keybinds could act while a screen was open or after the player left. Both now require an active player with no open screen.
- Scrollbar thumbs exceeded viewports shorter than their minimum height. Large scroll deltas could also overflow and jump back to the top. Both cases clamp correctly.
- A map backend returning `RETRY_LATER` on its first attempt scheduled no retry, so a stationary destination could remain missing indefinitely. After a previous failure the old retry deadline could instead cause per-tick retries. Deferred results now schedule a fresh future retry.
- Disabling map waypoints did not retry failed cleanup. The reconciler now retains a separate bounded cleanup retry schedule until the backend reports no owned automatic markers, coordinated with the compatibility review's retained-ownership backend fixes.
- Reputation commands always addressed the overworld and reported successful no-ops from console. They now require a player and use the command source's dimension, including `/execute in`. Reputation `set` rejects a delta outside the public integer award range instead of wrapping. Title-list counts include individual village titles. Asynchronous reload failures are logged and reported to the requester.

## Reviewed paths and retained contracts

Checked all packet registrations and C2S/S2C dispatch, packet/card/journal/guidance codecs, editor-ID construction, client mirrors and logout clearing, quest/project/log/journal screen layout and actions, keybinds, MCA screen-button insertion, both vanilla client mixins, HUD/marker state and geometry, map reconciliation/backoff/lifecycle, toasts and GUI resource references, client diagnostics and every server-command branch. Existing server-side quest/project identity, distance, session, ownership and resource-consumption validation remains authoritative; the request budget supplements those checks.

The NeoForge port retains its platform-specific payload codecs, registry buffers, screen rendering/background ordering, and input APIs. No save schema or public quest/project API was changed in this workstream.

## Regression coverage

Added cases in `PacketBoundsTest`, `EditorIdsPacketCodecTest`, `ScrollViewTest`, and `WaypointReconcilerTest`: malformed huge/negative lengths, unchanged empty/scalar wire collections, 10,000 valid elements, actual malformed editor packets, request-flood bound/refill/player isolation, prefix/individual-string truncation, tiny viewport thumbs, integer scroll overflow, deferred map apply scheduling, retry deadline advancement, and disabled cleanup recovery. Existing packet roundtrips, language/texture checks, marker geometry/state tests and lifecycle tests remain part of the full project test suite.

Builds and test results are collected by the coordinating task to avoid concurrent Gradle locks.

## Manual runtime verification still required

Use an actual client/server pair to verify delayed-menu replies, scroll retention, keyboard focus, narrator and long en_us/pt_br/custom datapack journal text at GUI scales 1 through 4; compare MCA 7.6.x/7.7.x interaction layouts. Verify Forge dedicated-server wrong-direction rejection and normal rapid interactions; repeat large real datapack snapshots within the loader's preexisting transport limit. Test map-startup delays, mod toggles, cleanup failure recovery, respawn/dimension changes and reconnects with actual JourneyMap/Xaero installations. Shader/resource-pack rendering, portraits, toast timing and GPU-specific behavior require visual runtime checks.
