package dev.otectus.mcaquests.quest.guidance;

import com.mojang.datafixers.util.Either;
import dev.otectus.mcaquests.McaQuests;
import dev.otectus.mcaquests.McaQuestsConfig;
import dev.otectus.mcaquests.quest.target.StructureTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Resumable structure navigation. World objects are inspected only on the server thread; the
 * worker uses ServerChunkCache's explicit off-thread request path, never vanilla's blocking locate.
 * Searches share a bounded cache and one outstanding chunk request across the entire server.
 *
 * <p>Biome searches share the same lifecycle since 1.7.0 ({@link #requestBiome}). One used to run in a
 * single call on the player's guidance pass and cost 140–170 ms on a real world; now it walks
 * {@link BiomeSpiral} a bounded number of samples per step under this queue's per-tick budget. Block
 * searches ({@link #requestBlock}) do the same with a resumable ring scan.
 */
@Mod.EventBusSubscriber(modid = McaQuests.MOD_ID)
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
    // A biome answer holds as long as a structure's: biomes do not move. A miss is retried sooner.
    private final SearchQueue<BiomeKey, BlockPos> biomeQueue = new SearchQueue<>(64, 200, 6000, 200);
    // A block can be harvested, so a hit is only trusted briefly; guidance re-checks it every pass anyway.
    private final SearchQueue<BlockKey, BlockPos> blockQueue = new SearchQueue<>(64, 200, 200, 200);
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
    // Shared within 128 blocks, like structure searches. The target is compared by value (a record).
    private record BiomeKey(ServerLevel level, Object target, int regionX, int regionZ, int radius) { }
    // A block search is local, so it is shared only within one chunk section of the origin.
    private record BlockKey(ServerLevel level, Object target, int sectionX, int sectionY, int sectionZ, int radius) { }

    /** Block reads per queue step: a few tenths of a millisecond. */
    private static final int BLOCK_PROBES_PER_STEP = 4096;

    /** Noise samples per queue step: about a tenth of a millisecond, so eight steps stay under the budget. */
    private static final int BIOME_SAMPLES_PER_STEP = 256;
    /** Vanilla's /locate biome uses 8; guidance wants a direction, not the first block (see BiomeTarget). */
    private static final int BIOME_HORIZONTAL_STEP = 32;
    private static final int BIOME_VERTICAL_STEP = 64;

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

    /**
     * The nearest position within {@code blockRadius} of {@code from} whose biome {@code matches}, found
     * over as many ticks as the queue's budget needs — the same position vanilla's
     * {@code findClosestBiome3d} would answer with the same steps. {@code target} identifies the search
     * so nearby requests for it share one walk.
     */
    public static CompletableFuture<Optional<BlockPos>> requestBiome(ServerLevel level, Object target,
                                                                     Predicate<Holder<Biome>> matches,
                                                                     BlockPos from, int blockRadius) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Biome guidance must be requested on the server thread");
        }
        StructureSearches searches = SERVERS.computeIfAbsent(level.getServer(), ignored -> new StructureSearches());
        int radius = Math.max(64, blockRadius);
        BiomeKey key = new BiomeKey(level, target, from.getX() >> 7, from.getZ() >> 7, radius);
        BlockPos origin = from.immutable();
        return searches.biomeQueue.request(key, searches.ticks, () -> new BiomeSearch(level, matches, origin, radius));
    }

    /**
     * The nearest loaded block {@code probe} accepts, scanning {@code scan} over as many ticks as the
     * queue's budget needs (1.7.0). {@code target} identifies the search so requests from the same chunk
     * section share one scan.
     */
    public static CompletableFuture<Optional<BlockPos>> requestBlock(ServerLevel level, Object target, BlockPos from,
                                                                     int radius,
                                                                     java.util.function.Supplier<dev.otectus.mcaquests.quest.target.BlockRingScan> scan,
                                                                     dev.otectus.mcaquests.quest.target.BlockRingScan.Probe probe) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("Block guidance must be requested on the server thread");
        }
        StructureSearches searches = SERVERS.computeIfAbsent(level.getServer(), ignored -> new StructureSearches());
        BlockKey key = new BlockKey(level, target, from.getX() >> 4, from.getY() >> 4, from.getZ() >> 4, radius);
        return searches.blockQueue.request(key, searches.ticks, () -> new BlockSearch(scan.get(), probe));
    }

    private static int searchRadius() {
        try {
            return McaQuestsConfig.COMMON.guidanceStructureSearchRadius.get();
        } catch (RuntimeException ignored) {
            return 8;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        StructureSearches searches = SERVERS.get(event.getServer());
        if (searches != null) {
            if (searches.outstandingChunk.isDone()) searches.releaseTicket();
            searches.queue.tick(++searches.ticks, 8, 2_000_000L);
            searches.areaQueue.tick(searches.ticks, 4, 1_000_000L);
            searches.biomeQueue.tick(searches.ticks, 8, 1_000_000L);
            searches.blockQueue.tick(searches.ticks, 8, 1_000_000L);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppingEvent event) {
        StructureSearches searches = SERVERS.remove(event.getServer());
        if (searches != null) {
            searches.queue.clear();
            searches.areaQueue.clear();
            searches.biomeQueue.clear();
            searches.blockQueue.clear();
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
            searches.biomeQueue.clear();
            searches.blockQueue.clear();
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

    /** A block search, a slice of the ring scan per step, reading only loaded blocks on the server thread. */
    private record BlockSearch(dev.otectus.mcaquests.quest.target.BlockRingScan scan,
                               dev.otectus.mcaquests.quest.target.BlockRingScan.Probe probe)
            implements SearchQueue.Task<BlockPos> {
        @Override
        public SearchQueue.Step<BlockPos> step() {
            Optional<BlockPos> found = scan.advance(BLOCK_PROBES_PER_STEP, probe);
            if (found.isPresent() || scan.exhausted()) {
                return SearchQueue.Step.finished(found);
            }
            return SearchQueue.Step.pending();
        }
    }

    /** A biome search, a slice of the spiral per step. The biome source is sampled on the server thread. */
    private static final class BiomeSearch implements SearchQueue.Task<BlockPos> {
        private final BiomeSource source;
        private final Climate.Sampler sampler;
        private final Set<Holder<Biome>> wanted;
        private final BiomeSpiral spiral;

        BiomeSearch(ServerLevel level, Predicate<Holder<Biome>> matches, BlockPos from, int radius) {
            this.source = level.getChunkSource().getGenerator().getBiomeSource();
            this.sampler = level.getChunkSource().randomState().sampler();
            // As vanilla does first: a biome this world can never generate is answered at once.
            this.wanted = source.possibleBiomes().stream().filter(matches).collect(Collectors.toUnmodifiableSet());
            this.spiral = new BiomeSpiral(from, radius, BIOME_HORIZONTAL_STEP, BIOME_VERTICAL_STEP,
                    level.getMinBuildHeight(), level.getMaxBuildHeight());
        }

        @Override
        public SearchQueue.Step<BlockPos> step() {
            if (wanted.isEmpty()) {
                return SearchQueue.Step.finished(Optional.empty());
            }
            Optional<BlockPos> found = spiral.advance(BIOME_SAMPLES_PER_STEP, (x, y, z) -> wanted.contains(
                    source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler)));
            if (found.isPresent() || spiral.exhausted()) {
                return SearchQueue.Step.finished(found);
            }
            return SearchQueue.Step.pending();
        }
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
        private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> chunk;

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
                Optional<ChunkAccess> loaded = chunk.getNow(null).left();
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
        private CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> chunk;

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
                Optional<ChunkAccess> loaded = chunk.getNow(null).left();
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
