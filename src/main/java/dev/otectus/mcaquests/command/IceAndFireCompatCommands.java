package dev.otectus.mcaquests.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.otectus.mcaquests.compat.CompatProvider;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * {@code /mcaquests compat iceandfire …} — what MCA: Quests can currently see of Ice &amp; Fire.
 *
 * <p>The question this exists to answer is the one the shared mod id makes hard: two different mods
 * call themselves {@code iceandfire}, so "Ice &amp; Fire is installed but my dragon quests never
 * appear" has several possible causes and no way to tell them apart from the game. {@code status}
 * prints the flavour, the class that answered the probe, and every capability by name, so the answer
 * is one command rather than a log hunt.
 *
 * <p>{@code probe} does the same after forcing a re-probe with the running server's registries, which
 * is the only way to get an answer for structures: they live in a dynamic registry that does not
 * exist during mod setup.
 *
 * <p>Both are read-only and level 2, matching {@code compat townstead}: the person who needs this is
 * the server owner whose content is not appearing.
 */
public final class IceAndFireCompatCommands {

    private static final String PROVIDER_ID = "iceandfire";

    private IceAndFireCompatCommands() {
    }

    /** The {@code iceandfire} subtree, grafted under {@code compat} by {@link CompatCommands}. */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("iceandfire")
                .then(Commands.literal("status")
                        .requires(source -> source.hasPermission(2))
                        .executes(IceAndFireCompatCommands::status))
                .then(Commands.literal("probe")
                        .requires(source -> source.hasPermission(2))
                        .executes(IceAndFireCompatCommands::probe));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        return report(ctx.getSource());
    }

    private static int probe(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        CompatRegistry.get().reprobeAll("the /mcaquests compat iceandfire probe command",
                source.getServer().registryAccess());
        source.sendSuccess(() -> Component.translatable("mcaquests.command.compat.iceandfire.reprobed"), false);
        return report(source);
    }

    private static int report(CommandSourceStack source) {
        CompatProvider provider = CompatRegistry.get().provider(PROVIDER_ID).orElse(null);
        if (provider == null || provider.status() == CompatStatus.ABSENT) {
            source.sendSuccess(() ->
                    Component.translatable("mcaquests.command.compat.iceandfire.not_installed"), false);
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
