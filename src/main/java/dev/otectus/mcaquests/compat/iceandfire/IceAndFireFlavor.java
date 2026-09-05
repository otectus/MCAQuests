package dev.otectus.mcaquests.compat.iceandfire;

import java.util.function.Predicate;

/**
 * Which of the two mods calling itself {@code iceandfire} is installed.
 *
 * <p>Ice &amp; Fire forked. The original (Alex's Mobs' author, {@code com.github.alexthe666.iceandfire})
 * and the Community Edition ({@code com.iafenvoy.iceandfire}) ship the <b>same mod id</b>, so
 * {@code ModList.isLoaded("iceandfire")} cannot tell them apart, and their content differs: CE
 * dropped the Myrmex entirely and added Dragon Seekers and Netherite armor. The only thing that
 * separates them before a registry exists is which entry-point class the classpath can see.
 *
 * <p>This is a <em>diagnostic</em>, never an authority. Nothing in MCA: Quests gates content on the
 * flavour where a registry lookup can answer instead — a fork can add Myrmex back, and a
 * flavour-based answer would then be confidently wrong. Only the two capabilities that have no
 * registry entry to point at ({@code brush_scales}, {@code dragon_forge_blood}) are decided here, and
 * they are reported as {@code FLAVOR_DECLARED} so a bug report can say so.
 */
public enum IceAndFireFlavor {

    /** Neither entry point is on the classpath. */
    NONE,

    /** {@code com.github.alexthe666.iceandfire} — the original mod. */
    ORIGINAL,

    /** {@code com.iafenvoy.iceandfire} — Ice &amp; Fire: Community Edition. */
    COMMUNITY_EDITION,

    /**
     * Both entry points answered. Somebody has installed two builds of one mod id, or repackaged one
     * inside the other; either way the honest answer is that we cannot say which content is live, so
     * the integration falls back to registry lookups alone.
     */
    AMBIGUOUS;

    /** The Community Edition entry point, in dotted form for {@code Class.forName}. */
    public static final String CE_CLASS = "com.iafenvoy.iceandfire.IceAndFire";

    /** The original mod's entry point, in dotted form for {@code Class.forName}. */
    public static final String ORIGINAL_CLASS = "com.github.alexthe666.iceandfire.IceAndFire";

    /**
     * Decides the flavour from class presence alone.
     *
     * <p>{@code classPresent} is given a dotted binary name and must answer without initialising the
     * class and without throwing: production passes {@code Class.forName(name, false, loader)}
     * wrapped in a {@code Throwable} catch, tests pass a set lookup.
     */
    public static IceAndFireFlavor detect(Predicate<String> classPresent) {
        boolean ce = classPresent.test(CE_CLASS);
        boolean original = classPresent.test(ORIGINAL_CLASS);
        if (ce && original) {
            return AMBIGUOUS;
        }
        if (ce) {
            return COMMUNITY_EDITION;
        }
        return original ? ORIGINAL : NONE;
    }
}
