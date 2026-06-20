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
                    .map(v -> new ScopeIdentity("v:" + v.id(), OptionalInt.of(v.id()), dim, v.anchor()))
                    .or(() -> Optional.of(new ScopeIdentity("anchor:" + sponsor.getUUID(), OptionalInt.empty(), dim, sponsorPos)));
            case PROFESSION -> {
                String prof = McaCompat.getProfessionId(sponsor).map(ResourceLocation::toString).orElse("none");
                yield resolveVillage(level, sponsor, sponsorPos, dim, radius)
                        .map(v -> new ScopeIdentity("p:" + v.id() + ":" + prof, OptionalInt.of(v.id()), dim, v.anchor()))
                        .or(() -> Optional.of(new ScopeIdentity("p:anchor:" + sponsor.getUUID() + ":" + prof,
                                OptionalInt.empty(), dim, sponsorPos)));
            }
        };
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
        if (villageId.isPresent()) {
            return McaCompat.isWithinVillage(level, villageId.getAsInt(), pos);
        }
        // Anchor fallback: a simple radius around the stored anchor.
        return anchor.distSqr(pos) <= (long) anchorRadius * anchorRadius;
    }

    private record ResolvedVillage(int id, BlockPos anchor) {
    }
}
