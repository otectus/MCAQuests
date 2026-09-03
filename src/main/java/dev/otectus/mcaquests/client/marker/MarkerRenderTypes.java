package dev.otectus.mcaquests.client.marker;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.gui.GuiTextures;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * The render types the marker needs, none of which vanilla exposes.
 *
 * <p>{@code RenderType.create} and the state shards it takes are protected, so reaching them means
 * extending {@link RenderType} — the standard route, and the only reason this class is a subclass of
 * something it never instantiates.
 *
 * <p>Two depth behaviours, deliberately separate. The visible types test depth, so the marker is
 * covered by the wall in front of it like anything else in the world; the occluded type does not,
 * and is what remains of the marker when the target is behind terrain. Everything about blending,
 * culling and write masks lives in this immutable state rather than in {@code RenderSystem} calls,
 * so no marker draw can leave the pipeline in a state the next renderer has to guess at.
 */
public final class MarkerRenderTypes extends RenderType {

    /**
     * Never called. The class exists only to be a subclass, which is the access this needs; it is
     * declared so that no compiler-generated public constructor suggests otherwise.
     */
    private MarkerRenderTypes() {
        super("", DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS, 0, false, false,
                () -> {
                }, () -> {
                });
    }

    /** The glyph off {@code icons.png}, drawn where the target can actually be seen. */
    public static final RenderType VISIBLE_ICON = RenderType.create(
            McaQuests.MOD_ID + ":marker_visible_icon", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            GuiTextures.ICON_SHEET, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /** Untextured geometry — rings, stems, columns — in the same depth-tested pass as the glyph. */
    public static final RenderType VISIBLE_SHAPE = RenderType.create(
            McaQuests.MOD_ID + ":marker_visible_shape", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /**
     * The glyph with no depth test at all.
     *
     * <p>Only {@code questMarkerOcclusion=FULL} reaches this: the marker as it was drawn before
     * 1.5.3, solid through a mountain. It is an accessibility and legacy choice rather than the
     * default, so it lives here rather than in the normal path.
     */
    public static final RenderType OCCLUDED_ICON = RenderType.create(
            McaQuests.MOD_ID + ":marker_occluded_icon", DefaultVertexFormat.POSITION_COLOR_TEX,
            VertexFormat.Mode.QUADS, 512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            GuiTextures.ICON_SHEET, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    /** What is left of the marker through a wall: colour only, no depth test and no depth write. */
    public static final RenderType OCCLUDED_OUTLINE = RenderType.create(
            McaQuests.MOD_ID + ":marker_occluded", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
}
