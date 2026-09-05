package dev.otectus.mcaquests.compat.bountiful;

import dev.otectus.mcaquests.compat.CapabilityEvidence;
import dev.otectus.mcaquests.compat.CompatCapability;
import dev.otectus.mcaquests.compat.CompatStatus;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bountiful installed, read from, and given content — but not observed.
 *
 * <p>This is the bridge for the case the integration was designed around first: our bounty pools and
 * decrees are mounted for Bountiful's own loader, a quest can ask the player to visit a board, and a
 * bounty's rarity can be read off a stack, but nothing tells us when a bounty is <em>cashed in</em>.
 * Bountiful publishes no completion callback, so without the guarded hook there is genuinely nothing
 * to listen to, and pretending otherwise would leave a bounty-completion quest sitting at zero
 * forever with no explanation.
 *
 * <p>So {@link Capability#CASH_IN_HOOK} is false here, and the quests that need it are never offered.
 * Everything else still works, which is the point of having this bridge at all rather than falling
 * back to the Noop one.
 */
public class DataOnlyBountifulBridge implements BountifulBridge {

    /** Bountiful's bounty board. Its registration is what proves there is content to point a quest at. */
    private static final ResourceLocation BOUNTY_BOARD =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "bountyboard");

    /** The only item a bounty is ever on; see {@link #inspect} for why that is checked first. */
    private static final ResourceLocation BOUNTY_ITEM =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "bounty");

    private final BountifulBinding.Resolution resolution;
    private volatile boolean boardRegistered;

    protected DataOnlyBountifulBridge(BountifulBinding.Resolution resolution) {
        this.resolution = resolution;
        this.boardRegistered = probeBoard();
    }

    /** A bridge over a freshly resolved binding. */
    public static DataOnlyBountifulBridge of(BountifulBinding.Resolution resolution) {
        return new DataOnlyBountifulBridge(resolution);
    }

    @Override
    public String id() {
        return MOD_ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("mcaquests.compat.bountiful.name");
    }

    @Override
    public Set<String> namespaces() {
        return Set.of(MOD_ID);
    }

    /**
     * {@link CompatStatus#FULL} only when everything this bridge can declare is there. A missing
     * rarity reader is {@link CompatStatus#PARTIAL}, not a failure: the specialist quest is not
     * offered and every other piece of Bountiful content still works.
     */
    @Override
    public CompatStatus status() {
        for (Capability capability : Capability.values()) {
            if (!has(capability)) {
                return CompatStatus.PARTIAL;
            }
        }
        return CompatStatus.FULL;
    }

    @Override
    public List<CompatCapability> capabilities() {
        List<CompatCapability> capabilities = new ArrayList<>();
        for (Capability capability : Capability.values()) {
            capabilities.add(new CompatCapability(capability.id(), has(capability),
                    evidenceFor(capability)));
        }
        return List.copyOf(capabilities);
    }

    @Override
    public boolean has(Capability capability) {
        return switch (capability) {
            // The mod is installed, so its loader will read whatever pools and decrees we mount.
            case DATA_PACK -> true;
            case BOARD_REGISTRY -> boardRegistered;
            // Nothing observes a cash-in on this bridge; HookedBountifulBridge is the one that can.
            case CASH_IN_HOOK -> false;
            case READ_RARITY -> resolution.has(Capability.READ_RARITY);
            case READ_OBJECTIVES -> resolution.has(Capability.READ_OBJECTIVES);
        };
    }

    /**
     * Re-asks the registry for the board. The binding itself is not re-resolved here — which handles
     * bound cannot change while the game is running — but registry contents can differ between the
     * first probe during mod setup and a world that has finished loading.
     */
    @Override
    public void reprobe(@Nullable RegistryAccess access) {
        boardRegistered = probeBoard();
    }

    /**
     * The bounty on {@code stack}, or empty when there is none.
     *
     * <p>Only the rarity is read. The objective and reward lists live on the bounty object Bountiful
     * builds from a stack's data components, and the one place that ever holds one is the cash-in
     * hook, which is handed it directly. Counting from a stack would mean owning a copy of their
     * component layout, which is a promise this integration deliberately does not make.
     *
     * <p>The item id is checked before the binding is touched at all. Bountiful 8.0 builds its bounty
     * object from any stack without complaint — it reads its fields lazily — so a non-bounty stack
     * would otherwise take two reflective hops to answer "unknown", once for every right-click any
     * player makes anywhere.
     */
    @Override
    public Optional<BountySnapshot> inspect(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isBountyItem(stack)) {
            return Optional.empty();
        }
        BountyRarity rarity = resolution.rarityOf(stack);
        if (rarity == BountyRarity.UNKNOWN) {
            return Optional.empty();
        }
        return Optional.of(new BountySnapshot(rarity, 0, 0));
    }

    /**
     * How many objectives a bounty lists, or {@code 0} when the reader is not bound.
     *
     * <p>Package-private and taking an {@link Object}, because the only caller is the cash-in hook and
     * the only thing it can be handed is Bountiful's own object. It is on this bridge rather than on
     * the interface deliberately: it is not something a caller outside this package should be able to
     * ask, since nothing outside it can hold that object in the first place.
     */
    int objectiveCountOf(Object bountyStack) {
        return resolution.countsOf(bountyStack)[0];
    }

    /**
     * The rank of a bounty object, or {@link BountyRarity#UNKNOWN} when the reader is not bound.
     *
     * <p>The stack-taking {@link #inspect} cannot answer this for the hook: on Bountiful 8.0 the
     * cash-in is handed the bounty object itself, and by the time it returns the item it came from is
     * gone. Package-private for the same reason {@link #objectiveCountOf} is.
     */
    BountyRarity rarityOf(Object bountyStack) {
        return resolution.rarityOf(bountyStack);
    }

    /**
     * The stack a bounty object was read from, or {@code null} when that reader did not bind.
     *
     * <p>Only ever a dedupe key, so {@code null} is an ordinary answer the hook falls back from
     * rather than a failure.
     */
    @Nullable
    ItemStack stackOf(Object bountyStack) {
        return resolution.stackOf(bountyStack);
    }

    @Override
    public void addCompletionListener(BountifulCompletionListener listener) {
        // Handed to BountifulCompat, which owns the list for every bridge -- see
        // BountifulCompat#addCompletionListener. A listener registered here is still never called
        // while this bridge is the one in use, because nothing on it observes a cash-in.
        BountifulCompat.addCompletionListener(listener);
    }

    /** Members of the manifest that did not bind, for the status command. */
    public List<String> unresolvedMembers() {
        return resolution.unresolved();
    }

    /**
     * A registry lookup for the board and a bound handle for the readers are two different kinds of
     * proof, and the status command says which one answered.
     */
    private CapabilityEvidence evidenceFor(Capability capability) {
        return switch (capability) {
            case BOARD_REGISTRY -> CapabilityEvidence.REGISTRY_CONFIRMED;
            case DATA_PACK, CASH_IN_HOOK -> CapabilityEvidence.FLAVOR_DECLARED;
            case READ_RARITY, READ_OBJECTIVES -> CapabilityEvidence.ADAPTER_CONFIRMED;
        };
    }

    /**
     * Asked of {@code BuiltInRegistries.BLOCK} rather than of Bountiful, so the answer is about what
     * this world actually contains. Any throw is a "no": registries are not answerable at every point
     * in startup, and a probe must not be the thing that decides when they are.
     */
    private static boolean probeBoard() {
        try {
            return BuiltInRegistries.BLOCK.containsKey(BOUNTY_BOARD);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether {@code stack} is Bountiful's bounty item, asked of the registry rather than of them. */
    private static boolean isBountyItem(ItemStack stack) {
        try {
            return BOUNTY_ITEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } catch (Throwable t) {
            return false;
        }
    }
}
