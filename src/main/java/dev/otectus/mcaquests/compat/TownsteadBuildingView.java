package dev.otectus.mcaquests.compat;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/**
 * A building Townstead has registered with MCA (Townstead spec §2.3).
 *
 * <p>Objectives test <em>this</em> — the registered building — never the blocks that make it up.
 * A player who assembles a dock-shaped pile of planks has not built a dock until Townstead says so,
 * and that distinction is the whole point of {@code mcaquests:townstead_building_registered}.
 *
 * <p>{@link #type()} carries Townstead's own building type id ({@code dock_l1}, {@code wool_shed},
 * {@code butcher_shop_l2}, …); {@link #level()} reads the trailing {@code _lN} where the type has
 * one, so a definition can ask for "a dock of at least level 2" without enumerating ids.
 */
public record TownsteadBuildingView(
        int id,
        int villageId,
        String type,
        int size,
        int centerX,
        int centerY,
        int centerZ,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ) {

    public BlockPos center() {
        return new BlockPos(centerX, centerY, centerZ);
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        return new BlockPos(maxX, maxY, maxZ);
    }

    /**
     * The tier encoded in the type id's {@code _lN} suffix, or {@code 1} for an untiered building.
     * {@code dock_l3} is level 3; {@code pen} is level 1.
     */
    public int level() {
        String digits = levelSuffix();
        return digits == null ? 1 : Integer.parseInt(digits);
    }

    /** The type id with any {@code _lN} suffix removed, so all three dock levels share a family. */
    public String family() {
        String digits = levelSuffix();
        return digits == null ? type : type.substring(0, type.length() - digits.length() - 2);
    }

    /**
     * The digits of a trailing {@code _lN}, or {@code null} when there is no such suffix. Kept in one
     * place so {@link #level()} and {@link #family()} can never disagree about what a tier looks like.
     * Parsing is bounded to {@code int} range by the length check, so {@link #level()} cannot overflow
     * on a pathological id.
     */
    @Nullable
    private String levelSuffix() {
        int marker = type.lastIndexOf("_l");
        if (marker <= 0) {
            return null;
        }
        String suffix = type.substring(marker + 2);
        if (suffix.isEmpty() || suffix.length() > 9 || !suffix.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return suffix;
    }
}
