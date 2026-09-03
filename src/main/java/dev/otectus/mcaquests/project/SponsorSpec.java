package dev.otectus.mcaquests.project;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Which villagers may sponsor (surface, host, and accept contributions for) a project, and what
 * happens when sponsorship is lost (spec 0.4.0).
 *
 * <ul>
 *   <li>{@code professions} — eligible sponsor professions; empty means any adult villager.</li>
 *   <li>{@code requiredCount} — how many distinct living sponsors the project wants. Shown on the
 *       project card when it is more than one, alongside how many have signed on. It does not gate
 *       anything: a project with one sponsor of three still runs, because refusing contributions until
 *       enough villagers volunteered would stall a project on something the player cannot influence.</li>
 *   <li>{@code adultOnly} — restrict sponsors to adults (default true).</li>
 *   <li>{@code pinnedSponsors} — explicit villager UUIDs that always count as sponsors.</li>
 *   <li>{@code onDeath} — sponsor-loss behavior; absent means use the config default.</li>
 * </ul>
 */
public record SponsorSpec(List<ResourceLocation> professions,
                          int requiredCount,
                          boolean adultOnly,
                          List<UUID> pinnedSponsors,
                          Optional<SponsorDeathBehavior> onDeath) {

    public static final SponsorSpec ANY = new SponsorSpec(List.of(), 1, true, List.of(), Optional.empty());

    public static final Codec<SponsorSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.listOf().lenientOptionalFieldOf("professions", List.of()).forGetter(SponsorSpec::professions),
            Codec.intRange(1, 100).lenientOptionalFieldOf("required_count", 1).forGetter(SponsorSpec::requiredCount),
            Codec.BOOL.lenientOptionalFieldOf("adult_only", true).forGetter(SponsorSpec::adultOnly),
            UUIDUtil.STRING_CODEC.listOf().lenientOptionalFieldOf("pinned_sponsors", List.of()).forGetter(SponsorSpec::pinnedSponsors),
            SponsorDeathBehavior.CODEC.lenientOptionalFieldOf("on_death").forGetter(SponsorSpec::onDeath)
    ).apply(instance, SponsorSpec::new));

    public boolean isGeneric() {
        return professions.isEmpty();
    }

    /** True when {@code professionId} (or a pinned UUID match handled elsewhere) may sponsor this project. */
    public boolean acceptsProfession(ResourceLocation professionId) {
        return isGeneric() || (professionId != null && professions.contains(professionId));
    }

    public SponsorDeathBehavior onDeathOr(SponsorDeathBehavior fallback) {
        return onDeath.orElse(fallback);
    }
}
