package dev.otectus.mcaquests.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import dev.otectus.mcaquests.compat.capitals.CapitalsBinding;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /mcaquests compat capitals …} — what MCA: Quests can currently see of MCA Capitals.
 *
 * <p>The question worth answering here is which of the nine capabilities bound. Capitals is a large
 * mod that moves internals between point releases, and the visible symptom of a capability that did
 * not bind — "the court quests stopped being offered" — looks the same whether the mod is missing,
 * the integration is switched off, or one data accessor was renamed. {@code status} separates those
 * three in one screen.
 *
 * <p>{@code probe} re-decides first, so a config change can be checked without a restart.
 *
 * <p>Both are read-only and level 2, matching the other {@code compat} subtrees.
 */
public final class CapitalsCompatCommands {

    private CapitalsCompatCommands() {
    }

    /** The {@code capitals} subtree, grafted under {@code compat} by {@link CompatCommands}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("capitals")
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(CapitalsCompatCommands::status))
                .then(Commands.literal("probe")
                        .requires(source -> source.hasPermission(2))
                        .executes(CapitalsCompatCommands::probe));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        return report(ctx.getSource());
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CompatRegistry.get().reprobeAll("the /mcaquests compat capitals probe command",
                source.getServer().registryAccess());
        source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.capitals.reprobed"), false);
        return report(source);
    }

    /**
     * Prints the provider even when it reports {@link CompatStatus#DISABLED}, unlike the
     * "not installed" short-circuit: an owner who switched the integration off and then forgot is
     * exactly the person running this command, and hiding the reason would leave them looking for a
     * bug instead of a config key.
     */
    private static int report(CommandSourceStack source) {
        CompatProvider provider = CompatRegistry.get().provider(CapitalsBinding.MOD_ID).orElse(null);
        if (provider == null || provider.status() == CompatStatus.ABSENT) {
            source.sendSuccess(() ->
                    Component.translatable("mcaquests.command.compat.capitals.not_installed"), false);
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
