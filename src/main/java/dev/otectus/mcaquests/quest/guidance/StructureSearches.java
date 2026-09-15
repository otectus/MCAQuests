package dev.otectus.mcaquests.quest.guidance;

import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.target.StructureTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resumable structure navigation. World objects are inspected only on the server thread; the
 * worker uses ServerChunkCache's explicit off-thread request path, never vanilla's blocking locate.
 * Searches share a bounded cache and one outstanding chunk request across the entire server.
 */
@EventBusSubscriber(modid = McaQuests.MOD_ID)
public final class StructureSearches {
    private static final Map<MinecraftServer, StructureSearches> SERVERS = new HashMap<>();
    private static final TicketType<ChunkPos> SEARCH_TICKET = TicketType.create(
            "mcaquests_structure_search", Comparator.comparingLong(ChunkPos::toLong));
    // Region tickets use 33 - distance as their chunk level. A negative distance requests only
    // STRUCTURE_STARTS; distance zero would unnecessarily generate a FULL chunk.
    private static final int TICKET_DISTANCE = 33 - ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);
    // A candidate chunk is the structure's start chunk, not its extent, so a village whose start sits
    // just outside the radius can still reach into it. Padding the enumeration errs toward finding one.
    private static final int FOOTPRINT_PAD = 128;
    // A pack with an extreme number of village types truncates rather than scanning unboundedly. The
    // list is distance-sorted, so what is dropped is the farthest and the gate errs permissive.
    private static final int MAX_CANDIDATES = 512;
    private final SearchQueue<Key, BlockPos> queue = new SearchQueue<>(128, 200, 6000, 200);
    // An area answer is only as good as the region it was scanned for, and its miss is a real answer:
    // hit and miss expire on the same short timer.
    private final SearchQueue<AreaKey, List<BoundingBox>> areaQueue = new SearchQueue<>(64, 200, 200, 200);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "MCA Quests structure requests");
        thread.setDaemon(true);
        return thread;
    });
    private CompletableFuture<?> outstandingChunk = CompletableFuture.completedFuture(null);
    private ServerChunkCache ticketSource;
    private ChunkPos ticketPos;
    private long ticks;

    // Sharing within 128 blocks avoids duplicate fortress searches for nearby players/objectives.
    private record Key(ServerLevel level, StructureTarget target, int regionX, int regionZ, int radius) { }
    private record Candidate(StructurePlacement placement, List<Holder<Structure>> structures, ChunkPos pos) { }
    // Area scans share the queue by caller-supplied token rather than by StructureTarget: they are asked
    // for by a set of structures, not by a quest's navigation target.
    private record AreaKey(ServerLevel level, ResourceLocation token, int regionX, int regionZ, int radius) { }
    private record AreaCandidate(List<Holder<Structure>> structures, ChunkPos pos, double distSqr) { }

    private StructureSearches() { }

    public static CompletableFuture<Optional<BlockPos>> request(ServerLevel level, StructureTarget target,
                                                                HolderSet<Structure> structures,
                                                                BlockPos from, int radius) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Structure guidance must be requested on the server thread");
        }
        StructureSearches searches = SERVERS.computeIfAbsent(level.getServer(), ignored -> new StructureSearches());
        int cappedRadius = Math.min(Math.max(0, radius), searchRadius());
        Key key = new Key(level, target, from.getX() >> 7, from.getZ() >> 7, cappedRadius);
        BlockPos origin = from.immutable();
        return searches.queue.request(key, searches.ticks,
                () -> searches.new Search(level, structures, origin, cappedRadius));
    }

    /**
     * Every valid start of {@code structures} whose candidate chunk lies within {@code blockRadius} of
     * {@code from}, as bounding boxes. Unlike {@link #request} this does not stop at the first hit.
     *
     * <p>An empty {@link Optional} means <b>could not answer</b> — the queue was at capacity
     * ({@link SearchQueue#request}) or a reload/shutdown cleared it — and never "there are no
     * structures here". Callers must not cache it as a negative result; a present but empty list is
     * the real "none found".
     */
    public static CompletableFuture<Optional<List<BoundingBox>>> requestAllWithin(ServerLevel level,
                                                                                 ResourceLocation token,
                                                                                 HolderSet<Structure> structures,
                                                                                 BlockPos from, int blockRadius) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Structure guidance must be requested on the server thread");
        }
        StructureSearches searches = SERVERS.computeIfAbsent(level.getServer(), ignored -> new StructureSearches());
        int radius = Math.max(0, blockRadius);
        AreaKey key = new AreaKey(level, token, from.getX() >> 7, from.getZ() >> 7, radius);
        BlockPos origin = from.immutable();
        return searches.areaQueue.request(key, searches.ticks,
                () -> searches.new AreaScan(level, structures, origin, radius));
    }

    private static int searchRadius() {
        try {
            return McaQuestsConfig.COMMON.guidanceStructureSearchRadius.get();
        } catch (RuntimeException ignored) {
            return 8;
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        StructureSearches searches = SERVERS.get(event.getServer());
        if (searches != null) {
            if (searches.outstandingChunk.isDone()) searches.releaseTicket();
            searches.queue.tick(++searches.ticks, 8, 2_000_000L);
            searches.areaQueue.tick(searches.ticks, 4, 1_000_000L);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        StructureSearches searches = SERVERS.remove(event.getServer());
        if (searches != null) {
            searches.queue.clear();
            searches.areaQueue.clear();
            searches.releaseTicket();
            searches.worker.shutdownNow();
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) clear(level.getServer());
    }

    @SubscribeEvent
    public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) clear(event.getPlayerList().getServer());
    }

    private static void clear(MinecraftServer server) {
        StructureSearches searches = SERVERS.get(server);
        if (searches != null) {
            searches.queue.clear();
            searches.areaQueue.clear();
            searches.releaseTicket();
        }
        // An already submitted vanilla chunk task belongs to the chunk system. Do not cancel it
        // or admit a second one until it finishes, even if its quest or level has gone away.
    }

    private void releaseTicket() {
        if (ticketSource != null) {
            ticketSource.removeRegionTicket(SEARCH_TICKET, ticketPos, TICKET_DISTANCE, ticketPos);
            ticketSource = null;
            ticketPos = null;
        }
    }

    /** The perimeter of a square, without revisiting corners or scanning its interior. */
    static ChunkPos offset(int radius, int index) {
        if (radius == 0) return new ChunkPos(0, 0);
        int side = radius * 2;
        return switch (index / side) {
            case 0 -> new ChunkPos(-radius, -radius + index);
            case 1 -> new ChunkPos(-radius + index - side, radius);
            case 2 -> new ChunkPos(radius, radius - (index - side * 2));
            default -> new ChunkPos(radius - (index - side * 3), -radius);
        };
    }

    private final class Search implements SearchQueue.Task<BlockPos> {
        private final ServerLevel level;
        private final BlockPos from;
        private final int radius;
        private final ChunkGeneratorStructureState state;
        private final List<Map.Entry<StructurePlacement, List<Holder<Structure>>>> random = new ArrayList<>();
        private final List<CompletableFuture<List<Candidate>>> rings = new ArrayList<>();
        private final List<Candidate> ringCandidates = new ArrayList<>();
        private boolean ringsReady;
        private int ringIndex;
        private int spreadRadius;
        private int spreadIndex;
        private int placementIndex;
        private Candidate candidate;
        private CompletableFuture<ChunkResult<ChunkAccess>> chunk;

        Search(ServerLevel level, HolderSet<Structure> targets, BlockPos from, int radius) {
            this.level = level;
            this.from = from;
            this.radius = radius;
            this.state = level.getChunkSource().getGeneratorState();
            Map<StructurePlacement, List<Holder<Structure>>> groups = new LinkedHashMap<>();
            for (Holder<Structure> target : targets) {
                for (StructurePlacement placement : state.getPlacementsForStructure(target)) {
                    groups.computeIfAbsent(placement, ignored -> new ArrayList<>()).add(target);
                }
            }
            // getPlacementsForStructure above finishes initialization of the placement/future maps
            // on this thread. They are then read-only and safely published to the worker. Only the
            // worker waits for vanilla's stronghold-position futures; no server tick calls join().
            for (var group : groups.entrySet()) {
                if (group.getKey() instanceof RandomSpreadStructurePlacement) {
                    random.add(Map.entry(group.getKey(), List.copyOf(group.getValue())));
                } else if (group.getKey() instanceof ConcentricRingsStructurePlacement ring) {
                    List<Holder<Structure>> structures = List.copyOf(group.getValue());
                    rings.add(CompletableFuture.supplyAsync(() -> {
                        List<ChunkPos> positions = state.getRingPositionsFor(ring);
                        return positions == null ? List.of() : positions.stream()
                                .map(pos -> new Candidate(ring, structures, pos)).toList();
                    }, worker));
                }
                // Vanilla locate also only understands these two placement types. Never fall back
                // to an unbounded synchronous search for an unknown modded placement.
            }
        }

        @Override
        public SearchQueue.Step<BlockPos> step() {
            if (chunk != null) {
                if (!chunk.isDone()) return SearchQueue.Step.pending();
                Optional<ChunkAccess> loaded = Optional.ofNullable(chunk.getNow(null).orElse(null));
                chunk = null;
                if (loaded.isPresent()) {
                    for (Holder<Structure> target : candidate.structures()) {
                        StructureStart start = loaded.get().getStartForStructure(target.value());
                        if (start != null && start.isValid()) {
                            return SearchQueue.Step.finished(Optional.of(candidate.placement()
                                    .getLocatePos(start.getChunkPos())));
                        }
                    }
                }
                return SearchQueue.Step.pending();
            }
            if (!outstandingChunk.isDone()) return SearchQueue.Step.pending();
            if (!ringsReady) {
                if (rings.stream().anyMatch(future -> !future.isDone())) return SearchQueue.Step.pending();
                rings.forEach(future -> ringCandidates.addAll(future.getNow(List.of())));
                ringCandidates.sort(Comparator.comparingDouble(value -> from.distSqr(
                        value.placement().getLocatePos(value.pos()))));
                ringsReady = true;
            }
            candidate = nextCandidate();
            if (candidate == null) return SearchQueue.Step.finished(Optional.empty());
            ServerChunkCache source = level.getChunkSource();
            ChunkPos pos = candidate.pos();
            // Vanilla's UNKNOWN request ticket expires after one tick. Keep this one candidate
            // alive across ticks until its future finishes, then release it from the server tick.
            releaseTicket();
            source.addRegionTicket(SEARCH_TICKET, pos, TICKET_DISTANCE, pos);
            ticketSource = source;
            ticketPos = pos;
            // IMPORTANT: getChunkFuture called ON the server thread runs managedBlock internally.
            // Its off-thread branch marshals scheduling to the main thread and returns immediately.
            // The worker only invokes that supported bridge; it never reads a chunk or its starts.
            chunk = CompletableFuture.supplyAsync(
                    () -> source.getChunkFuture(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true), worker)
                    .thenCompose(future -> future);
            outstandingChunk = chunk;
            return SearchQueue.Step.pending();
        }

        private Candidate nextCandidate() {
            if (ringIndex < ringCandidates.size()) return ringCandidates.get(ringIndex++);
            if (random.isEmpty() || spreadRadius > radius) return null;
            var group = random.get(placementIndex++);
            RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) group.getKey();
            ChunkPos delta = offset(spreadRadius, spreadIndex);
            ChunkPos pos = placement.getPotentialStructureChunk(state.getLevelSeed(),
                    (from.getX() >> 4) + placement.spacing() * delta.x,
                    (from.getZ() >> 4) + placement.spacing() * delta.z);
            if (placementIndex == random.size()) {
                placementIndex = 0;
                if (++spreadIndex == Math.max(1, 8 * spreadRadius)) {
                    spreadIndex = 0;
                    spreadRadius++;
                }
            }
            return new Candidate(placement, group.getValue(), pos);
        }
    }

    /**
     * An exhaustive sweep of every candidate village chunk within a radius, sharing the worker thread,
     * the region ticket and the one-outstanding-chunk invariant with {@link Search}. Only
     * {@link RandomSpreadStructurePlacement} is enumerated: concentric-ring placements (strongholds)
     * are never villages and their positions cost a join.
     */
    private final class AreaScan implements SearchQueue.Task<List<BoundingBox>> {
        private final ServerLevel level;
        private final List<AreaCandidate> candidates = new ArrayList<>();
        private final List<BoundingBox> found = new ArrayList<>();
        private int index;
        private AreaCandidate candidate;
        private CompletableFuture<ChunkResult<ChunkAccess>> chunk;

        AreaScan(ServerLevel level, HolderSet<Structure> targets, BlockPos from, int blockRadius) {
            this.level = level;
            ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
            Map<StructurePlacement, List<Holder<Structure>>> groups = new LinkedHashMap<>();
            for (Holder<Structure> target : targets) {
                for (StructurePlacement placement : state.getPlacementsForStructure(target)) {
                    if (placement instanceof RandomSpreadStructurePlacement) {
                        groups.computeIfAbsent(placement, ignored -> new ArrayList<>()).add(target);
                    }
                }
            }
            // Candidate chunks are pure math off the world seed, so the whole enumeration happens here
            // on the server thread and the step loop only ever reads chunks.
            long seed = state.getLevelSeed();
            int chunkX = from.getX() >> 4;
            int chunkZ = from.getZ() >> 4;
            int limit = blockRadius + FOOTPRINT_PAD;
            Map<ChunkPos, AreaCandidate> merged = new LinkedHashMap<>();
            for (var group : groups.entrySet()) {
                RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) group.getKey();
                int rings = Mth.ceil((double) limit / (placement.spacing() * 16.0));
                for (int dx = -rings; dx <= rings; dx++) {
                    for (int dz = -rings; dz <= rings; dz++) {
                        ChunkPos pos = placement.getPotentialStructureChunk(seed,
                                chunkX + placement.spacing() * dx, chunkZ + placement.spacing() * dz);
                        BlockPos locate = placement.getLocatePos(pos);
                        double offX = locate.getX() - from.getX();
                        double offZ = locate.getZ() - from.getZ();
                        double distSqr = offX * offX + offZ * offZ;
                        if (distSqr > (double) limit * limit) continue;
                        AreaCandidate existing = merged.get(pos);
                        if (existing == null) {
                            merged.put(pos, new AreaCandidate(new ArrayList<>(group.getValue()), pos, distSqr));
                        } else {
                            for (Holder<Structure> target : group.getValue()) {
                                if (!existing.structures().contains(target)) existing.structures().add(target);
                            }
                        }
                    }
                }
            }
            candidates.addAll(merged.values());
            candidates.sort(Comparator.comparingDouble(AreaCandidate::distSqr));
            if (candidates.size() > MAX_CANDIDATES) {
                candidates.subList(MAX_CANDIDATES, candidates.size()).clear();
            }
        }

        @Override
        public SearchQueue.Step<List<BoundingBox>> step() {
            if (chunk != null) {
                if (!chunk.isDone()) return SearchQueue.Step.pending();
                Optional<ChunkAccess> loaded = Optional.ofNullable(chunk.getNow(null).orElse(null));
                chunk = null;
                if (loaded.isPresent()) {
                    for (Holder<Structure> target : candidate.structures()) {
                        StructureStart start = loaded.get().getStartForStructure(target.value());
                        if (start != null && start.isValid()) found.add(start.getBoundingBox());
                    }
                }
                candidate = null;
                return SearchQueue.Step.pending();
            }
            if (!outstandingChunk.isDone()) return SearchQueue.Step.pending();
            // Always an answer, even an empty one: "no villages here" is the useful half of this search.
            if (index == candidates.size()) return SearchQueue.Step.finished(Optional.of(List.copyOf(found)));
            candidate = candidates.get(index++);
            ServerChunkCache source = level.getChunkSource();
            ChunkPos pos = candidate.pos();
            releaseTicket();
            source.addRegionTicket(SEARCH_TICKET, pos, TICKET_DISTANCE, pos);
            ticketSource = source;
            ticketPos = pos;
            chunk = CompletableFuture.supplyAsync(
                    () -> source.getChunkFuture(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS, true), worker)
                    .thenCompose(future -> future);
            outstandingChunk = chunk;
            return SearchQueue.Step.pending();
        }
    }
}
