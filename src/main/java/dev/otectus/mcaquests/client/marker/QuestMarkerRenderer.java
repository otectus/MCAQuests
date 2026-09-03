package dev.otectus.mcaquests.client.marker;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.ClientGuidanceData;
import dev.otectus.mcaquests.client.GuidanceText;
import dev.otectus.mcaquests.client.gui.GuiTextures;
import dev.otectus.mcaquests.quest.guidance.ActiveGuidance;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Draws the one place the tracked quest is sending the player: a small glyph on the target itself.
 *
 * <p>Before this the mod's only navigation aid was a line of text on the tracker, so "escort them
 * home" or "enter an ancient city" was an instruction the player had to translate into a direction
 * themselves. The marker is the translation.
 *
 * <p>Four deliberate constraints, three of them corrections of what shipped before 1.5.3:
 *
 * <ul>
 *   <li><b>Exactly one.</b> The server sends one target; there is no list here to filter, and no way
 *       for the world to fill up with markers as the player takes on more quests.</li>
 *   <li><b>On the target, not over it.</b> The old marker stood a twenty-four-block beam on the
 *       objective and put the glyph on top of it, which read as "somewhere up there" and was
 *       genuinely ambiguous between two villagers standing near each other. The glyph now sits on the
 *       upper body or just above the block, at a size chosen so it stays 18–24 pixels wide at any
 *       distance the player can see it from.</li>
 *   <li><b>A wall reads as a wall.</b> The main pass is depth tested, so terrain covers the marker
 *       like it covers everything else; what shows through is a faint hollow diamond, deliberately
 *       different from the marker in the open. {@code questMarkerOcclusion} keeps the old
 *       through-anything behaviour for anyone who wants it.</li>
 *   <li><b>It gets out of the way.</b> The fades take it to nothing inside the objective's arrival
 *       radius, so walking up to the bed removes the marker rather than leaving it standing in the
 *       player's face while they try to see what they came for.</li>
 * </ul>
 *
 * <p>An entity-backed target follows the live entity by id, interpolated onto the current frame by
 * {@link MarkerAnchorResolver}, so an escortee's marker moves smoothly between the server's
 * once-a-second recomputes rather than stepping twenty times a second. Nothing else here animates:
 * {@link MarkerVisualState} changes opacity over time and never position.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID, value = Dist.CLIENT)
public final class QuestMarkerRenderer {

    /** A sliver of daylight under a surface-aligned glyph, in logical pixels. */
    private static final double SURFACE_CLEARANCE_PX = 2.0D;
    /** Half the diamond frame, in logical pixels. The frame is 24 wide. */
    private static final float FRAME_HALF = 12.0F;
    /** Half the glyph inside it. */
    private static final float GLYPH_HALF = 10.0F;
    /** How far above the frame the label's underside sits, in logical pixels. */
    private static final int LABEL_GAP = 4;
    /** How wide a label may get before it is cut, in logical pixels. */
    private static final int LABEL_MAX_WIDTH = 160;
    /** How far inside the screen the edge indicator stays, in GUI-scaled pixels. */
    public static final double EDGE_INSET = 18.0D;

    /** The dark the frame is outlined in. Not black: black on night sky is a hole. */
    private static final int OUTLINE = 0x101820;
    /** Label text, just off white so it does not vibrate against its own background. */
    private static final int LABEL_TEXT = 0xF2F2F2;

    /** How high the ground ring floats above what it is drawn on, in blocks. */
    private static final double RING_LIFT = 0.02D;
    /** How thick the ring's stroke is, in blocks. */
    private static final double RING_STROKE = 0.035D;
    /** The ring's radius for a target that is not an entity, in blocks. */
    private static final double FIXED_RING_RADIUS = 0.45D;
    /** How many segments the ring is drawn in. Round enough at any size it is drawn at. */
    private static final int RING_SEGMENTS = 32;
    /** How wide the stem from ring to glyph is, in blocks. */
    private static final double STEM_WIDTH = 0.025D;
    /** Below this, the stem is shorter than it is wide and simply omitted. */
    private static final double STEM_MIN_LENGTH = 0.15D;
    /** How wide the HIGH_VISIBILITY column is, in blocks. */
    private static final double COLUMN_WIDTH = 0.18D;
    /** How tall it is, in blocks. */
    private static final double COLUMN_HEIGHT = 6.0D;
    /** How solid it is at the base, before the marker's own alpha. */
    private static final float COLUMN_ALPHA = 0.28F;

