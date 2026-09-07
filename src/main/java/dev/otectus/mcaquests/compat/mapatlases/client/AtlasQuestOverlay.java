package dev.otectus.mcaquests.compat.mapatlases.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.marker.MarkerColours;
import dev.otectus.mcaquests.compat.WaypointSpec;
import dev.otectus.mcaquests.compat.mapatlases.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.lang.Math;

/** Draws solely in native terrain clipping. A frame owns copied matrices and one immutable epoch. */
public final class AtlasQuestOverlay {
    private AtlasQuestOverlay() { }
    private record Captured(AtlasNativeBinding.Tile tile, Matrix4f matrix) { }
    private record Candidate(WaypointSpec spec, Captured tile, AtlasProjection.Point local,
                             AtlasProjection.Point screen) { }
    public record Hit(AtlasProjection.Rect bounds, List<WaypointSpec> quests) { }
    private record Observation(long time, int visible, Map<String, Integer> suppressed) { }
    private static Observation world = new Observation(0, 0, Map.of()), hud = world;
    private static List<Hit> hits = List.of();
    private static WeakReference<Screen> hitScreen = new WeakReference<>(null);
    private static long hitEpoch, hitTime;

    public static final class Frame {
        final AtlasRuntime runtime;
        final AtlasMarkerStore.Snapshot snapshot;
        final Object widget;
        final boolean hud;
        final AtlasProjection.Rect viewport;
        final List<Captured> tiles = new ArrayList<>();
        boolean failed;
        Frame(AtlasRuntime runtime, Object widget, boolean hud, AtlasProjection.Rect viewport) {
            this.runtime = runtime; this.widget = widget; this.hud = hud; this.viewport = viewport;
            snapshot = runtime.store().snapshot();
        }
    }
    public static Frame begin(Object widget, GuiGraphics graphics, int x, int y, int width, int height) {
        AtlasRuntime runtime = AtlasRuntime.get();
        if (runtime == null) return null;
        boolean hud = runtime.binding().isHud(widget);
        if (!hud) { hits = List.of(); hitScreen.clear(); }
        var config = McaQuestsConfig.CLIENT;
        if (!config.mapWaypoints.get() || !config.mapAtlasesWaypoints.get()
                || !(hud ? config.mapAtlasesMinimap.get() : config.mapAtlasesWorldMap.get())) return null;
        Matrix4f root = graphics.pose().last().pose();
        Vector3f a = root.transformPosition(x, y, 0, new Vector3f());
        Vector3f b = root.transformPosition(x + width, y + height, 0, new Vector3f());
        return new Frame(runtime, widget, hud, new AtlasProjection.Rect(Math.min(a.x,b.x), Math.min(a.y,b.y),
                Math.max(a.x,b.x), Math.max(a.y,b.y)));
    }
    public static void capture(Frame frame, Object holder, PoseStack pose) {
        if (frame == null || frame.failed) return;
        try { frame.tiles.add(new Captured(frame.runtime.binding().tile(holder), new Matrix4f(pose.last().pose()))); }
        catch (RuntimeException | LinkageError error) { frame.failed = true; frame.runtime.failed("capture", error); }
    }
    public static String policy(WaypointSpec spec, AtlasNativeBinding.Tile tile) {
        return policy(spec, tile, spec.pos().getX()+.5, spec.pos().getZ()+.5);
    }
    public static String policy(WaypointSpec spec, AtlasNativeBinding.Tile tile, double x, double z) {
        if (!tile.data().dimension.equals(spec.dimension())) return "other_dimension";
        if (!tile.allowsMarkers() || !Set.of("VANILLA", "MAZE", "ORE_MAZE").contains(tile.type())) return "map_type";
        var config = McaQuestsConfig.CLIENT;
        String slice = AtlasProjection.sliceReason(spec, tile.height(),
                config.mapAtlasesSlicePolicy.get() == McaQuestsConfig.Client.AtlasSlicePolicy.STRICT_SLICE,
                config.mapAtlasesSliceTolerance.get());
        if (!slice.equals("ready")) return slice;
        if (config.mapAtlasesCoverage.get() == McaQuestsConfig.Client.AtlasCoverage.EXPLORED_PIXELS) {
            if (!tile.type().equals("VANILLA")) return "unverified_fog";
            var point = AtlasProjection.project(x, z,
                    tile.data().centerX, tile.data().centerZ, tile.data().scale);
            if (!AtlasProjection.owns(point)) return "no_coverage";
            int pixel = (int) point.x() + (int) point.y() * 128;
            if ((tile.data().colors[pixel] & 255) / 4 == 0) return "unexplored";
        }
        return "ready";
    }
    public static void render(Frame frame, GuiGraphics graphics) {
        if (frame == null) return;
        try {
            if (frame.failed || frame.runtime.store().snapshot().epoch() != frame.snapshot.epoch()) return;
            Map<String, String> suppressed = new HashMap<>();
            Map<String, Candidate> candidates = new TreeMap<>();
            frame.tiles.sort(Comparator.comparing((Captured c) -> c.tile.type())
                    .thenComparingInt(c -> c.tile.data().centerX).thenComparingInt(c -> c.tile.data().centerZ));
            for (Captured capture : frame.tiles) {
                var data = capture.tile.data();
                if (data.scale < 0 || data.scale > 4) continue;
                for (WaypointSpec spec : frame.snapshot.near(data.dimension, data.centerX, data.centerZ, data.scale)) {
                    var live = frame.runtime.position(spec);
                    var point = AtlasProjection.project(live.x, live.z,
                            data.centerX, data.centerZ, data.scale);
                    if (!AtlasProjection.owns(point)) continue;
                    String reason = policy(spec, capture.tile, live.x, live.z);
                    if (!reason.equals("ready")) { suppressed.put(spec.key(), reason); continue; }
                    Vector3f screen = capture.matrix.transformPosition((float) point.x(), (float) point.y(), -2, new Vector3f());
                    if (!frame.viewport.contains(screen.x, screen.y)) continue;
                    candidates.putIfAbsent(spec.key(), new Candidate(spec, capture, point, new AtlasProjection.Point(screen.x, screen.y)));
                }
            }
            List<Candidate> ordered = candidates.values().stream().sorted(Comparator.comparing(Candidate::spec, AtlasMarkerStore.ORDER)).toList();
            Map<Long, List<Candidate>> groups = new LinkedHashMap<>();
            for (Candidate candidate : ordered) {
                // A bounded spatial grid groups coincident destinations deterministically, without pairwise scans.
                long x = (long)Math.floor(candidate.screen.x() / 10), y = (long)Math.floor(candidate.screen.y() / 10);
                groups.computeIfAbsent((x << 32) ^ (y & 0xffffffffL), ignored -> new ArrayList<>()).add(candidate);
            }
            int budget = frame.hud ? McaQuestsConfig.CLIENT.mapAtlasesMinimapBudget.get() : McaQuestsConfig.CLIENT.mapAtlasesWorldMapBudget.get();
            int drawn = 0, overflow = 0;
            List<Hit> currentHits = new ArrayList<>();
            Set<String> represented = new HashSet<>();
            for (List<Candidate> group : groups.values()) {
                if (drawn >= budget) {
                    overflow += group.size(); group.forEach(c -> suppressed.put(c.spec.key(), "budget")); continue;
                }
                Candidate main = group.get(0);
                AtlasProjection.Rect bounds = glyph(frame, graphics, main, group.size());
                group.forEach(c -> represented.add(c.spec.key()));
                currentHits.add(new Hit(bounds.intersect(frame.viewport), group.stream().map(Candidate::spec).toList()));
                drawn++;
            }
            if (frame.hud && McaQuestsConfig.CLIENT.mapAtlasesPrimaryEdgeArrow.get()) drawRim(frame, graphics, represented);
            if (overflow > 0) drawScreenText(graphics, "+" + overflow, (int)frame.viewport.left() + 3, (int)frame.viewport.top() + 3, 0xFFFFFFFF);
            // Every accepted destination remains represented in the accessible list, including offscreen ones.
            Object selected = frame.runtime.binding().surfaceSlice(frame.widget);
            List<AtlasNativeBinding.Tile> coverage = frame.runtime.binding().surfaceMaps(frame.widget).stream()
                    .map(frame.runtime.binding()::tile).filter(t -> t.slice().equals(selected)).toList();
            Map<String,String> coverageReasons = new HashMap<>();
            for (var tile : coverage) {
                var data = tile.data();
                if (data.scale < 0 || data.scale > 4) continue;
                for (WaypointSpec spec : frame.snapshot.near(data.dimension, data.centerX, data.centerZ, data.scale)) {
                    if (!AtlasProjection.owns(AtlasProjection.project(spec.pos().getX()+.5, spec.pos().getZ()+.5,
                            data.centerX, data.centerZ, data.scale))) continue;
                    String reason = policy(spec, tile);
                    coverageReasons.putIfAbsent(spec.key(), reason.equals("ready") ? "offscreen" : reason);
                }
            }
            for (WaypointSpec spec : frame.snapshot.all()) {
                if (represented.contains(spec.key()) || suppressed.containsKey(spec.key())) continue;
                String fallback = !coverage.isEmpty() && !coverage.get(0).data().dimension.equals(spec.dimension())
                        ? "other_dimension" : "no_coverage";
                suppressed.put(spec.key(), coverageReasons.getOrDefault(spec.key(), fallback));
            }
            Map<String,Integer> counts = new TreeMap<>(); suppressed.values().forEach(reason -> counts.merge(reason, 1, Integer::sum));
            Observation observation = new Observation(System.currentTimeMillis(), drawn, Map.copyOf(counts));
            if (frame.hud) hud = observation;
            else {
                world = observation; hits = List.copyOf(currentHits);
                hitScreen = new WeakReference<>(Minecraft.getInstance().screen);
                hitEpoch = frame.snapshot.epoch(); hitTime = observation.time;
            }
            AtlasHookState.observed(drawn > 0);
        } catch (RuntimeException | LinkageError error) {
            frame.runtime.failed("render", error); hits = List.of();
        } finally {
            // Flush before the caller disables its scissor. Do not alter its blend/depth/scissor state.
            try { graphics.flush(); } finally { frame.tiles.clear(); }
        }
    }
    private static AtlasProjection.Rect glyph(Frame frame, GuiGraphics graphics, Candidate candidate, int count) {
        PoseStack pose = graphics.pose(); pose.pushPose();
        try {
            pose.last().pose().set(candidate.tile.matrix);
            pose.translate(candidate.local.x(), candidate.local.y(), -2);
            frame.runtime.binding().transformIcon(pose);
            float scale = McaQuestsConfig.CLIENT.mapAtlasesMarkerScale.get().floatValue();
            pose.scale(scale, scale, 1);
            WaypointSpec spec = candidate.spec;
            int radius = spec.presentation().primary() ? 4 : 3, color = 0xFF000000 | MarkerColours.of(spec.kind());
            graphics.fill(-radius-1, -radius-1, radius+1, radius+1, 0xFF111118);
            graphics.fill(-radius, -radius, radius, radius, color);
            if (spec.presentation().approximate() || spec.presentation().lastKnown()) graphics.fill(-radius+1, -radius+1, radius-1, radius-1, 0xFF111118);
            Vector3f a = pose.last().pose().transformPosition(-radius-1, -radius-1, 0, new Vector3f());
            Vector3f b = pose.last().pose().transformPosition(radius+1, radius+1, 0, new Vector3f());
            pose.pushPose();
            try {
                pose.scale(0.5f, 0.5f, 1);
                String glyph = MarkerColours.initials(spec.kind());
                graphics.drawString(Minecraft.getInstance().font, glyph, -Minecraft.getInstance().font.width(glyph)/2, -4, -1, false);
                String hint = spec.presentation().lastKnown() ? "?" : spec.presentation().approximate() ? "~" : "";
                Integer height = candidate.tile.tile.height();
                if (height != null) hint += !spec.presentation().reliableHeight() ? "?" : spec.pos().getY() > height ? "^" : spec.pos().getY() < height ? "v" : "";
                if (spec.presentation().readyToTurnIn()) hint += "!";
                if (count > 1) hint += "+" + (count - 1);
                if (!hint.isEmpty()) graphics.drawString(Minecraft.getInstance().font, hint, radius * 2 + 1, -4, -1, true);
                if (spec.presentation().primary() && McaQuestsConfig.CLIENT.mapAtlasesLabels.get() == McaQuestsConfig.Client.AtlasLabels.HOVER_AND_PRIMARY)
                    graphics.drawString(Minecraft.getInstance().font,
                            Minecraft.getInstance().font.plainSubstrByWidth(spec.label(), 88), 0, radius * 2 + 2, -1, true);
            } finally { pose.popPose(); }
            return new AtlasProjection.Rect(Math.min(a.x,b.x), Math.min(a.y,b.y), Math.max(a.x,b.x), Math.max(a.y,b.y));
        } finally { pose.popPose(); }
    }
    private static void drawRim(Frame frame, GuiGraphics graphics, Set<String> represented) {
        if (frame.tiles.isEmpty()) return;
        WaypointSpec primary = frame.snapshot.all().stream().filter(s -> s.presentation().primary()).findFirst().orElse(null);
        if (primary == null || represented.contains(primary.key())) return;
        var player = Minecraft.getInstance().player;
        if (player == null || !player.level().dimension().equals(primary.dimension())) return;
        double dx = player.getX() - primary.pos().getX() - .5, dz = player.getZ() - primary.pos().getZ() - .5;
        if (dx * dx + dz * dz <= (double) primary.presentation().arriveRadius() * primary.presentation().arriveRadius()) return;
        Object selected = frame.runtime.binding().surfaceSlice(frame.widget);
        boolean covered = false;
        for (Object holder : frame.runtime.binding().surfaceMaps(frame.widget)) {
            var tile = frame.runtime.binding().tile(holder);
            if (tile.slice().equals(selected) && policy(primary, tile).equals("ready")
                    && AtlasProjection.owns(AtlasProjection.project(primary.pos().getX()+.5, primary.pos().getZ()+.5,
                    tile.data().centerX, tile.data().centerZ, tile.data().scale))) { covered = true; break; }
        }
        if (!covered) return;
        Captured reference = frame.tiles.get(0);
        var local = AtlasProjection.project(primary.pos().getX()+.5, primary.pos().getZ()+.5,
                reference.tile.data().centerX, reference.tile.data().centerZ, reference.tile.data().scale);
        Vector3f target = reference.matrix.transformPosition((float)local.x(), (float)local.y(), -2, new Vector3f());
        if (frame.viewport.contains(target.x, target.y)) return;
        var rim = AtlasProjection.rim(new AtlasProjection.Point(target.x,target.y), frame.viewport, 7);
        // Inset from native focused pins/cardinals; their focus and positions remain untouched.
        drawScreenText(graphics, MarkerColours.initials(primary.kind()), (int)rim.x()-3, (int)rim.y()-4,
                0xFF000000 | MarkerColours.of(primary.kind()));
    }
    private static void drawScreenText(GuiGraphics graphics, String text, int x, int y, int color) {
        PoseStack pose = graphics.pose(); pose.pushPose();
        try { pose.last().pose().identity(); pose.translate(0,0,200); graphics.drawString(Minecraft.getInstance().font, text, x, y, color, true); }
        finally { pose.popPose(); }
    }
    public static Optional<Hit> hit(Screen screen, double x, double y) {
        AtlasRuntime runtime = AtlasRuntime.get();
        if (runtime == null || screen != hitScreen.get() || System.currentTimeMillis()-hitTime > 250
                || runtime.store().snapshot().epoch() != hitEpoch || runtime.binding().busy(screen)) return Optional.empty();
        return hits.stream().filter(hit -> hit.bounds.contains(x,y)).findFirst();
    }
    public static List<Component> tooltip(Hit hit) {
        List<Component> lines = new ArrayList<>();
        for (WaypointSpec spec : hit.quests.stream().limit(8).toList()) {
            lines.add(Component.literal(spec.label()));
            if (!spec.presentation().questTitle().isEmpty()) lines.add(Component.literal(spec.presentation().questTitle()));
            lines.add(Component.translatable("mcaquests.atlas.kind." + spec.kind().name().toLowerCase(Locale.ROOT)));
            if (spec.presentation().approximate()) lines.add(Component.translatable("mcaquests.atlas.approximate"));
            if (spec.presentation().lastKnown()) lines.add(Component.translatable("mcaquests.atlas.last_known"));
            if (spec.presentation().readyToTurnIn()) lines.add(Component.translatable("mcaquests.atlas.ready_to_turn_in"));
            if (AtlasRuntime.get().binding().coordinates()) lines.add(Component.translatable("mcaquests.atlas.coordinates",
                    spec.pos().getX(),spec.pos().getY(),spec.pos().getZ(),spec.dimension().location().toString()));
        }
        if (hit.quests.size() > 1) lines.add(Component.translatable("mcaquests.atlas.group", hit.quests.size()));
        return lines;
    }
    public static int visible(boolean minimap) {
        Observation observation = minimap ? hud : world;
        return System.currentTimeMillis()-observation.time > 250 ? 0 : observation.visible;
    }
    public static Component suppressionSummary() {
        Observation latest = hud.time > world.time ? hud : world;
        if (System.currentTimeMillis()-latest.time > 250) return Component.translatable("mcaquests.atlas.reason.hidden_surface");
        var result = Component.empty();
        latest.suppressed.forEach((reason, count) -> result.append(Component.translatable("mcaquests.atlas.reason." + reason)).append(": " + count + "; "));
        return result;
    }
    public static void reset() { hits = List.of(); hitScreen.clear(); hitTime=0; world=new Observation(0,0,Map.of()); hud=world; }
}
