package dev.otectus.mcaquests.compat.ftbq;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.client.ClientKnownIds;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.project.state.BankedReward;
import dev.otectus.mcaquests.project.state.ProjectSavedData;
import dev.otectus.mcaquests.quest.title.TitleScope;
import dev.otectus.mcaquests.quest.title.TitleService;
import dev.otectus.mcaquests.quest.title.Titles;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * {@code mcaquests:grant_title} (spec §16.3) — grants an MCA: Quests title through
 * {@link TitleService#grantGlobal}/{@link TitleService#grantVillage}, the same funnel every other title
 * grant path (the native {@code grant_title} quest reward, reputation tier-ups, the admin command) uses.
 *
 * <p><b>Validation:</b> {@code title_id} is checked against {@link Titles#isDefined} at claim time — an
 * empty, unparsable, or undefined id logs one WARN per distinct raw id (mirrors
 * {@code McaReputationTierTask}'s per-key WARN-once set) and messages the player every time, but performs
 * no grant (spec: "unknown title id → WARN + player message + no-op").
 *
 * <p><b>Title-chains:</b> {@link TitleService#grantGlobal}/{@link TitleService#grantVillage} post
 * {@code TitleGrantedEvent} on a genuinely new grant, which {@code FtbqEventBridge} uses to sweep
 * {@code McaTitleTask} — so completing this reward can itself complete a downstream
 * {@code mcaquests:title} task in the same book. That loop is safe: {@code PlayerTitles.grantGlobal/
 * grantVillage} only fire the event on the transition into "granted" (monotone, latched), so a chain can
 * never re-trigger from an already-held title. Documented here as intended behaviour, not a bug.
 */
public class McaGrantTitleReward extends McaRewardBase {

    /** Matches {@code ProjectRewardDistributor.VILLAGE_RESOLUTION_RADIUS} (spec §16.1/§16.3 share it). */
    private static final int VILLAGE_RESOLUTION_RADIUS = 128;

    private static final Set<String> WARNED_UNKNOWN_TITLES = ConcurrentHashMap.newKeySet();

    private static final NameMap<TitleScope> SCOPE_NAME_MAP = NameMap.of(TitleScope.GLOBAL, TitleScope.values())
            .baseNameKey("ftbquests.reward.mcaquests.grant_title.scope")
            .create();

    private String titleId = "";
    private TitleScope scope = TitleScope.GLOBAL;

    public McaGrantTitleReward(long id, Quest quest) {
        super(id, quest);
    }

    @Override
    public RewardType getType() {
        return FtbqRewardTypes.GRANT_TITLE;
    }

    /** Package-private accessor for {@code FtbqBridgeImpl}'s book→MCA validate sweep (spec §21). */
    String titleId() {
        return titleId;
    }

    @Override
    protected void doClaim(ServerPlayer player, boolean notify) {
        ResourceLocation id = titleId.isEmpty() ? null : ResourceLocation.tryParse(titleId);
        if (id == null || !Titles.isDefined(id)) {
            warnUnknownTitle(player);
            return;
        }
        if (scope == TitleScope.GLOBAL) {
            TitleService.grantGlobal(player, id); // fires TitleGrantedEvent on a new grant (title-chains)
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            bank(player, id);
            return;
        }
        OptionalInt villageId = McaCompat.findNearestVillageId(level, player.blockPosition(), VILLAGE_RESOLUTION_RADIUS);
        if (villageId.isEmpty()) {
            bank(player, id);
            return;
        }
        TitleService.grantVillage(player, villageId.getAsInt(), id); // fires TitleGrantedEvent (title-chains)
    }

    private void warnUnknownTitle(ServerPlayer player) {
        if (WARNED_UNKNOWN_TITLES.add(titleId)) {
            McaQuests.LOGGER.warn("[MCA: Quests] FTBQ reward {} references unknown title '{}'; no-op",
                    getType().getTypeId(), titleId);
        }
        player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.unknown_title"));
    }

    private void bank(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ProjectSavedData.get(server).addBankedReward(player.getUUID(), BankedReward.title(id, scope.name()));
        player.sendSystemMessage(Component.translatable("mcaquests.ftbq.reward.banked_title"));
    }

    @Override
    public void writeData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.writeData(nbt, provider);
        nbt.putString("title_id", titleId);
        nbt.putString("scope", scope.name());
    }

    @Override
    public void readData(CompoundTag nbt, HolderLookup.Provider provider) {
        super.readData(nbt, provider);
        titleId = nbt.getString("title_id");
        scope = parseScope(nbt.getString("scope"));
    }

    @Override
    public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(titleId, Short.MAX_VALUE);
        SCOPE_NAME_MAP.write(buffer, scope);
    }

    @Override
    public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        titleId = buffer.readUtf(Short.MAX_VALUE);
        scope = SCOPE_NAME_MAP.read(buffer);
    }

    @Override
    public void fillConfigGroup(ConfigGroup config) {
        super.fillConfigGroup(config);
        // title_id is a synced known id (§20) — dropdown-with-free-text via IdConfigRows.
        IdConfigRows.addIdField(config, "title_id", "ftbquests.reward.mcaquests.grant_title.title_id",
                titleId, v -> titleId = v, "", ClientKnownIds.titleIds());
        config.addEnum("scope", scope, v -> scope = v, SCOPE_NAME_MAP)
                .setNameKey("ftbquests.reward.mcaquests.grant_title.scope");
    }

    private static TitleScope parseScope(String raw) {
        return parseEnum(TitleScope.class, raw, TitleScope.GLOBAL);
    }
}
