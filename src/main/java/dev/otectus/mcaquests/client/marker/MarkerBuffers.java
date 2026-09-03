package dev.otectus.mcaquests.client.marker;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * The marker's own vertex buffer, so flushing it cannot flush anybody else's.
 *
 * <p>The renderer used to draw into {@code minecraft.renderBuffers().bufferSource()} and then call
 * the unqualified {@code endBatch()}, which ends <em>every</em> batch queued on the shared source —
 * including vertices another mod, or vanilla itself, had buffered and not yet drawn. It works until
 * it does not, and when it does not the symptom appears in somebody else's renderer.
 *
 * <p>Created on first use, which is on the render thread inside a
 * {@code RenderLevelStageEvent}: {@link BufferBuilder} is not thread-safe and there is no second
 * caller, so there is nothing here to synchronise.
 */
public final class MarkerBuffers {

    /** Big enough for one marker's quads and its label, small enough to be nothing on the heap. */
    private static final int BUFFER_BYTES = 2048;

    private static MultiBufferSource.BufferSource source;

    private MarkerBuffers() {
    }

    /** The renderer-owned source. Only ever called from the client render thread. */
    public static MultiBufferSource.BufferSource get() {
        if (source == null) {
            source = MultiBufferSource.immediate(new BufferBuilder(BUFFER_BYTES));
        }
        return source;
    }
}
