package dev.otectus.mcaquests.quest.village;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.quest.guidance.StructureSearches;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * "Is this position within {@code minDistance} of any village?", for the escort offer gate and the
 * {@code mcaquests:giver_distance_from_any_village} condition.
 *
 * <p>Three sources answer it, cheapest first: MCA Reborn's in-memory village registry (which also
 * covers MCA Capitals, since a capital is an MCA village), a small meeting-point (bell) lookup over
 * chunks that are already loaded, and finally a bounded off-thread sweep of village structure starts
 * built on {@link StructureSearches}. Only the last one can see a vanilla or modded village that no
 * MCA villager lives in, and only it can be slow, so it never runs on the server thread.
 *
 * <p>While that sweep is in flight the answer is {@link Nearness#UNKNOWN}, and callers are expected to
 * fail toward <em>not</em> offering: offering an "escort me home" quest to a villager standing in its
 * own square is the mistake this exists to prevent, and withholding it for a few ticks is not.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class VillageProximity {
    /** Structures counted as villages. Village mods extend the gate by joining this tag. */
    public static final TagKey<Structure> VILLAGES =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation(McaQuests.MOD_ID, "villages"));
    /** Structure results are cached per 128-block region, so one scan answers for a whole area. */
    static final int CACHE_REGION_BITS = 7;
    private static final ResourceLocation SCAN_TOKEN =
            new ResourceLocation(McaQuests.MOD_ID, "village_proximity");
    private static final int MEMO_CAPACITY = 512;
    private static final int CACHE_CAPACITY = 512;
    // Access-ordered and bounded: a busy server with many players must not grow either map without end.
    private static final Map<MemoKey, Nearness> MEMO = boundedMap(MEMO_CAPACITY);
    private static final Map<CacheKey, List<BoundingBox>> CACHE = boundedMap(CACHE_CAPACITY);

    /** Whether a village is near, definitely not near, or not yet known. */
    public enum Nearness { NEAR, FAR, UNKNOWN }

    // The whole offer pass for one villager asks the same question repeatedly, once per candidate
    // quest. Game time in the key keeps that memo honest without a separate sweep to expire it.
    private record MemoKey(ResourceKey<Level> dimension, long pos, int minDistance, long gameTime) { }
    private record CacheKey(ResourceKey<Level> dimension, int regionX, int regionZ, int minDistance) { }

    private VillageProximity() {
    }

    private static <K, V> Map<K, V> boundedMap(int capacity) {
        return new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    /**
     * Whether {@code from} is within {@code minDistance} horizontal blocks of a village.
     *
     * <p>Never throws: every world and optional-mod lookup inside is defensive, because this runs
     * inside offer generation, where one bad answer would cost the player every quest that villager
     * has. A {@code minDistance} of zero or less disables the question and answers {@link Nearness#FAR}.
     */
    public static Nearness check(ServerLevel level, BlockPos from, double minDistance) {
        if (minDistance <= 0.0D) {
            return Nearness.FAR;
        }
        MinecraftServer server = level.getServer();
        // Everything below reads world state or submits chunk work; off-thread callers get no answer.
        if (server == null || !server.isSameThread()) {
            return Nearness.UNKNOWN;
        }
        MemoKey memoKey = new MemoKey(level.dimension(), from.asLong(), (int) minDistance, level.getGameTime());
        Nearness memo = MEMO.get(memoKey);
        if (memo != null) {
            return memo;
        }
        Nearness answer = compute(level, from, minDistance);
        MEMO.put(memoKey, answer);
        return answer;
    }

    private static Nearness compute(ServerLevel level, BlockPos from, double minDistance) {
        if (mcaVillageWithin(level, from, minDistance) || meetingPointWithin(level, from, minDistance)) {
            return Nearness.NEAR;
        }
        return structureAnswer(level, from, minDistance);
    }

    /** MCA Reborn's own village registry, which is also where an MCA Capitals capital lives. */
    private static boolean mcaVillageWithin(ServerLevel level, BlockPos from, double minDistance) {
        try {
            OptionalInt id = McaCompat.findNearestVillageId(level, from, Mth.ceil(minDistance));
            if (id.isEmpty()) {
                return false;
            }
            Optional<BlockPos> center = McaCompat.villageCenter(level, id.getAsInt());
            return center.isPresent() && horizontalDistance(from, center.get()) <= minDistance;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    /**
     * The village bell only ({@code PoiTypes.MEETING}), not {@code PoiTypeTags.VILLAGE} — that tag
     * includes every bed, so a single player-built shelter would read as a village.
     */
    private static boolean meetingPointWithin(ServerLevel level, BlockPos from, double minDistance) {
        try {
            int radius = (int) Math.min(minDistance, McaQuestsConfig.COMMON.villagePoiDetectionRadius.get());
            if (radius <= 0) {
                return false;
            }
            return level.getPoiManager()
                    .findClosest(holder -> holder.is(PoiTypes.MEETING), from, radius, PoiManager.Occupancy.ANY)
                    .isPresent();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static Nearness structureAnswer(ServerLevel level, BlockPos from, double minDistance) {
        CacheKey key = new CacheKey(level.dimension(), from.getX() >> CACHE_REGION_BITS,
                from.getZ() >> CACHE_REGION_BITS, (int) minDistance);
        List<BoundingBox> cached = CACHE.get(key);
        if (cached != null) {
            OptionalDouble nearest = nearestHorizontalDistance(from, cached);
            return nearest.isPresent() && nearest.getAsDouble() <= minDistance ? Nearness.NEAR : Nearness.FAR;
        }
        try {
            HolderSet<Structure> structures = villageStructures(level);
            if (structures.size() == 0) {
                return Nearness.FAR;
            }
            // Scanning from the region centre with the padded radius means the one result answers for
            // every position in the region, not just the one that asked.
            int half = 1 << (CACHE_REGION_BITS - 1);
            BlockPos centre = new BlockPos((key.regionX() << CACHE_REGION_BITS) + half, from.getY(),
                    (key.regionZ() << CACHE_REGION_BITS) + half);
            StructureSearches.requestAllWithin(level, SCAN_TOKEN, structures, centre, scanRadiusFor(minDistance))
                    // An absent result means "could not answer" and must never be stored as "no villages":
                    // that would open the gate permanently for this region.
                    .thenAccept(result -> result.ifPresent(boxes -> CACHE.put(key, List.copyOf(boxes))));
        } catch (RuntimeException | LinkageError exception) {
            McaQuests.LOGGER.debug("Village structure scan failed; answering UNKNOWN", exception);
        }
        return Nearness.UNKNOWN;
    }

    /** The {@link #VILLAGES} tag, plus whatever {@code extraVillageStructures} resolves to. */
    private static HolderSet<Structure> villageStructures(ServerLevel level) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        registry.getTag(VILLAGES).ifPresent(named -> named.forEach(holder -> add(holders, holder)));
        for (String entry : McaQuestsConfig.COMMON.extraVillageStructures.get()) {
            addConfigured(registry, entry, holders);
        }
        return HolderSet.direct(holders);
    }

    /** An id, or a {@code #tag} id. Anything that does not resolve is simply not a village. */
    private static void addConfigured(Registry<Structure> registry, String entry, List<Holder<Structure>> holders) {
        String trimmed = entry.trim();
        boolean tag = trimmed.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? trimmed.substring(1) : trimmed);
        if (id == null) {
            return;
        }
        if (tag) {
            registry.getTag(TagKey.create(Registries.STRUCTURE, id))
                    .ifPresent(named -> named.forEach(holder -> add(holders, holder)));
        } else {
            registry.getHolder(ResourceKey.create(Registries.STRUCTURE, id))
                    .ifPresent(holder -> add(holders, holder));
        }
    }

    private static void add(List<Holder<Structure>> holders, Holder<Structure> holder) {
        if (!holders.contains(holder)) {
            holders.add(holder);
        }
    }

    /**
     * How far the scan must reach from a region's centre for its result to answer for every position
     * inside that region: the gate distance plus the region's own half-diagonal.
     */
    static int scanRadiusFor(double minDistance) {
        return Mth.ceil(minDistance) + 96;
    }

    /** Horizontal distance from {@code pos} to {@code box}, zero when inside it in XZ. Y is ignored. */
    public static double horizontalDistance(BlockPos pos, BoundingBox box) {
        double dx = Math.max(0, Math.max(box.minX() - pos.getX(), pos.getX() - box.maxX()));
        double dz = Math.max(0, Math.max(box.minZ() - pos.getZ(), pos.getZ() - box.maxZ()));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The smallest {@link #horizontalDistance} to any of {@code boxes}, empty when there are none. */
    public static OptionalDouble nearestHorizontalDistance(BlockPos pos, List<BoundingBox> boxes) {
        OptionalDouble nearest = OptionalDouble.empty();
        for (BoundingBox box : boxes) {
            double distance = horizontalDistance(pos, box);
            if (nearest.isEmpty() || distance < nearest.getAsDouble()) {
                nearest = OptionalDouble.of(distance);
            }
        }
        return nearest;
    }

    private static double horizontalDistance(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel) {
            clear();
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        clear();
    }

    @SubscribeEvent
    public static void reload(OnDatapackSyncEvent event) {
        // Fires once per player on login as well; only the server-wide reload should wipe the cache.
        if (event.getPlayer() == null) {
            clear();
        }
    }

    private static void clear() {
        MEMO.clear();
        CACHE.clear();
    }
}
