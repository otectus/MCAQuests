package dev.otectus.mcaquests.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /mcaquests compat …} — the one place an operator can ask what MCA: Quests can currently see
 * of every optional mod, rather than one command per integration.
 *
 * <p>Read-only and level 2 throughout, for the same reason {@code compat townstead status} is: the
 * server owner who most needs this is the one whose content is not appearing, and making them find an
 * operator first helps nobody.
 *
 * <p>Ice &amp; Fire and Bountiful each build their own node, which is grafted straight on; the
 * Townstead subtree is grafted on <b>unchanged</b> — {@link TownsteadCompatCommands#node()}
 * still builds its own {@code compat townstead …} tree and this lifts its children across, so that
 * command keeps its exact behaviour and its own translation keys.
 */
public final class CompatCommands {

    private CompatCommands() {
    }

    /** The whole {@code compat} subtree, grafted onto {@code /mcaquests} by the main command. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> compat = Commands.literal("compat")
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(CompatCommands::status));
        for (CommandNode<CommandSourceStack> child : TownsteadCompatCommands.node().getArguments()) {
            compat.then(child);
        }
        compat.then(IceAndFireCompatCommands.node());
        compat.then(BountifulCompatCommands.node());
        return compat;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CompatRegistry registry = CompatRegistry.get();
        if (registry.providers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.status.none"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.status.header",
                registry.providers().size()), false);
        for (CompatProvider provider : registry.providers()) {
            source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.status.provider",
                    provider.displayName(), stateLabel(provider.status())), false);
            for (Component line : provider.diagnostics()) {
                source.sendSuccess(() -> line, false);
            }
            for (CompatCapability capability : provider.capabilities()) {
                source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.status.capability",
                        capability.id(), presenceLabel(capability.present()),
                        capability.evidence().name().toLowerCase(Locale.ROOT)), false);
            }
        }
        return registry.providers().size();
    }

    private static Component stateLabel(CompatStatus status) {
        return Component.translatable("mcaquests.command.compat.status.state."
                + status.name().toLowerCase(Locale.ROOT));
    }

    private static Component presenceLabel(boolean present) {
        return Component.translatable(present
                ? "mcaquests.command.compat.status.present"
                : "mcaquests.command.compat.status.absent");
    }
}
