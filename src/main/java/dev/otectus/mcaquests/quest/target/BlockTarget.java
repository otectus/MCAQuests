package dev.otectus.mcaquests.quest.target;

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
            BuiltInRegistries.BLOCK.byNameCodec().lenientOptionalFieldOf("block").forGetter(BlockTarget::block),
            TagKey.codec(Registries.BLOCK).lenientOptionalFieldOf("tag").forGetter(BlockTarget::tag)
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
     *   <li><b>Its only caller throttles it.</b> {@code LocateCache} runs this once and remembers,
     *       retrying a miss no more often than {@code guidanceSearchIntervalTicks}.</li>
     * </ul>
     *
     * <p>Vertical reach is deliberately much shorter than horizontal: the things worth pointing at are
     * on the surface near the player, and a tall box mostly buys stone.
     */
    public Optional<BlockPos> locate(ServerLevel level, BlockPos from, int radius) {
        int vertical = Math.max(4, radius / 4);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // Only the shell of each ring; the inside was covered by a smaller ring already.
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -vertical; dy <= vertical; dy++) {
                        cursor.set(from.getX() + dx, from.getY() + dy, from.getZ() + dz);
                        if (!level.isLoaded(cursor)) {
                            continue;
                        }
                        if (matches(level.getBlockState(cursor))) {
                            return Optional.of(cursor.immutable());
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public Component describe() {
        if (block.isPresent()) {
            return block.get().getName();
        }
        return tag.map(t -> DisplayNames.tagName(t.location())).orElse(Component.literal("?"));
    }
}
