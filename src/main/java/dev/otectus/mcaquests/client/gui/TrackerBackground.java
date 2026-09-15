package dev.otectus.mcaquests.client.gui;

/**
 * The arithmetic behind the quest tracker's background: which colours the {@code SHADED} style uses,
 * and how {@code questTrackerOpacity} scales them.
 *
 * <p>The two styles used to be the same picture. {@code PANEL} draws the nine-sliced HUD plate, whose
 * fill samples at roughly {@code 0xD8181818}; {@code SHADED} drew a flat {@code 0x80000000} rectangle
 * over exactly the same footprint. Two dark, near-opaque rectangles of the same size are not a choice,
 * so the setting looked broken even though it was being read and branched on correctly every frame.
 * {@code SHADED} is therefore a genuinely lighter treatment now — a soft wash that fades in from the
 * top, so the rows nearest the top of the tracker sit almost directly on the world.
 *
 * <p>Opacity scales the background only. Text, icons and progress bars are drawn afterwards at full
 * strength, so turning the background down to nothing leaves the tracker readable rather than fading
 * the whole overlay out.
 *
 * <p>Deliberately free of Minecraft, Mojang and Forge types — including the config class, which would
 * drag in {@code ForgeConfigSpec} — so it can be exercised by a plain JUnit test with no game
 * bootstrap. The overlay does the {@code HudBackground} branch itself and calls in for the numbers.
 */
public final class TrackerBackground {

    private TrackerBackground() {
    }

    /** The top of the {@code SHADED} wash — barely there, so the first rows read against the world. */
    public static final int SHADED_TOP = 0x50000000;
    /** The bottom of the {@code SHADED} wash, where the rows are densest and need the most backing. */
    public static final int SHADED_BOTTOM = 0x8C000000;

    /**
     * Scales the alpha byte of {@code argb} by {@code opacityPercent}, leaving the colour alone. The
     * percentage is clamped to 0–100, so a hand-edited config cannot produce a wrapped alpha.
     */
    public static int applyOpacity(int argb, int opacityPercent) {
        int pct = clamp(opacityPercent);
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = (alpha * pct + 50) / 100;
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * The same percentage as a shader alpha for {@code RenderSystem.setShaderColor}, which is how the
     * {@code PANEL} plate is dimmed — it is a texture, so its alpha cannot be scaled per-colour.
     */
    public static float panelAlpha(int opacityPercent) {
        return clamp(opacityPercent) / 100.0F;
    }

    /** The {@code SHADED} wash at the given opacity, as {@code {top, bottom}}. */
    public static int[] shadedGradient(int opacityPercent) {
        return new int[] { applyOpacity(SHADED_TOP, opacityPercent), applyOpacity(SHADED_BOTTOM, opacityPercent) };
    }

    private static int clamp(int opacityPercent) {
        return Math.max(0, Math.min(100, opacityPercent));
    }
}
