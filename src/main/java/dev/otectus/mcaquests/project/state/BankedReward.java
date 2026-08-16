package dev.otectus.mcaquests.project.state;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * The banked-claim payload of a {@code kind = "banked"} {@link PendingReward} (spec 1.0.0 §16, task
 * M3.1). Represents an FTB Quests reward claim — {@code mcaquests:village_reputation} (§16.1),
 * {@code mcaquests:hearts} (§16.2) or {@code mcaquests:grant_title} (§16.3) — that had no resolvable
 * target (no village / spouse / villager in range) at claim time, banked so the reward is never silently
 * wasted; re-attempted on login and once per in-game day until a target resolves.
 *
 * <p>Deliberately FTB-agnostic: a compact {@code (type, amount, titleId+scope, target)} shape covers all
 * three claim kinds without a wide union of always-empty fields, and {@code target} carries the FTB-side
 * target enum's {@code name()} as a plain string (e.g. {@code "SPOUSE"}, {@code "VILLAGE_RESIDENTS"}) so
 * this core class never imports an FTB type; the consuming reward classes (compat.ftbq, next task) are
 * free to parse it back with their own enum.
 */
public record BankedReward(Type type, int amount, @Nullable ResourceLocation titleId,
                            String titleScope, String target) {

    public enum Type {
        REPUTATION, HEARTS, TITLE
    }

    /** {@code mcaquests:village_reputation} banked claim (§16.1): amount may be negative. */
    public static BankedReward reputation(int amount) {
        return new BankedReward(Type.REPUTATION, amount, null, "", "");
    }

    /** {@code mcaquests:hearts} banked claim (§16.2): {@code target} is the FTB target enum's name. */
    public static BankedReward hearts(int amount, String target) {
        return new BankedReward(Type.HEARTS, amount, null, "", target);
    }

    /** {@code mcaquests:grant_title} banked claim (§16.3): {@code scope} is the FTB scope enum's name. */
    public static BankedReward title(ResourceLocation titleId, String scope) {
        return new BankedReward(Type.TITLE, 0, titleId, scope, "");
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putInt("amount", amount);
        if (titleId != null) {
            tag.putString("titleId", titleId.toString());
        }
        tag.putString("titleScope", titleScope);
        tag.putString("target", target);
        return tag;
    }

    /**
     * Empty means "skip": an unrecognised (future) {@code type} (forward-compat with
     * {@link PendingReward#load}), or a {@code TITLE} entry whose {@code titleId} key is absent or
     * unparseable — a {@code TITLE} banked reward is meaningless without a title to grant, so rather
     * than construct one with a null {@code titleId} (which would NPE or misbehave downstream at
     * delivery time) this entry is dropped, same as any other corrupt/forward-incompatible entry.
     */
    public static Optional<BankedReward> load(CompoundTag tag) {
        Type type;
        try {
            type = Type.valueOf(tag.getString("type"));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        int amount = tag.getInt("amount");
        ResourceLocation titleId;
        try {
            titleId = tag.contains("titleId") ? new ResourceLocation(tag.getString("titleId")) : null;
        } catch (RuntimeException e) {
            titleId = null;
        }
        if (type == Type.TITLE && titleId == null) {
            return Optional.empty();
        }
        String titleScope = tag.getString("titleScope");
        String target = tag.getString("target");
        return Optional.of(new BankedReward(type, amount, titleId, titleScope, target));
    }
}
