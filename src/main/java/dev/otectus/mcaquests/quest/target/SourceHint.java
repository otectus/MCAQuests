package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.guidance.GuidanceKind;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.quest.guidance.LocateCache;
import dev.otectus.mcaquests.quest.guidance.Portals;
import dev.otectus.mcaquests.quest.objective.ObjectiveProgress;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;

/**
 * Where a gathering objective's thing can actually be got — the optional {@code "source"} block.
 *
 * <pre>
 * "source": { "structure": "minecraft:fortress" }
 * "source": { "structure_tag": "mcaquests:ocean_ruins" }
 * "source": { "biome": "minecraft:warm_ocean" }
 * "source": { "biome_tag": "minecraft:is_ocean" }
 * "source": { "block": "minecraft:sweet_berry_bush" }
 * "source": { "block_tag": "minecraft:crops" }
 * "source": { "dimension": "minecraft:the_nether" }
 * "source": { "anchor": { "anchor": "workstation" } }
 * </pre>
 *
 * <h2>Why this is authored and not inferred</h2>
 *
 * <p>"Mark the nearest source of the item" is not a question the game can answer in general. There is
 * no index of where eight prismarine crystals are, and any guess the mod made would send the player
 * somewhere confidently wrong — which is worse than sending them nowhere, because they would go.
 *
 * <p>So the mod marks only what a server can genuinely find: a generated structure, a biome, the way
 * into a dimension, or a place it already knows about through a {@link LocationAnchor}. An objective
 * with no {@code source} produces no marker at all, and the quest text carries the whole instruction,
 * exactly as it did before. That is the honest failure, and it is the common one.
 *
 * <p>Note that a {@code dimension} source does <em>not</em> point at the dimension. It points at the
 * portal in the dimension the player is standing in, and stops pointing once they are through — see
 * {@link Portals}.
 */
