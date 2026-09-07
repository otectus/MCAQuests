package dev.otectus.mcaquests.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/** Registry ids in authored data must not silently resolve to a default such as air. */
public final class RegistryEntryCodec {
    private RegistryEntryCodec() { }

    public static <T> Codec<T> of(Registry<T> registry) {
        return ResourceLocation.CODEC.flatXmap(id -> registry.containsKey(id)
                        ? DataResult.success(registry.get(id))
                        : DataResult.error(() -> "Unknown entry '" + id + "' in registry " + registry.key().location()),
                value -> registry.getResourceKey(value)
                        .map(key -> DataResult.success(key.location()))
                        .orElseGet(() -> DataResult.error(() -> "Unregistered " + registry.key().location() + " entry")));
    }
}
