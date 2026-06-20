package dev.otectus.mcaquests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.data.QuestRegistry;
import dev.otectus.mcaquests.project.ProjectManager;
import dev.otectus.mcaquests.project.data.ProjectRegistry;
import dev.otectus.mcaquests.project.state.ProjectState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
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
                                .executes(McaQuestsCommand::debugVillager)))
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
                                        .executes(McaQuestsCommand::projectDebug)))));
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
        if (errors.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("All " + QuestRegistry.size() + " loaded quest(s) are valid."), false);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(errors.size() + " quest error(s) from the last load:"));
        errors.forEach(e -> ctx.getSource().sendFailure(Component.literal(" - " + e)));
        return 0;
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
