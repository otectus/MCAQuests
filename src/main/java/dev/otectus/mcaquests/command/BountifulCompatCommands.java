package dev.otectus.mcaquests.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.compat.bountiful.BountifulBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /mcaquests compat bountiful …} — what MCA: Quests can currently see of Bountiful.
 *
 * <p>The question worth answering here is narrower than Ice &amp; Fire's but harder to guess at from
 * inside the game: Bountiful can be installed and working while MCA: Quests is running in any of
 * three modes, and the visible symptom of the wrong one — "my bounty quests never advance" — looks
 * identical whether the hook is unavailable, the mode is {@code DATA_ONLY}, or the owner switched the
 * integration off months ago. {@code status} prints which of those it is in one line.
 *
 * <p>{@code probe} re-decides first, so a config change can be checked without a restart.
 *
 * <p>Both are read-only and level 2, matching the other {@code compat} subtrees.
 */
public final class BountifulCompatCommands {

    private BountifulCompatCommands() {
    }

    /** The {@code bountiful} subtree, grafted under {@code compat} by {@link CompatCommands}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("bountiful")
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(BountifulCompatCommands::status))
                .then(Commands.literal("probe")
                        .requires(source -> source.hasPermission(2))
                        .executes(BountifulCompatCommands::probe));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        return report(ctx.getSource());
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CompatRegistry.get().reprobeAll("the /mcaquests compat bountiful probe command",
                source.getServer().registryAccess());
        source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.bountiful.reprobed"), false);
        return report(source);
    }

    /**
     * Prints the provider even when it reports {@link CompatStatus#DISABLED}, unlike the
     * "not installed" short-circuit: an owner who switched the integration off and then forgot is
     * exactly the person running this command, and hiding the reason would leave them looking for a
     * bug instead of a config key.
     */
    private static int report(CommandSourceStack source) {
        CompatProvider provider = CompatRegistry.get().provider(BountifulBridge.MOD_ID).orElse(null);
        if (provider == null || provider.status() == CompatStatus.ABSENT) {
            source.sendSuccess(() ->
                    Component.translatable("mcaquests.command.compat.bountiful.not_installed"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.status.provider",
                provider.displayName(), Component.translatable("mcaquests.command.compat.status.state."
                        + provider.status().name().toLowerCase(Locale.ROOT))), false);
        for (Component line : provider.diagnostics()) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }
}
