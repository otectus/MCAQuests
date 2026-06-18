package dev.otectus.mcaquests.quest.target;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Matches a block by id ({@code "block": ...}) or tag ({@code "tag": ...}) — spec sections 14, 19. */
public record BlockTarget(Optional<Block> block, Optional<TagKey<Block>> tag) {

    public static final MapCodec<BlockTarget> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(BlockTarget::block),
            TagKey.codec(Registries.BLOCK).optionalFieldOf("tag").forGetter(BlockTarget::tag)
    ).apply(instance, BlockTarget::new));

    public boolean matches(BlockState state) {
        return (block.isPresent() && state.is(block.get())) || (tag.isPresent() && state.is(tag.get()));
    }

    public Component describe() {
        if (block.isPresent()) {
            return block.get().getName();
        }
        return tag.map(t -> (Component) Component.literal("#" + t.location())).orElse(Component.literal("?"));
    }
}
