package dev.otectus.mcaquests.rt;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.MapCodec;
import dev.otectus.mcaquests.api.McaQuestsApi;
import dev.otectus.mcaquests.api.QuestCompletionReceipt;
import dev.otectus.mcaquests.api.event.QuestCompletionReceiptReadyEvent;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.mca.McaDialogueHookProbe;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.data.UnavailableContent;
import dev.otectus.mcaquests.event.ConversationCredit;
import dev.otectus.mcaquests.project.ProjectDrift;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.project.ProjectRecovery;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.escort.EscortHoldRegistry;
import dev.otectus.mcaquests.quest.reward.QuestReward;
import dev.otectus.mcaquests.quest.reward.QuestRewardType;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import dev.otectus.mcaquests.state.ActiveQuest;
import dev.otectus.mcaquests.state.ContentOutageData;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.UUID;

/**
 * Disposable runtime test mod for the 1.7.0 reliability update. Never shipped.
 *
 * <p>Runs once, a few seconds after a dedicated server starts, against the real MCA and whatever
 * companion mods are installed, and writes one line per check to {@code mcaqrt-results.txt}:
 * which content loaded or was excluded, whether MCA's dialogue hook applied, whether a conversation
 * driven through the same calls the network handler makes is credited once (and a sneak-trade click is
 * not), whether placements count inside and not outside an area, and whether an instance-targeted
 * repair leaves a second instance alone and refuses a reused token.
 *
 * <p>The 1.7.0 fixes are checked the same way: personal place/break farming, a reward that throws during
 * a real turn-in, a project whose MCA village does not exist, a completion receipt through the real
 * player-file fence, and a {@code /reload}. The restart row ({@code -Dmcaquests.rt.phase=restart1..3})
 * carries escort holds, a reordered project phase and a Townstead outage across three boots of one world.
 */
@Mod("mcaquests_rt")
public final class ReliabilityRuntimeFixture {

    private static final ResourceLocation TALK_QUEST = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_talk");
    private static final ResourceLocation CENSUS = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_census");
    private static final ResourceLocation BUILD = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_build");
    private static final ResourceLocation PLACE_QUEST = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_place");
    private static final ResourceLocation BREAK_QUEST = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_break");
    private static final ResourceLocation HELD_QUEST = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_held");
    private static final ResourceLocation RECEIPT_QUEST = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_receipt");
    private static final ResourceLocation DELIVER_QUEST = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_deliver");
    private static final ResourceLocation DRIFT = ResourceLocation.fromNamespaceAndPath("mcaqrt", "rt_drift");
    private static final ResourceLocation RECEIPT_CONSUMER = ResourceLocation.fromNamespaceAndPath("mcaqrt", "receipts");
    private static final ResourceLocation WORKING_VILLAGE = ResourceLocation.fromNamespaceAndPath("mcaquests", "townstead_a_working_village");
    /** Owns the restart row's holds and is never online, so a held villager must stay held across boots. */
    private static final UUID OFFLINE_OWNER = UUID.fromString("7a7a7a7a-0000-4000-8000-0000000e5c07");
    private static final Path STATE = Path.of("mcaqrt-state.properties");

    /**
     * {@code single} (the default) runs every one-boot scenario. {@code restart1}, {@code restart2} and
     * {@code restart3} are three boots of one world: the first holds villagers, opens a project phase and
     * notes what content loaded; the row then removes Townstead and adds a datapack that reorders the
     * phase; the third boot restores Townstead.
     */
    private final String phase = System.getProperty("mcaquests.rt.phase", "single");
    private final List<String> results = new ArrayList<>();
    /** Holds the halt back to this tick so a second fixture sharing the boot can finish (the Townstead one). */
    private final int haltAt = Integer.getInteger("mcaquests.rt.haltAt", 0);
    private int ticks;
    private boolean done;
    private boolean halted;
    private int receiptEvents;
    private boolean reloadSynced;
    private int questsBeforeReload = -1;
    private FakePlayer guidancePlayer;
    private FakePlayer secondGuidancePlayer;
    private final List<String> guidanceTimings = new ArrayList<>();

    public ReliabilityRuntimeFixture() {
        if (!Boolean.getBoolean("mcaquests.rt.fixture")) {
            throw new IllegalStateException("The reliability fixture requires -Dmcaquests.rt.fixture=true");
        }
        ExplodingReward.TYPE = McaQuestsApi.registerReward(ResourceLocation.fromNamespaceAndPath("mcaqrt", "explode"),
                MapCodec.unit(new ExplodingReward()));
        NeoForge.EVENT_BUS.register(this);
        if ("client".equals(phase) && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.register(new ClientJournalProbe());
        }
    }

    /**
     * Client half of the {@code client} phase (1.7.1): watches the journal cache for the standing the
     * integrated server awards, without the journal screen ever asking for it. Loaded only on a client.
     */
    private static final class ClientJournalProbe {
        private boolean reported;
        private boolean published;

        @SubscribeEvent
        public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (reported) {
                return;
            }
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (!published && minecraft.player != null && minecraft.getSingleplayerServer() != null) {
                // MCA keeps its first-join character screen open, which would pause the integrated server.
                // A server opened to LAN never pauses for a menu, and the journal must arrive by itself.
                published = minecraft.getSingleplayerServer().publishServer(
                        net.minecraft.world.level.GameType.SURVIVAL, false, 25640);
                dev.otectus.mcaquests.McaQuests.LOGGER.info("MCAQ-RT client probe opened to LAN={} (screen {})",
                        published, minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
            }
            for (dev.otectus.mcaquests.network.JournalVillageEntry village
                    : dev.otectus.mcaquests.client.ClientJournalData.villages()) {
                // A fresh player: any standing in the cache arrived with the turn-in's push.
                if (village.reputation() > 0) {
                    reported = true;
                    dev.otectus.mcaquests.McaQuests.LOGGER.info("MCAQ-RT JOURNAL-PUSH PASS the client journal cache"
                            + " received village {} at {} with no journal request", village.villageId(),
                            village.reputation());
                }
            }
        }
    }

    /** A reward that always throws, so a real turn-in exercises the held-reward path. */
    private record ExplodingReward() implements QuestReward {
        static QuestRewardType<ExplodingReward> TYPE;

        @Override
        public QuestRewardType<?> type() {
            return TYPE;
        }

        @Override
        public Component describe() {
            return Component.literal("a reward that throws");
        }

        @Override
        public void grant(net.minecraft.server.level.ServerPlayer player, Entity villager) {
            throw new IllegalStateException("mcaqrt: this reward throws on purpose");
        }
    }

