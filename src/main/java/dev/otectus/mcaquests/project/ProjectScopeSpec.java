package dev.otectus.mcaquests.project;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A project's scope plus optional scope tuning. Accepts either a bare string
 * ({@code "scope": "village"}) or an object ({@code "scope": {"scope":"profession","professions":[...]}}).
 *
 * <ul>
 *   <li>{@code professions} — only consulted for {@code PROFESSION} scope: which professions form the
 *       shared group. Empty means "every profession in the village".</li>
 *   <li>{@code fallbackRadius} — block radius used to find/anchor a village when MCA village data is
 *       unavailable; defaults to the {@code defaultScopeFallbackRadius} config value.</li>
 * </ul>
 */
public record ProjectScopeSpec(ProjectScope scope, List<ResourceLocation> professions, Optional<Integer> fallbackRadius) {

    private static final Codec<ProjectScopeSpec> RECORD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ProjectScope.CODEC.fieldOf("scope").forGetter(ProjectScopeSpec::scope),
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(ProjectScopeSpec::professions),
            Codec.INT.optionalFieldOf("fallback_radius").forGetter(ProjectScopeSpec::fallbackRadius)
    ).apply(instance, ProjectScopeSpec::new));

    public static final Codec<ProjectScopeSpec> CODEC = Codec.either(ProjectScope.CODEC, RECORD_CODEC).xmap(
            either -> either.map(scope -> new ProjectScopeSpec(scope, List.of(), Optional.empty()), Function.identity()),
            spec -> spec.professions().isEmpty() && spec.fallbackRadius().isEmpty()
                    ? Either.left(spec.scope()) : Either.right(spec));

    public int fallbackRadiusOr(int fallback) {
        return fallbackRadius.orElse(fallback);
    }
}