public record SourceHint(Optional<ResourceLocation> structure, Optional<TagKey<Structure>> structureTag,
                         Optional<ResourceLocation> biome, Optional<TagKey<Biome>> biomeTag,
                         Optional<Block> block, Optional<TagKey<Block>> blockTag,
                         Optional<ResourceLocation> dimension, Optional<LocationAnchor> anchor) {

    /** Chunks a structure search may walk. Vanilla's own {@code /locate} reach. */
    private static final int STRUCTURE_SEARCH_CHUNKS = 100;
    /** Blocks a biome search may sample outward. */
    private static final int BIOME_SEARCH_BLOCKS = 3200;
    /** How close counts as "you are there" for a place rather than a person. */
    private static final int AREA_ARRIVE_RADIUS = 24;
    /** Blocks around the player a block search may reach. Beyond this it is not "nearby" any more. */
    private static final int BLOCK_SEARCH_RADIUS = 48;
    /** A block is a small thing, so the marker only fades once you are practically on it. */
    private static final int BLOCK_ARRIVE_RADIUS = 3;

    public static final Codec<SourceHint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.lenientOptionalFieldOf("structure").forGetter(SourceHint::structure),
            TagKey.codec(Registries.STRUCTURE).lenientOptionalFieldOf("structure_tag").forGetter(SourceHint::structureTag),
            ResourceLocation.CODEC.lenientOptionalFieldOf("biome").forGetter(SourceHint::biome),
            TagKey.codec(Registries.BIOME).lenientOptionalFieldOf("biome_tag").forGetter(SourceHint::biomeTag),
            BuiltInRegistries.BLOCK.byNameCodec().lenientOptionalFieldOf("block").forGetter(SourceHint::block),
            TagKey.codec(Registries.BLOCK).lenientOptionalFieldOf("block_tag").forGetter(SourceHint::blockTag),
            ResourceLocation.CODEC.lenientOptionalFieldOf("dimension").forGetter(SourceHint::dimension),
            LocationAnchor.CODEC.lenientOptionalFieldOf("anchor").forGetter(SourceHint::anchor)
    ).apply(instance, SourceHint::new));

    public static final MapCodec<Optional<SourceHint>> FIELD = CODEC.lenientOptionalFieldOf("source");

    private Optional<StructureTarget> structureTarget() {
        return structure.isPresent() || structureTag.isPresent()
                ? Optional.of(new StructureTarget(structure, structureTag))
                : Optional.empty();
    }

    private Optional<BlockTarget> blockTarget() {
        return block.isPresent() || blockTag.isPresent()
                ? Optional.of(new BlockTarget(block, blockTag))
                : Optional.empty();
    }

    private Optional<BiomeTarget> biomeTarget() {
        return biome.isPresent() || biomeTag.isPresent()
                ? Optional.of(new BiomeTarget(biome, biomeTag))
                : Optional.empty();
    }

    /**
     * Where to send the player for this, or empty when nothing here resolves in this world.
     *
     * <p>Checked in the order a player would care about: the dimension first, because being in the
     * wrong world makes every other answer irrelevant; then the anchor, which is exact; then the
     * structure and the biome, which are searches and are cached for good by {@link LocateCache}.
     */
    public Optional<GuidanceTarget> guidance(ServerPlayer player, ActiveQuest active,
                                             ObjectiveProgress progress, ServerLevel level) {
        Optional<GuidanceTarget> route = dimensionRoute(player, level);
        if (route.isPresent()) {
            return route;
        }
        Optional<GuidanceTarget> anchored = anchor.flatMap(a -> a.resolveTarget(player, active, level)
                .map(resolved -> GuidanceTarget.ofPos(resolved.pos(), level,
                        resolved.villageId().isPresent() ? GuidanceKind.VILLAGE : GuidanceKind.LOCATION,
                        a.describe(player, active, level), AREA_ARRIVE_RADIUS, false)));
        if (anchored.isPresent()) {
            return anchored;
        }
        // Before the long-range searches, because a berry bush twenty blocks away is a better answer
        // than an ocean two thousand blocks away, and far cheaper to have found.
        Optional<BlockTarget> blockAt = blockTarget();
        if (blockAt.isPresent()) {
            Optional<GuidanceTarget> found = blockGuidance(blockAt.get(), player, progress, level);
            if (found.isPresent()) {
                return found;
            }
        }
        Optional<StructureTarget> structureAt = structureTarget();
        if (structureAt.isPresent()) {
            StructureTarget target = structureAt.get();
            Optional<GuidanceTarget> found = LocateCache
                    .resolve(progress, "srcStruct", level,
                            () -> target.locate(level, player.blockPosition(), STRUCTURE_SEARCH_CHUNKS))
                    .map(pos -> GuidanceTarget.ofPos(pos, level, GuidanceKind.STRUCTURE,
                            target.describe(), AREA_ARRIVE_RADIUS, true));
            if (found.isPresent()) {
                return found;
            }
        }
        return biomeTarget().flatMap(target -> LocateCache
                .resolve(progress, "srcBiome", level,
                        () -> target.locate(level, player.blockPosition(), BIOME_SEARCH_BLOCKS))
                .map(pos -> GuidanceTarget.ofPos(pos, level, GuidanceKind.BIOME,
                        target.describe(), AREA_ARRIVE_RADIUS, true)));
    }

    /**
     * The nearest matching block, re-checked before it is trusted.
     *
     * <p>A block is the one search result that <em>expires</em>. A fortress stays where it is and a
     * biome cannot be harvested, so {@link LocateCache} remembering those forever is right. A berry
     * bush is picked, a crop is broken, and a marker left standing on the empty ground where one used
     * to be is worse than no marker — it is the mod insisting the player go somewhere they have
     * already been. So the cached position is verified against the world each pass and dropped the
     * moment it stops matching, which costs one block-state read.
     */
    private Optional<GuidanceTarget> blockGuidance(BlockTarget target, ServerPlayer player,
                                                   ObjectiveProgress progress, ServerLevel level) {
        Optional<BlockPos> found = LocateCache.resolve(progress, "srcBlock", level,
                () -> target.locate(level, player.blockPosition(), BLOCK_SEARCH_RADIUS));
        if (found.isPresent() && level.isLoaded(found.get())
                && !target.matches(level.getBlockState(found.get()))) {
            LocateCache.forget(progress, "srcBlock");
            found = LocateCache.resolve(progress, "srcBlock", level,
                    () -> target.locate(level, player.blockPosition(), BLOCK_SEARCH_RADIUS));
        }
        return found.map(pos -> GuidanceTarget.ofPos(pos, level, GuidanceKind.LOCATION,
                target.describe(), BLOCK_ARRIVE_RADIUS, false));
    }

    /** The portal into the named dimension, while the player is not in it yet. */
    private Optional<GuidanceTarget> dimensionRoute(ServerPlayer player, ServerLevel level) {
        if (dimension.isEmpty()) {
            return Optional.empty();
        }
        ResourceKey<Level> destination = ResourceKey.create(Registries.DIMENSION, dimension.get());
        if (level.dimension().equals(destination)) {
            return Optional.empty();
        }
        return Portals.routeTo(level, player.blockPosition(), destination)
                .map(pos -> GuidanceTarget.ofPos(pos, level, GuidanceKind.PORTAL,
                        Component.translatable("mcaquests.guidance.route_to",
                                dev.otectus.mcaquests.quest.DisplayNames.name(dimension.get())),
                        AREA_ARRIVE_RADIUS, false));
    }

    /** How the HUD names this source when it has no resolved position to name instead. */
    public Component describe() {
        return structureTarget().map(StructureTarget::describe)
                .or(() -> blockTarget().map(BlockTarget::describe))
                .or(() -> biomeTarget().map(BiomeTarget::describe))
                .or(() -> dimension.map(dev.otectus.mcaquests.quest.DisplayNames::name))
                .or(() -> anchor.map(LocationAnchor::describe))
                .orElseGet(Component::empty);
    }

    /**
     * Datapack validation. A {@code source} that names nothing is the shape an author reaches for
     * when they half-remember the field name, and it would otherwise be a marker that never appears
     * with no way to find out why.
     */
    public void validate(String prefix, List<String> errors) {
        if (structure.isEmpty() && structureTag.isEmpty() && biome.isEmpty() && biomeTag.isEmpty()
                && block.isEmpty() && blockTag.isEmpty() && dimension.isEmpty() && anchor.isEmpty()) {
            errors.add(prefix + " source must set at least one of 'structure', 'structure_tag', "
                    + "'biome', 'biome_tag', 'block', 'block_tag', 'dimension' or 'anchor'.");
        }
        anchor.ifPresent(a -> a.validate(prefix + " source anchor", errors));
    }
}
