package dev.otectus.mcaquests.rt;

import com.mojang.authlib.GameProfile;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.mca.McaDialogueHookProbe;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.data.UnavailableContent;
import dev.otectus.mcaquests.event.ConversationCredit;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.project.ProjectRecovery;
import dev.otectus.mcaquests.project.ProjectScope;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import dev.otectus.mcaquests.state.ActiveQuest;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Disposable runtime test mod for the 1.6.6 reliability update. Never shipped.
 *
 * <p>Runs once, a few seconds after a dedicated server starts, against the real MCA and whatever
 * companion mods are installed, and writes one line per check to {@code mcaqrt-results.txt}:
 * which content loaded or was excluded, whether MCA's dialogue hook applied, whether a conversation
 * driven through the same calls the network handler makes is credited once (and a sneak-trade click is
 * not), whether placements count inside and not outside an area, and whether an instance-targeted
 * repair leaves a second instance alone and refuses a reused token.
 */
@Mod("mcaquests_rt")
public final class ReliabilityRuntimeFixture {

    private static final ResourceLocation TALK_QUEST = new ResourceLocation("mcaqrt", "rt_talk");
    private static final ResourceLocation CENSUS = new ResourceLocation("mcaqrt", "rt_census");
    private static final ResourceLocation BUILD = new ResourceLocation("mcaqrt", "rt_build");

    private final List<String> results = new ArrayList<>();
    private int ticks;
    private boolean done;

    public ReliabilityRuntimeFixture() {
        if (!Boolean.getBoolean("mcaquests.rt.fixture")) {
            throw new IllegalStateException("The reliability fixture requires -Dmcaquests.rt.fixture=true");
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done || ++ticks < 100) {
            return;
        }
        done = true;
        MinecraftServer server = event.getServer();
        try {
            status();
            talk(server);
            placement(server);
            recovery(server);
        } catch (Throwable t) {
            record("ERROR " + t);
            for (StackTraceElement element : t.getStackTrace()) {
                record("  at " + element);
            }
        }
        try {
            Files.write(Path.of("mcaqrt-results.txt"), results);
        } catch (Exception e) {
            record("could not write results: " + e);
        }
        if (Boolean.getBoolean("mcaquests.rt.stop")) {
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
                new ResourceLocation("mcaquests", "townstead_known_far_and_wide")).isPresent()
                + " excluded=" + UnavailableContent.contains(UnavailableContent.Kind.PROJECT,
                new ResourceLocation("mcaquests", "townstead_known_far_and_wide")));
        record("project a_working_village loaded=" + ProjectRegistry.get(
                new ResourceLocation("mcaquests", "townstead_a_working_village")).isPresent());
        record("project walls_before_winter loaded=" + ProjectRegistry.get(
                new ResourceLocation("mcaquests", "walls_before_winter")).isPresent());
        record("quest road_the_missing_mile loaded=" + QuestRegistry.get(
                new ResourceLocation("mcaquests", "road_the_missing_mile")).isPresent());
        long townsteadQuests = QuestRegistry.all().stream().filter(q -> q.id().getPath().startsWith("townstead_")).count();
        record("loaded quests with townstead_ ids=" + townsteadQuests);
        for (String trade : List.of("minecraft:farmer", "minecraft:shepherd", "minecraft:butcher", "minecraft:fisherman",
                "townstead:cook")) {
            record("track " + trade + " = " + TownsteadBridge.Holder.get().professionTrack(trade));
        }
        // Which overload the pre-1.6.6 binder (first name/arity match in getMethods() order) took here.
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
                        record("pre-1.6.6 binding of " + member[0] + "#" + member[1] + " took ("
                                + method.getParameterTypes()[0].getName() + ", ...)");
                        break;
                    }
                }
            } catch (Throwable t) {
                record("pre-1.6.6 binding of " + member[0] + "#" + member[1] + ": " + t);
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
                new ResourceLocation("minecraft", "cartographer"), level.dimension().location(), level.getGameTime(), 1, null);
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
        InteractionResult result = ForgeHooks.onInteractEntityAt(player, target, hit, hand);
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
        villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
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
}