    @SubscribeEvent
    public void onReceiptReady(QuestCompletionReceiptReadyEvent event) {
        receiptEvents++;
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            reloadSynced = true;
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        ++ticks;
        MinecraftServer server = event.getServer();
        if ("client".equals(phase)) {
            clientPhase(server);
            return;
        }
        if (done) {
            haltWhenDue(server);
            return;
        }
        boolean finish = false;
        try {
            if (ticks == 100) {
                status();
                switch (phase) {
                    case "restart1" -> restart1(server);
                    case "restart2" -> restart2(server);
                    case "restart3" -> restart3(server);
                    case "delivery" -> delivery(server);
                    default -> {
                        talk(server);
                        delivery(server);
                        placement(server);
                        recovery(server);
                        farm(server);
                        held(server);
                        villageGone(server);
                        receipt(server);
                        guidanceSetup(server);
                        guidanceMeasure(server, "first");
                        startReload(server);
                    }
                }
                finish = !"single".equals(phase);
            } else if (ticks == 380 && "single".equals(phase)) {
                guidanceMeasure(server, "settled");
            } else if (ticks == 400) {
                finishReload();
                guidanceVerdict();
                finish = true;
            }
        } catch (Throwable t) {
            record("ERROR " + t);
            for (StackTraceElement element : t.getStackTrace()) {
                record("  at " + element);
            }
            finish = true;
        }
        if (!finish) {
            return;
        }
        done = true;
        try {
            Files.write(Path.of("mcaqrt-results.txt"), results);
        } catch (Exception e) {
            record("could not write results: " + e);
        }
        haltWhenDue(server);
    }

    private void haltWhenDue(MinecraftServer server) {
        if (!halted && Boolean.getBoolean("mcaquests.rt.stop") && ticks >= haltAt) {
            halted = true;
            server.halt(false);
        }
    }

    private void record(String line) {
        results.add(line);
        dev.otectus.mcaquests.McaQuests.LOGGER.info("MCAQ-RT {}", line);
    }

