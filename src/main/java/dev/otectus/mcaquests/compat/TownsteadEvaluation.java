package dev.otectus.mcaquests.compat;

import dev.otectus.mcaquests.compat.mca.McaHandles;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * One pass's worth of Townstead reads, memoised (Townstead spec §4.1).
 *
 * <p>An eligibility pass can ask the same question many times: a villager offering eight quests, five
 * of which gate on hunger, would otherwise build five identical snapshots. This is the same shape as
 * {@link McaVillagerSnapshot} — cheap things eagerly, everything else through a
 * {@code computeIfAbsent} — and it is deliberately <b>short-lived</b>. It lives for one pass and is
 * discarded, because a longer-lived cache would serve stale needs to a polling objective and quietly
 * complete quests on values that were true a minute ago.
 *
 * <p>Nothing Townstead-owned is stored here; every cached value is one of MCA: Quests' own view
 * records, so this object is safe to hold from {@code QuestContext} whether Townstead is installed
 * or not.
 */
public final class TownsteadEvaluation {

    private final Map<UUID, Optional<TownsteadVillagerView>> villagers = new HashMap<>();
    private final Map<Integer, Optional<TownsteadSpiritView>> spirits = new HashMap<>();
    private final Map<Long, Optional<TownsteadBuildingView>> buildings = new HashMap<>();
    private final Map<Integer, List<TownsteadVillageBuilding>> villageBuildings = new HashMap<>();
    private final Map<ResourceLocation, Optional<TownsteadRootView>> roots = new HashMap<>();
    private final Map<ResourceLocation, Optional<TownsteadGeneView>> genes = new HashMap<>();
    private final Map<String, TownsteadProfessionTrackView> tracks = new HashMap<>();

    @Nullable
    private Optional<TownsteadCalendarView> calendar;

    private static TownsteadBridge bridge() {
        return TownsteadBridge.Holder.get();
    }

    /** True when Townstead is installed and bound well enough to answer anything at all. */
    public static boolean available() {
        return bridge().isAvailable();
    }

    public static boolean has(TownsteadCapability capability) {
        return bridge().has(capability);
    }

    public Optional<TownsteadVillagerView> villager(@Nullable Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Optional<TownsteadVillagerView> cached = villagers.get(entity.getUUID());
        if (cached != null) {
            TownsteadCounters.cacheHit();
            return cached;
        }
        TownsteadCounters.villagerRead();
        Optional<TownsteadVillagerView> read = bridge().villager(entity);
        villagers.put(entity.getUUID(), read);
        return read;
    }

    public Optional<TownsteadCalendarView> calendar(@Nullable MinecraftServer server) {
        if (server == null) {
            return Optional.empty();
        }
        if (calendar == null) {
            calendar = bridge().calendar(server);
        }
        return calendar;
    }

    public Optional<TownsteadSpiritView> spirit(@Nullable ServerLevel level, int villageId) {
        if (level == null || villageId < 0) {
            return Optional.empty();
        }
        return spirits.computeIfAbsent(villageId, id -> bridge().spiritForVillage(level, id));
    }

