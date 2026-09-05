package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;

/**
 * True when a registered {@link CompatRegistry} provider reports a capability as present — the
 * mod-agnostic form of {@code mcaquests:townstead_available}.
 *
 * <pre>{@code {"type": "mcaquests:compat_capability", "provider": "townstead", "capability": "read_needs"}}</pre>
 *
 * <p>Set {@code "present": false} to gate on a capability being <em>absent</em>, which is how a pack
 * ships an alternative for players who do not have the content the main quest needs.
 *
 * <p>Registered unconditionally, like every other optional-mod condition in this mod: a datapack must
 * parse identically whether or not the mod is installed, so the type always exists and it is
 * evaluation that is gated. An unknown provider or capability id is simply not present — it is not a
 * parse error, because a pack may legitimately name a provider that an add-on registers.
 */
public record CompatCapabilityCondition(String provider, String capability, boolean present)
        implements QuestCondition {

    public static final Codec<CompatCapabilityCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("provider").forGetter(CompatCapabilityCondition::provider),
                    Codec.STRING.fieldOf("capability").forGetter(CompatCapabilityCondition::capability),
                    StrictCodecs.strictOptional(Codec.BOOL, "present", true)
                            .forGetter(CompatCapabilityCondition::present)
            ).apply(instance, CompatCapabilityCondition::new));

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.COMPAT_CAPABILITY;
    }

    @Override
    public boolean test(QuestContext context) {
        return CompatRegistry.get().has(provider, capability) == present;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.compat_capability", provider, capability);
    }
}