    /** How long a resolved surface is trusted before it is read again, in ticks. */
    private static final long FIXED_ANCHOR_TICKS = 20L;

    /** The ring's unit circle, computed once. */
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];

    static {
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = (Math.PI * 2.0D * i) / RING_SEGMENTS;
            RING_COS[i] = (float) Math.cos(angle);
            RING_SIN[i] = (float) Math.sin(angle);
        }
    }

    /** How solid the marker is because of when it appeared, as opposed to where it is. */
    private static final MarkerVisualState STATE = new MarkerVisualState();

    // The support surface under a fixed target costs two block-state reads and two shape lookups, and
    // answers the same thing every frame for as long as the target stands still. Cached against the
    // guidance revision, the target itself, and a second of game time -- the client has no cheap
    // "this block changed" hook, so the second is what covers a player mining the floor out.
    private static MarkerAnchor fixedAnchor;
    private static BlockPos fixedAnchorPos;
    private static ResourceKey<Level> fixedAnchorDimension;
    private static long fixedAnchorRevision = -1L;
    private static long fixedAnchorTick;

    // What was drawn last frame, so that a retarget has something to fade out. The anchor is frozen
    // rather than re-resolved: the old target is on its way out over a tenth of a second, and a
    // villager who walked two blocks in that time does not need following any more.
    private static GuidanceTarget lastTarget;
    private static MarkerAnchor lastAnchor;
    private static GuidanceTarget fadingTarget;
    private static MarkerAnchor fadingAnchor;

    // The label is a translated component with a distance in it, so it is rebuilt only when the
    // distance the player would read actually changes -- not sixty times a second while they stand
    // still.
    private static Component labelText;
    private static int labelWidth;
    private static int labelIdentity;
    private static long labelDistance = Long.MIN_VALUE;
    private static boolean labelApproximate;

    private QuestMarkerRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // After particles: past everything solid, so the marker blends over the world rather than
        // being blended into by it, and before the clouds and weather that should draw in front.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        MarkerSettings settings = MarkerSettings.current();
        if (!settings.enabled()) {
            // Not a fade: the player turned the feature off, and a marker that lingers for a tenth of
            // a second afterwards looks like the setting did not take.
            STATE.reset();
            forgetFading();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        // 1.21 hands the stage event a DeltaTracker rather than a float. {@code ignoreFreeze=false}
        // is the entity-rendering behaviour: while the world is frozen the tracker answers 1.0, so
        // the marker sits on the entity's last tick position instead of gliding on without it.
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        long now = Util.getMillis();
        // The primary entry, never the whole list: the snapshot names a destination per quest so the
        // tracker can print them all, and the server marks exactly one of them as the marker's.
        // Drawing the list would be five markers for five quests, which is the mistake highlighting
        // made.
        Optional<ActiveGuidance> guidance = ClientGuidanceData.primary();
        GuidanceTarget target = null;
        MarkerAnchor anchor = null;
        if (guidance.isPresent()
                && minecraft.level.dimension().equals(guidance.get().target().dimension())) {
            // A marker for another dimension would be drawn at a coordinate that means nothing here --
            // the Nether's are the overworld's divided by eight. Objectives answer with a route.
            target = guidance.get().target();
            anchor = anchor(minecraft.level, target, partialTick);
            MarkerVisualState.Key key = new MarkerVisualState.Key(
                    guidance.get().questId(), guidance.get().villagerUuid(), identity(target));
            if (STATE.observe(key, now, settings.reducedMotion())) {
                fadingTarget = lastTarget;
                fadingAnchor = lastAnchor;
            }
        } else if (STATE.clear(now)) {
            fadingTarget = lastTarget;
            fadingAnchor = lastAnchor;
        }
        lastTarget = target;
        lastAnchor = anchor;

        float lifetime = STATE.lifetimeAlpha(now);
        float previous = STATE.previousAlpha(now);
        if (previous <= 0.0F) {
            forgetFading();
        }
        if (lifetime <= 0.0F && previous <= 0.0F) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        int framebufferHeight = minecraft.getWindow().getHeight();
        float m11 = event.getProjectionMatrix().m11();

        // The one on its way out first, so the one arriving is drawn over it.
        if (previous > 0.0F && fadingTarget != null && fadingAnchor != null
                && minecraft.level.dimension().equals(fadingTarget.dimension())) {
            drawMarker(minecraft, event, camera, eye, fadingTarget, fadingAnchor, previous, settings,
                    m11, framebufferHeight, false);
        }
        if (lifetime > 0.0F && target != null) {
            drawMarker(minecraft, event, camera, eye, target, anchor, lifetime, settings,
                    m11, framebufferHeight, true);
        }
        MarkerBuffers.get().endBatch();
    }

    private static void forgetFading() {
        fadingTarget = null;
        fadingAnchor = null;
    }

    /**
     * What makes one marker a different marker rather than the same one somewhere else.
     *
     * <p>An entity is its network id, so a villager who walks stays the same marker. Anything else is
     * its position and kind, so a quest that moves the player from a bed to the workstation beside it
     * cross-fades rather than teleporting the glyph.
     */
    private static int identity(GuidanceTarget target) {
        return target.entityId().isPresent()
                ? target.entityId().getAsInt()
                : target.pos().hashCode() * 31 + target.kind().ordinal();
    }

    /**
     * Where to draw: the live entity when the target is one and it is loaded, otherwise the position
     * the server sent.
     *
     * <p>The sent position is the fallback for a villager who has since left render distance, which
     * is exactly when the marker is most worth having — so it still carries the entity's height, and
     * the glyph still lands on a body rather than on a pair of feet.
     */
    private static MarkerAnchor anchor(ClientLevel level, GuidanceTarget target, float partialTick) {
        if (target.entityId().isPresent()) {
            Entity entity = level.getEntity(target.entityId().getAsInt());
            if (entity != null) {
                return MarkerAnchorResolver.forEntity(entity.xo, entity.yo, entity.zo,
                        entity.getX(), entity.getY(), entity.getZ(),
                        entity.getBbWidth(), entity.getBbHeight(), partialTick);
            }
            return MarkerAnchorResolver.forUnloadedEntity(target.pos(), target.entityHeight());
        }
        return fixedAnchor(level, target.pos());
    }

    /** The support surface under {@code pos}, resolved at most once a second rather than per frame. */
    private static MarkerAnchor fixedAnchor(ClientLevel level, BlockPos pos) {
        long revision = ClientGuidanceData.revision();
        long tick = level.getGameTime();
        ResourceKey<Level> dimension = level.dimension();
        boolean stale = fixedAnchor == null
                || revision != fixedAnchorRevision
                || !pos.equals(fixedAnchorPos)
                || !dimension.equals(fixedAnchorDimension)
                || Math.abs(tick - fixedAnchorTick) >= FIXED_ANCHOR_TICKS;
        if (stale) {
            fixedAnchor = MarkerAnchorResolver.forFixed(pos, shapeOf(level, pos),
                    shapeOf(level, pos.below()));
            fixedAnchorPos = pos.immutable();
            fixedAnchorDimension = dimension;
            fixedAnchorRevision = revision;
            fixedAnchorTick = tick;
        }
        return fixedAnchor;
    }

    /**
     * What a block occupies: what you would stand on, or failing that what you can see.
     *
     * <p>A bed or an anvil answers with its collision shape. A portal, a torch or a bell has none
     * and would otherwise read as thin air, so the visual shape is asked second — the marker belongs
     * on top of the thing the objective named, not on the floor under it.
     */
    private static VoxelShape shapeOf(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape collision = state.getCollisionShape(level, pos);
        return collision.isEmpty() ? state.getShape(level, pos) : collision;
    }

    /**
     * One marker, at whatever strength its lifetime says.
     *
     * @param lifetime how solid this marker is before distance is taken into account
     * @param primary  true for the current target, which is the one the HUD indicator is about; the
     *                 one fading out publishes nothing, so the indicator does not flick between two
     */
    private static void drawMarker(Minecraft minecraft, RenderLevelStageEvent event, Camera camera,
                                   Vec3 eye, GuidanceTarget target, MarkerAnchor anchor,
                                   float lifetime, MarkerSettings settings,
                                   float m11, int framebufferHeight, boolean primary) {
        double relX = anchor.x() - eye.x;
        double relZ = anchor.z() - eye.z;
        double distance = MarkerGeometry.horizontalDistance(relX, relZ);
        float alpha = lifetime * Math.min(
                MarkerGeometry.arrivalAlpha(distance, target.arriveRadius()),
                MarkerGeometry.farAlpha(distance, target.arriveRadius(), settings.maxDistance()));
        if (alpha <= 0.0F) {
            return;
        }

        // Depth along the camera's own forward axis, not the distance to the eye: the perspective
        // divide is a function of the former, and using the latter would make the marker grow as it
        // slid toward the edge of the screen.
        double relY = anchor.glyphY() - eye.y;
        Vector3f look = camera.getLookVector();
        double depth = relX * look.x() + relY * look.y() + relZ * look.z();
        double worldPerPixel = MarkerGeometry.worldPerPixel(depth, m11, framebufferHeight);
        double worldSize = MarkerGeometry.worldSize(distance, settings.maxDistance(), worldPerPixel);
        boolean hudGlyph = MarkerGeometry.usesHudFallback(worldSize, worldPerPixel);
        float pixel = (float) (worldSize / (FRAME_HALF * 2.0F));

        double glyphOffset = glyphOffset(anchor, worldSize, pixel);

        boolean edgeActive = false;
        if (primary) {
            MarkerProjection.Projection projection = MarkerProjection.project(
                    relX, anchor.glyphY() - eye.y, relZ,
                    event.getPoseStack().last().pose(), event.getProjectionMatrix());
            MarkerProjection.EdgePoint edge = MarkerProjection.clamp(projection,
                    minecraft.getWindow().getGuiScaledWidth(),
                    minecraft.getWindow().getGuiScaledHeight(), EDGE_INSET);
            edgeActive = settings.edgeIndicator() && !edge.onScreen();
            MarkerFrameState.CURRENT.publish(
                    MarkerFrameState.CURRENT.nextFrameId(event.getRenderTick()),
                    edgeActive, hudGlyph && !edgeActive, edge.x(), edge.y(), edge.angleRadians(),
                    MarkerColours.of(target.kind()), Math.round(distance), target.kind(), alpha);
        }
        if (edgeActive) {
            // The billboard would be somewhere off the side of the screen, or behind the player. The
            // HUD draws the arrow instead; a world quad clamped to the edge would be a lie about
            // where the target is.
            return;
        }

        MultiBufferSource.BufferSource buffers = MarkerBuffers.get();
        PoseStack pose = event.getPoseStack();
        int colour = settings.highContrast() ? 0xFFFFFF : MarkerColours.of(target.kind());
        McaQuestsConfig.Client.MarkerOcclusion occlusion = settings.occlusion();
        boolean throughWalls = occlusion == McaQuestsConfig.Client.MarkerOcclusion.FULL;

        pose.pushPose();
        try {
            // Camera-relative in doubles before anything touches a float matrix: at the world border
            // the target's own coordinates have no float precision left, but the offset always does.
            pose.translate(relX, anchor.baseY() - eye.y, relZ);

            // The occluded pass first and without depth, so that where the target is visible the
            // depth-tested pass simply covers it, and where it is not this is all that remains.
            if (occlusion == McaQuestsConfig.Client.MarkerOcclusion.DIM_OUTLINE) {
                pose.pushPose();
                try {
                    billboard(pose, camera, glyphOffset, pixel);
                    occludedOutline(pose, buffers, colour, alpha, settings.highContrast());
                } finally {
                    pose.popPose();
                }
            }

            if (settings.style() != McaQuestsConfig.Client.MarkerStyle.ICON_ONLY) {
                ring(pose, buffers, anchor, colour, alpha);
                stem(pose, buffers, eye, anchor, glyphOffset, colour, alpha);
            }
            if (settings.style() == McaQuestsConfig.Client.MarkerStyle.HIGH_VISIBILITY) {
                column(pose, buffers, colour, alpha);
            }

            if (!hudGlyph) {
                pose.pushPose();
                try {
                    billboard(pose, camera, glyphOffset, pixel);
                    frame(pose, buffers, colour, alpha, settings.highContrast(), throughWalls);
                    glyph(pose, buffers, target, alpha, throughWalls);
                    if (settings.style() != McaQuestsConfig.Client.MarkerStyle.ICON_ONLY
                            && MarkerGeometry.labelVisible(settings.labels(), distance)) {
                        label(minecraft.font, pose, buffers, target, distance, alpha,
                                settings.highContrast(), throughWalls);
                    }
                } finally {
                    pose.popPose();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    /**
     * Face the camera and switch to logical pixels.
     *
     * <p>A full spherical billboard, the same one vanilla builds for a name tag: the marker is most
     * often read while looking steeply up or down at it, and a Y-axis-only billboard foreshortens the
     * glyph and makes the label unreadable at exactly that angle. Both scales are negative because
     * the camera's frame has +Y down and +X left; after this, everything is drawn in screen pixels.
     */
    private static void billboard(PoseStack pose, Camera camera, double glyphOffset, float pixel) {
        pose.translate(0.0D, glyphOffset, 0.0D);
        pose.mulPose(camera.rotation());
        pose.scale(-pixel, -pixel, pixel);
    }

    /**
     * How far the glyph's centre sits above the marker's base.
     *
     * <p>A body anchor is already a centre. A surface anchor is the top of a block, so half the frame
     * plus two pixels of daylight is added — otherwise the lower half of the glyph would be buried in
     * the bed. Expressed in apparent pixels rather than blocks, so the gap looks the same from every
     * distance instead of growing with the marker.
     */
    private static double glyphOffset(MarkerAnchor anchor, double worldSize, float pixel) {
        double offset = anchor.glyphY() - anchor.baseY();
        if (anchor.alignment() == MarkerAnchor.VerticalAlignment.BOTTOM_ON_SURFACE) {
            offset += worldSize / 2.0D + SURFACE_CLEARANCE_PX * pixel;
        }
        return offset;
    }

    /** What shows through a wall: a hollow diamond, dim, and visibly not the marker in the open. */
    private static void occludedOutline(PoseStack pose, MultiBufferSource buffers, int colour,
                                        float alpha, boolean highContrast) {
        VertexConsumer builder = buffers.getBuffer(MarkerRenderTypes.OCCLUDED_OUTLINE);
        Matrix4f matrix = pose.last().pose();
        float outline = highContrast ? 3.0F : 2.0F;
        float fill = highContrast ? 0.40F : 0.22F;
        band(builder, matrix, FRAME_HALF, FRAME_HALF - outline, OUTLINE, alpha * 0.30F);
        band(builder, matrix, FRAME_HALF - outline, FRAME_HALF - outline - 2.0F, colour, alpha * fill);
    }

    /** The frame the glyph sits in: a dark outline, a stroke in the kind's colour, and a dark fill. */
    private static void frame(PoseStack pose, MultiBufferSource buffers, int colour, float alpha,
                              boolean highContrast, boolean throughWalls) {
        VertexConsumer builder = buffers.getBuffer(throughWalls
                ? MarkerRenderTypes.OCCLUDED_OUTLINE : MarkerRenderTypes.VISIBLE_SHAPE);
        Matrix4f matrix = pose.last().pose();
        float outline = highContrast ? 3.0F : 2.0F;
        // Three filled diamonds, smallest last. Nothing writes depth, so draw order alone stacks them
        // and there is no z-fighting to offset away from.
        diamond(builder, matrix, FRAME_HALF, highContrast ? 0x000000 : OUTLINE, alpha * 0.95F);
        diamond(builder, matrix, FRAME_HALF - outline, colour, alpha * 0.95F);
        diamond(builder, matrix, FRAME_HALF - outline - 2.0F, 0x000000, alpha * 0.35F);
    }

    /** A filled diamond of half-diagonal {@code half}, centred on the origin. */
    private static void diamond(VertexConsumer builder, Matrix4f matrix, float half, int colour,
                                float alpha) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int a = (int) (alpha * 255.0F);
        builder.addVertex(matrix, 0.0F, -half, 0.0F).setColor(r, g, b, a);
        builder.addVertex(matrix, half, 0.0F, 0.0F).setColor(r, g, b, a);
        builder.addVertex(matrix, 0.0F, half, 0.0F).setColor(r, g, b, a);
        builder.addVertex(matrix, -half, 0.0F, 0.0F).setColor(r, g, b, a);
    }

    /** The ring between two diamonds, so the middle stays empty rather than being painted over. */
    private static void band(VertexConsumer builder, Matrix4f matrix, float outer, float inner,
                             int colour, float alpha) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int a = (int) (alpha * 255.0F);
        // Four trapezoids, one per side of the diamond.
        trapezoid(builder, matrix, 0.0F, -outer, outer, 0.0F, inner, 0.0F, 0.0F, -inner, r, g, b, a);
        trapezoid(builder, matrix, outer, 0.0F, 0.0F, outer, 0.0F, inner, inner, 0.0F, r, g, b, a);
        trapezoid(builder, matrix, 0.0F, outer, -outer, 0.0F, -inner, 0.0F, 0.0F, inner, r, g, b, a);
        trapezoid(builder, matrix, -outer, 0.0F, 0.0F, -outer, 0.0F, -inner, -inner, 0.0F, r, g, b, a);
    }

    private static void trapezoid(VertexConsumer builder, Matrix4f matrix,
                                  float x0, float y0, float x1, float y1,
                                  float x2, float y2, float x3, float y3,
                                  int r, int g, int b, int a) {
        builder.addVertex(matrix, x0, y0, 0.0F).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y1, 0.0F).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y2, 0.0F).setColor(r, g, b, a);
        builder.addVertex(matrix, x3, y3, 0.0F).setColor(r, g, b, a);
    }

    /** The kind's glyph off {@code icons.png}, inside the frame. */
    private static void glyph(PoseStack pose, MultiBufferSource buffers, GuidanceTarget target,
                              float alpha, boolean throughWalls) {
        GuiTextures.Sprite sprite = MarkerIcons.of(target.kind());
        VertexConsumer builder = buffers.getBuffer(throughWalls
                ? MarkerRenderTypes.OCCLUDED_ICON : MarkerRenderTypes.VISIBLE_ICON);
        Matrix4f matrix = pose.last().pose();
        float u0 = sprite.u() / (float) GuiTextures.SHEET;
        float u1 = (sprite.u() + sprite.width()) / (float) GuiTextures.SHEET;
        float v0 = sprite.v() / (float) GuiTextures.SHEET;
        float v1 = (sprite.v() + sprite.height()) / (float) GuiTextures.SHEET;
        int a = (int) (alpha * 255.0F);
        // Pixel space runs the same way the sheet does -- +Y down -- so the V pair is not swapped.
        builder.addVertex(matrix, -GLYPH_HALF, -GLYPH_HALF, 0.0F).setColor(255, 255, 255, a).setUv(u0, v0);
        builder.addVertex(matrix, -GLYPH_HALF, GLYPH_HALF, 0.0F).setColor(255, 255, 255, a).setUv(u0, v1);
        builder.addVertex(matrix, GLYPH_HALF, GLYPH_HALF, 0.0F).setColor(255, 255, 255, a).setUv(u1, v1);
        builder.addVertex(matrix, GLYPH_HALF, -GLYPH_HALF, 0.0F).setColor(255, 255, 255, a).setUv(u1, v0);
    }

    /** The target's name and how far it is, above the frame, in vanilla's name-tag style. */
    private static void label(Font font, PoseStack pose, MultiBufferSource buffers,
                              GuidanceTarget target, double distance, float alpha,
                              boolean highContrast, boolean throughWalls) {
        Component text = label(font, target, distance);
        float top = -FRAME_HALF - LABEL_GAP - font.lineHeight;
        float half = labelWidth / 2.0F;

        VertexConsumer builder = buffers.getBuffer(throughWalls
                ? MarkerRenderTypes.OCCLUDED_OUTLINE : MarkerRenderTypes.VISIBLE_SHAPE);
        Matrix4f matrix = pose.last().pose();
        int background = (int) (alpha * (highContrast ? 0.80F : 0.65F) * 255.0F);
        trapezoid(builder, matrix, -half - 2.0F, top - 1.0F, half + 2.0F, top - 1.0F,
                half + 2.0F, top + font.lineHeight + 1.0F, -half - 2.0F, top + font.lineHeight + 1.0F,
                0, 0, 0, background);

        int textAlpha = (int) (alpha * 0.95F * 255.0F) << 24;
        // NORMAL rather than SEE_THROUGH: a label that reads through the mountain in front of it is a
        // label the player cannot tell from one on this side of it. FULL occlusion is the exception,
        // because being told everything through everything is the whole of what it asks for.
        font.drawInBatch(text, -half, top, LABEL_TEXT | textAlpha, false, matrix, buffers,
                throughWalls ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL,
                0, LightTexture.FULL_BRIGHT);
    }

    /**
     * The label text, rebuilt only when what it would say has changed.
     *
     * <p>Translating a component and measuring it is not free, and the answer changes at most once
     * per whole block walked. Cached on the target, on the distance the player actually reads, and on
     * whether that distance is being called approximate.
     */
    private static Component label(Font font, GuidanceTarget target, double distance) {
        int identity = identity(target);
        long rounded = Math.round(distance);
        if (labelText != null && identity == labelIdentity && rounded == labelDistance
                && target.approximate() == labelApproximate) {
            return labelText;
        }
        Component text = GuidanceText.markerLabel(target, distance);
        int width = font.width(text);
        if (width > LABEL_MAX_WIDTH) {
            // Cut rather than wrapped: a marker is glanced at, and two lines of text floating over a
            // villager is a sign, not a label.
            String ellipsis = "...";
            String cut = font.plainSubstrByWidth(text.getString(),
                    LABEL_MAX_WIDTH - font.width(ellipsis));
            text = Component.literal(cut + ellipsis);
            width = font.width(text);
        }
        labelText = text;
        labelWidth = width;
        labelIdentity = identity;
        labelDistance = rounded;
        labelApproximate = target.approximate();
        return text;
    }

    /**
     * The ring on the ground under the target.
     *
     * <p>Left in world orientation rather than billboarded, and depth tested: it is the part of the
     * marker that says <em>which patch of ground</em>, and a ring that faces the camera says nothing
     * about the floor it is on.
     */
    private static void ring(PoseStack pose, MultiBufferSource buffers, MarkerAnchor anchor,
                             int colour, float alpha) {
        double radius = anchor.alignment() == MarkerAnchor.VerticalAlignment.BOTTOM_ON_SURFACE
                ? FIXED_RING_RADIUS
                : MarkerGeometry.ringRadius(anchor.targetWidth());
        VertexConsumer builder = buffers.getBuffer(MarkerRenderTypes.VISIBLE_SHAPE);
        Matrix4f matrix = pose.last().pose();
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int a = (int) (alpha * 0.55F * 255.0F);
        float y = (float) RING_LIFT;
        float inner = (float) (radius - RING_STROKE / 2.0D);
        float outer = (float) (radius + RING_STROKE / 2.0D);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float c0 = RING_COS[i];
            float s0 = RING_SIN[i];
            float c1 = RING_COS[i + 1];
            float s1 = RING_SIN[i + 1];
            builder.addVertex(matrix, inner * c0, y, inner * s0).setColor(r, g, b, a);
            builder.addVertex(matrix, outer * c0, y, outer * s0).setColor(r, g, b, a);
            builder.addVertex(matrix, outer * c1, y, outer * s1).setColor(r, g, b, a);
            builder.addVertex(matrix, inner * c1, y, inner * s1).setColor(r, g, b, a);
        }
    }

    /**
     * The thread from the ring up to the glyph, so the two read as one marker.
     *
     * <p>Camera-facing about its vertical axis only: it is a vertical line in the world and should
     * stay one, but it must not vanish edge-on when the player walks round it.
     */
    private static void stem(PoseStack pose, MultiBufferSource buffers, Vec3 eye, MarkerAnchor anchor,
                             double glyphOffset, int colour, float alpha) {
        double top = glyphOffset - RING_LIFT;
        if (top < STEM_MIN_LENGTH) {
            return;
        }
        double dx = anchor.x() - eye.x;
        double dz = anchor.z() - eye.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        // Perpendicular to the line from the camera, in the horizontal plane: the widest the strip
        // can be from where the player is standing.
        float px;
        float pz;
        if (length < 1.0E-4D) {
            px = (float) (STEM_WIDTH / 2.0D);
            pz = 0.0F;
        } else {
            px = (float) (-dz / length * STEM_WIDTH / 2.0D);
            pz = (float) (dx / length * STEM_WIDTH / 2.0D);
        }
        VertexConsumer builder = buffers.getBuffer(MarkerRenderTypes.VISIBLE_SHAPE);
        Matrix4f matrix = pose.last().pose();
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int a = (int) (alpha * 0.35F * 255.0F);
        float y0 = (float) RING_LIFT;
        float y1 = (float) glyphOffset;
        builder.addVertex(matrix, -px, y0, -pz).setColor(r, g, b, a);
        builder.addVertex(matrix, px, y0, pz).setColor(r, g, b, a);
        builder.addVertex(matrix, px, y1, pz).setColor(r, g, b, a);
        builder.addVertex(matrix, -px, y1, -pz).setColor(r, g, b, a);
    }

    /**
     * The {@code HIGH_VISIBILITY} column: a short translucent shaft standing on the target.
     *
     * <p>Depth tested like the rest of the marker, and it does not move the glyph — the old beam put
     * the icon on top of a twenty-four-block column, which is what made the marker read as being in
     * the sky. Two crossed quads rather than four sides: from any angle at least one is near face-on,
     * and a box would read as a solid block of colour from a corner.
     */
    private static void column(PoseStack pose, MultiBufferSource buffers, int colour, float alpha) {
        VertexConsumer builder = buffers.getBuffer(MarkerRenderTypes.VISIBLE_SHAPE);
        Matrix4f matrix = pose.last().pose();
        float half = (float) (COLUMN_WIDTH / 2.0D);
        float top = (float) COLUMN_HEIGHT;
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int a = (int) (alpha * COLUMN_ALPHA * 255.0F);
        columnQuad(builder, matrix, -half, -half, half, half, top, r, g, b, a);
        columnQuad(builder, matrix, -half, half, half, -half, top, r, g, b, a);
    }

    /** One vertical quad, at full strength on the ground and gone by the top. */
    private static void columnQuad(VertexConsumer builder, Matrix4f matrix, float x0, float z0,
                                   float x1, float z1, float top, int r, int g, int b, int a) {
        builder.addVertex(matrix, x0, 0.0F, z0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, 0.0F, z1).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, top, z1).setColor(r, g, b, 0);
        builder.addVertex(matrix, x0, top, z0).setColor(r, g, b, 0);
    }
}