    public Optional<TownsteadBuildingView> buildingAt(@Nullable ServerLevel level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return Optional.empty();
        }
        return buildings.computeIfAbsent(pos.asLong(), key -> bridge().buildingAt(level, pos));
    }

    /**
     * Every building registered to a village, memoised for the pass. Reads MCA's registry through
     * {@code McaHandles} rather than Townstead, because MCA owns buildings and Townstead only adds type
     * ids to them — so this works identically for a vanilla-MCA library and a Townstead dock.
     */
    public List<TownsteadVillageBuilding> buildingsIn(@Nullable ServerLevel level, int villageId) {
        if (level == null || villageId < 0) {
            return List.of();
        }
        return villageBuildings.computeIfAbsent(villageId, id -> {
            Object village = McaHandles.village(level, id);
            if (village == null) {
                return List.of();
            }
            List<TownsteadVillageBuilding> out = new ArrayList<>();
            for (Object building : McaHandles.villageBuildings(village)) {
                String type = McaHandles.buildingType(building);
                if (type.isEmpty()) {
                    continue;
                }
                out.add(new TownsteadVillageBuilding(
                        McaHandles.buildingId(building),
                        type,
                        McaHandles.buildingSize(building),
                        McaHandles.buildingCenter(building).orElse(BlockPos.ZERO)));
            }
            return List.copyOf(out);
        });
    }

    /** How many buildings of a type (or of any tier of it) the village has. */
    public int countBuildings(@Nullable ServerLevel level, int villageId, String type, int minimumLevel) {
        int count = 0;
        for (TownsteadVillageBuilding building : buildingsIn(level, villageId)) {
            if (building.matches(type) && building.level() >= minimumLevel) {
                count++;
            }
        }
        return count;
    }

    public Optional<TownsteadRootView> root(@Nullable ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return roots.computeIfAbsent(id, bridge()::root);
    }

    public Optional<TownsteadGeneView> gene(@Nullable ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return genes.computeIfAbsent(id, bridge()::gene);
    }

    /**
     * What a profession's progression can reach (spec §5.1), memoised for the pass.
     *
     * <p>The bridge caches these for the whole game run already -- a track cannot change without a
     * datapack reload -- so this second layer exists only to keep an eligibility pass that asks about
     * the same trade for eight villagers down to one map lookup each.
     */
    public TownsteadProfessionTrackView professionTrack(@Nullable String professionId) {
        if (professionId == null || professionId.isEmpty()) {
            return TownsteadProfessionTrackView.none("");
        }
        return tracks.computeIfAbsent(professionId, bridge()::professionTrack);
    }

    /**
     * True when this profession can be asked to advance <em>and</em> we are entitled to say so.
     *
     * <p>Deliberately false when {@code READ_PROFESSION_SPEC} is unbound: an unreadable registry means
     * "cannot tell", and content that cannot be proven achievable must hide rather than be offered on
     * a guess. That is the whole of the 1.4.0 fisherman defect, expressed as one method.
     */
    public boolean canProgress(@Nullable String professionId) {
        return has(TownsteadCapability.READ_PROFESSION_SPEC) && professionTrack(professionId).progressive();
    }

    /**
     * The snapshot a query's {@link TownsteadQuery.Source} names, for an already-resolved target, or
     * {@code null} when it cannot be produced — which the query turns into its {@code missing} answer
     * rather than into a comparison against a default.
     *
     * <p>The subject of each source:
     * <ul>
     *   <li>{@code villager} — the target itself.</li>
     *   <li>{@code calendar} — the server; the target is ignored.</li>
     *   <li>{@code spirit} — the target's home village.</li>
     *   <li>{@code root} — the root the target descends from.</li>
     *   <li>{@code building} — the registered building at the target's workstation, or failing that at
     *       their home. A villager standing in a field is still "at" the farm they work.</li>
     *   <li>{@code gene} — the first path segment is the gene id, so one condition can address any
     *       gene without a second field to carry it. The remaining segments walk that gene.</li>
     * </ul>
     */
    @Nullable
    public Object subject(TownsteadQuery query, @Nullable Entity target) {
        ServerLevel level = target != null && target.level() instanceof ServerLevel server ? server : null;
        return switch (query.source()) {
            case VILLAGER -> villager(target).orElse(null);
            case CALENDAR -> calendar(target == null ? null : target.getServer()).orElse(null);
            case SPIRIT -> {
                OptionalInt villageId = homeVillage(target);
                yield villageId.isPresent() ? spirit(level, villageId.getAsInt()).orElse(null) : null;
            }
            case ROOT -> villager(target)
                    .map(TownsteadVillagerView::rootId)
                    .map(ResourceLocation::tryParse)
                    .flatMap(this::root)
                    .orElse(null);
            case BUILDING -> buildingFor(level, target).orElse(null);
            case GENE -> gene(ResourceLocation.tryParse(query.path().get(0))).orElse(null);
        };
    }

    /**
     * The path a query walks once its source has resolved. Identical to {@link TownsteadQuery#path()}
     * except for {@code gene}, whose first segment named the gene itself and has been consumed.
     */
    public static List<String> effectivePath(TownsteadQuery query) {
        List<String> path = query.path();
        return query.source() == TownsteadQuery.Source.GENE ? path.subList(1, path.size()) : path;
    }

    private Optional<TownsteadBuildingView> buildingFor(@Nullable ServerLevel level, @Nullable Entity target) {
        if (level == null || target == null) {
            return Optional.empty();
        }
        Optional<TownsteadBuildingView> atWork = McaCompat.getWorkstationPos(target)
                .flatMap(pos -> buildingAt(level, pos));
        return atWork.isPresent() ? atWork : McaCompat.getHomePos(target).flatMap(pos -> buildingAt(level, pos));
    }

    private static OptionalInt homeVillage(@Nullable Entity target) {
        return target == null ? OptionalInt.empty() : McaCompat.getHomeVillageId(target);
    }
}
