package dev.otectus.mcaquests.quest.situation;

import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.situation.state.SituationSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The villager an open situation is <em>about</em>.
 *
 * <p>A situation instance has always carried a focal villager — the one who collapsed, the one who caught
 * the infection, the one whose kin went missing — but nothing could target them. A situation offer is a
 * quest definition derived from the situation's <em>definition</em>, not its instance, so its objectives
 * could only speak in generalities: {@code cure_the_infected} asked the giver to cure "a relative of
 * whoever is talking", which is not the same person as the villager whose infection opened the situation,
 * and on a village-scoped situation is very often nobody at all.
 *
 * <p>This is the lookup that closes that gap, split out of {@code QuestManager} so a
 * {@code "mode": "situation_focus"} target can reach it from the offer path, the accept path and the quest
 * log alike. Everything fails safe to {@code empty}, which makes the objective unofferable rather than
 * pointed at the wrong villager.
 */
public final class SituationFocus {

    private SituationFocus() {
    }

    /**
     * The open situation behind a synthetic offer id, scoped to the giver's village.
     *
     * <p>Village-scoped because that is how the offer reached this villager in the first place: a
     * situation is surfaced by any eligible resident, so "which instance is this offer" is answered by
     * "the open one of this definition in this village".
     */
    public static Optional<SituationInstance> openInstance(@Nullable MinecraftServer server,
                                                           Entity giver, ResourceLocation syntheticId) {
        if (server == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> sourceId = SituationIds.sourceIdOf(syntheticId);
        OptionalInt villageId = McaCompat.getHomeVillageId(giver);
        if (sourceId.isEmpty() || villageId.isEmpty()) {
            return Optional.empty();
        }
        return SituationSavedData.get(server).openInstancesInVillage(villageId.getAsInt()).stream()
                .filter(instance -> instance.defId().equals(sourceId.get()))
                .findFirst();
    }

    /** The focal villager of the open situation behind a synthetic offer id, if it names one. */
    public static Optional<UUID> focalVillager(@Nullable MinecraftServer server, Entity giver,
                                               ResourceLocation syntheticId) {
        return openInstance(server, giver, syntheticId).flatMap(SituationInstance::villagerUuid);
    }

    /** The focal villager of one instance by id — the accepted-quest path, which stores the instance. */
    public static Optional<UUID> focalVillager(@Nullable MinecraftServer server, @Nullable UUID instanceId) {
        if (server == null || instanceId == null) {
            return Optional.empty();
        }
        return SituationSavedData.get(server).getInstance(instanceId)
                .flatMap(SituationInstance::villagerUuid);
    }
}
