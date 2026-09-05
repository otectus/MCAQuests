package dev.otectus.mcaquests.quest.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.bountiful.BountifulBridge;
import dev.otectus.mcaquests.compat.bountiful.BountifulCompat;
import dev.otectus.mcaquests.compat.bountiful.BountyCompletion;
import dev.otectus.mcaquests.compat.bountiful.BountyRarity;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.guidance.GuidanceTarget;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Optional;

/**
 * Cash in a number of Bountiful bounties, optionally of at least a given rarity.
 *
 * <p>The one objective in this mod that cannot work on its own. Everything else counts something
 * Minecraft or MCA already reports; this counts an event only a guarded hook into another mod can
 * see, so it says plainly when it cannot be advanced rather than sitting at zero. Both reasons are
 * separate because they fail separately: without the cash-in hook nothing is countable at all, and
 * without the rarity reader a {@code min_rarity} cannot be honoured even though plain counting still
 * works.
 *
 * <p>Naming an unmet minimum as "unavailable" rather than quietly ignoring it is the whole design.
 * Treating an unreadable rarity as {@link BountyRarity#UNKNOWN} and letting it pass would satisfy
 * "complete a rare contract" with the easiest bounty on the board, which is a wrong answer nobody
 * would ever notice.
 *
 * @param count     how many bounties must be cashed in
 * @param minRarity the lowest rank that counts, when the pack set one
 * @param source    where the pack says bounties are found, for the marker; nothing is inferred
 */
public record BountifulBountiesObjective(int count, Optional<BountyRarity> minRarity,
                                         Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape an add-on is most likely to build in code: a count, any rarity, no marker. Kept as a
     * constructor for the reason every other objective keeps one — adding a record component is a
     * source break for every caller of the canonical constructor.
     */
    public BountifulBountiesObjective(int count) {
        this(count, Optional.empty(), Optional.empty());
    }

    /**
     * Bountiful's own rank names, case-insensitively, and nothing else.
     *
     * <p>{@link BountyRarity#UNKNOWN} is rejected rather than accepted: it is this mod's word for "we
     * could not read it", not a rank a pack can ask for, and a quest requiring an unknown rarity could
     * never be satisfied by anything. Failing at parse time says so to whoever wrote the pack, which
     * is the only moment anybody is in a position to fix it.
     */
    private static final MapCodec<Optional<BountyRarity>> MIN_RARITY =
            Codec.STRING.optionalFieldOf("min_rarity").flatXmap(
                    written -> written.isEmpty()
                            ? DataResult.success(Optional.empty())
                            : rarityOf(written.get()).map(Optional::of),
                    rarity -> DataResult.success(rarity.map(BountyRarity::name)));

    /**
     * Read as a string and converted here rather than through an {@code optionalFieldOf} over a
     * validating codec, because DataFixerUpper turns a failed optional field into an absent one: a
     * misspelt rarity would silently become "any rarity at all", which is the loudest possible wrong
     * answer arriving completely quietly.
     */
    private static DataResult<BountyRarity> rarityOf(String name) {
        BountyRarity rarity = BountyRarity.fromName(name);
        return rarity == BountyRarity.UNKNOWN
                ? DataResult.error(() -> "Unknown Bountiful rarity: " + name)
                : DataResult.success(rarity);
    }

    public static final Codec<BountifulBountiesObjective> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1)
                            .forGetter(BountifulBountiesObjective::count),
                    MIN_RARITY.forGetter(BountifulBountiesObjective::minRarity),
                    SourceHint.FIELD.forGetter(BountifulBountiesObjective::source)
            ).apply(instance, BountifulBountiesObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.BOUNTIFUL_BOUNTIES;
    }

    @Override
    public Component describe() {
        return withRarity(Component.translatable("mcaquests.objective.bountiful_bounties", count));
    }

    /** With progress in hand, say how far along it is; a part-finished contract otherwise reads as none. */
    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        if (count <= 1) {
            return describe();
        }
        return withRarity(Component.translatable("mcaquests.objective.bountiful_bounties.count", count,
                current(player, progress), count));
    }

    /** Where bounties are taken from, when the pack said. Nothing is inferred; see {@link SourceHint}. */
    @Override
    public Optional<GuidanceTarget> guidance(ServerPlayer player, ActiveQuest active,
                                             ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return Optional.empty();
        }
        return source.flatMap(hint -> hint.guidance(player, active, progress, level));
    }

    @Override
    public void validate(ResourceLocation questId, int index, List<String> errors) {
        source.ifPresent(hint ->
                hint.validate("Quest '" + questId + "': objective[" + index + "]", errors));
    }

    /**
     * Not offered when the installation cannot count what it asks for, so a quest is never handed to a
     * player who could not finish it.
     */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return unsupported();
    }

    /**
     * Suspended, not failed, when the hook or the rarity reader goes away — the count and the quest
     * survive a Bountiful update or a mode change and resume when it comes back.
     */
    @Override
    public Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                 ObjectiveProgress progress, ServerLevel level) {
        return unsupported();
    }

    private Optional<Component> unsupported() {
        BountifulBridge bridge = BountifulCompat.bridge();
        if (!bridge.has(BountifulBridge.Capability.CASH_IN_HOOK)) {
            return Optional.of(Component.translatable("mcaquests.objective.unavailable.bountiful_hook"));
        }
        if (minRarity.isPresent() && !bridge.has(BountifulBridge.Capability.READ_RARITY)) {
            return Optional.of(Component.translatable("mcaquests.objective.unavailable.bountiful_rarity"));
        }
        return Optional.empty();
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public int current(ServerPlayer player, ObjectiveProgress progress) {
        return Math.min(progress.count(), count);
    }

    @Override
    public boolean isSatisfied(ServerPlayer player, ObjectiveProgress progress) {
        return progress.count() >= count;
    }

    @Override
    public boolean isEventDriven() {
        return true;
    }

    /**
     * True when {@code completion} is a bounty this objective counts.
     *
     * <p>A completion whose rarity could not be read never satisfies a minimum, because
     * {@link BountyRarity#atLeast} says so — which is why an unreadable rarity cannot quietly count as
     * the easiest one.
     */
    public boolean matches(BountyCompletion completion) {
        return minRarity.isEmpty() || completion.parsedRarity().atLeast(minRarity.get());
    }

    /** Adds the "of X rarity or better" clause, when there is one. */
    private Component withRarity(Component base) {
        return minRarity
                .<Component>map(rarity -> Component.translatable(
                        "mcaquests.objective.bountiful_bounties.min_rarity", base,
                        Component.translatable(rarity.translationKey())))
                .orElse(base);
    }
}
