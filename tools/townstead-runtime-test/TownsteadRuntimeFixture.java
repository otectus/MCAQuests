package dev.otectus.mcaquests.townsteadfixture;

import dev.otectus.mcaquests.compat.NeedMutation;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadMutationResult;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Disposable runtime test mod for the Townstead binding. Never included in the MCA: Quests release
 * JAR. Runs on a dedicated server with no player: spawns one MCA villager at spawn, waits for
 * Townstead to attach, then reads and writes through whichever bridge bound and writes every
 * outcome to {@code townstead-fixture-results.txt} in the server directory. The same fixture runs
 * against a Townstead 0.7.x (reflective bridge) and 0.8 (typed bridge), so the two can be compared.
 */
@Mod("mcaquests_townstead_fixture")
public final class TownsteadRuntimeFixture {

    private MinecraftServer server;
    private int tick;
    private Entity villager;
    private volatile boolean collapseEventSeen;
    private boolean done;

    public TownsteadRuntimeFixture() {
        if (!Boolean.getBoolean("mcaquests.townstead.fixture")) {
            throw new IllegalStateException("Fixture requires explicit opt-in (-Dmcaquests.townstead.fixture=true)");
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void started(ServerStartedEvent event) {
        server = event.getServer();
        report("server started; fixture armed");
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || done) {
            return;
        }
        tick++;
        try {
            TownsteadBridge bridge = TownsteadBridge.Holder.get();
            ServerLevel level = server.overworld();
            if (tick == 40) {
                report("bridge status=" + bridge.status() + " binding=" + bridge.bindingPath() + " variant="
                        + bridge.variant().orElse("?") + " version=" + bridge.detectedVersion() + " capabilities="
                        + bridge.capabilities().size() + "/" + TownsteadCapability.values().length
                        + " unresolved=" + bridge.unresolvedMembers());
                subscribeToCollapseEvent();
            }
            if (tick == 60) {
                EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("mca", "male_villager"));
                if (type == null) {
                    report("FAIL no mca:male_villager entity type");
                    finish();
                    return;
                }
                BlockPos spawn = level.getSharedSpawnPos();
                villager = type.create(level);
                villager.setPos(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D);
                level.addFreshEntity(villager);
                report("spawned " + villager.getType() + " at " + spawn);
            }
            if (tick == 260) {
                Optional<TownsteadVillagerView> view = bridge.villager(villager);
                report("read villager present=" + view.isPresent()
                        + view.map(v -> " hunger=" + v.needs().hunger() + " fatigue=" + v.needs().fatigue()
                                + " energy=" + v.needs().energy() + " collapsed=" + v.needs().collapsed()
                                + " gated=" + v.needs().gated() + " profession='" + v.professionId() + "'"
                                + " stage='" + v.lifeStage() + "'").orElse(""));
                report("calendar present=" + bridge.calendar(server).isPresent()
                        + " track(minecraft:farmer)=" + bridge.professionTrack("minecraft:farmer")
                        + " knownSkills=" + bridge.knownSkillIds().size());
            }
            if (tick == 280) {
                report("hunger -10 -> " + describe(bridge.changeNeeds(villager,
                        NeedMutation.delta(NeedMutation.Need.HUNGER, -10))));
                report("fatigue target 3 -> " + describe(bridge.changeNeeds(villager,
                        NeedMutation.target(NeedMutation.Need.FATIGUE, 3))));
                report("thirst +1 -> " + describe(bridge.changeNeeds(villager,
                        NeedMutation.delta(NeedMutation.Need.THIRST, 1))));
                report("xp farmer +25 -> " + describe(bridge.awardProfessionXp(villager, "minecraft:farmer", 25, true)));
                // Scenario 5, the daily-cap half: a request past the cap lands capped, and the next one
                // is refused outright. The day-boundary half still needs a real clock and a client.
                report("xp farmer +100000 (past any cap) -> " + describe(bridge.awardProfessionXp(villager,
                        "minecraft:farmer", 100000, true)));
                report("xp farmer +25 again -> " + describe(bridge.awardProfessionXp(villager, "minecraft:farmer", 25, true)));
                report("learn unknown skill -> " + describe(bridge.learnSkill(villager,
                        new ResourceLocation("mcaquests", "no_such_skill"), false)));
                // Scenario 6: a skill reward applied twice is an idempotent success, then forgettable.
                Optional<ResourceLocation> known = bridge.knownSkillIds().stream().sorted().findFirst();
                report("known skill=" + known.map(ResourceLocation::toString).orElse("none"));
                if (known.isPresent()) {
                    report("learn known (force) -> " + describe(bridge.learnSkill(villager, known.get(), true)));
                    report("learn known again -> " + describe(bridge.learnSkill(villager, known.get(), true)));
                    report("has known=" + bridge.hasSkill(villager, known.get()) + " learned=" + bridge.learnedSkills(villager));
                    report("forget known -> " + describe(bridge.forgetSkill(villager, known.get())));
                    report("forget known again -> " + describe(bridge.forgetSkill(villager, known.get())));
                }
            }
            if (tick == 300) {
                Optional<TownsteadVillagerView> view = bridge.villager(villager);
                report("re-read hunger=" + view.map(v -> String.valueOf(v.needs().hunger())).orElse("?")
                        + " fatigue=" + view.map(v -> String.valueOf(v.needs().fatigue())).orElse("?"));
                report("energy target 0 (collapse) -> " + describe(bridge.changeNeeds(villager,
                        NeedMutation.target(NeedMutation.Need.ENERGY, 0))));
            }
            if (tick == 700) {
                Optional<TownsteadVillagerView> view = bridge.villager(villager);
                report("after collapse push: collapsed=" + view.map(v -> String.valueOf(v.needs().collapsed())).orElse("?")
                        + " townstead collapse event seen by fixture=" + collapseEventSeen);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "mcaquests compat townstead status");
                report("DONE");
                finish();
            }
        } catch (Throwable error) {
            report("FAIL tick " + tick + " " + error);
            finish();
        }
    }

    /**
     * Listens to Townstead's own API feed for the collapse, purely to show the feed posts on this
     * build. Reflective and optional: on 0.7.x there is no API and this reports "unavailable".
     */
    private void subscribeToCollapseEvent() {
        try {
            Class<?> apiClass = Class.forName("com.aetherianartificer.townstead.api.v1.TownsteadApiV1");
            Object api = apiClass.getMethod("get").invoke(null);
            Object events = apiClass.getMethod("events").invoke(api);
            Class<?> eventType = Class.forName("com.aetherianartificer.townstead.api.v1.event.VillagerCollapsedEvent");
            Method subscribe = events.getClass().getMethod("subscribe", Class.class, Consumer.class);
            subscribe.setAccessible(true);
            Consumer<Object> listener = e -> collapseEventSeen = true;
            subscribe.invoke(events, eventType, listener);
            report("api events feed: subscribed to VillagerCollapsedEvent");
        } catch (ClassNotFoundException absent) {
            report("api events feed: unavailable (no api.v1)");
        } catch (Throwable t) {
            report("api events feed: FAIL " + t);
        }
    }

    private static String describe(TownsteadMutationResult result) {
        return result.reason() + " requested=" + result.requested() + " applied=" + result.applied() + " before="
                + result.before() + " after=" + result.after() + " tiers=" + result.oldTier() + "->" + result.newTier();
    }

    private void finish() {
        done = true;
    }

    private void report(String message) {
        try {
            Path path = server == null ? Path.of("townstead-fixture-results.txt")
                    : server.getServerDirectory().toPath().resolve("townstead-fixture-results.txt");
            Files.writeString(path, message + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

}
