package dev.otectus.mcaquests.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.data.QuestRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("villager")
                                .executes(McaQuestsCommand::debugVillager))));
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
