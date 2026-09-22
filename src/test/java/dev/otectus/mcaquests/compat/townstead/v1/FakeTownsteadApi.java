package dev.otectus.mcaquests.compat.townstead.v1;

import com.aetherianartificer.townstead.api.v1.CalendarApi;
import com.aetherianartificer.townstead.api.v1.ChroniclesApi;
import com.aetherianartificer.townstead.api.v1.EventsApi;
import com.aetherianartificer.townstead.api.v1.HangoutsApi;
import com.aetherianartificer.townstead.api.v1.ProfessionsApi;
import com.aetherianartificer.townstead.api.v1.RegistriesApi;
import com.aetherianartificer.townstead.api.v1.SchedulesApi;
import com.aetherianartificer.townstead.api.v1.SocialApi;
import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.VillagersApi;
import com.aetherianartificer.townstead.api.v1.VillagesApi;
import com.aetherianartificer.townstead.api.v1.WorkApi;
import com.aetherianartificer.townstead.api.v1.event.Subscription;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.api.v1.model.CareerSnapshot;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProfessionProgressSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProfessionSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProgressionTrackSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SkillSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillagerRecord;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.result.NeedResult;
import com.aetherianartificer.townstead.api.v1.result.SkillResult;
import com.aetherianartificer.townstead.api.v1.result.XpResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * A {@link TownsteadApiV1} built from the real API types, with the villagers, professions and
 * events facades scripted per test and every other facade refusing to be called. Compiled against
 * the pinned API jar, so a signature drift upstream fails here before it fails in a player's log.
 */
final class FakeTownsteadApi implements TownsteadApiV1 {

    int apiVersion = 1;
    int apiRevision = 1;
    String modVersion = "0.8.0+1.20.1";

    final ScriptedVillagers villagers = new ScriptedVillagers();
    final ScriptedProfessions professions = new ScriptedProfessions();
    final RecordingEvents events = new RecordingEvents();

    @Override
    public int getApiVersion() {
        return apiVersion;
    }

    @Override
    public int getApiRevision() {
        return apiRevision;
    }

    @Override
    public String getModVersion() {
        return modVersion;
    }

    @Override
    public VillagersApi villagers() {
        return villagers;
    }

    @Override
    public VillagesApi villages() {
        return refusing(VillagesApi.class);
    }

    @Override
    public ProfessionsApi professions() {
        return professions;
    }

    @Override
    public CalendarApi calendar() {
        return refusing(CalendarApi.class);
    }

    @Override
    public SocialApi social() {
        return refusing(SocialApi.class);
    }

    @Override
    public ChroniclesApi chronicles() {
        return refusing(ChroniclesApi.class);
    }

    @Override
    public EventsApi events() {
        return events;
    }

    @Override
    public WorkApi work() {
        return refusing(WorkApi.class);
    }

    @Override
    public HangoutsApi hangouts() {
        return refusing(HangoutsApi.class);
    }

    @Override
    public SchedulesApi schedules() {
        return refusing(SchedulesApi.class);
    }

    @Override
    public RegistriesApi registries() {
        return refusing(RegistriesApi.class);
    }

    @SuppressWarnings("unchecked")
    private static <T> T refusing(Class<T> facade) {
        return (T) Proxy.newProxyInstance(facade.getClassLoader(), new Class<?>[]{facade}, (proxy, method, args) -> {
            throw new UnsupportedOperationException(facade.getSimpleName() + "." + method.getName() + " is not scripted");
        });
    }

    /** Records the last mutation request and answers with whatever the test scripted. */
    static final class ScriptedVillagers implements VillagersApi {
        /** When set, every call raises it: what an API whose signatures moved looks like from here. */
        LinkageError drift;
        String lastNeedId;
        Integer lastValue;
        Boolean lastRelative;
        ResourceLocation lastSource;
        NeedResult nextNeedResult;
        VillagerSnapshot snapshot;
        VillagerRecord record;

        @Override
        public Optional<VillagerSnapshot> snapshot(Entity entity) {
            if (drift != null) throw drift;
            return Optional.ofNullable(snapshot);
        }

