package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.McaQuests;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Pure conversions between a situation definition id and the synthetic offer-quest id under which its
 * dynamic offer reuses the quest lifecycle (0.8.0). Kept free of any codec/registry static state so the
 * mapping is unit-testable without bootstrapping Minecraft registries.
 *
 * <p>The synthetic id encodes the source id's namespace and path into the mod namespace:
 * {@code mcaquests:situation/<ns>/<path>}. ('/' is valid in a path but not a namespace, so a synthetic id
 * can never collide with a hand-authored quest id.)
 */
public final class SituationIds {

    /** Path prefix marking a synthetic situation-offer id. */
    public static final String SYNTHETIC_PREFIX = "situation/";

    private SituationIds() {
    }

    public static ResourceLocation syntheticId(ResourceLocation defId) {
        return ResourceLocation.fromNamespaceAndPath(McaQuests.MOD_ID, SYNTHETIC_PREFIX + defId.getNamespace() + "/" + defId.getPath());
    }

    public static boolean isSyntheticId(ResourceLocation candidate) {
        return candidate.getNamespace().equals(McaQuests.MOD_ID) && candidate.getPath().startsWith(SYNTHETIC_PREFIX);
    }

    /** The original source id encoded into a synthetic id, or empty if it is not a (well-formed) synthetic id. */
    public static Optional<ResourceLocation> sourceIdOf(ResourceLocation syntheticId) {
        if (!isSyntheticId(syntheticId)) {
            return Optional.empty();
        }
        String rest = syntheticId.getPath().substring(SYNTHETIC_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(ResourceLocation.fromNamespaceAndPath(rest.substring(0, slash), rest.substring(slash + 1)));
    }
}
