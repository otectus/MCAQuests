package dev.otectus.mcaquests.compat.iceandfire;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Every Ice &amp; Fire id MCA: Quests has an opinion about, grouped by what that opinion is.
 *
 * <p><b>This is an expectation and diagnostic catalog, never an assertion that an id exists.</b>
 * Nothing here is consulted to decide whether content is available — {@link IceAndFireCapabilities}
 * asks the live registry for that, and the answer may disagree with any group below without anything
 * being wrong. Two mods share the id {@code iceandfire} and either may add or drop content in a
 * point release; a manifest that was treated as truth would turn every such release into a crash or a
 * silently broken quest. What the manifest is for is saying <em>which</em> ids we looked for, so
 * {@code /mcaquests compat iceandfire status} can name the missing ones.
 *
 * <p>Group membership is checked by {@code IceAndFireManifestTest} and the two groups a quest may
 * name — {@link #QUEST_SAFE_ENTITIES} and {@link #EXCLUDED_TECHNICAL} — are proved disjoint there.
 */
public final class IceAndFireRegistryManifest {

    /** Shared by both builds of the mod, which is the whole problem {@link IceAndFireFlavor} solves. */
    public static final String MOD_ID = "iceandfire";

    // --- entities --------------------------------------------------------------------------------

    /** The three dragons. Present in both builds and the anchor of {@code iceandfire.core}. */
    public static final List<ResourceLocation> DRAGONS = List.of(
            id("fire_dragon"),
            id("ice_dragon"),
            id("lightning_dragon"));

    /**
     * Entities a quest may legitimately target: they spawn in the world, can be killed and are not a
     * rendering or projectile helper. The dragons lead the list; everything after them is a mob a
     * player can meet.
     */
    public static final List<ResourceLocation> QUEST_SAFE_ENTITIES = List.of(
            id("fire_dragon"),
            id("ice_dragon"),
            id("lightning_dragon"),
            id("hippogryph"),
            id("amphithere"),
            id("siren"),
            id("hippocampus"),
            id("sea_serpent"),
            id("gorgon"),
            id("cyclops"),
            id("deathworm"),
            id("cockatrice"),
            id("stymphalian_bird"),
            id("troll"),
            id("hydra"),
            id("ghost"),
            id("pixie"),
            id("dread_thrall"),
            id("dread_ghoul"),
            id("dread_beast"),
            id("dread_scuttler"),
            id("dread_lich"),
            id("dread_knight"),
            id("dread_horse"));

    /**
     * Registered entities a quest must never name: multiparts, projectiles, eggs, dropped skulls and
     * the petrified-player statue. Killing one is either impossible or an implementation detail of
     * killing something else, so an objective aimed at one can never complete.
     */
    public static final List<ResourceLocation> EXCLUDED_TECHNICAL = List.of(
            id("stone_statue"),
            id("dragon_multipart"),
            id("multipart"),
            id("hydra_multipart"),
            // Upstream spelling. Both builds register "cylcops_multipart" with the l and y swapped;
            // correcting it here would mean looking for an id that does not exist.
            id("cylcops_multipart"),
            id("dragon_egg"),
            id("dragon_arrow"),
            id("dragon_skull"),
            id("fire_dragon_charge"),
            id("ice_dragon_charge"),
            id("lightning_dragon_charge"),
            id("hippogryph_egg"),
            id("deathworm_egg"),
            id("cockatrice_egg"),
            id("stymphalian_feather"),
            id("stymphalian_arrow"),
            id("amphithere_arrow"),
            id("sea_serpent_bubbles"),
            id("sea_serpent_arrow"),
            id("chain_tie"),
            id("pixie_charge"),
            id("tide_trident"),
            id("mob_skull"),
            id("dread_lich_skull"),
            id("hydra_breath"),
            id("hydra_arrow"),
            id("ghost_sword"));

    /**
     * The Myrmex hive. Present in the original mod only — Community Edition removed it — which is
     * exactly why it is a capability rather than an assumption: a future CE release may bring it back
     * and the registry, not this list, will be what notices.
     */
    public static final List<ResourceLocation> MYRMEX_ENTITIES = List.of(
            id("myrmex_worker"),
            id("myrmex_soldier"),
            id("myrmex_queen"),
            id("myrmex_royal"),
            id("myrmex_sentinel"),
            id("myrmex_swarmer"),
            id("myrmex_egg"));

    /** The seven undead of the Dread army; all or nothing, because a dread quest names several. */
    public static final List<ResourceLocation> DREAD_MOBS = List.of(
            id("dread_thrall"),
            id("dread_ghoul"),
            id("dread_beast"),
            id("dread_scuttler"),
            id("dread_lich"),
            id("dread_knight"),
            id("dread_horse"));

    // --- items -----------------------------------------------------------------------------------

    /** Community Edition's dragon-locating items, in ascending rarity. */
    public static final List<ResourceLocation> DRAGON_SEEKERS = List.of(
            id("dragon_seeker"),
            id("epic_dragon_seeker"),
            id("legendary_dragon_seeker"));

    /**
     * The fourth seeker, kept out of {@link #DRAGON_SEEKERS} on purpose: it is a creative/endgame
     * item and no shipped quest may ever require it.
     */
    public static final ResourceLocation GODLY_DRAGON_SEEKER = id("godly_dragon_seeker");

    /**
     * The four Netherite dragon armor pieces.
     *
     * <p>These ids are <em>derived</em>, not read: CE generates dragon armor from a material enum
     * crossed with a body part, so {@code dragonarmor_netherite_head} is what that generator should
     * produce but no source file contains the string. The runtime registry probe is authoritative —
     * if the generator's naming ever changes, the capability reports absent and no quest is offered,
     * which is the correct failure.
     */
    public static final List<ResourceLocation> NETHERITE_DRAGON_ARMOR = List.of(
            id("dragonarmor_netherite_head"),
            id("dragonarmor_netherite_neck"),
            id("dragonarmor_netherite_body"),
            id("dragonarmor_netherite_tail"));

    /** Community Edition's Netherite hippogryph armor. A single item, unlike the dragon set. */
    public static final ResourceLocation NETHERITE_HIPPOGRYPH_ARMOR = id("netherite_hippogryph_armor");

    // --- worldgen --------------------------------------------------------------------------------

    /**
     * The three structures worth naming in a quest destination. These live in a dynamic registry, so
     * they can only be probed when a {@code RegistryAccess} is at hand.
     */
    public static final List<ResourceLocation> STRUCTURES = List.of(
            id("gorgon_temple"),
            id("graveyard"),
            id("mausoleum"));

    private IceAndFireRegistryManifest() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
