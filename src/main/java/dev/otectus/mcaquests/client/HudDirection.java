package dev.otectus.mcaquests.client;

import net.minecraft.util.Mth;

/**
 * Turns "where is the quest target relative to where I am looking" into one of eight compass labels for
 * the HUD tracker. Deliberately free of Minecraft entity types so the bearing maths can be unit-tested.
 */
public final class HudDirection {

    /**
     * Suffixes of the {@code mcaquests.hud.dir.*} translation keys, ordered clockwise from straight ahead
     * — matching how the relative bearing increases.
     */
    private static final String[] DIRECTIONS = {
            "ahead", "ahead_right", "right", "behind_right",
            "behind", "behind_left", "left", "ahead_left"
    };

    private HudDirection() {
    }

    /**
     * The bearing to a target {@code dx}/{@code dz} blocks away, as seen by a player facing
     * {@code yawDegrees}.
     *
     * <p>Minecraft's axes are unusual: {@code -Z} is north and yaw 0 faces {@code +Z} (south), increasing
     * clockwise. {@code atan2(-dx, dz)} converts the offset into that same clockwise-from-south frame, so
     * subtracting the player's yaw leaves a relative bearing where 0 is straight ahead.
     *
     * <p>Returns {@code "ahead"} when the target is exactly on top of the player, rather than the
     * arbitrary answer {@code atan2(0, 0)} would give.
     */
    public static String compass(double dx, double dz, float yawDegrees) {
        if (dx == 0.0D && dz == 0.0D) {
            return DIRECTIONS[0];
        }
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = Mth.wrapDegrees(bearing - yawDegrees);
        return DIRECTIONS[Math.floorMod((int) Math.round(relative / 45.0D), DIRECTIONS.length)];
    }

    /** The translation key for {@link #compass}'s result. */
    public static String key(double dx, double dz, float yawDegrees) {
        return "mcaquests.hud.dir." + compass(dx, dz, yawDegrees);
    }
}
