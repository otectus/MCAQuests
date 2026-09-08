package dev.otectus.mcaquests.quest.objective;

import dev.otectus.mcaquests.data.StrictCodecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.CompatRegistry;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import dev.otectus.mcaquests.quest.target.SourceHint;
import dev.otectus.mcaquests.state.ActiveQuest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Use an item a number of times. Credited by {@code QuestProgressEvents} from a right-click, or from
 * a completed use when {@code require_success} is set.
 *
 * <p>The item is held as a plain {@link ResourceLocation} rather than an {@code ItemTarget}, because
 * the quests that need this objective are the compatibility ones: a Dragon Seeker exists on one build
 * of Ice &amp; Fire and not the other. A strict item codec would fail the whole definition on an
 * install without it, so the id is kept as written and the objective reports itself unavailable
 * instead — the quest keeps its title, its progress and its place in the log.
 */
public record UseItemObjective(ResourceLocation item, int count, boolean requireSuccess,
                               Optional<SourceHint> source) implements QuestObjective {

    /**
     * The shape an add-on is most likely to build in code: an item and a count, credited on a plain
     * right-click and drawing no marker. Kept as a constructor for the same reason the other
     * objectives keep theirs — a record's canonical constructor is a source break every time a
     * component is added.
     */
    public UseItemObjective(ResourceLocation item, int count) {
        this(item, count, false, Optional.empty());
    }

    public static final Codec<UseItemObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(UseItemObjective::item),
            StrictCodecs.strictOptional(ExtraCodecs.POSITIVE_INT, "count", 1).forGetter(UseItemObjective::count),
            StrictCodecs.strictOptional(Codec.BOOL, "require_success", false).forGetter(UseItemObjective::requireSuccess),
            SourceHint.FIELD.forGetter(UseItemObjective::source)
    ).apply(instance, UseItemObjective::new));

    @Override
    public QuestObjectiveType<?> type() {
        return ObjectiveTypes.USE_ITEM;
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.objective.use_item", itemName());
    }

    /**
     * With progress in hand a multi-use objective says how far along it is, because "Use the Dragon
     * Seeker" three times over is otherwise indistinguishable from having done nothing.
     */
    @Override
    public Component describe(ServerPlayer player, ActiveQuest active, ObjectiveProgress progress,
                              ServerLevel level) {
        if (count <= 1) {
            return describe();
        }
        return Component.translatable("mcaquests.objective.use_item.count", itemName(),
                current(player, progress), count);
    }

    /**
     * Where the thing this asks for can actually be got, when the pack said. Nothing is inferred; see
     * {@link SourceHint}.
     */
    @Override
    public Optional<dev.otectus.mcaquests.quest.guidance.GuidanceTarget> guidance(
            ServerPlayer player, ActiveQuest active, ObjectiveProgress progress, ServerLevel level) {
        if (isSatisfied(player, progress)) {
            return Optional.empty();
        }
        return source.flatMap(hint -> hint.guidance(player, active, progress, level));
    }

    @Override
    public void validate(ResourceLocation questId, int index, java.util.List<String> errors) {
        source.ifPresent(hint ->
                hint.validate("Quest '" + questId + "': objective[" + index + "]", errors));
    }

    /**
     * An item this world has never heard of cannot be used, so the quest is never offered — and a
     * copy already accepted suspends rather than fails, keeping its count for the day the mod that
     * owns the item comes back.
     */
    @Override
    public Optional<Component> unofferableReason(QuestContext context) {
        return missing();
    }

    @Override
    public Optional<Component> unavailableReason(ServerPlayer player, ActiveQuest active,
                                                 ObjectiveProgress progress, ServerLevel level) {
        return missing();
    }

    private Optional<Component> missing() {
        return isRegistered() ? Optional.empty() : Optional.of(CompatRegistry.get().describeMissing(item));
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

    @Override
    public ItemStack icon() {
        return resolved().map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** True when the used stack is the item this asks for. */
    public boolean matches(ItemStack stack) {
        return resolved().map(stack::is).orElse(false);
    }

    /**
     * Whether releasing {@code released} after {@code charge} ticks actually loosed a projectile.
     *
     * <p>Neither weapon that shoots arrows ends its use with {@code LivingEntityUseItemEvent.Finish}. A
     * drawn bow ends on {@code Stop} when it is let go, and a crossbow declares {@code useOnRelease},
     * so its use never completes at all: loading ends on {@code Stop} and the shot comes later, out of
     * any use. {@code require_success} on either would otherwise never credit.
     *
     * <p>For a bow the two things vanilla itself asks before firing are asked here too: there has to be
     * ammunition, and the draw has to have reached {@link BowItem#getPowerForTime(int)} of {@code 0.1}
     * — a tap of the button flings nothing and is not a use. A crossbow was charged long before it
     * fired and its shot always reports a charge of {@code 1}, which would fail that draw test, so
     * ammunition is the only thing asked of it.
     */
    public static boolean loosedArrow(ItemStack released, boolean hasAmmo, int charge) {
        if (released.getItem() instanceof CrossbowItem) {
            return hasAmmo;
        }
        return hasAmmo && BowItem.getPowerForTime(charge) >= 0.1F;
    }

    private boolean isRegistered() {
        return resolved().isPresent();
    }

    /**
     * The registered item, if this world has one. Asked of the vanilla registry rather than
     * {@code ForgeRegistries.ITEMS} because the latter is defaulted: an unknown id there answers with
     * air, which would read as "registered" and quietly match nothing.
     */
    private Optional<Item> resolved() {
        return BuiltInRegistries.ITEM.getOptional(item);
    }

    /** The item's own name when this world has it, and the "unavailable" line when it does not. */
    private Component itemName() {
        return resolved().<Component>map(Item::getDescription)
                .orElseGet(() -> Component.translatable("mcaquests.target.unavailable", item.toString()));
    }
}
