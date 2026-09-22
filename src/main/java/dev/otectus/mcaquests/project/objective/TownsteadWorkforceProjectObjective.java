package dev.otectus.mcaquests.project.objective;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.McaCompat;
import dev.otectus.mcaquests.compat.TownsteadBridge;
import dev.otectus.mcaquests.compat.TownsteadCapability;
import dev.otectus.mcaquests.compat.TownsteadEvaluation;
import dev.otectus.mcaquests.compat.TownsteadVillagerView;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.project.ProjectDefinition;
import dev.otectus.mcaquests.project.state.ProjectState;
import dev.otectus.mcaquests.project.state.SharedObjectiveProgress;
import dev.otectus.mcaquests.quest.TownsteadNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

/**
 * A project phase that finishes when enough of the village can do the job (Townstead spec 5.4).
 *
 * <pre>{@code
 * { "type": "mcaquests:townstead_workforce_project",
 *   "professions": ["minecraft:farmer", "minecraft:fisherman"], "minimum_tier": 2, "count": 3 }
 * }</pre>
 *
 * <p>The tier is Townstead's <b>profession tier</b> — Novice (1) to Master (5), earned by residents
 * completing work tasks in their trade under a daily XP cap. It is not the vanilla trading level and
 * not the player's experience, and the project card now says so in those words.
 *
 * <h2>Which trades count ({@code profession_policy}, 1.7.0)</h2>
 * <ul>
 *   <li>{@code "listed"} (the default, and the behaviour every existing datapack has): only the
 *       {@code professions} named, each still required to have a Townstead track that reaches
 *       {@code minimum_tier}.</li>
 *   <li>{@code "any_progressive"}: any trade whose Townstead track reaches {@code minimum_tier}. The
 *       {@code professions} list is then only an example shown to the player. The bundled workforce
 *       projects use this, so a village of fishermen and cooks is not told to go and find farmers.</li>
 * </ul>
 * This class's documentation from 1.4.1 described the second behaviour while the code did the first; the
 * two are now separate, explicit choices, and no existing restriction was broadened.
 *
 * <p>Only loaded residents of the bound village can be read, so this counts what can be seen and keeps
 * a high-water mark: a qualified resident who wanders out of loaded range has not stopped being
 * qualified. What was observed — loaded residents, those below tier, those whose trade has no track —
 * is kept with the progress so the card can say why the count is what it is, instead of looking stuck.
 */
