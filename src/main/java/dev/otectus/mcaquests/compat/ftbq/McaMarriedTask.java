package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.task.TaskType;
import dev.otectus.mcaquests.compat.McaCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code mcaquests:married} (spec §15.10) — the simplest pilot task: no fields, check is a single
 * {@link McaCompat#isPlayerMarried(ServerPlayer)} call. Poll-driven ({@link #autoSubmitOnPlayerTick()}
 * from {@link McaBooleanTaskBase}); {@code checkOnLogin()} stays the inherited default {@code true}
 * so a wedding that happened before the book unlocked is picked up on the player's next login.
 */
public class McaMarriedTask extends McaBooleanTaskBase {

    public McaMarriedTask(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public TaskType getType() {
        return FtbqTaskTypes.MARRIED;
    }

    @Override
    protected boolean check(ServerPlayer player) {
        return McaCompat.isPlayerMarried(player);
    }

    @Override
    public MutableComponent getAltTitle() {
        return Component.translatable("ftbquests.task.mcaquests.married");
    }
}
