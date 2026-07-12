package dev.otectus.mcaquests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.project.state.ProjectState;
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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Admin/debug commands under {@code /mcaquests} (spec section 24). Phase 0 ships only
 * {@code debug villager}, which exercises the entire {@link McaCompat} adapter end-to-end.
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
                                        .executes(McaQuestsCommand::titleClear)))));
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
                () -> Component.literal(" village #" + village + ": " + set), false));
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

    private static int reputationGet(CommandContext<CommandSourceStack> ctx) {
        int village = IntegerArgumentType.getInteger(ctx, "village");
        MinecraftServer server = ctx.getSource().getServer();
        int rep = ProjectSavedData.get(server).reputation("v:" + village);
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
        String identity = "v:" + village;
        int current = ProjectSavedData.get(server).reputation(identity);
        int newRep = ReputationService.award(server, identity, amount - current, asPlayer(ctx));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Village #" + village + " reputation set to " + newRep + "."), true);
        return newRep;
    }

    private static int reputationAdd(CommandContext<CommandSourceStack> ctx) {
        int village = IntegerArgumentType.getInteger(ctx, "village");
        int delta = IntegerArgumentType.getInteger(ctx, "delta");
        MinecraftServer server = ctx.getSource().getServer();
        int newRep = ReputationService.award(server, "v:" + village, delta, asPlayer(ctx));
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
                .thenRunAsync(() -> src.sendSuccess(() -> Component.literal(
                        "Quests reloaded: " + QuestRegistry.size() + " loaded, "
                                + QuestRegistry.lastErrors().size() + " error(s)."), true), server);
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

    private static Entity nearestMcaVillager(ServerPlayer player, double radius) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<Entity> candidates = player.level().getEntities(player, box, McaCompat::isMcaVillager);
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }
}
