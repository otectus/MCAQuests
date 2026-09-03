package dev.otectus.mcaquests.quest;

import com.mojang.serialization.MapCodec;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every codec reached through a {@code dispatch("type", …)} registry must still be a
 * {@link MapCodec}, so its fields stay inline beside {@code "type"}.
 *
 * <h2>Why this exists</h2>
 *
 * <p>DFU's {@code KeyDispatchCodec} reads the dispatched codec's fields from the <em>same</em>
 * object as {@code "type"}, so {@code {"type": "mcaquests:is_family_member", "relation": "child"}}
 * works. On 1.20.1 that only held when the registered codec was a {@code MapCodecCodec}; anything
 * else was read from a nested {@code "value"} key.
 *
 * <p>Chaining {@code .flatXmap(…)} or {@code .xmap(…)} onto the result of
 * {@code RecordCodecBuilder.create(…)} silently converted it to that other kind. The declaration still
 * compiled, still looked right, and every hand-written data file then failed to parse — and because
 * {@code QuestDefinition} reads conditions through {@code optionalFieldOf("conditions")}, whose DFU
 * implementation <b>swallows a decode error and substitutes {@code Optional.empty()}</b>, the failure
 * is completely silent. Thirty-six shipped quests lost their entire condition gate this way: a quest
 * gated on being the player's own child was being offered by every villager in the world, and the
 * only visible symptom anywhere was one unrelated-looking age-eligibility warning.
 *
 * <p>The registry now holds a {@link MapCodec} outright, so the shape to keep is
 * {@code RecordCodecBuilder.<T>mapCodec(…).flatXmap(T::validate, T::validate)} — this test guards
 * the property the registry type only states.
 *
 * @see dev.otectus.mcaquests.data.BuiltinPackParsesTest for the data-side half of this guarantee
 */
class DispatchedCodecInlinesTest {

    static {
        TestBootstrap.ensureBootstrapped();
        // Touch a codec that pulls the registries up in the order the loader does; initialising a
        // *Types class first reaches Registries before the bootstrap flag has had any effect.
        assertTrue(QuestDefinition.CODEC != null);
    }

    @Test
    @DisplayName("every dispatched condition, objective and reward codec inlines its fields")
    void dispatchedCodecsAreMapCodecs() throws Exception {
        List<String> offenders = new ArrayList<>();
        int checked = 0;
        for (String owner : List.of(
                "dev.otectus.mcaquests.quest.condition.ConditionTypes",
                "dev.otectus.mcaquests.quest.objective.ObjectiveTypes",
                "dev.otectus.mcaquests.quest.reward.RewardTypes")) {
            for (Map.Entry<ResourceLocation, ?> entry : registry(owner).entrySet()) {
                MapCodec<?> codec = codecOf(entry.getValue());
                checked++;
                if (codec == null) {
                    offenders.add(entry.getKey() + " has no MapCodec");
                }
            }
        }
        assertTrue(checked > 0, "no dispatched types found, so this would pass vacuously");
        assertEquals(List.of(), offenders,
                "these codecs no longer inline under dispatch, so every data file using them fails to "
                        + "parse — silently, because optionalFieldOf swallows the error. Build them as "
                        + "RecordCodecBuilder.<T>mapCodec(...).flatXmap(...) instead of chaining "
                        + "onto RecordCodecBuilder.create(...). Offenders: " + offenders);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, ?> registry(String className) throws Exception {
        Field byId = Class.forName(className).getDeclaredField("BY_ID");
        byId.setAccessible(true);
        return (Map<ResourceLocation, ?>) byId.get(null);
    }

    private static MapCodec<?> codecOf(Object type) throws Exception {
        Method codec = type.getClass().getMethod("codec");
        return (MapCodec<?>) codec.invoke(type);
    }
}
