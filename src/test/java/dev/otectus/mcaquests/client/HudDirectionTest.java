package dev.otectus.mcaquests.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The HUD's compass cue is pure maths over Minecraft's unusual axes — yaw 0 faces +Z (south), -Z is
 * north, and yaw increases clockwise — so it is worth pinning every octant explicitly. Getting a sign
 * wrong here sends the player the opposite way and looks like a broken quest, not a broken formula.
 */
class HudDirectionTest {

    @Nested
    @DisplayName("facing south (yaw 0, looking down +Z)")
    class FacingSouth {

        @Test
        @DisplayName("a target further along +Z is straight ahead")
        void ahead() {
            assertEquals("ahead", HudDirection.compass(0, 10, 0f),
                    "yaw 0 faces +Z, so a target at +Z must read as ahead");
        }

        @Test
        @DisplayName("a target at -Z is behind")
        void behind() {
            assertEquals("behind", HudDirection.compass(0, -10, 0f),
                    "the target is directly opposite the facing direction");
        }

        @Test
        @DisplayName("a target at -X is to the right")
        void right() {
            assertEquals("right", HudDirection.compass(-10, 0, 0f),
                    "facing +Z, -X is on the player's right hand");
        }

        @Test
        @DisplayName("a target at +X is to the left")
        void left() {
            assertEquals("left", HudDirection.compass(10, 0, 0f),
                    "facing +Z, +X is on the player's left hand");
        }

        @Test
        @DisplayName("the four diagonals land on the diagonal labels")
        void diagonals() {
            assertEquals("ahead_right", HudDirection.compass(-10, 10, 0f), "-X +Z is forward and right");
            assertEquals("behind_right", HudDirection.compass(-10, -10, 0f), "-X -Z is back and right");
            assertEquals("behind_left", HudDirection.compass(10, -10, 0f), "+X -Z is back and left");
            assertEquals("ahead_left", HudDirection.compass(10, 10, 0f), "+X +Z is forward and left");
        }
    }

    @Nested
    @DisplayName("the cue is relative to where the player looks")
    class RelativeToYaw {

        @Test
        @DisplayName("turning to face the target makes it read as ahead")
        void turningTowardsTheTarget() {
            assertEquals("right", HudDirection.compass(-10, 0, 0f), "before turning, the target is to the right");
            assertEquals("ahead", HudDirection.compass(-10, 0, 90f),
                    "after turning 90 degrees clockwise the same target must be straight ahead");
        }

        @Test
        @DisplayName("turning away makes it read as behind")
        void turningAway() {
            assertEquals("behind", HudDirection.compass(0, 10, 180f),
                    "facing the opposite way must flip ahead to behind");
        }

        @Test
        @DisplayName("yaw beyond +/-180 wraps rather than falling off the end of the labels")
        void wrapsPastHalfTurn() {
            assertEquals(HudDirection.compass(0, 10, 0f), HudDirection.compass(0, 10, 360f),
                    "a full extra turn is the same direction; the index must wrap, not overflow");
            assertEquals(HudDirection.compass(0, 10, 90f), HudDirection.compass(0, 10, -270f),
                    "negative yaw must wrap the same way Minecraft's own yaw does");
        }
    }

    @Test
    @DisplayName("standing on top of the target reads as ahead and never NaNs")
    void zeroOffset() {
        assertEquals("ahead", HudDirection.compass(0, 0, 0f),
                "a zero offset has no meaningful bearing, so it must degrade to 'ahead' rather than to"
                        + " whatever atan2(0, 0) happens to return");
        assertEquals("ahead", HudDirection.compass(0, 0, 137f), "and that must hold whichever way we face");
    }

    @Test
    @DisplayName("the translation key is the label under the hud.dir prefix")
    void translationKey() {
        assertEquals("mcaquests.hud.dir.ahead", HudDirection.key(0, 10, 0f),
                "the HUD builds its Component straight from this, so the prefix must match the lang file");
    }
}
