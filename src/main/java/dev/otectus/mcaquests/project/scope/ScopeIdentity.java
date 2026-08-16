package dev.otectus.mcaquests.project.scope;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.OptionalInt;

/**
 * The resolved identity of a project's scope: the stable key string plus the persistence anchor.
 *
 * @param identity  stable identity string (e.g. {@code v:12}, {@code f:<uuid>}, {@code u:<uuid>}).
 * @param villageId the backing MCA village id when scope is village/profession and a village was found.
 * @param dimension the dimension the anchor lives in.
 * @param anchor    village center (or sponsor/player position) — used for fallback re-resolution and
 *                  the in-village radius gate.
 */
public record ScopeIdentity(String identity, OptionalInt villageId, ResourceLocation dimension, BlockPos anchor) {
}