        @Override
        public Optional<VillagerSnapshot> snapshot(MinecraftServer server, UUID id) {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public Optional<NeedsSnapshot> needs(Entity entity) {
            return Optional.ofNullable(snapshot).map(VillagerSnapshot::needs);
        }

        @Override
        public Optional<VillagerRecord> record(MinecraftServer server, UUID id) {
            return Optional.ofNullable(record);
        }

        @Override
        public List<String> needIds() {
            return List.of(NeedsSnapshot.HUNGER, NeedsSnapshot.SATURATION, NeedsSnapshot.THIRST,
                    NeedsSnapshot.QUENCHED, NeedsSnapshot.ENERGY, NeedsSnapshot.TEMPERATURE);
        }

        @Override
        public NeedResult setNeed(Entity entity, String needId, int value, ResourceLocation source) {
            lastNeedId = needId;
            lastValue = value;
            lastRelative = false;
            lastSource = source;
            return nextNeedResult;
        }

        @Override
        public NeedResult adjustNeed(Entity entity, String needId, int delta, ResourceLocation source) {
            if (drift != null) throw drift;
            lastNeedId = needId;
            lastValue = delta;
            lastRelative = true;
            lastSource = source;
            return nextNeedResult;
        }
    }

    static final class ScriptedProfessions implements ProfessionsApi {
        ProgressionTrackSnapshot track;
        XpResult nextXpResult;
        SkillResult nextSkillResult;
        Integer lastXpRequested;
        Boolean lastRespectCap;
        Boolean lastForce;
        ResourceLocation lastSource;

        @Override
        public Optional<ProgressionTrackSnapshot> track(String professionId) {
            return Optional.ofNullable(track);
        }

        @Override
        public Optional<ProfessionSnapshot> profession(String professionId) {
            return Optional.empty();
        }

        @Override
        public List<String> professionIds() {
            return List.of();
        }

        @Override
        public Optional<ProfessionProgressSnapshot> progress(Entity entity, String professionId) {
            return Optional.empty();
        }

        @Override
        public Optional<CareerSnapshot> career(Entity entity) {
            return Optional.empty();
        }

        @Override
        public Set<ResourceLocation> skillIds() {
            return Set.of();
        }

        @Override
        public boolean isKnownSkill(ResourceLocation skillId) {
            return false;
        }

        @Override
        public Optional<SkillSnapshot> skill(ResourceLocation skillId) {
            return Optional.empty();
        }

        @Override
        public Set<ResourceLocation> learnedSkills(Entity entity) {
            return Set.of();
        }

        @Override
        public boolean hasSkill(Entity entity, ResourceLocation skillId) {
            return false;
        }

        @Override
        public XpResult awardXp(Entity entity, String professionId, int amount, boolean respectDailyCap,
                                ResourceLocation source) {
            lastXpRequested = amount;
            lastRespectCap = respectDailyCap;
            lastSource = source;
            return nextXpResult;
        }

        @Override
        public SkillResult learnSkill(Entity entity, ResourceLocation skillId, boolean force, ResourceLocation source) {
            lastForce = force;
            lastSource = source;
            return nextSkillResult;
        }

        @Override
        public SkillResult forgetSkill(Entity entity, ResourceLocation skillId, boolean force, ResourceLocation source) {
            lastForce = force;
            lastSource = source;
            return nextSkillResult;
        }
    }

    /** Hands out real, closable subscriptions and can be told to fail on the n-th one. */
    static final class RecordingEvents implements EventsApi {
        final List<Recorded<?>> subscriptions = new ArrayList<>();
        int failOnSubscribe = -1;

        @Override
        public <E extends TownsteadEvent> Subscription subscribe(Class<E> type, Consumer<E> listener) {
            if (subscriptions.size() == failOnSubscribe) {
                throw new IllegalStateException("scripted failure on subscription " + failOnSubscribe);
            }
            Recorded<E> recorded = new Recorded<>(type, listener);
            subscriptions.add(recorded);
            return recorded;
        }

        long active() {
            return subscriptions.stream().filter(Subscription::isActive).count();
        }

        static final class Recorded<E extends TownsteadEvent> implements Subscription {
            final Class<E> type;
            final Consumer<E> listener;
            private boolean active = true;

            Recorded(Class<E> type, Consumer<E> listener) {
                this.type = type;
                this.listener = listener;
            }

            @Override
            public boolean isActive() {
                return active;
            }

            @Override
            public void close() {
                active = false;
            }
        }
    }
}
