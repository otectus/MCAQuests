package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadProfessionTrackView;
import dev.otectus.mcaquests.compat.TownsteadTarget;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.TownsteadNames;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.TownsteadTargetResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * True when a profession's progression can actually take a villager where a quest wants them to go
 * (spec §5.1).
 *
 * <pre>{@code
 * {
 *   "type": "mcaquests:townstead_profession_track",
 *   "target": "giver",
 *   "profession": "minecraft:farmer",
 *   "minimum_max_tier": 3,
 *   "minimum_remaining_xp": 150,
 *   "missing": false
 * }
 * }</pre>
 *
 * <p><b>Why this exists.</b> Townstead answers {@code spec(profession)} for every profession, handing
 * back a zero/default progression for ones it has no track for. MCA: Quests 1.4.0 could not tell that
 * apart from a real track, so it offered a fisherman 120 profession XP that no Townstead work task
 * ever awards; the quest was accepted and then waited forever. This condition is how a definition
 * proves, against the <em>loaded</em> registry, that its goal is reachable before it is ever offered.
 *
 * <p>It is deliberately not a whitelist of professions. Townstead supports datapack-provided
 * progression definitions, so a pack that adds a real fisherman track makes fisherman content
 * eligible with no code change here — and a pack that removes one makes it ineligible again.
 *
 * <p>With no {@code profession}, the condition asks about whatever the target currently practises,
 * which is what pairs it with {@code townstead_profession_progress}'s own "the trade they were doing
 * when you accepted" semantics.
 */
public record TownsteadProfessionTrackCondition(TownsteadTarget target, Optional<String> profession,
                                                OptionalInt minimumMaxTier,
                                                OptionalInt minimumRemainingXp,
                                                boolean missing) implements QuestCondition {

    public static final Codec<TownsteadProfessionTrackCondition> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    StrictCodecs.strictOptional(TownsteadTarget.CODEC, "target", TownsteadTarget.GIVER)
                            .forGetter(TownsteadProfessionTrackCondition::target),
                    StrictCodecs.strictOptional(Codec.STRING, "profession")
                            .forGetter(TownsteadProfessionTrackCondition::profession),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_max_tier")
                            .forGetter(condition -> box(condition.minimumMaxTier)),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "minimum_remaining_xp")
                            .forGetter(condition -> box(condition.minimumRemainingXp)),
                    StrictCodecs.strictOptional(Codec.BOOL, "missing", false)
                            .forGetter(TownsteadProfessionTrackCondition::missing)
            ).apply(instance, (target, profession, tier, xp, missing) ->
                    new TownsteadProfessionTrackCondition(target, profession, unbox(tier), unbox(xp),
                            missing)));

    private static Optional<Integer> box(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static OptionalInt unbox(Optional<Integer> value) {
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.TOWNSTEAD_PROFESSION_TRACK;
    }

    @Override
    public boolean test(QuestContext context) {
        TownsteadEvaluation evaluation = context.mca().townstead();
        // An unreadable registry is "cannot tell", not "no track": it takes the definition's own
        // `missing` answer, which defaults to false so unproven content hides rather than misleads.
        if (!TownsteadEvaluation.has(TownsteadCapability.READ_PROFESSION_SPEC)) {
            return missing;
        }

        String wanted = profession.orElse(null);
        int currentXp = 0;
        if (wanted == null) {
            TownsteadVillagerView view = read(context, evaluation);
            if (view == null || !view.hasProfession()) {
                return missing;
            }
            wanted = view.professionId();
            currentXp = view.professionXp();
        } else if (minimumRemainingXp.isPresent()) {
            // Only an explicit-profession remaining-XP test needs the villager's standing; skipping
            // the read otherwise keeps a pure "does this track exist" gate free of a target resolve.
            TownsteadVillagerView view = read(context, evaluation);
            if (view != null && view.professionId().equalsIgnoreCase(wanted)) {
                currentXp = view.professionXp();
            }
        }

        TownsteadProfessionTrackView track = evaluation.professionTrack(wanted);
        if (!track.progressive()) {
            return false; // proven impossible, whatever `missing` says
        }
        if (minimumMaxTier.isPresent() && !track.supportsTier(minimumMaxTier.getAsInt())) {
            return false;
        }
        return minimumRemainingXp.isEmpty()
                || track.supportsXpDelta(currentXp, minimumRemainingXp.getAsInt());
    }

    @Nullable
    private TownsteadVillagerView read(QuestContext context, TownsteadEvaluation evaluation) {
        Entity entity = TownsteadTargetResolver
                .resolveForOffer(target, context.player(), context.villager(), context.level())
                .orElse(null);
        return entity == null ? null : evaluation.villager(entity).orElse(null);
    }

    @Override
    public Component describe() {
        return profession.isEmpty()
                ? Component.translatable("mcaquests.condition.townstead_profession_track_any",
                        minimumMaxTier.orElse(1))
                : Component.translatable("mcaquests.condition.townstead_profession_track",
                        TownsteadNames.profession(profession.get()), minimumMaxTier.orElse(1));
    }
}
