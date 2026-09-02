package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.api.PollingObjective;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.RelativeCandidate;
import dev.otectus.mcaquests.quest.target.BiomeTarget;
import dev.otectus.mcaquests.quest.target.StructureTarget;
import dev.otectus.mcaquests.quest.target.VillagerTarget;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Search the wilds for a relative of the quest giver who has gone missing, and find them.
 *
 * <p>This exists because MCA defines a <em>missing</em> relative as one with a family-tree entry, not
 * deceased, and <b>no entity anywhere in the world</b>. A quest gated on that status therefore has no
 * villager to target, name, or highlight — which is why the built-in "missing kin" quests used to be
 * plain "visit a forest" errands with the lost child existing only in the dialogue.
 *
 * <p>The objective closes that gap: the relative is bound by UUID at accept (so the quest log names the
 * real person from the start, read out of MCA's persistent family tree), and the moment the player is
 * genuinely out searching — inside the named {@code biome} / {@code structure}, and at least
 * {@code min_distance} from the giver — they are <b>materialised into the world nearby</b> with their own
 * identity intact and immediately highlighted. From then on they are an ordinary villager, so later chain
 * stages can escort or deliver to them through the normal {@code "mode": "family"} path.
 *
 * <p>Completion is always "the relative exists and you are within {@code discover_radius} of them", so a
 * relative who is already in the world (found by another player, or loaded by MCA) is simply walked up to
 * rather than duplicated — {@link McaCompat#materializeRelative} refuses to spawn a second copy.
 *
 * <pre>
 * { "type": "mcaquests:find_missing_relative",
 *   "relative": { "mode": "family", "relation": "child" },
 *   "biome": { "tag": "minecraft:is_forest" },
 *   "min_distance": 96 }
 * </pre>
 */
public record FindMissingRelativeObjective(VillagerTarget relative, Optional<BiomeTarget> biome,
                                           Optional<StructureTarget> structure, int minDistance,
                                           int discoverRadius, int spawnDistance)
        implements QuestObjective, VillagerTargeted, PollingObjective {

    /**
     * How far above the player the surface must be before we treat them as underground and place the
     * relative at the player's own Y instead. Without this, finding someone "in the old mineshaft" would
     * drop them on the roof of the world above it.
     */
    private static final int UNDERGROUND_THRESHOLD = 8;

    public static final Codec<FindMissingRelativeObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            VillagerTarget.CODEC.fieldOf("relative").forGetter(FindMissingRelativeObjective::relative),
            BiomeTarget.MAP_CODEC.codec().optionalFieldOf("biome").forGetter(FindMissingRelativeObjective::biome),
            StructureTarget.MAP_CODEC.codec().optionalFieldOf("structure").forGetter(FindMissingRelativeObjective::structure),
            Codec.intRange(0, 4096).optionalFieldOf("min_distance", 96).forGetter(FindMissingRelativeObjective::minDistance),
            Codec.intRange(1, 64).optionalFieldOf("discover_radius", 24).forGetter(FindMissingRelativeObjective::discoverRadius),
            Codec.intRange(1, 64).optionalFieldOf("spawn_distance", 12).forGetter(FindMissingRelativeObjective::spawnDistance)
    ).apply(instance, FindMissingRelativeObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.FIND_MISSING_RELATIVE;
    }

    @Override
    public Component describe() {
        return describeWith(relative.describe());
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ServerLevel level) {
        return describeWith(relative.describeResolved(player, active, level));
    }

    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        return describeWith(ObjectiveSupport.describeLocked(relative, player, active, progress, level));
    }

    /** "Find Hans" on its own, or "Find Hans — search the Forest" when a place was named. */
    private Component describeWith(Component name) {
        Optional<Component> place = searchPlace();
        return place.<Component>map(p -> Component.translatable("mcaquests.objective.find_missing_relative_in", name, p))
                .orElseGet(() -> Component.translatable("mcaquests.objective.find_missing_relative", name));
    }

    private Optional<Component> searchPlace() {
        if (biome.isPresent()) {
            return Optional.of(biome.get().describe());
        }
        return structure.map(StructureTarget::describe);
    }

    @Override
    public VillagerTarget targetSelector() {
        return relative;
    }

    @Override
    public Optional<LivingEntity> highlightTarget(ServerPlayer player, ActiveQuest active,
                                                  ObjectiveProgress progress, ServerLevel level) {
        // Deliberately keeps highlighting after completion: the whole point is that the player can find
        // their way back to the villager they just found, and later stages of the arc lead them home.
        return ObjectiveSupport.resolveLocked(relative, player, active, progress, level);
    }

    /**
     * The person once they have been materialised, and the place to search for them until then.
     *
     * <p>The inherited {@link VillagerTargeted} answer is only half of this objective. Before the
     * relative is placed there is no entity anywhere to point at, and the player has been told to
     * search "the old mineshaft" with no way to find one — which is the state the objective spends
     * almost all of its time in. So the marker is the structure or biome the search is anchored to
     * until somebody is actually out there, and the person afterwards.
     *
     * <p>Unlike every other implementor this keeps answering once satisfied, for the same reason
     * {@link #highlightTarget} does: the point of finding someone is being able to walk back to them.
     */
    @Override
    public java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        java.util.Optional<net.minecraft.world.entity.LivingEntity> found =
                highlightTarget(player, active, progress, level);
        if (found.isPresent()) {
            return found.map(entity -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofEntity(entity, dev.otectus.mcaquests.quest.guidance.GuidanceKind.VILLAGER,
                    dev.otectus.mcaquests.compat.McaCompat.getVillagerDisplayName(entity)));
        }
        if (isSatisfied(player, progress)) {
            return java.util.Optional.empty();
        }
        java.util.Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> area = structure.flatMap(target -> dev.otectus.mcaquests.quest.guidance.LocateCache
                .resolve(progress, "searchStruct", level,
                        () -> target.locate(level, player.blockPosition(), SEARCH_CHUNKS))
                .map(pos -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(pos, level, dev.otectus.mcaquests.quest.guidance.GuidanceKind.STRUCTURE,
                        target.describe(), discoverRadius, true)));
        if (area.isPresent()) {
            return area;
        }
        return biome.flatMap(target -> dev.otectus.mcaquests.quest.guidance.LocateCache
                .resolve(progress, "searchBiome", level,
                        () -> target.locate(level, player.blockPosition(), SEARCH_BLOCKS))
                .map(pos -> dev.otectus.mcaquests.quest.guidance.GuidanceTarget.ofPos(pos, level, dev.otectus.mcaquests.quest.guidance.GuidanceKind.BIOME,
                        target.describe(), discoverRadius, true)));
    }

    /** Chunks a structure search may walk, and blocks a biome search may sample. */
    private static final int SEARCH_CHUNKS = 100;
    private static final int SEARCH_BLOCKS = 3200;

    @Override
    public int required() {
        return 1;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), 1);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= 1;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * Per-second poll: complete when the bound relative is in the world and close by, materialising them
     * first if the player has reached a plausible place to find them. Cheap — a biome lookup and a couple
     * of distance checks — as {@link PollingObjective} requires; the one expensive step (the spawn) happens
     * at most once, behind the {@code count} latch.
     */
    @Override
    public boolean poll(ServerPlayer player, ActiveQuest quest, ObjectiveProgress progress) {
        if (progress.count() >= 1 || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        UUID bound = progress.targetUuid();
        if (bound == null) {
            // Not bound at accept (a non-MCA giver, or no such relative in the tree) — nothing to find.
            return false;
        }
        Entity existing = level.getEntity(bound);
        if (existing != null) {
            if (existing.isAlive() && ObjectiveSupport.withinRadius(player, existing, discoverRadius)) {
                progress.setCount(1);
                return true;
            }
            return false; // already in the world, but not found yet — the highlight leads the way
        }
        // "Is this person actually missing?" is asked once, in one place. This used to be an inline
        // getEntity/roll pair that omitted the deceased and probablyGenerated checks and leaned on
        // materializeRelative to refuse — which worked, but meant two definitions of missing that could
        // drift. Empty means MCA could not be read: pause, never spawn.
        if (!McaCompat.describeVillager(level, level.getEntity(quest.villagerUuid()), bound)
                .map(RelativeCandidate::isMissing).orElse(false)) {
            // Alive and on a village roll, just not loaded — they are not missing, so never spawn a second
            // copy. The player has to travel to them; the HUD hint points the way.
            return false;
        }
        if (!isSearching(player, quest, level)) {
            return false;
        }
        if (McaCompat.materializeRelative(level, bound, spawnPos(player, level, bound)).isEmpty()) {
            return false; // spawn refused or MCA unavailable — pause and retry, never fail
        }
        progress.setCount(1);
        return true;
    }

    /** True when the player is somewhere the missing relative could plausibly be found. */
    private boolean isSearching(ServerPlayer player, ActiveQuest quest, ServerLevel level) {
        BlockPos pos = player.blockPosition();
        if (biome.isPresent() && !biome.get().matches(level.getBiome(pos))) {
            return false;
        }
        if (structure.isPresent() && !structure.get().matches(level, pos)) {
            return false;
        }
        // Far enough from the giver to count as "out searching". An unloaded giver means the player has
        // travelled well away already, so the check passes.
        Entity giver = level.getEntity(quest.villagerUuid());
        return giver == null || !ObjectiveSupport.withinRadius(player, giver, minDistance);
    }

    /**
     * Where the relative appears: {@link #spawnOffset} blocks away on a stable bearing, dropped onto the
     * surface — unless the surface is far above the player, in which case they are underground and the
     * relative belongs at their level (a mineshaft search should not put the child on the hillside above).
     */
    private BlockPos spawnPos(ServerPlayer player, ServerLevel level, UUID bound) {
        Vec3iOffset offset = spawnOffset(seedFor(player, bound), spawnDistance);
        BlockPos flat = player.blockPosition().offset(offset.x(), 0, offset.z());
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, flat);
        return surface.getY() - player.getBlockY() > UNDERGROUND_THRESHOLD ? flat : surface;
    }

    /** Stable per-(player, relative) seed, so the same search never lands them in a different direction. */
    private static long seedFor(ServerPlayer player, UUID bound) {
        return player.getUUID().hashCode() * 31L + bound.hashCode() * 17L;
    }

    /**
     * A horizontal offset {@code distance} blocks out on a bearing derived from {@code seed}. Pure and
     * seed-stable so a reconnect mid-search cannot teleport the relative to the other side of the player.
     */
    public static Vec3iOffset spawnOffset(long seed, int distance) {
        // (bits >>> 11) * 2^-53 is how a double in [0, 1) is built from 53 random bits.
        double angle = (mix(seed) >>> 11) * 0x1.0p-53 * Math.PI * 2.0D;
        return new Vec3iOffset((int) Math.round(Math.cos(angle) * distance),
                (int) Math.round(Math.sin(angle) * distance));
    }

    /**
     * SplitMix64's finaliser, used instead of {@code new Random(seed).nextDouble()}.
     *
     * <p>That idiom looks equivalent but is not: {@link Random}'s linear congruential generator barely
     * mixes the low bits of a seed into its <em>first</em> output, so nearby seeds all yield ~0.731 and
     * every relative would be found on the same bearing. This avalanches the whole seed before use.
     */
    private static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A horizontal (x, z) offset — a tiny value type so {@link #spawnOffset} stays unit-testable. */
    public record Vec3iOffset(int x, int z) {
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        String prefix = "Quest '" + questId + "': objective[" + index + "]";
        relative.validate(prefix + " relative", errors);
        if (relative.mode() == VillagerTarget.Mode.SELF) {
            errors.add(prefix + " uses villager mode 'self' for 'relative', but the quest giver is never the"
                    + " one who is missing.");
        }
        structure.ifPresent(s -> s.validate(prefix, errors));
        biome.ifPresent(b -> b.validate(prefix + " biome", errors));
    }
}