public record TownsteadWorkforceProjectObjective(List<String> professions, int minimumTier, int count,
                                                 ProfessionPolicy professionPolicy)
        implements TownsteadProjectObjective {

    /** Which residents' trades may count. */
    public enum ProfessionPolicy {
        LISTED, ANY_PROGRESSIVE;

        static final Codec<ProfessionPolicy> CODEC = Codec.STRING.flatXmap(raw -> switch (raw.toLowerCase(Locale.ROOT)) {
            case "listed" -> DataResult.success(LISTED);
            case "any_progressive" -> DataResult.success(ANY_PROGRESSIVE);
            default -> DataResult.error(() -> "Unknown profession_policy '" + raw
                    + "'; expected listed or any_progressive");
        }, policy -> DataResult.success(policy.name().toLowerCase(Locale.ROOT)));
    }

    static final String K_OBSERVED = "workforce_observed";
    static final String K_BELOW = "workforce_below_tier";
    static final String K_NO_TRACK = "workforce_no_track";
    static final String K_TRADES = "workforce_qualifying_trades";

    public static final MapCodec<TownsteadWorkforceProjectObjective> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    Codec.STRING.listOf().fieldOf("professions")
                            .forGetter(TownsteadWorkforceProjectObjective::professions),
                    StrictCodecs.strictOptional(ExtraCodecs.NON_NEGATIVE_INT, "minimum_tier", 1)
                            .forGetter(TownsteadWorkforceProjectObjective::minimumTier),
                    StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1)
                            .forGetter(TownsteadWorkforceProjectObjective::count),
                    StrictCodecs.strictOptional(ProfessionPolicy.CODEC, "profession_policy", ProfessionPolicy.LISTED)
                            .forGetter(TownsteadWorkforceProjectObjective::professionPolicy)
            ).apply(instance, TownsteadWorkforceProjectObjective::new));

    public TownsteadWorkforceProjectObjective {
        professions = List.copyOf(professions);
    }

    /** The pre-1.7.0 shape: listed professions only. */
    public TownsteadWorkforceProjectObjective(List<String> professions, int minimumTier, int count) {
        this(professions, minimumTier, count, ProfessionPolicy.LISTED);
    }

    @Override
    public ProjectObjectiveType<?> type() {
        return ProjectObjectiveTypes.TOWNSTEAD_WORKFORCE;
    }

    @Override
    public Set<TownsteadCapability> requiredCapabilities() {
        return Set.of(TownsteadCapability.READ_PROFESSION, TownsteadCapability.READ_PROFESSION_SPEC);
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public boolean poll(MinecraftServer server, ServerLevel level, ProjectDefinition definition,
                        ProjectState state, SharedObjectiveProgress progress) {
        OptionalInt village = state.villageId();
        TownsteadBridge bridge = TownsteadBridge.Holder.get();
        if (village.isEmpty() || !bridge.has(TownsteadCapability.READ_PROFESSION)
                || !bridge.has(TownsteadCapability.READ_PROFESSION_SPEC)) {
            // Without the track registry we cannot tell a real tier from an unreachable one, so the
            // phase waits rather than counting residents it may be lying about.
            return false;
        }
        TownsteadEvaluation evaluation = new TownsteadEvaluation();
        Tally tally = new Tally();
        for (Entity resident : McaCompat.loadedVillageResidents(level, village.getAsInt())) {
            TownsteadVillagerView view = evaluation.villager(resident).orElse(null);
            if (view == null) {
                continue;
            }
            tally.observed++;
            if (!view.hasProfession() || !eligibleTrade(view.professionId())) {
                continue;
            }
            if (!evaluation.professionTrack(view.professionId()).supportsTier(minimumTier)) {
                tally.noTrack++;
                continue;
            }
            tally.trades.add(view.professionId());
            if (view.professionLevel() >= minimumTier) {
                tally.qualified++;
            } else {
                tally.below++;
            }
        }
        tally.record(progress.extra());
        int next = Math.min(count, tally.qualified);
        if (next <= progress.count()) {
            return false; // a villager who wandered out of range has not stopped being a farmer
        }
        progress.setCount(next);
        return true;
    }

    /** Whether this trade is one the definition lets count, before asking about its track. */
    boolean eligibleTrade(String professionId) {
        return professionPolicy == ProfessionPolicy.ANY_PROGRESSIVE || listed(professionId);
    }

    private boolean listed(String professionId) {
        String id = professionId.toLowerCase(Locale.ROOT);
        for (String wanted : professions) {
            if (id.equals(wanted.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** One sweep's observation of the loaded residents. */
    private static final class Tally {
        int observed;
        int qualified;
        int below;
        int noTrack;
        final Set<String> trades = new TreeSet<>();

        void record(CompoundTag extra) {
            extra.putInt(K_OBSERVED, observed);
            extra.putInt(K_BELOW, below);
            extra.putInt(K_NO_TRACK, noTrack);
            ListTag list = new ListTag();
            trades.forEach(trade -> list.add(StringTag.valueOf(trade)));
            extra.put(K_TRADES, list);
        }
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.project.objective.townstead_workforce_tier",
                count, minimumTier, Component.translatable("townstead.profession.level." + minimumTier));
    }

    @Override
    public ProjectObjectiveStatus status(ProjectObjectiveContext context) {
        ProjectObjectiveStatus base = TownsteadProjectObjective.super.status(context);
        if (base != ProjectObjectiveStatus.IN_PROGRESS) {
            return base;
        }
        // Nothing could be seen: not a failure, just not observed yet.
        return context.progress().extra().contains(K_OBSERVED) && context.progress().extra().getInt(K_OBSERVED) == 0
                ? ProjectObjectiveStatus.UNOBSERVED : base;
    }

    @Override
    public List<Component> explain(ProjectObjectiveContext context) {
        List<Component> lines = new ArrayList<>();
        Component tierName = Component.translatable("townstead.profession.level." + minimumTier);
        Component village = context.state() != null && context.level() != null && context.state().villageId().isPresent()
                ? McaCompat.villageName(context.level(), context.state().villageId().getAsInt())
                        .<Component>map(Component::literal)
                        .orElseGet(() -> Component.translatable("mcaquests.project.help.this_village"))
                : Component.translatable("mcaquests.project.help.this_village");
        lines.add(Component.translatable("mcaquests.project.help.workforce.rule", count, village, minimumTier, tierName));
        lines.add(Component.translatable("mcaquests.project.help.workforce.not_vanilla"));
        if (professionPolicy == ProfessionPolicy.LISTED) {
            lines.add(Component.translatable("mcaquests.project.help.workforce.listed", tradeList(professions)));
        } else {
            lines.add(Component.translatable("mcaquests.project.help.workforce.any", minimumTier));
        }
        CompoundTag extra = context.progress().extra();
        if (extra.contains(K_OBSERVED)) {
            ListTag trades = extra.getList(K_TRADES, Tag.TAG_STRING);
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < trades.size(); i++) {
                seen.add(trades.getString(i));
            }
            lines.add(Component.translatable("mcaquests.project.help.workforce.observed",
                    extra.getInt(K_OBSERVED), context.progress().count(), extra.getInt(K_BELOW)));
            if (!seen.isEmpty()) {
                lines.add(Component.translatable("mcaquests.project.help.workforce.trades_seen", tradeList(seen)));
            }
            if (extra.getInt(K_NO_TRACK) > 0) {
                lines.add(Component.translatable("mcaquests.project.help.workforce.no_track",
                        extra.getInt(K_NO_TRACK), minimumTier));
            }
        } else {
            lines.add(Component.translatable("mcaquests.project.help.workforce.not_observed"));
        }
        lines.add(Component.translatable("mcaquests.project.help.workforce.how"));
        lines.add(Component.translatable("mcaquests.project.help.workforce.assign"));
        return lines;
    }

    private static Component tradeList(List<String> ids) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(TownsteadNames.profession(ids.get(i)));
        }
        return out;
    }
}
