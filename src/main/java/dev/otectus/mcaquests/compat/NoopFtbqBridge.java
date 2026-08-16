package dev.otectus.mcaquests.compat;

import net.minecraft.server.level.ServerPlayer;

/**
 * The default {@link FtbqBridge}: FTB Quests is absent, disabled, or its bootstrap failed. Every
 * method returns its documented safe default. Zero FTB imports (enforced by
 * {@code NoFtbqClassloadTest}). {@link #isReal()} and {@link #validateBookReferences()} are not
 * overridden here — their interface defaults ({@code false} / empty list) already are this class's
 * documented behaviour.
 */
final class NoopFtbqBridge implements FtbqBridge {

    static final NoopFtbqBridge INSTANCE = new NoopFtbqBridge();

    private NoopFtbqBridge() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isQuestCompleted(ServerPlayer player, String hexId) {
        return false;
    }

    @Override
    public boolean isChapterCompleted(ServerPlayer player, String hexId) {
        return false;
    }

    @Override
    public boolean isTaskCompleted(ServerPlayer player, String hexId) {
        return false;
    }

    @Override
    public boolean questIdExists(String hexId) {
        return false;
    }

    @Override
    public boolean chapterIdExists(String hexId) {
        return false;
    }

    @Override
    public boolean taskIdExists(String hexId) {
        return false;
    }

    @Override
    public boolean grantProgress(ServerPlayer player, ProgressAction action, String hexId) {
        return false;
    }

    @Override
    public void recheckAll(ServerPlayer player) {
        // no-op
    }

    @Override
    public int[] integrationObjectCounts() {
        return new int[]{0, 0};
    }
}
