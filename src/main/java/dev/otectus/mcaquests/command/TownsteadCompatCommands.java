package dev.otectus.mcaquests.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadCounters;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * {@code /mcaquests compat townstead …} — the operator-facing view of the Townstead integration
 * (Townstead spec §12).
 *
 * <p>Three things it deliberately does <b>not</b> do. It never mutates anything, so it is safe to run
 * on a live server at permission level 2. It never speaks in reflection terminology to a normal
 * player — "hunger is unavailable" rather than "handle unbound". And it never reaches into
 * {@code compat.townstead}: everything here comes through {@link TownsteadBridge}, so this class
 * loads perfectly well on a server that has never heard of Townstead, which is exactly the server
 * whose owner most needs {@code status} to explain why nothing is happening.
 */
public final class TownsteadCompatCommands {

    private static final String MOD_ID = "townstead";
    private static final double SNAPSHOT_RADIUS = 10.0D;

    private TownsteadCompatCommands() {
    }

    /** The {@code compat townstead} subtree, grafted onto {@code /mcaquests} by the main command. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("compat")
                .then(Commands.literal("townstead")
                        .then(Commands.literal("status")
                                .requires(source -> source.hasPermission(2))
                                .executes(TownsteadCompatCommands::status))
                        .then(Commands.literal("probe")
                                .requires(source -> source.hasPermission(2))
                                .executes(TownsteadCompatCommands::probe))
                        .then(Commands.literal("snapshot")
                                .requires(source -> source.hasPermission(2))
                                .executes(TownsteadCompatCommands::snapshot)));
    }

    // ------------------------------------------------------------------------------------- status

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!ModList.get().isLoaded(MOD_ID)) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.not_installed"), false);
            return 0;
        }

        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.status.detected",
                bridge.detectedVersion(), bridge.variant().orElse("?")), false);
        source.sendSuccess(() -> Component.translatable(statusKey(bridge.status()),
                bridge.capabilities().size(), TownsteadCapability.values().length), false);

        List<String> missing = missingCapabilities(bridge);
        if (!missing.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.status.missing",
                    String.join(", ", missing)), false);
        }
        List<String> unresolved = bridge.unresolvedMembers();
        if (!unresolved.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.status.unresolved",
                    unresolved.size(), String.join(", ", unresolved)), false);
        }

        if (McaQuestsConfig.COMMON.townsteadDebugBindingLogs.get()) {
            // Only on request, and only when asked for. A server that is running fine says nothing;
            // a server that is not can be asked what the integration has actually been doing.
            source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.status.counters",
                    TownsteadCounters.describe()), false);
        }
        source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.status.toggles",
                onOff(McaQuestsConfig.COMMON.townsteadContentEnabled.get()),
                onOff(McaQuestsConfig.COMMON.townsteadReactionsEnabled.get()),
                onOff(McaQuestsConfig.COMMON.townsteadNeedRewardsEnabled.get()),
                onOff(McaQuestsConfig.COMMON.townsteadProfessionXpRewardsEnabled.get()),
                onOff(McaQuestsConfig.COMMON.townsteadSkillRewardsEnabled.get())), false);
        return 1;
    }

    private static String statusKey(TownsteadStatus status) {
        return switch (status) {
            case FULL -> "mcaquests.command.townstead.status.full";
            case PARTIAL -> "mcaquests.command.townstead.status.partial";
            case DISABLED -> "mcaquests.command.townstead.status.disabled";
            case ABSENT -> "mcaquests.command.townstead.status.absent";
        };
    }

    // -------------------------------------------------------------------------------------- probe

    /**
     * Checks each capability by <em>contract</em> rather than by asking the binding whether it bound:
     * a capability is only useful if a real read through it produces a value, and the two can differ
     * when Townstead is present but has no state for the subject at hand. Non-mutating throughout.
     */
    private static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (!bridge.isAvailable()) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.probe.unavailable"), false);
            return 0;
        }

        ServerPlayer player = source.getPlayer();
        Entity villager = player == null ? null : McaQuestsCommand.nearestMcaVillager(player, SNAPSHOT_RADIUS);
        ServerLevel level = source.getLevel();
        TownsteadEvaluation evaluation = new TownsteadEvaluation();

        report(source, TownsteadCapability.READ_CALENDAR,
                evaluation.calendar(source.getServer()).isPresent());

        if (villager == null) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.probe.no_villager"), false);
        } else {
            boolean villagerRead = evaluation.villager(villager).isPresent();
            report(source, TownsteadCapability.READ_VILLAGER, villagerRead);
            report(source, TownsteadCapability.READ_NEEDS,
                    villagerRead && evaluation.villager(villager).orElseThrow().needs() != null);
            report(source, TownsteadCapability.READ_SCHEDULE,
                    villagerRead && evaluation.villager(villager).orElseThrow().schedule() != null);
            report(source, TownsteadCapability.READ_PROFESSION,
                    villagerRead && evaluation.villager(villager).orElseThrow().hasProfession());
            report(source, TownsteadCapability.READ_BUILDING,
                    McaCompat.getWorkstationPos(villager)
                            .flatMap(pos -> evaluation.buildingAt(level, pos)).isPresent());

            OptionalInt village = McaCompat.getHomeVillageId(villager);
            report(source, TownsteadCapability.READ_SPIRIT,
                    village.isPresent() && evaluation.spirit(level, village.getAsInt()).isPresent());
        }
        return 1;
    }

    private static void report(CommandSourceStack source, TownsteadCapability capability, boolean live) {
        boolean bound = TownsteadBridge.Holder.get().has(capability);
        String key = !bound
                ? "mcaquests.command.townstead.probe.unbound"
                : live ? "mcaquests.command.townstead.probe.ok" : "mcaquests.command.townstead.probe.no_data";
        source.sendSuccess(() -> Component.translatable(key, capability.name()), false);
    }

    // ----------------------------------------------------------------------------------- snapshot

    /**
     * Dumps the nearest villager's Townstead state <b>using the paths a quest author would write</b>,
     * not Townstead's internal field names — so the output can be pasted straight into a
     * {@code townstead_value} condition.
     */
    private static int snapshot(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("mcaquests.command.townstead.snapshot.needs_player"));
            return 0;
        }
        Entity villager = McaQuestsCommand.nearestMcaVillager(player, SNAPSHOT_RADIUS);
        if (villager == null) {
            source.sendFailure(Component.translatable("mcaquests.command.townstead.snapshot.no_villager"));
            return 0;
        }

        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        var view = evaluation.villager(villager).orElse(null);
        if (view == null) {
            source.sendFailure(Component.translatable("mcaquests.command.townstead.snapshot.no_state"));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("mcaquests.command.townstead.snapshot.header",
                view.name()), false);
        for (String line : lines(view)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static List<String> lines(dev.otectus.mcaquests.compat.TownsteadVillagerView view) {
        List<String> lines = new ArrayList<>();
        lines.add(path("lifeStage", view.lifeStage()));
        lines.add(path("professionId", view.professionId()));
        lines.add(path("professionLevel", view.professionLevel()));
        lines.add(path("professionXp", view.professionXp()));
        lines.add(path("personalityId", view.personalityId()));
        lines.add(path("rootId", view.rootId()));
        lines.add(path("needs.hunger", view.needs().hunger()));
        lines.add(path("needs.thirst", view.needs().thirst()));
        lines.add(path("needs.fatigue", view.needs().fatigue()));
        lines.add(path("needs.energy", view.needs().energy()));
        lines.add(path("needs.collapsed", view.needs().collapsed()));
        lines.add(path("needs.gated", view.needs().gated()));
        lines.add(path("schedule.currentActivity", view.schedule().currentActivity()));
        lines.add(path("schedule.plannedActivity", view.schedule().plannedActivity()));
        lines.add(path("schedule.templateId", view.schedule().templateId()));
        lines.add(path("heritage", view.heritage().keySet().stream().collect(Collectors.joining(", "))));
        return lines;
    }

    private static String path(String key, @Nullable Object value) {
        return "  " + key + " = " + value;
    }

    private static List<String> missingCapabilities(TownsteadBridge bridge) {
        List<String> missing = new ArrayList<>();
        for (TownsteadCapability capability : TownsteadCapability.values()) {
            if (!bridge.has(capability)) {
                missing.add(capability.name());
            }
        }
        return missing;
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value
                ? "mcaquests.command.townstead.toggle.on"
                : "mcaquests.command.townstead.toggle.off");
    }
}
