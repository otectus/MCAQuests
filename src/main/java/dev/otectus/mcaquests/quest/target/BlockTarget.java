package dev.otectus.mcaquests.quest.target;

import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.data.RegistryEntryCodec;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.quest.DisplayNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Matches a block by id ({@code "block": ...}) or tag ({@code "tag": ...}) — spec sections 14, 19. */
public record BlockTarget(Optional<Block> block, Optional<TagKey<Block>> tag) {

    public static final MapCodec<BlockTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StrictCodecs.strictOptional(RegistryEntryCodec.of(BuiltInRegistries.BLOCK), "block").forGetter(BlockTarget::block),
            StrictCodecs.strictOptional(TagKey.codec(Registries.BLOCK), "tag").forGetter(BlockTarget::tag)
    ).apply(instance, BlockTarget::new));

    public boolean matches(BlockState state) {
        return (block.isPresent() && state.is(block.get())) || (tag.isPresent() && state.is(tag.get()));
    }

    /**
     * The nearest matching block to {@code from} within {@code radius}, or empty.
     *
     * <p>Unlike the structure and biome searches this cannot consult an index — there is no registry
     * of where the wheat is — so it reads block states directly. Three things keep that affordable:
     *
     * <ul>
     *   <li><b>It expands outward and stops at the first hit.</b> The common case is a crop within a
     *       few blocks of the villager who asked for it, which costs a few hundred reads, not the
     *       whole box.</li>
     *   <li><b>It never leaves loaded chunks.</b> A block in an unloaded chunk cannot be walked to
     *       any sooner for having been found, and reading one would drag chunks into memory for a
     *       marker.</li>
     *   <li><b>It is throttled and spread out.</b> Guidance runs it through {@link #locateAsync} on the
     *       server-wide search queue, a slice per step (1.7.0), and {@code LocateCache} remembers the
     *       answer, retrying a miss no more often than {@code guidanceSearchIntervalTicks}.</li>
     * </ul>
     *
     * <p>Vertical reach is deliberately much shorter than horizontal: the things worth pointing at are
     * on the surface near the player, and a tall box mostly buys stone.
     */
    public Optional<BlockPos> locate(ServerLevel level, BlockPos from, int radius) {
        return scan(from, radius).advance(Integer.MAX_VALUE, probe(level));
    }

    /**
     * The same search as {@link #locate}, run on the server-wide search queue a slice at a time (1.7.0)
     * instead of in one call on the player's guidance pass. Guidance uses this through
     * {@code LocateCache.resolveAsync}.
     */
    public java.util.concurrent.CompletableFuture<Optional<BlockPos>> locateAsync(ServerLevel level, BlockPos from,
                                                                                  int radius) {
        return dev.otectus.mcaquests.quest.guidance.StructureSearches.requestBlock(level, this, from, radius,
                () -> scan(from, radius), probe(level));
    }

    /** Rings out to {@code radius}, each column a quarter of that up and down (at least four). */
    private static BlockRingScan scan(BlockPos from, int radius) {
        return new BlockRingScan(from, radius, Math.max(4, radius / 4));
    }

    /** Only loaded blocks are read: a search never generates or loads a chunk. */
    private BlockRingScan.Probe probe(ServerLevel level) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            cursor.set(x, y, z);
            return level.isLoaded(cursor) && matches(level.getBlockState(cursor));
        };
    }

    public Component describe() {
        if (block.isPresent()) {
            return block.get().getName();
        }
        return tag.map(t -> DisplayNames.tagName(t.location())).orElse(Component.literal("?"));
    }
}
