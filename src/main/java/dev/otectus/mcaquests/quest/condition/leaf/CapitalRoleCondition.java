package dev.otectus.mcaquests.quest.condition.leaf;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.otectus.mcaquests.compat.capitals.CapitalRef;
import dev.otectus.mcaquests.compat.capitals.CapitalRole;
import dev.otectus.mcaquests.compat.capitals.CapitalsBridge;
import dev.otectus.mcaquests.compat.capitals.CapitalsCompat;
import dev.otectus.mcaquests.compat.capitals.CapitalsQueries;
import dev.otectus.mcaquests.data.StrictCodecs;
import dev.otectus.mcaquests.quest.condition.ConditionTypes;
import dev.otectus.mcaquests.quest.condition.QuestCondition;
import dev.otectus.mcaquests.quest.condition.QuestConditionType;
import dev.otectus.mcaquests.quest.condition.QuestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Locale;
import java.util.Optional;

/**
 * True when the player or the quest giver holds a court office or noble rank (MCA Capitals).
 *
 * <pre>{@code
 * {"type": "mcaquests:capital_role", "subject": "player", "role": "knight", "present": false}
 * }</pre>
 *
 * <p>This is how a pack says "only for a knight of this court" or, with {@code "present": false},
 * "only for someone who is not one yet" — the gate the knighting quest opens with. There is no config
 * key for it: whether a title is required is a property of the quest, not of the server.
 *
 * <p>{@code capital} chooses which court is asked: {@code giver} (the default) is the capital the
 * giver's village belongs to, so a knight of one realm is a commoner in the next; {@code any} accepts
 * the rank held anywhere.
 */
public record CapitalRoleCondition(Subject subject, CapitalRole role, Scope capital, boolean present)
        implements QuestCondition {

    /** Whose rank is asked about. */
    public enum Subject {
        PLAYER,
        GIVER;

        public static final Codec<Subject> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown capital role subject: '" + name
                                + "' (expected player/giver)");
                    }
                },
                subject -> DataResult.success(subject.name().toLowerCase(Locale.ROOT)));
    }

    /** Which court the rank must be held in. */
    public enum Scope {
        GIVER,
        ANY;

        public static final Codec<Scope> CODEC = Codec.STRING.flatXmap(
                name -> {
                    try {
                        return DataResult.success(valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        return DataResult.error(() -> "Unknown capital scope: '" + name
                                + "' (expected giver/any)");
                    }
                },
                scope -> DataResult.success(scope.name().toLowerCase(Locale.ROOT)));
    }

    // mapCodec(...)...codec(), never create(...) chained: a Codec that is not a MapCodecCodec makes
    // DFU's dispatch look for the fields under a nested "value" key instead of inline beside "type".
    // See DispatchedCodecInlinesTest.
    public static final Codec<CapitalRoleCondition> CODEC = RecordCodecBuilder.<CapitalRoleCondition>mapCodec(
            instance -> instance.group(
                    StrictCodecs.strictOptional(Subject.CODEC, "subject", Subject.PLAYER)
                            .forGetter(CapitalRoleCondition::subject),
                    CapitalRole.CODEC.fieldOf("role").forGetter(CapitalRoleCondition::role),
                    StrictCodecs.strictOptional(Scope.CODEC, "capital", Scope.GIVER)
                            .forGetter(CapitalRoleCondition::capital),
                    StrictCodecs.strictOptional(Codec.BOOL, "present", true)
                            .forGetter(CapitalRoleCondition::present)
            ).apply(instance, CapitalRoleCondition::new))
            .flatXmap(CapitalRoleCondition::validate, CapitalRoleCondition::validate).codec();

    /**
     * Rejects a rank the subject can never hold, at reload rather than in front of a player. Capitals
     * seats villagers in offices it never grants a player and grants players titles it never seats a
     * villager in, so "is the giver an archduke?" is not a question with a false answer — it is a
     * question that was written by mistake.
     */
    private static DataResult<CapitalRoleCondition> validate(CapitalRoleCondition condition) {
        if (condition.subject == Subject.PLAYER && !condition.role.appliesToPlayer()) {
            return DataResult.error(() -> "capital_role: a player cannot hold the role '"
                    + condition.role.key() + "'");
        }
        if (condition.subject == Subject.GIVER && !condition.role.appliesToVillager()) {
            return DataResult.error(() -> "capital_role: a villager cannot hold the role '"
                    + condition.role.key() + "'");
        }
        return DataResult.success(condition);
    }

    @Override
    public QuestConditionType<?> type() {
        return ConditionTypes.CAPITAL_ROLE;
    }

    @Override
    public boolean test(QuestContext context) {
        return (capital == Scope.GIVER ? heldInGiverCapital(context) : heldAnywhere(context)) == present;
    }

    private boolean heldInGiverCapital(QuestContext context) {
        Entity giver = context.villager();
        if (giver == null || !(giver.level() instanceof ServerLevel level)) {
            return false;
        }
        return CapitalsQueries.giverCapital(giver)
                .map(cap -> holds(level, cap, context))
                .orElse(false);
    }

    private boolean heldAnywhere(QuestContext context) {
        MinecraftServer server = context.level().getServer();
        CapitalsBridge bridge = CapitalsCompat.bridge();
        for (CapitalRef cap : bridge.allCapitals()) {
            Optional<ServerLevel> level = bridge.capitalLevel(server, cap);
            if (level.isPresent() && holds(level.get(), cap, context)) {
                return true;
            }
        }
        return false;
    }

    private boolean holds(ServerLevel level, CapitalRef cap, QuestContext context) {
        CapitalsBridge bridge = CapitalsCompat.bridge();
        return subject == Subject.PLAYER
                ? bridge.playerHasRole(level, cap, context.player().getUUID(), role)
                : context.villager() != null
                        && bridge.villagerHasRole(level, cap, context.villager().getUUID(), role);
    }

    @Override
    public Component describe() {
        return Component.translatable("mcaquests.condition.capital_role",
                Component.translatable("mcaquests.target.capital_role." + role.key()));
    }
}
