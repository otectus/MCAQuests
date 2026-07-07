package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.quest.QuestDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * The single resolution chokepoint for an accepted/active quest's definition (0.8.0). Ordinary quests
 * live in {@link QuestRegistry}; dynamic situation offers do not — their base definition is synthesized
 * on demand from {@link SituationRegistry} under a stable synthetic id. Every lifecycle site that needs
 * the definition behind an {@code ActiveQuest#questId()} resolves through here, so situation offers reuse
 * the entire quest lifecycle unchanged.
 *
 * <p>For a non-situation id this is exactly {@link QuestRegistry#get(ResourceLocation)}, so existing
 * behaviour is unchanged.
 */
public final class QuestDefinitions {

    private QuestDefinitions() {
    }

    public static Optional<QuestDefinition> resolve(ResourceLocation id) {
        Optional<QuestDefinition> quest = QuestRegistry.get(id);
        if (quest.isPresent()) {
            return quest;
        }
        if (SituationIds.isSyntheticId(id)) {
            return SituationRegistry.getBySyntheticId(id).map(SituationDefinition::toOfferQuestDefinition);
        }
        return Optional.empty();
    }
}
