package dev.otectus.mcaquests.reputation;

import dev.otectus.mcaquests.compat.IncidentSelector;
import dev.otectus.mcaquests.compat.ReputationAward;
import dev.otectus.mcaquests.compat.ReputationBackend;
import dev.otectus.mcaquests.compat.ReputationBridge;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.RecordIncidentReward;
import dev.otectus.mcaquests.quest.reward.ResolveIncidentReward;
import dev.otectus.mcaquests.support.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two incident rewards land on the village the quest froze at accept when the giver cannot name one
 * (1.7.1), exactly as the quest's own standing and a village-scoped title from the same turn-in do.
 */
class IncidentRewardVillageFallbackTest {

    static {
        TestBootstrap.ensureBootstrapped();
    }

    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final UUID PLAYER = new UUID(9L, 9L);
    private static final UUID GIVER = new UUID(5L, 5L);
    private static final ResourceLocation QUEST = new ResourceLocation("testpack", "restitution");

    @AfterEach
    void restoreBackend() {
        ReputationBridge.resetForTest();
    }

    private static QuestReward.RewardContext frozenAt(OptionalInt village) {
        return new QuestReward.RewardContext(GIVER, Component.literal("Anna"), OVERWORLD, village, QUEST,
                Optional.of(new UUID(1L, 1L)));
    }

    @Test
    @DisplayName("record_incident with no giver entity records against the frozen village, naming the giver")
    void recordFallsBackToFrozenVillage() throws Exception {
        Recording backend = new Recording();
        ReputationBridge.setBackendForTest(backend);
        new RecordIncidentReward(new ResourceLocation("mcareputation", "restitution_completed"), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty())
                .grant(player(), null, frozenAt(OptionalInt.of(0)));

        assertEquals(1, backend.recorded.size());
        ReputationAward award = backend.recorded.get(0);
        assertEquals(OVERWORLD, award.dimension());
        assertEquals(0, award.villageId(), "village 0 is a real MCA village");
        assertEquals(GIVER, award.subjectUuid());
        assertEquals("Anna", award.subjectName());
    }

    @Test
    @DisplayName("resolve_incident with no giver entity resolves against the frozen village")
    void resolveFallsBackToFrozenVillage() throws Exception {
        Recording backend = new Recording();
        ReputationBridge.setBackendForTest(backend);
        new ResolveIncidentReward(Optional.of(new ResourceLocation("mcareputation", "villager_assaulted")),
                List.of("active"), List.of(), "atoned")
                .grant(player(), null, frozenAt(OptionalInt.of(4)));

        assertEquals(List.of(OVERWORLD + "/4"), backend.resolved);
    }

    @Test
    @DisplayName("with no village anywhere, neither reward writes anything")
    void noVillageNoWrite() throws Exception {
        Recording backend = new Recording();
        ReputationBridge.setBackendForTest(backend);
        ServerPlayer player = player();
        new RecordIncidentReward(new ResourceLocation("mcareputation", "restitution_completed"), Optional.empty(),
                Optional.empty(), List.of(), Optional.empty()).grant(player, null, frozenAt(OptionalInt.empty()));
        new ResolveIncidentReward(Optional.of(new ResourceLocation("mcareputation", "villager_assaulted")),
                List.of(), List.of(), "atoned").grant(player, null);

        assertTrue(backend.recorded.isEmpty());
        assertTrue(backend.resolved.isEmpty());
    }

    /** A player with an identity and nothing else; neither reward reads the world. */
    private static ServerPlayer player() throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field access = unsafeType.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        ServerPlayer player = (ServerPlayer) unsafeType.getMethod("allocateInstance", Class.class)
                .invoke(access.get(null), ServerPlayer.class);
        Field uuid = Entity.class.getDeclaredField("uuid");
        uuid.setAccessible(true);
        uuid.set(player, PLAYER);
        return player;
    }

    /** Records every incident write it is asked for. */
    private static final class Recording implements ReputationBackend {

        private final List<ReputationAward> recorded = new ArrayList<>();
        private final List<String> resolved = new ArrayList<>();

        @Override
        public boolean isCanonical() {
            return true;
        }

        @Override
        public String backendName() {
            return "test:recording";
        }

        @Override
        public int score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId) {
            return 0;
        }

        @Override
        public String tierId(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                             ResourceLocation ladder) {
            return "stranger";
        }

        @Override
        public int tierIndex(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                             ResourceLocation ladder) {
            return 0;
        }

        @Override
        public Map<Integer, Integer> villageScores(MinecraftServer server, UUID player, ResourceLocation dimension) {
            return Map.of();
        }

        @Override
        public Optional<String> tierHighWater(MinecraftServer server, UUID player, ResourceLocation dimension,
                                              int villageId, ResourceLocation ladder) {
            return Optional.empty();
        }

        @Override
        public int award(ReputationAward award) {
            return 0;
        }

        @Override
        public boolean grantTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                                  int villageId, ResourceLocation title, boolean global) {
            return false;
        }

        @Override
        public boolean hasTitle(MinecraftServer server, UUID player, @Nullable ResourceLocation dimension,
                                int villageId, ResourceLocation title, boolean global) {
            return false;
        }

        @Override
        public Set<ResourceLocation> globalTitles(MinecraftServer server, UUID player) {
            return Set.of();
        }

        @Override
        public Set<ResourceLocation> villageTitles(MinecraftServer server, UUID player, ResourceLocation dimension,
                                                   int villageId) {
            return Set.of();
        }

        @Override
        public boolean hasIncident(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId,
                                   IncidentSelector selector) {
            return false;
        }

        @Override
        public boolean resolveIncident(MinecraftServer server, UUID player, ResourceLocation dimension,
                                       int villageId, IncidentSelector selector, String resolution,
                                       @Nullable String dedupeKey) {
            resolved.add(dimension + "/" + villageId);
            return true;
        }

        @Override
        public boolean recordIncident(ReputationAward award) {
            recorded.add(award);
            return true;
        }
    }
}