    private void status() {
        record("townstead=" + TownsteadBridge.Holder.get().status() + " capitals=" + CapitalsCompat.bridge().status());
        record("loaded quests=" + QuestRegistry.size() + " projects=" + ProjectRegistry.all().size()
                + " situations=" + SituationRegistry.all().size());
        for (UnavailableContent.Kind kind : UnavailableContent.Kind.values()) {
            record("excluded " + kind + "=" + UnavailableContent.all(kind).size());
        }
        record("project known_far_and_wide loaded=" + ProjectRegistry.get(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "townstead_known_far_and_wide")).isPresent()
                + " excluded=" + UnavailableContent.contains(UnavailableContent.Kind.PROJECT,
                ResourceLocation.fromNamespaceAndPath("mcaquests", "townstead_known_far_and_wide")));
        record("project a_working_village loaded=" + ProjectRegistry.get(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "townstead_a_working_village")).isPresent());
        record("project walls_before_winter loaded=" + ProjectRegistry.get(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "walls_before_winter")).isPresent());
        record("quest road_the_missing_mile loaded=" + QuestRegistry.get(
                ResourceLocation.fromNamespaceAndPath("mcaquests", "road_the_missing_mile")).isPresent());
        long townsteadQuests = QuestRegistry.all().stream().filter(q -> q.id().getPath().startsWith("townstead_")).count();
        record("loaded quests with townstead_ ids=" + townsteadQuests);
        for (String trade : List.of("minecraft:farmer", "minecraft:shepherd", "minecraft:butcher", "minecraft:fisherman",
                "townstead:cook")) {
            record("track " + trade + " = " + TownsteadBridge.Holder.get().professionTrack(trade));
        }
        // Which overload the pre-1.7.0 binder (first name/arity match in getMethods() order) took here.
        String[][] overloaded = {
                {"villager.ProfessionProgressions", "spec", "1"},
                {"profession.skill.LearnedSkills", "learned", "1"},
                {"profession.skill.LearnedSkills", "has", "2"},
                {"profession.skill.LearnedSkills", "learn", "2"},
                {"profession.skill.LearnedSkills", "forceLearn", "2"},
                {"profession.skill.LearnedSkills", "forget", "2"}};
        for (String[] member : overloaded) {
            try {
                Class<?> owner = Class.forName("com.aetherianartificer.townstead." + member[0], false,
                        ReliabilityRuntimeFixture.class.getClassLoader());
                for (java.lang.reflect.Method method : owner.getMethods()) {
                    if (method.getName().equals(member[1]) && method.getParameterCount() == Integer.parseInt(member[2])) {
                        record("pre-1.7.0 binding of " + member[0] + "#" + member[1] + " took ("
                                + method.getParameterTypes()[0].getName() + ", ...)");
                        break;
                    }
                }
            } catch (Throwable t) {
                record("pre-1.7.0 binding of " + member[0] + "#" + member[1] + ": " + t);
            }
        }
    }

    // ------------------------------------------------------------------ conversations

    private void talk(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-00000000c0de"), "RTPlayer"));
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        Villager first = spawnCartographer(level, spawn.offset(1, 0, 0), "mca:male_villager");
        Villager second = spawnCartographer(level, spawn.offset(-1, 0, 0), "mca:female_villager");
        if (first == null || second == null) {
            record("talk SKIPPED: MCA villagers could not be spawned");
            return;
        }
        record("dialogue hook before first click: applied=" + McaDialogueHookProbe.applied() + " " + McaDialogueHookProbe.describe());

        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (data == null) {
            record("talk SKIPPED: no quest data on the fake player");
            return;
        }
        ActiveQuest active = ActiveQuest.create(TALK_QUEST, first.getUUID(), Component.literal("giver"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(), 1, null);
        data.add(active);

        ProjectSavedData projects = ProjectSavedData.get(server);
        ProjectState census = new ProjectState(CENSUS, ProjectScope.VILLAGE, "anchor:rt-census", level.dimension().location(),
                spawn, OptionalInt.empty(), level.getGameTime(), 1);
        census.freezeAnchorRadius(32);
        projects.putInstance(census);
        census.addParticipant(player.getUUID());

        InteractionResult click1 = click(player, first);
        record("click 1 (empty hand) result=" + click1 + " quest=" + active.progress(0).count() + "/2 project="
                + census.progress(0).count() + "/2 hookObserved=" + McaDialogueHookProbe.wasObserved()
                + " hookActive=" + ConversationCredit.dialogueHookActive());
        InteractionResult again = click(player, first);
        record("click 2 (same villager) result=" + again + " quest=" + active.progress(0).count() + "/2 project="
                + census.progress(0).count() + "/2");

        player.setShiftKeyDown(true);
        InteractionResult sneak = click(player, second);
        player.setShiftKeyDown(false);
        record("click 3 (sneaking, opens trading) result=" + sneak + " quest=" + active.progress(0).count()
                + "/2 project=" + census.progress(0).count() + "/2");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        InteractionResult held = click(player, second);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        record("click 4 (holding a stick) result=" + held + " quest=" + active.progress(0).count() + "/2 project="
                + census.progress(0).count() + "/2");
        record("TALK " + (active.progress(0).count() == 2 && census.progress(0).count() == 2 ? "PASS" : "FAIL")
                + " expected quest=2/2 project=2/2 with the sneak click not counted");
        projects.removeInstance(census.key().asString());
    }

    /** The calls the server's interaction packet handler makes for a client's right-click. */
    private static InteractionResult click(FakePlayer player, Entity target) {
        InteractionHand hand = InteractionHand.MAIN_HAND;
        Vec3 hit = new Vec3(0.0, target.getBbHeight() * 0.5, 0.0);
        InteractionResult result = CommonHooks.onInteractEntityAt(player, target, hit, hand);
        if (result == null) {
            result = target.interactAt(player, hit, hand);
        }
        if (!result.consumesAction()) {
            result = player.interactOn(target, hand);
        }
        return result;
    }

    private Villager spawnCartographer(ServerLevel level, BlockPos pos, String type) {
        Entity entity = EntityType.byString(type).map(t -> t.create(level)).orElse(null);
        if (!(entity instanceof Villager villager)) {
            record("could not create " + type + ": " + entity);
            return null;
        }
        villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER));
        level.addFreshEntity(villager);
        return villager;
    }

    // ------------------------------------------------------------------ placement

    private void placement(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-00000000b10c"), "RTBuilder"));
        ProjectSavedData projects = ProjectSavedData.get(server);
        ProjectState build = new ProjectState(BUILD, ProjectScope.VILLAGE, "anchor:rt-build", level.dimension().location(),
                spawn, OptionalInt.empty(), level.getGameTime(), 1);
        build.freezeAnchorRadius(16);
        projects.putInstance(build);

        boolean inside = place(level, player, spawn.offset(5, 0, 5));
        int afterInside = build.progress(0).count();
        boolean outside = place(level, player, spawn.offset(40, 0, 0));
        int afterOutside = build.progress(0).count();
        BlockPos again = spawn.offset(5, 0, 5);
        level.setBlockAndUpdate(again, Blocks.AIR.defaultBlockState());
        place(level, player, again);
        int afterReplace = build.progress(0).count();
        record("placement inside placed=" + inside + " count=" + afterInside + "; outside placed=" + outside + " count="
                + afterOutside + "; re-placed counted spot count=" + afterReplace);
        record("PLACE " + (afterInside == 1 && afterOutside == 1 && afterReplace == 1 ? "PASS" : "FAIL")
                + " expected 1,1,1 (inside counts, 40 blocks out does not, a re-placed spot does not count twice)");
        projects.removeInstance(build.key().asString());
    }

    /** Places cobblestone on top of whatever is at {@code pos} through the item's own use path. */
    private static boolean place(ServerLevel level, FakePlayer player, BlockPos pos) {
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).below();
        player.moveTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 2.5, 0, 0);
        ItemStack stack = new ItemStack(Items.COBBLESTONE, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return result.consumesAction();
    }

    // ------------------------------------------------------------------ recovery

    private void recovery(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ProjectSavedData projects = ProjectSavedData.get(server);
        ProjectState a = new ProjectState(BUILD, ProjectScope.VILLAGE, "anchor:rt-a", level.dimension().location(),
                BlockPos.ZERO, OptionalInt.empty(), level.getGameTime(), 1);
        ProjectState b = new ProjectState(BUILD, ProjectScope.VILLAGE, "anchor:rt-b", level.dimension().location(),
                new BlockPos(500, 64, 500), OptionalInt.empty(), level.getGameTime(), 1);
        projects.putInstance(a);
        projects.putInstance(b);
        List<ProjectState> both = ProjectRecovery.instancesOf(server, BUILD);
        List<Component> preview = ProjectRecovery.preview("RT", server, both.get(0), ProjectRecovery.Operation.SKIP_NO_REWARDS, -1, 0);
        String last = preview.get(preview.size() - 1).getString();
        String token = last.substring(last.lastIndexOf(' ') + 1);
        int phaseBefore = both.get(1).currentPhase();
        Component applied = ProjectRecovery.confirm("RT", server, token);
        Component reused = ProjectRecovery.confirm("RT", server, token);
        ProjectState first = projects.getInstance(both.get(0).key().asString()).orElseThrow();
        ProjectState second = projects.getInstance(both.get(1).key().asString()).orElseThrow();
        record("repair applied='" + applied.getString() + "' reused='" + reused.getString() + "'");
        record("REPAIR " + (first.currentPhase() == 1 && second.currentPhase() == phaseBefore
                && reused.getString().startsWith("Unknown") && first.isPhaseDistributed(0)
                ? "PASS" : "FAIL") + " expected: only the chosen instance advanced, the token refused on reuse");
        projects.removeInstance(a.key().asString());
        projects.removeInstance(b.key().asString());
    }

    // ------------------------------------------------------------------ personal place/break farming (1.7.0)

    private void farm(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-00000000fa53"), "RTFarmer"));
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (data == null) {
            record("FARM SKIPPED: no quest data on the fake player");
            return;
        }
        ActiveQuest placeQuest = ActiveQuest.create(PLACE_QUEST, UUID.randomUUID(), Component.literal("giver"), null,
                level.dimension().location(), level.getGameTime(), 1, null);
        ActiveQuest breakQuest = ActiveQuest.create(BREAK_QUEST, UUID.randomUUID(), Component.literal("giver"), null,
                level.dimension().location(), level.getGameTime(), 1, null);
        data.add(placeQuest);
        data.add(breakQuest);

        BlockPos a = placeItem(level, player, spawn.offset(10, 0, -10), Items.COBBLESTONE);
        int place1 = placeQuest.progress(0).count();
        boolean broke1 = a != null && player.gameMode.destroyBlock(a);
        int break1 = breakQuest.progress(0).count();
        BlockPos again = placeItem(level, player, spawn.offset(10, 0, -10), Items.COBBLESTONE);
        int place2 = placeQuest.progress(0).count();
        boolean broke2 = again != null && player.gameMode.destroyBlock(again);
        int break2 = breakQuest.progress(0).count();
        BlockPos natural = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                spawn.offset(12, 0, -12));
        level.setBlockAndUpdate(natural, Blocks.COBBLESTONE.defaultBlockState());
        boolean broke3 = player.gameMode.destroyBlock(natural);
        int break3 = breakQuest.progress(0).count();
        BlockPos fresh = placeItem(level, player, spawn.offset(14, 0, -14), Items.COBBLESTONE);
        int place3 = placeQuest.progress(0).count();
        record("farm place A=" + a + " place=" + place1 + "; break A broke=" + broke1 + " break=" + break1
                + "; re-place A at " + again + " place=" + place2 + "; break again broke=" + broke2 + " break=" + break2
                + "; break a block nobody placed broke=" + broke3 + " break=" + break3 + "; place elsewhere at " + fresh
                + " place=" + place3);
        record("FARM " + (place1 == 1 && break1 == 0 && a != null && a.equals(again) && place2 == 1 && break2 == 0
                && break3 == 1 && place3 == 2 ? "PASS" : "FAIL")
                + " expected place 1,1,2 and break 0,0,1 (own blocks never count as broken, a spot counts once)");
        data.remove(placeQuest);
        data.remove(breakQuest);
    }

    /** Places one of {@code item} on top of whatever is at {@code pos}; the placed position, or null. */
    private static BlockPos placeItem(ServerLevel level, FakePlayer player, BlockPos pos, net.minecraft.world.item.Item item) {
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).below();
        player.moveTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 2.5, 0, 0);
        ItemStack stack = new ItemStack(item, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return result.consumesAction() ? ground.above() : null;
    }

    // ------------------------------------------------------------------ a real client (1.7.1)

    private int realPlayerSeenAt = -1;

    /**
     * On an integrated server, once a real player has been in the world for three seconds: a giver
     * outside any village, a delivery quest carrying frozen village 515151, the goods, and Deliver &amp;
     * complete. The standing it earns must reach the client's journal cache by itself.
     */
    private void clientPhase(MinecraftServer server) {
        if (done) {
            return;
        }
        net.minecraft.server.level.ServerPlayer real = server.getPlayerList().getPlayers().stream()
                .filter(p -> !(p instanceof FakePlayer)).findFirst().orElse(null);
        if (real == null) {
            return;
        }
        if (realPlayerSeenAt < 0) {
            realPlayerSeenAt = ticks;
            return;
        }
        if (ticks - realPlayerSeenAt < 60) {
            return;
        }
        done = true;
        ServerLevel level = real.serverLevel();
        Villager giver = spawnCartographer(level, real.blockPosition().offset(2, 0, 0), "mca:female_villager");
        PlayerQuestData data = QuestCapabilities.get(real).orElse(null);
        if (giver == null || data == null) {
            record("CLIENT SKIPPED: no MCA villager or no quest data");
            return;
        }
        ActiveQuest active = ActiveQuest.create(DELIVER_QUEST, giver.getUUID(), Component.literal("giver"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(),
                java.util.OptionalLong.of(level.getDayTime()), OptionalInt.of(515151), 2, null, null);
        data.add(active);
        real.getInventory().add(new ItemStack(Items.BREAD, 3));
        real.getInventory().add(new ItemStack(Items.APPLE, 2));
        QuestManager.deliver(real, giver.getUUID(), dev.otectus.mcaquests.quest.delivery.DeliveryRequest.fromMenuPacket(
                active.instance(), DELIVER_QUEST, 1, giver.getUUID(), dev.otectus.mcaquests.quest.delivery.DeliveryRequest.ALL_UNITS,
                it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET, 0, UUID.randomUUID()), true);
        record("CLIENT server side: questGone=" + !data.active().contains(active) + " standing by village="
                + dev.otectus.mcaquests.quest.reputation.QuestReputation.villageScores(server, real.getUUID(),
                level.dimension().location()) + " (the giver's live village wins over the frozen 515151 when"
                + " one is in range)");
    }

    // ------------------------------------------------------------------ deliveries and standing (1.7.1)

    /**
     * Both delivery objective types through every route a player has, then the turn-in's standing.
     *
     * <p>Gift goes through MCA's own {@code VillagerCommandHandler#handle(ServerPlayer, "gift")}, so the
     * real mixin, the real handler and the real villager decide it. The Deliver button goes through the
     * same {@code QuestManager#deliver} the packet handler calls. The giver is spawned outside any MCA
     * village, the way a quest finished in the field meets its giver, while the quest carries the village
     * frozen when it was accepted; the standing it earns must land on that village.
     */
    private void delivery(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-0000000de11e"), "RTDeliver"));
        Villager giver = spawnCartographer(level, spawn.offset(8, 0, 0), "mca:female_villager");
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (giver == null || data == null) {
            record("DELIVERY SKIPPED: no MCA villager or no quest data");
            return;
        }
        player.moveTo(giver.getX() - 1.5, giver.getY(), giver.getZ(), 0, 0);
        player.getInventory().clearContent();
        int frozenVillage = 515151;
        dev.otectus.mcaquests.quest.reputation.QuestReputation.Community community =
                new dev.otectus.mcaquests.quest.reputation.QuestReputation.Community(level.dimension().location(), frozenVillage);
        ActiveQuest active = ActiveQuest.create(DELIVER_QUEST, giver.getUUID(), Component.literal("giver"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(),
                java.util.OptionalLong.of(level.getDayTime()), OptionalInt.of(frozenVillage), 2, null, null);
        data.add(active);
        dev.otectus.mcaquests.quest.QuestDefinition def = QuestRegistry.get(DELIVER_QUEST).orElseThrow();
        dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective bread =
                (dev.otectus.mcaquests.quest.objective.ItemDeliveryObjective) def.objectives().get(0);
        dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective apples =
                (dev.otectus.mcaquests.quest.objective.DeliverToVillagerObjective) def.objectives().get(1);
        record("delivery giver home village=" + McaCompat.getHomeVillageId(giver) + " frozen=" + frozenVillage
                + " backend=" + dev.otectus.mcaquests.compat.ReputationBridge.backend().backendName());

        // Gift, twice, through MCA's own command handler: one apple per gesture, no hearts for a payment.
        Object handler = giver.getClass().getMethod("getInteractions").invoke(giver);
        java.lang.reflect.Method handle = handler.getClass().getMethod("handle",
                net.minecraft.server.level.ServerPlayer.class, String.class);
        int heartsBefore = McaCompat.getHearts(player, giver);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 2));
        Object gift1 = handle.invoke(handler, player, "gift");
        int afterGift1 = apples.deliveredUnits(active.progress(1));
        Object gift2 = handle.invoke(handler, player, "gift");
        int afterGift2 = apples.deliveredUnits(active.progress(1));
        int applesLeft = player.getMainHandItem().is(Items.APPLE) ? player.getMainHandItem().getCount() : 0;
        int heartsAfter = McaCompat.getHearts(player, giver);
        record("gift apple 1 returned=" + gift1 + " units=" + afterGift1 + "/2; gift 2 returned=" + gift2 + " units="
                + afterGift2 + "/2 applesLeft=" + applesLeft + " hearts " + heartsBefore + "->" + heartsAfter
                + " hookObserved=" + dev.otectus.mcaquests.compat.mca.McaGiftHookProbe.applied());

        // Gift one loaf to the item_delivery, then the Deliver button pays the rest from the pack.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 1));
        Object gift3 = handle.invoke(handler, player, "gift");
        int breadAfterGift = bread.deliveredUnits(active.progress(0));
        player.getInventory().setItem(9, new ItemStack(Items.BREAD, 5));
        QuestManager.deliver(player, giver.getUUID(), dev.otectus.mcaquests.quest.delivery.DeliveryRequest.fromMenuPacket(
                active.instance(), DELIVER_QUEST, 0, giver.getUUID(), dev.otectus.mcaquests.quest.delivery.DeliveryRequest.ALL_UNITS,
                it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET, breadAfterGift, UUID.randomUUID()), false);
        int breadAfterMenu = bread.deliveredUnits(active.progress(0));
        int breadCarried = player.getInventory().countItem(Items.BREAD);
        boolean complete = QuestManager.isComplete(player, def, active);
        record("gift bread returned=" + gift3 + " deposited=" + breadAfterGift + "/3; Deliver button deposited="
                + breadAfterMenu + "/3 carried=" + breadCarried + " complete=" + complete);

        int scoreBefore = dev.otectus.mcaquests.quest.reputation.QuestReputation.score(player, community);
        boolean turnedIn = QuestManager.turnIn(player, giver, DELIVER_QUEST);
        int scoreAfter = dev.otectus.mcaquests.quest.reputation.QuestReputation.score(player, community);
        java.util.Map<Integer, Integer> known = dev.otectus.mcaquests.quest.reputation.QuestReputation
                .villageScores(server, player.getUUID(), level.dimension().location());
        int breadAfterTurnIn = player.getInventory().countItem(Items.BREAD);
        int expected = dev.otectus.mcaquests.McaQuestsConfig.COMMON.mediumQuestReputation.get();
        record("turnIn=" + turnedIn + " questGone=" + !data.active().contains(active) + " bread after turn-in="
                + breadAfterTurnIn + " standing " + scoreBefore + "->" + scoreAfter + " (expected +" + expected
                + ") journal villages=" + known);
        record("DELIVERY " + (Boolean.TRUE.equals(gift1) && Boolean.TRUE.equals(gift2) && afterGift1 == 1 && afterGift2 == 2
                && applesLeft == 0 && heartsAfter == heartsBefore && Boolean.TRUE.equals(gift3) && breadAfterGift == 1
                && breadAfterMenu == 3 && breadCarried == 3 && complete && turnedIn && breadAfterTurnIn == 3
                ? "PASS" : "FAIL") + " expected: Gift pays 1/2 then 2/2 with no hearts, one loaf by Gift, the rest by"
                + " the Deliver button, and a turn-in that charges nothing more");
        record("STANDING " + (turnedIn && scoreAfter - scoreBefore == expected && known.containsKey(frozenVillage)
                ? "PASS" : "FAIL") + " expected: the completed quest's standing lands on the village frozen at accept"
                + " when the giver is standing outside it, and the journal lists that village");
        villageStanding(server);
    }

    /**
     * The ordinary case: a giver who lives in a real MCA village. The village is made the way MCA makes
     * one — a house with a door and a bed, reported to its {@code VillageManager} — and the villager
     * finds a home in it through MCA's own {@code Residency#seekHome}.
     */
    private void villageStanding(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos floor = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                spawn.offset(40, 0, 40)).below();
        // A 5x5 room, three high, planked, with a door on the south wall and a bed inside.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3 || dy == 0 || dy == 4;
                    level.setBlockAndUpdate(floor.offset(dx, dy, dz),
                            wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos door = floor.offset(0, 1, 3);
        level.setBlockAndUpdate(door, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(door.above(), Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        BlockPos bedFoot = floor.offset(-1, 1, 0);
        net.minecraft.world.level.block.state.BlockState bed = Blocks.RED_BED.defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(bedFoot, bed);
        level.setBlockAndUpdate(bedFoot.north(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART,
                net.minecraft.world.level.block.state.properties.BedPart.HEAD));

        Villager resident = spawnCartographer(level, floor.offset(1, 1, 0), "mca:male_villager");
        if (resident == null) {
            record("VILLAGE-STANDING SKIPPED: no MCA villager");
            return;
        }
        String root = resident.getClass().getName().substring(0, resident.getClass().getName().indexOf(".entity."));
        Class<?> managerType = Class.forName(root + ".server.world.data.VillageManager", true, resident.getClass().getClassLoader());
        Object manager = managerType.getMethod("get", ServerLevel.class).invoke(null, level);
        Object built = managerType.getMethod("processBuilding", BlockPos.class).invoke(manager, door);
        Object residency = resident.getClass().getMethod("getResidency").invoke(resident);
        residency.getClass().getMethod("seekHome").invoke(residency);
        OptionalInt home = McaCompat.getHomeVillageId(resident);
        record("village building=" + built + " resident home=" + home + " name=" + McaCompat.getHomeVillageName(resident));
        if (home.isEmpty()) {
            record("VILLAGE-STANDING SKIPPED: MCA did not house the villager (building " + built + ")");
            return;
        }

        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-0000000de12e"), "RTVillage"));
        player.moveTo(resident.getX() - 1.0, resident.getY(), resident.getZ(), 0, 0);
        player.getInventory().clearContent();
        PlayerQuestData data = QuestCapabilities.get(player).orElseThrow();
        ActiveQuest active = ActiveQuest.create(DELIVER_QUEST, resident.getUUID(), Component.literal("giver"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(),
                java.util.OptionalLong.of(level.getDayTime()), home, 2, null, null);
        data.add(active);
        player.getInventory().setItem(9, new ItemStack(Items.BREAD, 3));
        player.getInventory().setItem(10, new ItemStack(Items.APPLE, 2));
        QuestManager.deliver(player, resident.getUUID(), dev.otectus.mcaquests.quest.delivery.DeliveryRequest.fromMenuPacket(
                active.instance(), DELIVER_QUEST, 1, resident.getUUID(), dev.otectus.mcaquests.quest.delivery.DeliveryRequest.ALL_UNITS,
                it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET, 0, UUID.randomUUID()), true);
        dev.otectus.mcaquests.quest.reputation.QuestReputation.Community community =
                new dev.otectus.mcaquests.quest.reputation.QuestReputation.Community(level.dimension().location(), home.getAsInt());
        int score = dev.otectus.mcaquests.quest.reputation.QuestReputation.score(player, community);
        boolean gone = !data.active().contains(active);
        java.util.Map<Integer, Integer> known = dev.otectus.mcaquests.quest.reputation.QuestReputation
                .villageScores(server, player.getUUID(), level.dimension().location());
        int expected = dev.otectus.mcaquests.McaQuestsConfig.COMMON.mediumQuestReputation.get();
        record("village deliver-and-complete questGone=" + gone + " bread=" + player.getInventory().countItem(Items.BREAD)
                + " standing=" + score + " (expected " + expected + ") journal villages=" + known);
        record("VILLAGE-STANDING " + (gone && score == expected && known.getOrDefault(home.getAsInt(), 0) == expected
                ? "PASS" : "FAIL") + " expected: Deliver & complete pays the apples, charges the bread at turn-in, and the"
                + " giver's own village records the standing");
    }

    // ------------------------------------------------------------------ held rewards (1.7.0)

    private void held(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-0000000be1d0"), "RTHeld"));
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 3.5, 0, 0);
        Villager giver = spawnCartographer(level, spawn.offset(0, 0, 4), "mca:male_villager");
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (giver == null || data == null) {
            record("HELD SKIPPED: no MCA villager or no quest data");
            return;
        }
        ActiveQuest active = ActiveQuest.create(HELD_QUEST, giver.getUUID(), Component.literal("giver"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(), 1, null);
        active.progress(0).setCount(1);
        data.add(active);
        int xpBefore = player.totalExperience;
        boolean turnedIn = QuestManager.turnIn(player, giver, HELD_QUEST);
        int heldAfter = data.heldRewards().size();
        String heldType = heldAfter > 0 ? data.heldRewards().get(0).rewardType() : "none";
        String retry = heldAfter > 0 ? QuestManager.retryHeldReward(player, 0).getString() : "nothing held";
        int heldAfterRetry = data.heldRewards().size();
        PlayerQuestData reloaded = new PlayerQuestData();
        reloaded.load(data.save());
        int persisted = reloaded.heldRewards().size();
        if (heldAfterRetry > 0) {
            data.removeHeldReward(0);
        }
        record("held turnIn=" + turnedIn + " questGone=" + !data.active().contains(active) + " xp " + xpBefore + "->"
                + player.totalExperience + " held=" + heldAfter + " type=" + heldType + " retry='" + retry
                + "' heldAfterRetry=" + heldAfterRetry + " persisted=" + persisted + " afterDismiss=" + data.heldRewards().size());
        record("HELD " + (turnedIn && !data.active().contains(active) && player.totalExperience > xpBefore && heldAfter == 1
                && "mcaqrt:explode".equals(heldType) && heldAfterRetry == 1 && persisted == 1 && data.heldRewards().isEmpty()
                ? "PASS" : "FAIL") + " expected: the quest completes, the other reward pays, the thrown one is held,"
                + " survives a save, fails its retry and is dismissed");
    }

    // ------------------------------------------------------------------ vanished village (1.7.0)

    private void villageGone(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int missing = 424242;
        java.util.Optional<Boolean> known = McaCompat.villageKnown(level, missing);
        ProjectSavedData projects = ProjectSavedData.get(server);
        ProjectState gone = new ProjectState(BUILD, ProjectScope.VILLAGE, "v:" + missing, level.dimension().location(),
                level.getSharedSpawnPos(), OptionalInt.of(missing), level.getGameTime(), 1);
        gone.progress(0).setCount(2);
        projects.putInstance(gone);
        boolean detected = ProjectManager.villageGone(server, gone);
        List<Component> preview = ProjectRecovery.preview("RT", server, gone, ProjectRecovery.Operation.REBIND_ANCHOR, -1, 0);
        String last = preview.get(preview.size() - 1).getString();
        String token = last.substring(last.lastIndexOf(' ') + 1);
        Component applied = ProjectRecovery.confirm("RT", server, token);
        ProjectState moved = ProjectRecovery.instancesOf(server, BUILD).stream()
                .filter(state -> state.villageId().isEmpty()).findFirst().orElse(null);
        record("village " + missing + " known=" + known + " gone=" + detected + " rebind='" + applied.getString() + "' now="
                + (moved == null ? "none" : moved.identity() + " progress=" + moved.progress(0).count()
                + " stillGone=" + ProjectManager.villageGone(server, moved)));
        record("GONE " + (known.equals(java.util.Optional.of(false)) && detected && moved != null
                && moved.progress(0).count() == 2 && !ProjectManager.villageGone(server, moved) ? "PASS" : "FAIL")
                + " expected: MCA answers 'no such village', the instance is detected and rebinds to its anchor with its progress");
        ProjectRecovery.instancesOf(server, BUILD).forEach(state -> projects.removeInstance(state.key().asString()));
    }

    // ------------------------------------------------------------------ completion receipts (1.7.0)

    private void receipt(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-0000000cec17"), "RTReceipt"));
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() - 3.5, 0, 0);
        Villager giver = spawnCartographer(level, spawn.offset(0, 0, -4), "mca:female_villager");
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (giver == null || data == null) {
            record("RECEIPT SKIPPED: no MCA villager or no quest data");
            return;
        }
        List<QuestCompletionReceipt> before = McaQuestsApi.readCompletionReceipts(player, RECEIPT_CONSUMER, 8);
        ActiveQuest active = ActiveQuest.create(RECEIPT_QUEST, giver.getUUID(), Component.literal("giver"),
                ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(), 1, null);
        active.progress(0).setCount(1);
        data.add(active);
        int eventsBefore = receiptEvents;
        boolean turnedIn = QuestManager.turnIn(player, giver, RECEIPT_QUEST);
        Path file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
        List<QuestCompletionReceipt> after = McaQuestsApi.readCompletionReceipts(player, RECEIPT_CONSUMER, 8);
        String status = McaQuestsApi.completionReceiptStatus(player);
        boolean acknowledged = !after.isEmpty() && McaQuestsApi.acknowledgeCompletionReceipt(player, RECEIPT_CONSUMER,
                after.get(0).providerEpoch(), after.get(0).receiptId());
        List<QuestCompletionReceipt> drained = McaQuestsApi.readCompletionReceipts(player, RECEIPT_CONSUMER, 8);
        record("receipt before=" + before.size() + " turnIn=" + turnedIn + " playerFile=" + Files.exists(file) + " after="
                + after.size() + (after.isEmpty() ? "" : " quest=" + after.get(0).questId()) + " readyEvents="
                + (receiptEvents - eventsBefore) + " status=" + status + " acknowledged=" + acknowledged + " drained="
                + drained.size());
        record("RECEIPT " + (before.isEmpty() && turnedIn && Files.exists(file) && after.size() == 1
                && RECEIPT_QUEST.equals(after.get(0).questId()) && receiptEvents == eventsBefore + 1
                && "ready".equals(status) && acknowledged && drained.isEmpty() ? "PASS" : "FAIL")
                + " expected: one receipt, visible only after PlayerList#save wrote and reread the player file, then acknowledged");
    }

    // ------------------------------------------------------------------ /reload (1.7.0)

    private void startReload(MinecraftServer server) {
        questsBeforeReload = QuestRegistry.size();
        reloadSynced = false;
        server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(t -> {
            record("reload failed: " + t);
            return null;
        });
        record("reload started with " + questsBeforeReload + " quests loaded");
    }

    private void finishReload() {
        boolean ledger = ContentOutageData.current().isPresent();
        record("RELOAD " + (reloadSynced && QuestRegistry.size() == questsBeforeReload && ledger ? "PASS" : "FAIL")
                + " synced=" + reloadSynced + " quests=" + QuestRegistry.size() + "/" + questsBeforeReload
                + " ledger=" + ledger + " expected: the reload completes, re-syncs and keeps the outage ledger");
    }

    // ------------------------------------------------------------------ guidance cost (1.7.0, F17)

    /** One player holding up to ten bundled quests whose objectives name a place or a villager. */
    private void guidanceSetup(MinecraftServer server) {
        guidancePlayer = guidancePlayer(server, "7a7a7a7a-0000-4000-8000-00000000901d", "RTGuide", false);
    }

    private FakePlayer guidancePlayer(MinecraftServer server, String id, String name, boolean blockSources) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString(id), name));
        player.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        Villager giver = spawnCartographer(level, spawn.offset(3, 0, 3), "mca:male_villager");
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (giver == null || data == null) {
            record("GUIDANCE SKIPPED: no MCA villager or no quest data");
            return null;
        }
        java.util.regex.Pattern guided = java.util.regex.Pattern.compile(
                "Kill|Break|Reach|EnterStructure|Deliver|TalkToProfession|FindMissingRelative|Escort|VisitBiome");
        List<dev.otectus.mcaquests.quest.QuestDefinition> picked = QuestRegistry.all().stream()
                .filter(def -> def.template().isEmpty() && !def.id().getNamespace().equals("mcaqrt"))
                .filter(def -> blockSources
                        ? def.objectives().stream().anyMatch(o -> o.toString().contains("SourceHint[")
                                && (o.toString().contains("block=Optional[") || o.toString().contains("blockTag=Optional[")))
                        : def.objectives().stream().anyMatch(o -> guided.matcher(o.getClass().getSimpleName()).find()))
                .sorted(java.util.Comparator.comparing(def -> def.id().toString()))
                .limit(10).toList();
        for (dev.otectus.mcaquests.quest.QuestDefinition def : picked) {
            data.add(ActiveQuest.create(def.id(), giver.getUUID(), Component.literal("giver"),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(),
                    def.objectives().size(), null));
        }
        record(name + " holds " + picked.size() + " quests: " + picked.stream().map(def -> def.id().getPath()).toList());
        return player;
    }

    /** Twenty guidance walks for the first player, timed one by one; a fresh second player once warm. */
    private void guidanceMeasure(MinecraftServer server, String label) {
        if (guidancePlayer == null) {
            return;
        }
        measureWalks(server, guidancePlayer, label);
        if ("settled".equals(label)) {
            secondGuidancePlayer = guidancePlayer(server, "7a7a7a7a-0000-4000-8000-00000000902d", "RTGuide2", false);
            if (secondGuidancePlayer != null) {
                measureWalks(server, secondGuidancePlayer, "second-player");
            }
            FakePlayer blocks = guidancePlayer(server, "7a7a7a7a-0000-4000-8000-00000000903d", "RTGuide3", true);
            if (blocks != null) {
                measureWalks(server, blocks, "block-sources-first");
            }
            FakePlayer blocksWarm = guidancePlayer(server, "7a7a7a7a-0000-4000-8000-00000000904d", "RTGuide4", true);
            if (blocksWarm != null) {
                measureWalks(server, blocksWarm, "block-sources");
            }
        }
    }

    private void measureWalks(MinecraftServer server, FakePlayer player, String label) {
        PlayerQuestData data = QuestCapabilities.get(player).orElse(null);
        if (data == null) {
            return;
        }
        ServerLevel level = server.overworld();
        long max = 0L;
        long total = 0L;
        long first = 0L;
        long restMax = 0L;
        int errors = 0;
        for (int i = 0; i < 20; i++) {
            long started = System.nanoTime();
            try {
                dev.otectus.mcaquests.quest.guidance.GuidanceService.snapshot(player, level, data);
            } catch (Throwable t) {
                errors++;
                if (errors == 1) {
                    record("guidance walk threw: " + t);
                }
            }
            long elapsed = System.nanoTime() - started;
            max = Math.max(max, elapsed);
            total += elapsed;
            if (i == 0) {
                first = elapsed;
            } else {
                restMax = Math.max(restMax, elapsed);
            }
        }
        guidanceTimings.add(label + " max=" + (max / 1000) + "us avg=" + (total / 20 / 1000) + "us errors=" + errors);
        record("guidance " + label + ": 20 walks, first " + (first / 1000) + " us, max of the other 19 "
                + (restMax / 1000) + " us, average " + (total / 20 / 1000) + " us, errors " + errors);
    }

    /**
     * Every warm group must stay under 2 ms a walk: the first player once settled, a second player from
     * their very first walk, and a second player holding the bundled block-source quests. The first walk
     * down each code path also pays for JIT and class loading, so those groups are reported, not judged.
     */
    private void guidanceVerdict() {
        if (guidancePlayer == null || guidanceTimings.size() < 2) {
            return;
        }
        boolean pass = true;
        for (String group : guidanceTimings) {
            if (group.startsWith("first") || group.startsWith("block-sources-first")) {
                continue;
            }
            long maxMicros = Long.parseLong(group.replaceAll(".*max=(\\d+)us.*", "$1"));
            pass &= group.endsWith("errors=0") && maxMicros < 2000;
        }
        record("GUIDANCE " + (pass ? "PASS" : "FAIL") + " " + String.join("; ", guidanceTimings)
                + " expected: every warm guidance walk — settled, a new player's first, block sources — under 2 ms,"
                + " the threshold above which the plan called for bounding guidance work server-wide");
    }

    // ------------------------------------------------------------------ restart row (1.7.0)

    private void restart1(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        Villager v = spawnCartographer(level, spawn.offset(2, 0, 2), "mca:male_villager");
        Villager w = spawnCartographer(level, spawn.offset(-2, 0, 2), "mca:female_villager");
        Villager x = spawnCartographer(level, spawn.offset(2, 0, -2), "mca:male_villager");
        Villager y = spawnCartographer(level, spawn.offset(-2, 0, -2), "mca:female_villager");
        if (v == null || w == null || x == null || y == null) {
            record("RESTART1 SKIPPED: MCA villagers could not be spawned");
            return;
        }
        w.setNoAi(true); // another mod froze this one; a release must leave it frozen
        McaCompat.holdVillagerInPlace(v, OFFLINE_OWNER);
        McaCompat.holdVillagerInPlace(w, OFFLINE_OWNER);
        McaCompat.holdVillagerInPlace(x, OFFLINE_OWNER);
        EscortHoldRegistry.enqueueRelease(x.getUUID(), OFFLINE_OWNER); // abandoned while it could not be reached
        y.setNoAi(true); // a hold from before 1.7.0: frozen, with no marker and no lease
        y.setInvulnerable(true);
        record("escort holds placed: " + flags("V", v) + flags("W", w) + flags("X", x) + flags("Y", y));

        Properties state = new Properties();
        state.setProperty("v", v.getStringUUID());
        state.setProperty("w", w.getStringUUID());
        state.setProperty("x", x.getStringUUID());
        state.setProperty("y", y.getStringUUID());

        dev.otectus.mcaquests.project.ProjectDefinition def = ProjectRegistry.get(DRIFT).orElseThrow();
        ProjectState drift = new ProjectState(DRIFT, ProjectScope.VILLAGE, "anchor:rt-drift", level.dimension().location(),
                spawn, OptionalInt.empty(), level.getGameTime(), 1);
        drift.freezeAnchorRadius(24);
        ProjectDrift.capture(drift, def);
        drift.progress(0).setCount(2);
        ProjectSavedData.get(server).putInstance(drift);
        state.setProperty("drift", drift.key().asString());
        record("drift phase opened as " + def.phase(0).objectives().stream().map(o -> o.describe().getString()).toList()
                + " progress=[" + drift.progress(0).count() + "," + drift.progress(1).count() + "]");

        ResourceLocation townsteadQuest = QuestRegistry.all().stream().map(q -> q.id())
                .filter(id -> id.getPath().startsWith("townstead_"))
                .min(java.util.Comparator.comparing(ResourceLocation::toString)).orElse(null);
        ContentOutageData ledger = ContentOutageData.current().orElseThrow();
        long now = level.getGameTime();
        state.setProperty("townsteadQuest", String.valueOf(townsteadQuest));
        state.setProperty("boot1End", Long.toString(now));
        saveState(state);
        boolean covered = townsteadQuest != null && ledger.covers(ContentOutageData.questKey(townsteadQuest));
        record("outage baseline quest=" + townsteadQuest + " covered=" + covered + " project covered="
                + ledger.covers(ContentOutageData.projectKey(WORKING_VILLAGE)) + " gameTime=" + now);
        record("RESTART1 " + (held(v) && held(w) && townsteadQuest != null && !covered ? "PASS" : "FAIL")
                + " expected: V and W held with markers, Townstead content loaded and not covered");
    }

    private void restart2(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        Properties state = loadState();
        Entity v = level.getEntity(UUID.fromString(state.getProperty("v")));
        Entity w = level.getEntity(UUID.fromString(state.getProperty("w")));
        Entity x = level.getEntity(UUID.fromString(state.getProperty("x")));
        Entity y = level.getEntity(UUID.fromString(state.getProperty("y")));
        record("after restart: " + flags("V", v) + flags("W", w) + flags("X", x) + flags("Y", y));
        record("ESCORT-RESTART " + (held(v) && held(w) && released(x) && frozenWithoutMarker(y) ? "PASS" : "FAIL")
                + " expected: V and W still held for their offline owner, X's queued release paid on load,"
                + " Y (pre-1.7.0 hold) left alone");

        ProjectState drift = ProjectSavedData.get(server).getInstance(state.getProperty("drift")).orElse(null);
        dev.otectus.mcaquests.project.ProjectDefinition def = ProjectRegistry.get(DRIFT).orElse(null);
        if (drift == null || def == null) {
            record("DRIFT FAIL instance=" + (drift != null) + " definition=" + (def != null));
        } else {
            String objectives = def.phase(0).objectives().stream().map(o -> o.describe().getString()).toList().toString();
            boolean drifted = ProjectDrift.drifted(drift, def);
            FakePlayer builder = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-00000000d71f"), "RTDrift"));
            BlockPos placedStone = placeItem(level, builder, spawn.offset(4, 0, 4), Items.STONE_BRICKS);
            BlockPos placedBricks = placeItem(level, builder, spawn.offset(5, 0, 4), Items.BRICKS);
            record("drift definition now " + objectives + " drifted=" + drifted + " placed " + placedStone + "," + placedBricks
                    + " progress=[" + drift.progress(0).count() + "," + drift.progress(1).count() + "]");
            record("DRIFT " + (drifted && placedStone != null && placedBricks != null && drift.progress(0).count() == 2
                    && drift.progress(1).count() == 0 ? "PASS" : "FAIL")
                    + " expected: the reordered phase is detected and nothing is credited while it is paused");
        }

        ContentOutageData ledger = ContentOutageData.current().orElseThrow();
        String quest = state.getProperty("townsteadQuest");
        boolean questCovered = ledger.covers(ContentOutageData.questKey(ResourceLocation.parse(quest)));
        boolean projectCovered = ledger.covers(ContentOutageData.projectKey(WORKING_VILLAGE));
        state.setProperty("boot2End", Long.toString(level.getGameTime()));
        saveState(state);
        record("OUTAGE-OPEN " + (questCovered && projectCovered && !QuestRegistry.get(ResourceLocation.parse(quest)).isPresent()
                ? "PASS" : "FAIL") + " quest " + quest + " covered=" + questCovered + " project covered=" + projectCovered
                + " expected: with Townstead removed its content is excluded and the ledger has an open outage");
    }

    private void restart3(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getSharedSpawnPos();
        Properties state = loadState();

        ContentOutageData ledger = ContentOutageData.current().orElseThrow();
        ResourceLocation quest = ResourceLocation.parse(state.getProperty("townsteadQuest"));
        String key = ContentOutageData.questKey(quest);
        long from = Long.parseLong(state.getProperty("boot1End"));
        long boot2End = Long.parseLong(state.getProperty("boot2End"));
        long now = level.getGameTime();
        long overlap = ledger.overlap(key, from, now);
        ActiveQuest active = ActiveQuest.create(quest, UUID.randomUUID(), Component.literal("giver"), null,
                level.dimension().location(), from, 1, null);
        active.setOutageAccountedUntil(from);
        java.lang.reflect.Method credit = Class.forName("dev.otectus.mcaquests.event.QuestProgressEvents")
                .getDeclaredMethod("creditOutage", ContentOutageData.class, ActiveQuest.class, long.class);
        credit.setAccessible(true);
        boolean stillCovered = (boolean) credit.invoke(null, ledger, active, now);
        long credited = active.suspendedTicks();
        long expected = boot2End - from;
        record("outage quest " + quest + " loaded=" + QuestRegistry.get(quest).isPresent() + " covered=" + ledger.covers(key)
                + " overlap=" + overlap + " credited=" + credited + " boot2 ran " + expected + " ticks; project overlap="
                + ledger.overlap(ContentOutageData.projectKey(WORKING_VILLAGE), from, now));
        record("OUTAGE " + (!stillCovered && QuestRegistry.get(quest).isPresent() && credited == overlap && credited > 0
                && Math.abs(credited - expected) <= 5 ? "PASS" : "FAIL")
                + " expected: the outage closes on return and an accepted quest is credited exactly the boot it was missing");

        Entity v = level.getEntity(UUID.fromString(state.getProperty("v")));
        Entity w = level.getEntity(UUID.fromString(state.getProperty("w")));
        Entity y = level.getEntity(UUID.fromString(state.getProperty("y")));
        if (v != null) {
            McaCompat.releaseVillagerHold(v);
        }
        if (w != null) {
            McaCompat.releaseVillagerHold(w);
        }
        if (y != null) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "mcaquests escort release " + y.getStringUUID());
        }
        record("after releases: " + flags("V", v) + flags("W", w) + flags("Y", y));
        record("ESCORT-RELEASE " + (released(v) && w instanceof Mob wm && wm.isNoAi() && !wm.isInvulnerable()
                && !EscortHoldRegistry.isMarkedHeld(wm) && released(y) ? "PASS" : "FAIL")
                + " expected: V moving and vulnerable, W back to the frozen-but-vulnerable state another mod gave it,"
                + " Y freed by /mcaquests escort release");

        ProjectState drift = ProjectSavedData.get(server).getInstance(state.getProperty("drift")).orElse(null);
        dev.otectus.mcaquests.project.ProjectDefinition def = ProjectRegistry.get(DRIFT).orElse(null);
        if (drift == null || def == null) {
            record("REBASE FAIL instance=" + (drift != null) + " definition=" + (def != null));
            return;
        }
        List<Component> preview = ProjectRecovery.preview("RT", server, drift, ProjectRecovery.Operation.REBASE, -1, 0);
        String last = preview.get(preview.size() - 1).getString();
        String token = last.substring(last.lastIndexOf(' ') + 1);
        Component applied = ProjectRecovery.confirm("RT", server, token);
        ProjectState rebased = ProjectSavedData.get(server).getInstance(state.getProperty("drift")).orElseThrow();
        boolean drifted = ProjectDrift.drifted(rebased, def);
        FakePlayer builder = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("7a7a7a7a-0000-4000-8000-00000000d71f"), "RTDrift"));
        BlockPos placed = placeItem(level, builder, spawn.offset(6, 0, 4), Items.BRICKS);
        record("rebase '" + applied.getString() + "' drifted=" + drifted + " placed bricks at " + placed + " progress=["
                + rebased.progress(0).count() + "," + rebased.progress(1).count() + "]");
        record("REBASE " + (!drifted && rebased.progress(0).count() == 2 && rebased.progress(1).count() == 1 ? "PASS" : "FAIL")
                + " expected: the rebase accepts the new phase, keeps counts in place, and credit resumes");
        ProjectSavedData.get(server).removeInstance(rebased.key().asString());
    }

    private static String flags(String name, Entity entity) {
        if (!(entity instanceof Mob mob)) {
            return name + "[missing] ";
        }
        return name + "[noAi=" + mob.isNoAi() + " invulnerable=" + mob.isInvulnerable() + " marker="
                + EscortHoldRegistry.isMarkedHeld(mob) + " lease=" + EscortHoldRegistry.leaseOf(mob.getUUID())
                .map(lease -> lease.releasePending() ? "pending" : "held").orElse("none") + "] ";
    }

    private static boolean held(Entity entity) {
        return entity instanceof Mob mob && mob.isNoAi() && mob.isInvulnerable() && EscortHoldRegistry.isMarkedHeld(mob)
                && EscortHoldRegistry.leaseOf(mob.getUUID()).filter(lease -> !lease.releasePending()).isPresent();
    }

    private static boolean released(Entity entity) {
        return entity instanceof Mob mob && !mob.isNoAi() && !mob.isInvulnerable() && !EscortHoldRegistry.isMarkedHeld(mob)
                && EscortHoldRegistry.leaseOf(mob.getUUID()).isEmpty();
    }

    private static boolean frozenWithoutMarker(Entity entity) {
        return entity instanceof Mob mob && mob.isNoAi() && mob.isInvulnerable() && !EscortHoldRegistry.isMarkedHeld(mob);
    }

    private static Properties loadState() throws IOException {
        Properties state = new Properties();
        try (Reader reader = Files.newBufferedReader(STATE)) {
            state.load(reader);
        }
        return state;
    }

    private static void saveState(Properties state) throws IOException {
        try (Writer writer = Files.newBufferedWriter(STATE)) {
            state.store(writer, "MCA: Quests reliability fixture, restart row");
        }
    }
}
