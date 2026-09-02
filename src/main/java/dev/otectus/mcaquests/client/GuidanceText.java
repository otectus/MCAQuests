package dev.otectus.mcaquests.client;

import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.client.marker.MarkerGeometry;
import dev.otectus.mcaquests.quest.DisplayNames;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Turns one destination into one line of text: what it is, how far, which way, and where.
 *
 * <p>Three surfaces draw that line — the HUD tracker, the quest log's destination row, and the world
 * marker's floating label — and before this they each built it themselves. The mod has already been
 * bitten twice by that shape: the scrollbar was a byte-identical private copy in three screens, and
 * the objective counter is <em>still</em> formatted two different ways in three files
 * ({@code "  3/24"} on the HUD, {@code "  (3/24)"} in the two menus). This is the third such string
 * and it is not going to be the third such copy.
 *
 * <h2>Coordinates</h2>
 *
 * <p>The mod could say "84 blocks ahead-right" and never "1024, 68, -330". The server had the number
 * the whole time — it is what the marker is drawn at — and dropped it at the client boundary, so a
 * player who wanted to write the place down, type it into a minimap, or send it to somebody else
 * could not. {@code showQuestTargetCoordinates} appends it.
 *
 * <p>A destination in another dimension now carries its coordinates too. The old line said only
 * "Nether Fortress — in the Nether", on the sound argument that a bearing across dimensions would be
 * a lie; but a <em>coordinate</em> across dimensions is not a lie, it is exactly what the player
 * needs written down before they go looking for a portal.
 */
public final class GuidanceText {

    private GuidanceText() {
    }

    /**
     * "Anna's home — 84 blocks ahead-right (1024, 68, -330)", or the cross-dimension form.
     *
     * @param player whose position and facing the distance and bearing are measured from
     */
    public static Component line(GuidanceTarget target, Player player, Level level) {
        return line(target, player, level, showCoordinates());
    }

    /** As {@link #line(GuidanceTarget, Player, Level)}, with the coordinate group forced on or off. */
    public static Component line(GuidanceTarget target, Player player, Level level,
                                 boolean withCoordinates) {
        Component coordinates = coordinates(target.pos());
        if (level == null || !level.dimension().equals(target.dimension())) {
            return withCoordinates
                    ? Component.translatable("mcaquests.hud.other_dimension_coords", target.label(),
                            DisplayNames.name(target.dimension().location()), coordinates)
                    : Component.translatable("mcaquests.hud.other_dimension", target.label(),
                            DisplayNames.name(target.dimension().location()));
        }
        double dx = (target.pos().getX() + 0.5D) - player.getX();
        double dz = (target.pos().getZ() + 0.5D) - player.getZ();
        long blocks = Math.round(MarkerGeometry.horizontalDistance(dx, dz));
        Component bearing = Component.translatable(HudDirection.key(dx, dz, player.getYRot()));
        return withCoordinates
                ? Component.translatable(coordinatesKey(target), target.label(), blocks, bearing,
                        coordinates)
                : Component.translatable(key(target), target.label(), blocks, bearing);
    }

    /**
     * Which of the three phrasings this target wants.
     *
     * <p>{@code lastKnown} beats {@code approximate} because it is the stronger caveat: "about 400
     * blocks" promises the place is right and the distance rounded, while "last seen 400 blocks away"
     * warns that the person may have moved. Saying both would be a sentence nobody wrote a key for.
     */
    static String key(GuidanceTarget target) {
        if (target.lastKnown()) {
            return "mcaquests.hud.target_last_known";
        }
        return target.approximate() ? "mcaquests.hud.target_approx" : "mcaquests.hud.target";
    }

    static String coordinatesKey(GuidanceTarget target) {
        if (target.lastKnown()) {
            return "mcaquests.hud.target_last_known_coords";
        }
        return target.approximate()
                ? "mcaquests.hud.target_approx_coords"
                : "mcaquests.hud.target_coords";
    }

    /** "1024, 68, -330", localisable because not every language separates numbers with a comma. */
    public static Component coordinates(BlockPos pos) {
        return Component.translatable("mcaquests.hud.coords", pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * "1024 68 -330" — what the quest log copies to the clipboard.
     *
     * <p>Space-separated and unlocalised on purpose: this is not read, it is pasted, and every place
     * a player would paste it — {@code /tp}, a minimap's coordinate box, a chat message to a friend —
     * expects the vanilla spacing.
     */
    public static String plain(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** The world marker's floating label: the name and the distance, with no bearing to read. */
    public static Component markerLabel(GuidanceTarget target, double distance) {
        return Component.translatable(
                target.approximate() ? "mcaquests.marker.label_approx" : "mcaquests.marker.label",
                target.label(), (long) Math.round(distance));
    }

    private static boolean showCoordinates() {
        try {
            return McaQuestsConfig.CLIENT.showQuestTargetCoordinates.get();
        } catch (RuntimeException e) {
            return true; // no config attached (a unit test); the shipped default
        }
    }
}
