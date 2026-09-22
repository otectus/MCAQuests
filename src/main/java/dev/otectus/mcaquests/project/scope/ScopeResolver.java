package dev.otectus.mcaquests.project.scope;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.ProjectScopeSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Maps a sponsoring villager (and the interacting player) to a stable scope identity (spec 0.4.0 §4).
 * All MCA access goes through {@link McaCompat}, so this class is MCA-import-free and degrades to a safe
 * anchor fallback whenever MCA village/family data is unavailable.
 */
public final class ScopeResolver {

    private ScopeResolver() {
    }

    public static Optional<ScopeIdentity> resolve(ServerLevel level, Entity sponsor, ServerPlayer player,
                                                  ProjectScopeSpec spec, int defaultFallbackRadius) {
        ResourceLocation dim = level.dimension().location();
        BlockPos sponsorPos = sponsor.blockPosition();
        int radius = spec.fallbackRadiusOr(defaultFallbackRadius);

        return switch (spec.scope()) {
            case PLAYER -> {
                UUID id = player.getUUID();
                yield Optional.of(new ScopeIdentity("u:" + id, OptionalInt.empty(), dim, player.blockPosition()));
            }
            case VILLAGER -> {
                UUID id = sponsor.getUUID();
                yield Optional.of(new ScopeIdentity("e:" + id, OptionalInt.empty(), dim, sponsorPos));
            }
            case FAMILY -> McaCompat.getFamilyRootId(sponsor)
                    .map(root -> new ScopeIdentity("f:" + root, OptionalInt.empty(), dim, sponsorPos))
                    .or(() -> Optional.of(new ScopeIdentity("f:self:" + sponsor.getUUID(), OptionalInt.empty(), dim, sponsorPos)));
            case VILLAGE -> resolveVillage(level, sponsor, sponsorPos, dim, radius)
                    .map(v -> new ScopeIdentity(villageIdentity(v.id(), dim), OptionalInt.of(v.id()), dim, v.anchor()))
                    .or(() -> Optional.of(new ScopeIdentity("anchor:" + sponsor.getUUID(), OptionalInt.empty(), dim, sponsorPos)));
            case PROFESSION -> {
                String prof = McaCompat.getProfessionId(sponsor).map(ResourceLocation::toString).orElse("none");
                yield resolveVillage(level, sponsor, sponsorPos, dim, radius)
                        .map(v -> new ScopeIdentity(professionIdentity(v.id(), dim, prof), OptionalInt.of(v.id()), dim, v.anchor()))
                        .or(() -> Optional.of(new ScopeIdentity("p:anchor:" + sponsor.getUUID() + ":" + prof,
                                OptionalInt.empty(), dim, sponsorPos)));
            }
        };
    }

    /**
     * The identity of a village scope. MCA numbers villages per dimension, so village 3 of the Nether and
     * village 3 of the Overworld are different places; before 1.7.0 both were {@code v:3}, and a Nether
     * sponsor's donations landed in the Overworld village's project. Overworld identities keep their
     * historical spelling, so no Overworld save changes; any other dimension is qualified.
     */
    public static String villageIdentity(int villageId, ResourceLocation dimension) {
        return isOverworld(dimension) ? "v:" + villageId : "v:" + villageId + "@" + dimension;
    }

    /** The identity of a profession scope; qualified the same way as {@link #villageIdentity}. */
    public static String professionIdentity(int villageId, ResourceLocation dimension, String profession) {
        return isOverworld(dimension)
                ? "p:" + villageId + ":" + profession
                : "p:" + villageId + "@" + dimension + ":" + profession;
    }

    /**
     * The identity a pre-1.7.0 instance should carry, given the dimension it was saved in. Unchanged for
     * the Overworld, for already-qualified identities and for scopes that were never numbered per
     * dimension (players, villagers, families, anchors).
     */
    public static String dimensionQualified(String identity, ResourceLocation dimension) {
        if (isOverworld(dimension) || identity.indexOf('@') >= 0) {
            return identity;
        }
        java.util.regex.Matcher village = LEGACY_VILLAGE.matcher(identity);
        if (village.matches()) {
            return villageIdentity(Integer.parseInt(village.group(1)), dimension);
        }
        java.util.regex.Matcher profession = LEGACY_PROFESSION.matcher(identity);
        if (profession.matches()) {
            return professionIdentity(Integer.parseInt(profession.group(1)), dimension, profession.group(2));
        }
        return identity;
    }

    private static final java.util.regex.Pattern LEGACY_VILLAGE = java.util.regex.Pattern.compile("v:(-?\\d+)");
    private static final java.util.regex.Pattern LEGACY_PROFESSION = java.util.regex.Pattern.compile("p:(-?\\d+):(.+)");

    private static boolean isOverworld(ResourceLocation dimension) {
        return net.minecraft.world.level.Level.OVERWORLD.location().equals(dimension);
    }

    /** A village id + anchor for the sponsor, via home village then nearest-village fallback. */
    private static Optional<ResolvedVillage> resolveVillage(ServerLevel level, Entity sponsor, BlockPos sponsorPos,
                                                            ResourceLocation dim, int radius) {
        OptionalInt home = McaCompat.getHomeVillageId(sponsor);
        if (home.isPresent()) {
            BlockPos anchor = McaCompat.getHomeVillageCenter(sponsor).orElse(sponsorPos);
            return Optional.of(new ResolvedVillage(home.getAsInt(), anchor));
        }
        OptionalInt nearest = McaCompat.findNearestVillageId(level, sponsorPos, radius);
        if (nearest.isPresent()) {
            BlockPos anchor = McaCompat.villageCenter(level, nearest.getAsInt()).orElse(sponsorPos);
            return Optional.of(new ResolvedVillage(nearest.getAsInt(), anchor));
        }
        return Optional.empty();
    }

    /** True when {@code pos} is within the project's village (or its anchor radius when not village-bound). */
    public static boolean isWithinScope(ServerLevel level, ProjectScope scope, OptionalInt villageId,
                                        BlockPos anchor, int anchorRadius, BlockPos pos) {
        return isWithinScope(level, scope, villageId, anchor, anchorRadius, 0, pos);
    }

    /**
     * As above, with {@code villageMargin} blocks of allowance beyond the village's registered buildings.
     * The margin widens a village only; an anchor-bound scope is its radius.
     */
    public static boolean isWithinScope(ServerLevel level, ProjectScope scope, OptionalInt villageId,
                                        BlockPos anchor, int anchorRadius, int villageMargin, BlockPos pos) {
        if (villageId.isPresent()) {
            return McaCompat.isWithinVillage(level, villageId.getAsInt(), pos, villageMargin);
        }
        // Anchor fallback: a simple radius around the stored anchor.
        return anchor.distSqr(pos) <= (long) anchorRadius * anchorRadius;
    }

    private record ResolvedVillage(int id, BlockPos anchor) {
    }
}
