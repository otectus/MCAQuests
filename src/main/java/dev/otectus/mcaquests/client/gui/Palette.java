package dev.otectus.mcaquests.client.gui;

/**
 * Every colour the mod's interface uses, named once.
 *
 * <p>Before this class there was not a single named colour constant in the codebase: roughly two
 * dozen raw ARGB literals were scattered across five screens, the HUD and four toasts, and the same
 * meaning was spelled differently in different files. That is how {@code 0xFFD24C} came to sit next
 * to {@code 0xFFD24D} — the reputation toast's heading and the log's "suspended" marker are the same
 * idea, one blue-channel unit apart, and nobody could have caught it reviewing a diff.
 *
 * <h2>Dark on light, because the panel is vanilla's</h2>
 *
 * <p>The screens are drawn on vanilla's own container grey ({@code #C6C6C6} face, {@code #8B8B8B}
 * inset — see {@code tools/gen_gui_textures.py}), so every colour below is a <em>dark</em> ink chosen
 * to sit on it, the way vanilla labels its own inventories. The first cut of these sheets went the
 * other way: dark oak panels, because the mod's text was already light. That reasoning was backwards
 * — it preserved a palette and retinted the game — and reversing it is what makes the interface read
 * as part of Minecraft rather than beside it.
 *
 * <p>Two consequences worth knowing before adding a colour here:
 * <ul>
 *   <li>Text on a panel is drawn <b>without a shadow</b>. Vanilla's {@code Font} shadow is a dark
 *       offset copy, which on a light ground reads as a smear rather than as depth. Use the
 *       {@code GuiGraphics.drawString(font, text, x, y, colour, false)} overload on every screen.</li>
 *   <li>A new colour must clear roughly 4.5:1 against {@code #C6C6C6}, or it will vanish for a
 *       player at a low brightness setting. That is why the greens and blues here are so much
 *       darker than the ones the HUD uses.</li>
 * </ul>
 *
 * <p>Text colours carry no alpha byte. Vanilla's {@code Font} forces a zero-alpha colour opaque, so
 * {@code 0x404040} draws as expected.
 *
 * @see Hud for the one surface that is still light-on-dark, and why.
 */
public final class Palette {

    private Palette() {
    }

    // --- text on a panel ---------------------------------------------------------------------

    /** Screen titles and primary body text. Vanilla's own label grey. */
    public static final int TEXT = 0x404040;
    /**
     * A quest, project or village name.
     *
     * <p>The same ink as {@link #TEXT} on purpose. On the old dark panel a title was picked out in
     * warm tan; on a light card the frame around it already says "this is a card", and a second
     * colour competing with the objective lines below it only made the eye work harder.
     */
    public static final int TITLE = 0x404040;
    /** A quest whose objectives are all satisfied, and a completed project phase. */
    public static final int READY = 0x206020;
    /** Spoken dialogue. Softer than body text, so flavour never outweighs the objective. */
    public static final int DIALOGUE = 0x505050;
    /** Objective and requirement lines. */
    public static final int OBJECTIVE = 0x555555;
    /** Sub-labels: profession, chain, scope, phase. */
    public static final int SUBTITLE = 0x707070;
    /** "You have no quests" and friends. */
    public static final int EMPTY = 0x808080;
    /** Section headings and links. */
    public static final int HEADING = 0x2C5C8A;
    /** An earned player title. */
    public static final int PLAYER_TITLE = 0x5B3E8A;
    /** Reward lines. */
    public static final int REWARD = 0x2A6B2A;
    /** Your own share of a shared total. */
    public static final int CONTRIBUTION = 0x2C5C8A;
    /** Read-only Townstead context — the quietest thing on any screen, deliberately. */
    public static final int CONTEXT = 0x4E6570;
    /**
     * Something is not wrong, but it is not proceeding either: a suspended quest, a deadline
     * approaching, a reputation tier changing.
     */
    public static final int WARNING = 0x8A6A00;
    /** A deadline close enough to matter, and an objective whose target is gone. */
    public static final int URGENT = 0xA02020;
    /** The line naming where the quest is sending you. */
    public static final int DIRECTION = 0x2C5C8A;
    /** A control that exists but cannot be used right now. */
    public static final int DISABLED = 0x8B8B8B;

    // --- text on a widget --------------------------------------------------------------------
    // Buttons keep vanilla's dark widget face (see tools/gen_gui_textures.py), because a vanilla
    // container is a light slab with dark controls on it. So their labels are vanilla's own, and
    // they keep their shadow: on a dark button a shadow is depth, not a smear.

    /** A button label. Vanilla's {@code AbstractWidget} white. */
    public static final int BUTTON_LABEL = 0xFFFFFF;
    /** A button label on a control that cannot be used. Vanilla's own disabled grey. */
    public static final int BUTTON_LABEL_DISABLED = 0xA0A0A0;

    /** Quest-ready toast heading. Toasts use vanilla's light {@code toast/advancement} frame, so: dark ink. */
    public static final int TOAST_READY = 0x7A5A00;
    /** Situation-opened toast heading. */
    public static final int TOAST_SITUATION = 0x7A5A00;

    /**
     * The HUD tracker's colours, which are the mirror image of the ones above.
     *
     * <p>{@code Panel.hud} is a translucent dark plate drawn <em>over the live world</em>, not a
     * panel inside a window. A light grey slab there is unreadable against snow, sand and sky, and
     * dark ink on it is unreadable full stop. So the tracker keeps the light-on-dark palette the
     * whole mod used before 1.5.0, and keeps its text shadows, which do work on a dark ground.
     *
     * <p>This is the only exception. Anything drawn inside a window uses the constants above.
     */
    public static final class Hud {

        private Hud() {
        }

        /** Quest and project titles in the tracker. */
        public static final int TEXT = 0xFFFFFF;
        /** Section headings ("Quests", "Projects"). */
        public static final int TITLE = 0xFFFFFF;
        /** A quest whose objectives are all satisfied. */
        public static final int READY = 0x7BD97B;
        /** Objective lines and counters. */
        public static final int OBJECTIVE = 0xBFBFBF;
        /** Sub-labels under a tracked entry. */
        public static final int SUBTITLE = 0xBFBFBF;
        /** A deadline approaching. */
        public static final int WARNING = 0xFFD24D;
        /** A deadline close enough to matter. */
        public static final int URGENT = 0xFF5C5C;
        /** The guidance line: where you are being sent, how far, and which way. */
        public static final int DIRECTION = 0x8AD8FF;
        /** The project section heading. */
        public static final int HEADING = 0x5CC8FF;

        /**
         * The {@code SHADED} tracker background, for players who want a plain scrim rather than the
         * nine-sliced plate. Used with {@code GuiGraphics.fill}, so the alpha byte is significant.
         */
        public static final int FILL_SHADED = 0x80000000;
    }
}
