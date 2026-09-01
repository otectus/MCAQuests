package dev.otectus.mcaquests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.FtbqBridge;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.mca.McaBinding;
import dev.otectus.mcaquests.data.FtbqReferenceWalker;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.network.FtbqEditorIdsSync;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.quest.QuestDefinition;
import dev.otectus.mcaquests.quest.QuestManager;
import dev.otectus.mcaquests.quest.reputation.ReputationService;
import dev.otectus.mcaquests.quest.reputation.ReputationTier;
import dev.otectus.mcaquests.quest.reputation.ReputationTierSet;
import dev.otectus.mcaquests.quest.reputation.ReputationTiers;
import dev.otectus.mcaquests.quest.situation.SituationDefinition;
import dev.otectus.mcaquests.quest.situation.SituationManager;
import dev.otectus.mcaquests.quest.situation.SituationRegistry;
import dev.otectus.mcaquests.quest.situation.state.SituationInstance;
import dev.otectus.mcaquests.quest.title.TitleService;
import dev.otectus.mcaquests.state.PlayerQuestData;
import dev.otectus.mcaquests.state.PlayerTitles;
import dev.otectus.mcaquests.state.QuestCapabilities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Admin/debug commands under {@code /mcaquests} (spec section 24). {@code debug villager} exercises
 * the entire {@link McaCompat} adapter end-to-end, and {@code debug mca} reports which MCA package
 * layout the runtime binding resolved against — ask for that one first on any MCA-shaped bug report.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class McaQuestsCommand {

    private McaQuestsCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcaquests")
                .then(Commands.literal("list")
                        .requires(src -> src.hasPermission(2))
                        .executes(McaQuestsCommand::listQuests))
                .then(Commands.literal("validate")
                        .requires(src -> src.hasPermission(3))
                        .executes(McaQuestsCommand::validateQuests))
                .then(Commands.literal("reload")
                        .requires(src -> src.hasPermission(3))
                        .executes(McaQuestsCommand::reloadQuests))
                .then(Commands.literal("export-schema")
                        .requires(src -> src.hasPermission(3))
                        .executes(McaQuestsCommand::exportSchema))
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("villager")
                                .executes(McaQuestsCommand::debugVillager))
                        .then(Commands.literal("mca")
                                .executes(McaQuestsCommand::debugMca))
                        .then(Commands.literal("quest")
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(McaQuestsCommand::debugQuest))))
                .then(Commands.literal("project")
                        .then(Commands.literal("list")
                                .requires(src -> src.hasPermission(2))
                                .executes(McaQuestsCommand::projectList))
                        .then(Commands.literal("info")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(McaQuestsCommand::projectInfo)))
                        .then(Commands.literal("validate")
                                .requires(src -> src.hasPermission(3))
                                .executes(McaQuestsCommand::projectValidate))
                        .then(Commands.literal("reset")
                                .requires(src -> src.hasPermission(3))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(McaQuestsCommand::projectReset)))
                        .then(Commands.literal("advance")
                                .requires(src -> src.hasPermission(3))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(McaQuestsCommand::projectAdvance)))
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(McaQuestsCommand::projectDebug))))
                .then(Commands.literal("situation")
                        .then(Commands.literal("list")
                                .requires(src -> src.hasPermission(2))
                                .executes(McaQuestsCommand::situationList))
                        .then(Commands.literal("info")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("id", ResourceLocationArgument.id())
                                        .executes(McaQuestsCommand::situationInfo)))
                        .then(Commands.literal("validate")
                                .requires(src -> src.hasPermission(3))
                                .executes(McaQuestsCommand::situationValidate))
                        .then(Commands.literal("debug")
                                .requires(src -> src.hasPermission(2))
                                .executes(McaQuestsCommand::situationDebug)))
                .then(Commands.literal("reputation")
                        .then(Commands.literal("get")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("village", IntegerArgumentType.integer())
                                        .executes(McaQuestsCommand::reputationGet)))
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(3))
                                .then(Commands.argument("village", IntegerArgumentType.integer())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer())
                                                .executes(McaQuestsCommand::reputationSet))))
                        .then(Commands.literal("add")
                                .requires(src -> src.hasPermission(3))
                                .then(Commands.argument("village", IntegerArgumentType.integer())
                                        .then(Commands.argument("delta", IntegerArgumentType.integer())
                                                .executes(McaQuestsCommand::reputationAdd))))
                        .then(Commands.literal("tiers")
                                .requires(src -> src.hasPermission(2))
                                .executes(McaQuestsCommand::reputationTiers)))
                .then(Commands.literal("title")
                        .then(Commands.literal("grant")
                                .requires(src -> src.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("title", ResourceLocationArgument.id())
                                                .executes(McaQuestsCommand::titleGrant)
                                                .then(Commands.argument("village", IntegerArgumentType.integer())
                                                        .executes(McaQuestsCommand::titleGrantVillage)))))
                        .then(Commands.literal("list")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(McaQuestsCommand::titleList)))
                        .then(Commands.literal("clear")
                                .requires(src -> src.hasPermission(3))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(McaQuestsCommand::titleClear))))
                .then(Commands.literal("ftbq")
                        .then(Commands.literal("status")
                                .requires(src -> src.hasPermission(2))
                                .executes(McaQuestsCommand::ftbqStatus))
                        .then(Commands.literal("validate")
                                .requires(src -> src.hasPermission(3))
                                .executes(McaQuestsCommand::ftbqValidate))
                        .then(Commands.literal("recheck")
                                .requires(src -> src.hasPermission(2))
                                .executes(McaQuestsCommand::ftbqRecheckSelf)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(McaQuestsCommand::ftbqRecheckPlayer))))
                .then(TownsteadCompatCommands.node()));
    }

    /**
     * Operator-facing label for a player: their MCA character name plus their unique Minecraft username
     * when the two differ. MCA names are player-settable and non-unique, so the username keeps admin
     * feedback tied to a specific account (a mistargeted grant is otherwise undetectable from output).
     */
    private static String adminName(ServerPlayer player) {
        String mca = McaCompat.getPlayerName(player);
        String account = player.getGameProfile().getName();
        return mca.equals(account) ? account : mca + " (" + account + ")";
    }

    private static int titleGrant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation title = ResourceLocationArgument.getId(ctx, "title");
        String name = adminName(target);
        boolean added = TitleService.grantGlobal(target, title);
        ctx.getSource().sendSuccess(() -> Component.literal((added ? "Granted" : "Already had")
                + " global title '" + title + "' to " + name + "."), true);
        return added ? 1 : 0;
    }

    private static int titleGrantVillage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation title = ResourceLocationArgument.getId(ctx, "title");
        int village = IntegerArgumentType.getInteger(ctx, "village");
        String name = adminName(target);
        boolean added = TitleService.grantVillage(target, village, title);
        ctx.getSource().sendSuccess(() -> Component.literal((added ? "Granted" : "Already had")
                + " title '" + title + "' to " + name
                + " for village #" + village + "."), true);
        return added ? 1 : 0;
    }

    private static int titleList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String name = adminName(target);
        PlayerTitles titles = QuestCapabilities.get(target).map(PlayerQuestData::titles).orElse(null);
        if (titles == null || titles.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    name + " has no titles."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Titles for " + name + ":"), false);
        if (!titles.global().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(" global: " + titles.global()), false);
        }
        titles.byVillage().forEach((village, set) -> ctx.getSource().sendSuccess(
                () -> Component.literal(" village " + village + ": " + set), false));
        return titles.global().size() + titles.byVillage().size();
    }

    private static int titleClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        String name = adminName(target);
        QuestCapabilities.get(target).ifPresent(d -> d.titles().clear());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cleared all titles for " + name + "."), true);
        return 1;
    }

    // ---------------------------------------------------------------- ftbq (spec §21, task M5.3)

    /**
     * {@code /mcaquests ftbq status}: whether FTB Quests is present, its version (via {@link ModList},
     * core-safe — no {@code compat.ftbq} import needed), whether the real bridge started, the
     * {@code enableFtbQuestsIntegration} master switch, the mcaquests task/reward counts currently in
     * the book, and whether the editor known-ids sync is active. Degrades to a single line when FTB
     * Quests isn't even installed (spec §24).
     */
    private static int ftbqStatus(CommandContext<CommandSourceStack> ctx) {
        boolean detected = ModList.get() != null && ModList.get().isLoaded("ftbquests");
        if (!detected) {
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.status.not_installed"), false);
            return 0;
        }
        String version = ModList.get().getModContainerById("ftbquests")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
        FtbqBridge bridge = FtbqBridge.Holder.get();
        boolean bridgeActive = bridge.isReal();
        boolean masterSwitch = McaQuestsConfig.COMMON.enableFtbQuestsIntegration.get();
        boolean idsSync = FtbqEditorIdsSync.shouldSync();

        ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.status.detected", version), false);
        ctx.getSource().sendSuccess(() -> Component.translatable(bridgeActive
                ? "mcaquests.command.ftbq.status.bridge_active"
                : "mcaquests.command.ftbq.status.bridge_inactive"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable(masterSwitch
                ? "mcaquests.command.ftbq.status.master_switch_on"
                : "mcaquests.command.ftbq.status.master_switch_off"), false);
        if (bridgeActive) {
            int[] counts = bridge.integrationObjectCounts();
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.status.book_objects",
                    counts[0], counts[1]), false);
        } else {
            // The Noop bridge can only ever answer {0, 0} — an honest "unavailable" beats a fake zero count.
            ctx.getSource().sendSuccess(
                    () -> Component.translatable("mcaquests.command.ftbq.status.book_objects_unavailable"), false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(idsSync
                ? "mcaquests.command.ftbq.status.ids_sync_on"
                : "mcaquests.command.ftbq.status.ids_sync_off"), false);
        return 1;
    }

    /**
     * {@code /mcaquests ftbq validate}: the two §21 sweeps, with the severity model both spec passages
     * imply. A finding is an <b>error</b> only when the id is genuinely malformed (could never resolve
     * against any registry state — {@link FtbqBridge.BookReference#malformed()}); an unresolved but
     * well-formed reference is a <b>warning</b> in BOTH directions, because both directions bless the
     * forward-reference pattern: spec §20 says book authors "legitimately reference ids from datapacks
     * they haven't written yet", and §17 (via {@link dev.otectus.mcaquests.compat.FtbqIds}) says a
     * datapack may reference a book chapter/quest/task that hasn't been built yet. The MCA → book
     * sweep can only ever produce warnings here: {@code FtbqIds.hexIdCodec} already rejects malformed
     * hex ids at datapack load, so every id the walker sees is well-formed by construction. Errors go
     * through {@code sendFailure}; warnings through {@code sendSuccess}, so a forward reference never
     * renders as a failure. Degrades to a single line when FTB Quests is absent or the integration is
     * disabled (spec §24).
     */
    private static int ftbqValidate(CommandContext<CommandSourceStack> ctx) {
        FtbqBridge bridge = FtbqBridge.Holder.get();
        if (!bridge.isAvailable()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.validate.inactive"), false);
            return 1;
        }

        List<FtbqBridge.BookReference> bookErrors = new ArrayList<>();
        List<FtbqBridge.BookReference> bookWarnings = new ArrayList<>();
        for (FtbqBridge.BookReference ref : bridge.validateBookReferences()) {
            (ref.malformed() ? bookErrors : bookWarnings).add(ref);
        }
        List<FtbqReferenceWalker.Reference> datapackWarnings = new ArrayList<>();
        for (QuestDefinition def : QuestRegistry.all()) {
            for (FtbqReferenceWalker.Reference ref : FtbqReferenceWalker.collect(def)) {
                boolean resolves = switch (ref.kind()) {
                    case QUEST -> bridge.questIdExists(ref.hexId());
                    case CHAPTER -> bridge.chapterIdExists(ref.hexId());
                    case TASK -> bridge.taskIdExists(ref.hexId());
                };
                if (!resolves) {
                    datapackWarnings.add(ref);
                }
            }
        }

        if (bookErrors.isEmpty() && bookWarnings.isEmpty() && datapackWarnings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.validate.all_valid"), false);
        }
        if (!bookErrors.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("mcaquests.command.ftbq.validate.book_errors_header",
                    bookErrors.size()));
            bookErrors.forEach(ref -> ctx.getSource().sendFailure(Component.translatable(
                    "mcaquests.command.ftbq.validate.book_finding.malformed",
                    ref.chapterName(), ref.questCode(), ref.taskName(), ref.field(), ref.unknownId())));
        }
        if (!bookWarnings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.validate.book_warnings_header",
                    bookWarnings.size()), false);
            bookWarnings.forEach(ref -> ctx.getSource().sendSuccess(() -> Component.translatable(
                    "mcaquests.command.ftbq.validate.book_finding.unresolved",
                    ref.chapterName(), ref.questCode(), ref.taskName(), ref.field(), ref.unknownId()), false));
        }
        if (!datapackWarnings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.validate.datapack_warnings_header",
                    datapackWarnings.size()), false);
            datapackWarnings.forEach(ref -> ctx.getSource().sendSuccess(() -> Component.translatable(
                    "mcaquests.command.ftbq.validate.datapack_finding",
                    ref.questId(), ref.field(), ref.kind().name().toLowerCase(Locale.ROOT), ref.hexId()), false));
        }
        int errorCount = bookErrors.size();
        int warningCount = bookWarnings.size() + datapackWarnings.size();
        ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.validate.summary",
                errorCount, warningCount), false);
        return errorCount == 0 ? 1 : 0;
    }

    private static int ftbqRecheckSelf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ftbqRecheck(ctx, ctx.getSource().getPlayerOrException());
    }

    private static int ftbqRecheckPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ftbqRecheck(ctx, EntityArgument.getPlayer(ctx, "player"));
    }

    /** {@code /mcaquests ftbq recheck [player]}: {@code bridge.recheckAll} for {@code target} (default self). */
    private static int ftbqRecheck(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        FtbqBridge bridge = FtbqBridge.Holder.get();
        if (!bridge.isAvailable()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.recheck.inactive"), false);
            return 0;
        }
        String name = adminName(target);
        bridge.recheckAll(target);
        ctx.getSource().sendSuccess(() -> Component.translatable("mcaquests.command.ftbq.recheck.done", name), true);
        return 1;
    }

    private static int reputationGet(CommandContext<CommandSourceStack> ctx) {
        int village = IntegerArgumentType.getInteger(ctx, "village");
        MinecraftServer server = ctx.getSource().getServer();
        // Standing is per player from 1.1.0, so this reports the executor's own standing. An
        // administrator inspecting somebody else uses /mcareputation get <player>, which exists
        // precisely because this command's signature cannot name one.
        ServerPlayer viewer = asPlayer(ctx);
        int rep = viewer == null ? 0 : dev.otectus.mcaquests.quest.reputation.QuestReputation.score(viewer,
                dev.otectus.mcaquests.quest.reputation.QuestReputation.inLevel(server.overworld(), village));
        ReputationTierSet ladder = ReputationTiers.getDefault();
        ReputationTier tier = ladder.tierFor(rep);
        String next = ladder.nextTier(rep)
                .map(t -> t.name() + " at " + t.threshold())
                .orElse("(max)");
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Village #" + village + ": reputation " + rep + " — tier " + tier.name() + " — next: " + next), false);
        return rep;
    }

    private static int reputationSet(CommandContext<CommandSourceStack> ctx) {
        int village = IntegerArgumentType.getInteger(ctx, "village");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer subject = asPlayer(ctx);
        var community = dev.otectus.mcaquests.quest.reputation.QuestReputation.inLevel(server.overworld(), village);
        int current = subject == null ? 0 : dev.otectus.mcaquests.quest.reputation.QuestReputation.score(subject, community);
        int newRep = subject == null ? current : dev.otectus.mcaquests.quest.reputation.QuestReputation.award(server, subject.getUUID(),
                community, amount - current, null, null, null);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Village #" + village + " reputation set to " + newRep + "."), true);
        return newRep;
    }

    private static int reputationAdd(CommandContext<CommandSourceStack> ctx) {
        int village = IntegerArgumentType.getInteger(ctx, "village");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayer subject = asPlayer(ctx);
        int newRep = subject == null ? 0 : dev.otectus.mcaquests.quest.reputation.QuestReputation.award(server, subject.getUUID(),
                dev.otectus.mcaquests.quest.reputation.QuestReputation.inLevel(server.overworld(), village), delta, null, null, null);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Village #" + village + " reputation now " + newRep + " (" + (delta >= 0 ? "+" : "") + delta + ")."), true);
        return newRep;
    }

    private static int reputationTiers(CommandContext<CommandSourceStack> ctx) {
        ReputationTierSet ladder = ReputationTiers.getDefault();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Default reputation ladder (" + ladder.tiers().size() + " tier(s)):"), false);
        for (ReputationTier tier : ladder.tiers()) {
            String title = tier.grantsTitle().map(t -> "  grants " + t).orElse("");
            ctx.getSource().sendSuccess(() -> Component.literal(
                    " - " + tier.threshold() + "+  " + tier.name() + " [" + tier.id() + "]" + title), false);
        }
        return ladder.tiers().size();
    }

    private static ServerPlayer asPlayer(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getEntity() instanceof ServerPlayer p ? p : null;
    }

    private static int projectList(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<ProjectState> instances = ProjectManager.activeInstances(server);
        ctx.getSource().sendSuccess(() -> Component.literal(
                ProjectRegistry.size() + " project definition(s) loaded; " + instances.size() + " active instance(s):"), false);
        for (ProjectState state : instances) {
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + state.projectId()
                    + " [" + state.scope().lower() + "] key=" + state.identity()
                    + " phase " + (state.currentPhase() + 1) + " (" + state.status().lower() + ")"), false);
        }
        return instances.size();
    }

    private static int projectInfo(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        List<ProjectState> matching = ProjectManager.activeInstances(server).stream()
                .filter(s -> s.projectId().equals(id)).toList();
        if (matching.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No active instance of '" + id + "'. Use /mcaquests project list."));
            return 0;
        }
        for (ProjectState state : matching) {
            ctx.getSource().sendSuccess(() -> Component.literal(id + " [" + state.scope().lower() + "] "
                    + state.identity() + "  phase " + (state.currentPhase() + 1)
                    + "  status=" + state.status().lower()
                    + "  sponsors=" + state.sponsors().size()
                    + "  participants=" + state.participants().size()
                    + "  reputation=" + ProjectManager.reputationOf(server, state.identity())), false);
            for (int i = 0; i < state.progressCount(); i++) {
                final int idx = i;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "   objective " + idx + ": shared " + state.progress(idx).count()), false);
            }
        }
        return matching.size();
    }

    private static int projectValidate(CommandContext<CommandSourceStack> ctx) {
        List<String> errors = ProjectRegistry.lastErrors();
        if (errors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "All " + ProjectRegistry.size() + " loaded project(s) are valid."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(errors.size() + " project note(s) from the last load:"), false);
        errors.forEach(e -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + e), false));
        return 0;
    }

    private static int projectReset(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        int n = ProjectManager.adminReset(ctx.getSource().getServer(), id);
        ctx.getSource().sendSuccess(() -> Component.literal("Reset " + n + " instance(s) of '" + id + "'."), true);
        return n;
    }

    private static int projectAdvance(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        int n = ProjectManager.adminAdvance(ctx.getSource().getServer(), id);
        ctx.getSource().sendSuccess(() -> Component.literal("Advanced " + n + " instance(s) of '" + id + "'."), true);
        return n;
    }

    private static int projectDebug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = nearestMcaVillager(player, 10.0D);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("No MCA villager within 10 blocks."));
            return 0;
        }
        ProjectManager.explainAvailability(player, target, id)
                .forEach(line -> ctx.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    // ---------------------------------------------------------------- situations (0.8.0)

    private static int situationList(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        long now = server.overworld().getGameTime();
        List<SituationInstance> open = SituationManager.openInstances(server);
        ctx.getSource().sendSuccess(() -> Component.literal(
                SituationRegistry.size() + " situation definition(s) loaded; " + open.size() + " open:"), false);
        for (SituationInstance instance : open) {
            String scope = SituationRegistry.get(instance.defId()).map(def -> def.scope().lower()).orElse("?");
            long remaining = instance.remainingTicks(now);
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + instance.defId() + " [" + scope
                    + "] village=" + instance.villageId() + " closes in " + remaining + "t"
                    + " participants=" + instance.participants().size()), false);
        }
        return open.size();
    }

    private static int situationInfo(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        MinecraftServer server = ctx.getSource().getServer();
        long now = server.overworld().getGameTime();
        List<SituationInstance> matching = SituationManager.openInstances(server).stream()
                .filter(instance -> instance.defId().equals(id)).toList();
        if (matching.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No open situation '" + id + "'. Use /mcaquests situation list."));
            return 0;
        }
        for (SituationInstance instance : matching) {
            long remaining = instance.remainingTicks(now);
            ctx.getSource().sendSuccess(() -> Component.literal(id + "  village=" + instance.villageId()
                    + "  status=" + instance.status().lower() + "  closes in " + remaining + "t"
                    + "  participants=" + instance.participants().size()
                    + instance.villagerUuid().map(uuid -> "  villager=" + uuid).orElse("")), false);
        }
        return matching.size();
    }

    private static int situationValidate(CommandContext<CommandSourceStack> ctx) {
        var errors = SituationRegistry.lastErrors();
        var warnings = new java.util.ArrayList<>(SituationRegistry.lastWarnings());
        for (SituationDefinition def : SituationRegistry.all()) {
            if (def.offer().objectives().isEmpty()) {
                warnings.add("Situation '" + def.id() + "' has an offer with no objectives (acceptable but never completable).");
            }
        }
        if (errors.isEmpty() && warnings.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("All " + SituationRegistry.size() + " loaded situation(s) are valid."), false);
            return 1;
        }
        if (!errors.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(errors.size() + " situation error(s) from the last load:"));
            errors.forEach(e -> ctx.getSource().sendFailure(Component.literal(" - " + e)));
        }
        if (!warnings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(warnings.size() + " warning(s):"), false);
            warnings.forEach(w -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + w), false));
        }
        return errors.isEmpty() ? 1 : 0;
    }

    private static int situationDebug(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = nearestMcaVillager(player, 10.0D);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("No MCA villager within 10 blocks."));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        java.util.OptionalInt villageId = McaCompat.getHomeVillageId(target);
        if (villageId.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("That villager has no home village, so it can't surface situations."), false);
            return 0;
        }
        int vid = villageId.getAsInt();
        List<SituationInstance> open = SituationManager.openInstances(server).stream()
                .filter(instance -> instance.villageId() == vid).toList();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Village #" + vid + ": " + open.size() + " open situation(s)."), false);
        for (SituationInstance instance : open) {
            String scope = SituationRegistry.get(instance.defId()).map(def -> def.scope().lower()).orElse("?");
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + instance.defId() + " [" + scope + "]"), false);
        }
        return open.size();
    }

    private static int listQuests(CommandContext<CommandSourceStack> ctx) {
        var quests = QuestRegistry.all();
        if (quests.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No quests loaded."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Loaded " + quests.size() + " quest(s):"), false);
        quests.forEach(q -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + q.id()), false));
        return quests.size();
    }

    private static int validateQuests(CommandContext<CommandSourceStack> ctx) {
        var errors = QuestRegistry.lastErrors();
        var warnings = new java.util.ArrayList<>(QuestRegistry.lastWarnings());
        // Progression cross-refs are computed here (after all datapacks loaded) since runtime fails safe.
        warnings.addAll(dev.otectus.mcaquests.data.ProgressionValidator.collectWarnings(QuestRegistry.all()));
        // Achievability against the *running* Townstead: the only place a quest that parses perfectly
        // and can still never be finished shows up (spec 5.11).
        warnings.addAll(dev.otectus.mcaquests.data.TownsteadContentValidator.collectWarnings(
                QuestRegistry.all(),
                dev.otectus.mcaquests.project.data.ProjectRegistry.all(),
                dev.otectus.mcaquests.quest.situation.SituationRegistry.all()));
        if (errors.isEmpty() && warnings.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("All " + QuestRegistry.size() + " loaded quest(s) are valid."), false);
            return 1;
        }
        if (!errors.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(errors.size() + " quest error(s) from the last load:"));
            errors.forEach(e -> ctx.getSource().sendFailure(Component.literal(" - " + e)));
        }
        if (!warnings.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(warnings.size() + " warning(s):"), false);
            warnings.forEach(w -> ctx.getSource().sendSuccess(() -> Component.literal(" - " + w), false));
        }
        return errors.isEmpty() ? 1 : 0;
    }

    private static int reloadQuests(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        var server = src.getServer();
        src.sendSuccess(() -> Component.literal("Reloading datapacks (quests)..."), true);
        server.reloadResources(server.getPackRepository().getSelectedIds())
                .thenRunAsync(() -> {
                    src.sendSuccess(() -> Component.literal(
                            "Quests reloaded: " + QuestRegistry.size() + " loaded, "
                                    + QuestRegistry.lastErrors().size() + " error(s)."), true);
                    // Every loader has swapped by the time this callback runs, so this is the one point
                    // after a reload where the achievability pass can see all three registries at once.
                    dev.otectus.mcaquests.data.TownsteadAchievabilityReport.run();
                    // §12.6: re-evaluate FTB progress for online players now that the registry swap is
                    // done (new/changed quest ids may change what mcaquests-side FTB tasks match). Routed
                    // strictly through the FtbqBridge interface — never compat.ftbq — so this is a free
                    // no-op via NoopFtbqBridge when FTB Quests is absent or disabled.
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        FtbqBridge.Holder.get().recheckAll(player);
                        // Task M5.1: re-sync FTB editor known-ids too — a reload may add/remove quest,
                        // chain, ladder, tier, title, project, or situation ids the editor dropdowns offer.
                        FtbqEditorIdsSync.maybeSend(player);
                    }
                }, server);
        return 1;
    }

    private static int exportSchema(CommandContext<CommandSourceStack> ctx) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(McaQuests.MOD_ID);
        Path file = dir.resolve("example_quest.json");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, EXAMPLE_QUEST);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Failed to write example quest: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Wrote an annotated example quest to " + file + " (see DATAPACK.md for the full field reference)."), false);
        return 1;
    }

    /** A representative, valid quest covering the common fields — emitted by {@code /mcaquests export-schema}. */
    private static final String EXAMPLE_QUEST = """
            {
              "format_version": 1,
              "id": "mcaquests:example_quest",
              "enabled": true,
              "weight": 10,
              "category": "delivery",
              "title": { "text": "An Example Quest" },
              "repeat": { "type": "cooldown", "cooldown_ticks": 24000 },
              "giver": {
                "professions": ["minecraft:farmer", "minecraft:fisherman"],
                "adult_only": true,
                "min_hearts": 0
              },
              "dialogue": {
                "offer": { "text": "Could you bring me 10 wheat?" },
                "accept": { "text": "Thank you kindly!" },
                "decline": { "text": "Maybe another time." },
                "in_progress": { "text": "Any luck with that wheat?" },
                "ready": { "text": "You have it all? Wonderful!" },
                "complete": { "text": "Bless you, friend." }
              },
              "objectives": [
                { "type": "mcaquests:item_delivery", "item": "minecraft:wheat", "count": 10, "consume": true }
              ],
              "rewards": [
                { "type": "mcaquests:item", "item": "minecraft:emerald", "count": 2 },
                { "type": "mcaquests:xp", "amount": 20 },
                { "type": "mcaquests:hearts", "amount": 20 }
              ],
              "turn_in": { "mode": "original_giver" },
              "conditions": {
                "all_of": [
                  { "type": "mcaquests:time", "period": "DAY" },
                  { "type": "mcaquests:hearts", "min": 0 }
                ]
              },
              "priority": 1,
              "weight_bonus": [
                { "when": { "type": "mcaquests:hearts", "min": 50 }, "amount": 10 }
              ],
              "chain": {
                "chain": "mcaquests:example_arc",
                "stage": 2,
                "stage_total": 4,
                "relationship_arc": { "text": "An Example Arc" },
                "chapter": { "text": "Chapter Two" },
                "prerequisites": ["mcaquests:example_arc_stage1"],
                "unlocks": ["mcaquests:example_arc_stage3"]
              }
            }
            """;

    /**
     * Reports which MCA package layout the runtime binding matched, and anything in its manifest that
     * did not resolve. This is the single most useful thing to ask a bug reporter for: MCA has
     * repackaged mid-version-line before, and the difference between "bound to forge.net.mca.",
     * "bound to net.conczin.mca." and "no root matched" explains most MCA-shaped reports outright.
     */
    private static int debugMca(CommandContext<CommandSourceStack> ctx) {
        String report = "MCA binding: " + McaBinding.describe();
        ctx.getSource().sendSuccess(() -> Component.literal(report), false);
        return 1;
    }

    private static int debugVillager(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = nearestMcaVillager(player, 10.0D);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("No MCA villager within 10 blocks."));
            return 0;
        }

        int hearts = McaCompat.getHearts(player, target);
        String profession = McaCompat.getProfessionId(target)
                .map(net.minecraft.resources.ResourceLocation::toString)
                .orElse("<none>");
        String message = "MCA villager debug:"
                + "\n  uuid=" + McaCompat.getVillagerUuid(target)
                + "\n  name=" + McaCompat.getVillagerDisplayName(target).getString()
                + "\n  profession=" + profession
                + "\n  adult=" + McaCompat.isAdult(target)
                + "\n  hearts=" + hearts;
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        QuestManager.explainChainAvailability(player, target)
                .forEach(chainLine -> ctx.getSource().sendSuccess(() -> chainLine, false));
        return 1;
    }

    /** Explains why one specific quest is or is not offered by the nearest MCA villager (chain diagnostics). */
    private static int debugQuest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Entity target = nearestMcaVillager(player, 10.0D);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("No MCA villager within 10 blocks."));
            return 0;
        }
        QuestManager.explainOffer(player, target, id)
                .forEach(explainLine -> ctx.getSource().sendSuccess(() -> explainLine, false));
        return 1;
    }

    static Entity nearestMcaVillager(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<Entity> candidates = player.level().getEntities(player, box, McaCompat::isMcaVillager);
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }
}
