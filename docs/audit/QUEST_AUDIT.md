# MCA: Quests — quest completability and consistency audit

Static audit of the bundled quest pack and the Java that drives it. Nothing was executed in-game;
every claim below is from reading source, data files, and the Forge / MCA jars in the Gradle cache.
Companion inventory: [inventory.md](inventory.md). No source was changed in this pass.

**Headline.** 262 quests: 250 COMPLETABLE, 11 RISKY, 1 BROKEN. No unresolved ids, no unknown
types, no chain gaps, no missing lang keys. The plumbing is sound: progress is server-authoritative,
capped, persisted in full, turn-in is idempotent, the ready toast fires once. The real problems are a
handful of logic gaps, listed in section 4 and ranked in section 5.

## 1 Scope and method

| Input | Source |
|---|---|
| Quest / project JSON | `src/main/resources/data/mcaquests/mcaquests/{quests,projects}/**` (262 + 21 files) |
| Vanilla registries | Forge 1.20.1-47.4.10 mapped sources jar (`Items`, `Blocks`, `EntityType`, `Biomes`, tags, structures) |
| Vanilla data | `minecraft_repo/versions/1.20.1/client-extra.jar` (loot tables, advancements, tags, biome spawners) |
| MCA Reborn | 7.6.20+1.20.1 mapped jar (lang keys, `ProfessionsMCA`, `Memories`, `Config`) |
| Townstead / MCA: Reputation / FTB Quests | no jar on disk; anything that depends on them is labelled UNVERIFIABLE |

Method: a census script produced the inventory and every mechanical check; seven reader passes judged
each quest from a digest; five code-review passes covered events, the tick loop, lifecycle and sync,
persistence, and projects/chains. Every BROKEN or Bug claim was re-verified against source before it
was graded.

Label used in verdict reasons: `UNVERIFIABLE(townstead)` means the quest depends on Townstead
internals and is covered only by `TownsteadContentValidator` / `BuiltinTownsteadAchievabilityTest`
against a live install.

## 2 Corrections to the audit brief

These change how the rest of the report should be read.

1. **33 objective types, not 10** (`quest/objective/ObjectiveTypes.java:20-104`): 8 are Townstead-only,
   1 is FTB Quests-only. Projects use a separate registry of 8 (`project/objective/ProjectObjectiveTypes.java`).
2. **Conditions are evaluated only at offer time.** The only gating call site is
   `quest/OfferFilters.java:143`; `accept()` (`QuestManager.java:460`), `canTurnInAt` (`:1248`) and
   `completeQuest` (`:838`) never re-test them. A condition therefore cannot strand or fail an accepted
   quest. "Objective vs condition contradiction" reduces to "the offer gate lets a quest through whose
   objective cannot advance". Exactly one quest has that (F-B01).
3. **Chains branch by conditions, not by next-stage fields.** `ChainSpec` (`quest/ChainSpec.java:25-31`)
   has `stage`, `stage_total`, `prerequisites`, `unlocks`; fail/abandon branches are quests gated on
   `quest_failed` / `quest_abandoned`. `unlocks` has no runtime effect (`data/QuestChainValidator.java:151-163`).
   "Part N of M" is the hand-authored `stage_total` (`ChainSpec.java:73-81`); the census confirmed it
   matches the distinct stage count in every chain.
4. **There is no `expiry` key.** Deadlines live under `failure` (`deadline_ticks`, `deadline_time`,
   `require_weather`, `fail_on_giver_death`, `fail_on_target_lost`, `failure_hearts`, `retry_after`,
   `block_retry`; `quest/FailureSpec.java:59-68`). 8 quests carry a deadline, 17 set `fail_on_giver_death`.
5. **Abandon is always possible**, including from the log when the giver is dead
   (`network/QuestAbandonFromLogC2SPacket.java:12-21`, `QuestManager.java:175`). No quest can be
   permanently stranded in the log.
6. **`once` means once per player for ordinary quests and once per giver for chain quests**
   (`QuestManager.java:1614-1618`); arcs are per-villager by design (`DATAPACK.md:857`). Failure records
   history but adds no cooldown unless `retry_after` / `block_retry` is set (`QuestManager.java:1117-1127`),
   so a failed one-time quest is re-offerable.
7. **Turn-in `DEFAULT` mode is the original giver** (`quest/TurnInSpec.java:24`). Giver death fails the
   quest only under `fail_on_giver_death` or the config `failQuestIfGiverDies` (default false,
   `McaQuestsConfig.java:178`); otherwise the quest waits for an abandon.
8. **MCA facts from the 7.6.20 jar.** Professions registered by MCA: `mca:adventurer`, `mca:archer`,
   `mca:cultist`, `mca:guard`, `mca:mercenary`, `mca:outlaw`; all four the pack uses exist. Hearts are an
   unclamped `int` (`net.mca.entity.ai.Memories.setHearts` has no bounds); MCA's own thresholds are friend
   40, engagement 50, greet 75, marriage 100, bounty hunter -150. The pack's four `hearts` conditions all
   sit inside 0-100. The mod caps hearts rewards at `maxHeartsReward` = 100 (`McaQuestsConfig.java:191`).
9. **The seven `templates/` files are ordinary offerable quests**, not expanded at load. Their rewards live
   under `template.rewards` and are substituted at accept (`QuestDefinition.java:126-132`,
   `QuestManager.java:505-528`); the census's `zero_rewards` flag on them is an artefact.
10. **`find_api.py` is unreliable for Forge event classes in this repo** (it searches NeoForge
    intermediates and reported compiling classes as missing). Not used.

## 3 Verdict table

COMPLETABLE: a survival player can finish it with reasonable effort. RISKY: completable but hinges on
luck, an unhinted structure, or a villager the quest itself endangers. BROKEN: the objective cannot
advance from the state the offer gate allows.

| Quest | Folder | Verdict | Reason |
|---|---|---|---|
| `adventurer_into_the_deep` | adventurer | COMPLETABLE | simple nether visit |
| `adventurer_relic_hunt` | adventurer | COMPLETABLE | ancient city entry title hints at the dark below |
| `adventurer_trailblazer` | adventurer | COMPLETABLE | mountain biome visit |
| `atlas_of_four_horizons` | adventurer | COMPLETABLE | 4 biome visits plus talk to cartographer all reachable |
| `drowned_ledger` | adventurer | COMPLETABLE | 8 prismarine crystals come from sea lanterns in the ocean ruins the quest already targets; dialogue could hint it |
| `echoes_below` | adventurer | COMPLETABLE | ancient city echo shards and recovery compass craft all standard survival |
| `last_banner_home` | adventurer | RISKY | vindicators only spawn via raids or woodland mansions with no hint in title/dialogue |
| `nether_relay` | adventurer | COMPLETABLE | nether visit blaze kill and rod delivery all consistent |
| `relic_beneath_the_well` | adventurer | COMPLETABLE | trail ruins sherds and decorated pot craft all obtainable |
| `trial_by_fire` | adventurer | COMPLETABLE | nether blaze kill diamond sword craft and rod delivery consistent |
| `archer_cull_from_afar` | archer | COMPLETABLE | skeleton kill count reasonable |
| `archer_fletchings` | archer | COMPLETABLE | feather delivery easily farmed from chickens |
| `archer_marksman` | archer | COMPLETABLE | skeleton kill count reasonable |
| `archer_spider_nest` | archer | COMPLETABLE | spider kill with time condition at offer only |
| `armorer_coal_for_winter` | armorer | COMPLETABLE | coal obtainable by mining or smelting |
| `armorer_copper_plating` | armorer | COMPLETABLE | copper ingot delivery from mining |
| `armorer_forge_ahead` | armorer | COMPLETABLE | two shields craftable easily |
| `armorer_iron_for_the_forge` | armorer | COMPLETABLE | iron ingot delivery from mining |
| `butcher_fresh_cuts` | butcher | COMPLETABLE | beef obtainable from cows |
| `butcher_hearty_meal` | butcher | COMPLETABLE | cooked beef deliverable |
| `butcher_poultry_run` | butcher | COMPLETABLE | cooked chicken deliverable |
| `butcher_smoked_supply` | butcher | COMPLETABLE | cooked porkchop deliverable |
| `cartographer_edge_of_the_world` | cartographer | COMPLETABLE | end visit requires stronghold and eyes of ender but title hints at far travel |
| `cartographer_guide_the_surveyor` | cartographer | COMPLETABLE | escort with fail_on_giver_death is fair since escort target is the giver |
| `cartographer_high_ground` | cartographer | COMPLETABLE | mountain biome visit |
| `cartographer_scout_the_land` | cartographer | COMPLETABLE | forest biome visit rewarding a map |
| `cartographer_spare_parchment` | cartographer | COMPLETABLE | paper delivery easily farmed from sugar cane |
| `aging_parent_1_warm_meal` | chains | COMPLETABLE | stage1 cold-start ok deliver bread |
| `aging_parent_2_remedy` | chains | COMPLETABLE | heal_entity golden carrot obtainable |
| `aging_parent_3_last_walk` | chains | COMPLETABLE | fail_on_giver_death with retry_after; failure is recoverable |
| `bell_shields_for_neighbors` | chains | COMPLETABLE | stage1 cold-start ok delivery |
| `bell_the_lantern_line` | chains | COMPLETABLE | build_near_location obtainable materials |
| `bell_watch_before_dawn` | chains | COMPLETABLE | defend_location no deadline so cannot fail |
| `bell_when_the_horns_answer` | chains | RISKY | finale fails on giver death; arc is per-villager so it must be redone from stage 1 with another giver |
| `courting_1_first_gift` | chains | COMPLETABLE | stage1 cold-start hearts>=15 gate reasonable |
| `courting_2_walk_together` | chains | COMPLETABLE | escort with retry_after recovers on fail |
| `courting_3_meet_the_family` | chains | COMPLETABLE | deliver cake to related villager |
| `courting_4_the_proposal` | chains | COMPLETABLE | gold ingot delivery finale no failure risk |
| `farmer_family_1_wheat` | chains | COMPLETABLE | stage1 cold-start delivery |
| `farmer_family_2_expand` | chains | COMPLETABLE | bone meal delivery |
| `farmer_family_3_apprentice` | chains | COMPLETABLE | deadline+retry_after recovers on fail |
| `farmer_family_4_feast` | chains | COMPLETABLE | cake delivery finale |
| `guard_safety_1_clear` | chains | COMPLETABLE | stage1 cold-start kill zombies |
| `guard_safety_2_patrol` | chains | COMPLETABLE | branches to 2b on fail so chain not dead |
| `guard_safety_2b_amends` | chains | COMPLETABLE | fail-branch of stage2 reachable via quest_failed |
| `guard_safety_3_militia` | chains | COMPLETABLE | any_of(2 |
| `jobless_friendship_1_errand` | chains | COMPLETABLE | stage1 cold-start obtain apples |
| `jobless_friendship_2_favor` | chains | COMPLETABLE | branches to 2b on abandon |
| `jobless_friendship_2b_understanding` | chains | COMPLETABLE | abandon-branch reachable via quest_abandoned |
| `jobless_friendship_3_friend` | chains | COMPLETABLE | any_of(2 |
| `librarian_knowledge_1_ink` | chains | COMPLETABLE | stage1 cold-start obtain ink sacs |
| `librarian_knowledge_2_lost_book` | chains | COMPLETABLE | obtain books |
| `librarian_knowledge_3_share` | chains | COMPLETABLE | hearts>=40 gate plausible by stage3 |
| `lost_child_1_trail` | chains | COMPLETABLE | stage1 cold-start visit forest biome |
| `lost_child_2_deeper` | chains | COMPLETABLE | find_missing_relative+escort branches to 2b on fail |
| `lost_child_2b_cold_trail` | chains | COMPLETABLE | fail-branch reachable via quest_failed |
| `lost_child_3_homecoming` | chains | COMPLETABLE | any_of(2 |
| `mapmaker_expedition_1_survey` | chains | COMPLETABLE | stage1 cold-start paper delivery |
| `mapmaker_expedition_2_expedition` | chains | COMPLETABLE | branches to 2b on fail |
| `mapmaker_expedition_2b_salvage` | chains | COMPLETABLE | fail-branch reachable |
| `mapmaker_expedition_3_frontier` | chains | COMPLETABLE | any_of(2 |
| `remedy_embers_and_wart` | chains | COMPLETABLE | nether visit+wart+blaze kill all obtainable |
| `remedy_gold_against_ash` | chains | COMPLETABLE | craft golden apple+obtain items |
| `remedy_the_fevered_word` | chains | COMPLETABLE | stage1 cold-start infected gate then cleric delivery |
| `remedy_the_returning_voice` | chains | RISKY | finale fails on giver death (giver is the infected villager); arc must be redone with another villager |
| `road_caravan_through` | chains | RISKY | finale fails on giver death; arc must be redone from stage 1 with another cartographer |
| `road_clear_the_cut` | chains | COMPLETABLE | defend_location no deadline so cannot fail |
| `road_raise_the_waystation` | chains | COMPLETABLE | build_near_location obtainable materials |
| `road_the_missing_mile` | chains | COMPLETABLE | stage1 cold-start reach+talk cartographer |
| `cleric_brave_the_nether` | cleric | COMPLETABLE | visit_dimension the_nether is a sticky latch reachable via portal |
| `cleric_glow_in_the_dark` | cleric | COMPLETABLE | glowstone dust obtainable from cave/nether glowstone or trading |
| `cleric_grim_harvest` | cleric | COMPLETABLE | rotten flesh trivially farmed from zombies |
| `cleric_night_pilgrimage` | cleric | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `cleric_tend_the_flock` | cleric | COMPLETABLE | talk_to_profession minecraft:none is the unemployed villager profession id and is common |
| `cleric_tend_the_wounded` | cleric | COMPLETABLE | heal_entity golden carrot with health_below offer condition is consistent |
| `cleric_urgent_medicine` | cleric | COMPLETABLE | glistering melon craftable from gold nugget+melon within deadline |
| `cleric_urgent_medicine_recovery` | cleric | COMPLETABLE | chained recovery quest gated on quest_failed |
| `farmer_build_the_hayloft` | farmer | COMPLETABLE | hay block craftable/farmable |
| `farmer_root_cellar` | farmer | COMPLETABLE | carrot delivery trivial |
| `farmer_sow_before_the_rain` | farmer | COMPLETABLE | weather offer condition does not block obtain_item progress |
| `farmer_wheat_request` | farmer | COMPLETABLE | wheat delivery trivial farming |
| `fisherman_a_taste_of_the_sea` | fisherman | COMPLETABLE | pufferfish obtainable via fishing |
| `fisherman_fresh_catch` | fisherman | COMPLETABLE | cod delivery trivial via fishing |
| `fisherman_rain_catch` | fisherman | RISKY | failure block includes require_weather; quest can fail if rain ends before 5 salmon caught |
| `fisherman_salmon_run` | fisherman | COMPLETABLE | fish_item salmon no weather requirement |
| `fisherman_storm_catch` | fisherman | COMPLETABLE | weather offer condition only gates offer |
| `fletcher_a_fine_bow` | fletcher | COMPLETABLE | craft_item bow trivial |
| `fletcher_feather_light` | fletcher | COMPLETABLE | feather delivery via chicken farming |
| `fletcher_feathers_for_fighters` | fletcher | COMPLETABLE | arrow delivery craftable |
| `fletcher_full_quiver` | fletcher | COMPLETABLE | arrow x32 craftable from flint/feather/stick |
| `fletcher_knapping` | fletcher | COMPLETABLE | flint delivery via gravel mining |
| `guard_carry_word_home` | guard | COMPLETABLE | reach_location with distance/time offer conditions is fine |
| `guard_clear_the_night` | guard | COMPLETABLE | simple zombie kill quest |
| `guard_dawn_defense` | guard | COMPLETABLE | zombie kill with time deadline; farmable |
| `guard_defend_the_captain` | guard | COMPLETABLE | defend_villager under time deadline; giver is guard not target |
| `guard_hold_the_gate` | guard | COMPLETABLE | defend_location x8 under time deadline |
| `guard_last_stand` | guard | COMPLETABLE | defend_location x12 with deadline and failure_hearts; harder but doable |
| `guard_night_watch` | guard | COMPLETABLE | simple zombie kill with time offer condition |
| `guard_phantom_vigil` | guard | COMPLETABLE | phantoms spawn naturally after sleepless nights |
| `guard_post_raid_sweep` | guard | COMPLETABLE | zombie kill gated on village_member condition |
| `guard_skeleton_patrol` | guard | COMPLETABLE | simple skeleton kill quest |
| `guard_spider_season` | guard | COMPLETABLE | simple spider kill quest |
| `guard_the_creeper_problem` | guard | COMPLETABLE | simple creeper kill quest |
| `guard_torch_the_dark` | guard | COMPLETABLE | build_near_location torches |
| `guard_wolf_at_the_door` | guard | COMPLETABLE | zombie kill x8 |
| `leatherworker_cobblers_apprentice` | leatherworker | COMPLETABLE | craft leather boots |
| `leatherworker_rabbit_run` | leatherworker | COMPLETABLE | rabbit hide obtainable by hunting rabbits |
| `leatherworker_the_tannery` | leatherworker | COMPLETABLE | deliver 12 leather |
| `leatherworker_tough_hide` | leatherworker | COMPLETABLE | deliver 8 leather |
| `librarian_ink_and_quill` | librarian | COMPLETABLE | ink sac from squids |
| `librarian_paper_trail` | librarian | COMPLETABLE | deliver 20 paper from sugar cane |
| `librarian_take_the_census` | librarian | COMPLETABLE | talk to 3 farmers |
| `librarian_well_read` | librarian | COMPLETABLE | deliver 3 books |
| `mason_clay_works` | mason | COMPLETABLE | clay ball obtainable near water |
| `mason_lay_the_path` | mason | COMPLETABLE | place 32 stone bricks |
| `mason_quarry_work` | mason | COMPLETABLE | break 48 stone |
| `mason_rebuild_the_wall` | mason | COMPLETABLE | build near location gated on village_member |
| `mason_solid_foundations` | mason | COMPLETABLE | deliver 24 stone |
| `mercenary_bounty` | mercenary | COMPLETABLE | kill 5 creepers |
| `mercenary_contract_killing` | mercenary | COMPLETABLE | kill 8 zombies |
| `mercenary_pillager_bounty` | mercenary | COMPLETABLE | raiders tag covers common raid mobs |
| `mercenary_witch_hunt` | mercenary | RISKY | witches mainly spawn in swamp huts or via lightning-struck villagers; no hint in title/dialogue toward a structure |
| `feast_of_many_tables` | relations | COMPLETABLE | multi-item delivery no conditions |
| `honey_for_the_healer` | relations | COMPLETABLE | breed/craft/deliver chain no conditions |
| `honored_envoy` | relations | COMPLETABLE | obtain paper gated by reputation tier |
| `horse_for_the_courier` | relations | COMPLETABLE | tame horse and reach location |
| `long_way_home` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `monument_of_names` | relations | COMPLETABLE | build+deliver memorial no conditions |
| `relations_a_meal_for_mother` | relations | COMPLETABLE | simple delivery to parent |
| `relations_anniversary_gift` | relations | COMPLETABLE | spouse+hearts>=40 redundant with marriage=100 but not broken |
| `relations_child_treat` | relations | COMPLETABLE | simple delivery to child |
| `relations_childs_first_toy` | relations | COMPLETABLE | simple delivery |
| `relations_cure_infected_kin` | relations | BROKEN | offer gate requires only same_village, not infected; cure_villager cannot advance unless the kin later gets infected |
| `relations_cure_my_spouse` | relations | COMPLETABLE | condition guards spouse infected status matching objective |
| `relations_escort_me_home` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_escort_to_market` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_family_reunion_feast` | relations | COMPLETABLE | simple delivery to family |
| `relations_grandparents_blanket` | relations | COMPLETABLE | simple bed delivery |
| `relations_guard_village_patrol` | relations | COMPLETABLE | kill zombies near village |
| `relations_heal_my_beloved` | relations | COMPLETABLE | spouse+health_below condition matches heal objective |
| `relations_lead_me_home` | relations | COMPLETABLE | escort with OR distance/time gating |
| `relations_letter_to_brother` | relations | COMPLETABLE | trivial single paper delivery |
| `relations_light_the_beacon` | relations | COMPLETABLE | build near location lanterns |
| `relations_long_road_back` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_mend_the_quarrel` | relations | COMPLETABLE | simple poppy delivery |
| `relations_missing_child_search` | relations | COMPLETABLE | condition guards child missing status matching objective |
| `relations_protect_my_child` | relations | COMPLETABLE | protect family child |
| `relations_repair_the_well` | relations | COMPLETABLE | build near location no conditions |
| `relations_reunite_with_spouse` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_search_the_ruins` | relations | COMPLETABLE | condition guards sibling missing status matching objective |
| `relations_see_my_child_home` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_sick_villager_remedy` | relations | COMPLETABLE | health_below or infected condition matches golden apple delivery |
| `relations_spouse_flowers` | relations | COMPLETABLE | spouse+hearts>=50 redundant with marriage=100 but not broken |
| `relations_stranded_at_dusk` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_sweetheart_cocoa` | relations | COMPLETABLE | spouse cookie delivery |
| `relations_teach_them_to_fish` | relations | COMPLETABLE | simple cooked cod delivery |
| `relations_trade_with_blacksmith` | relations | COMPLETABLE | trade with weaponsmith no conditions |
| `relations_walk_me_to_bed` | relations | COMPLETABLE | escort with fail_on_giver_death: giver death fails the quest cleanly and it can be re-offered; keeping them alive is the quest |
| `relations_walk_the_walls` | relations | COMPLETABLE | kill zombies near village at time window |
| `relations_widow_memorial` | relations | COMPLETABLE | deceased spouse plus surviving family delivery target |
| `shepherd_a_dyers_dozen` | shepherd | COMPLETABLE | simple wool delivery |
| `shepherd_spin_a_yarn` | shepherd | COMPLETABLE | obtain string |
| `shepherd_warm_blankets` | shepherd | COMPLETABLE | craft white carpet |
| `shepherd_wool_gathering` | shepherd | COMPLETABLE | simple wool delivery |
| `template_cartographer_survey` | templates | COMPLETABLE | template: 6 biome expansions judged all real biomes |
| `template_farmer_crop_request` | templates | COMPLETABLE | template: 6 crop expansions judged all farmable |
| `template_fisherman_catch` | templates | COMPLETABLE | template: fish tag expansion fair |
| `template_guard_mob_cull` | templates | COMPLETABLE | template: 5 hostile-mob expansions judged |
| `template_kin_errand` | templates | COMPLETABLE | template: 7 item expansions judged family delivery |
| `template_librarian_knowledge` | templates | COMPLETABLE | template: 3 item expansions judged |
| `template_mercenary_bounty` | templates | COMPLETABLE | template: 8 hostile-mob expansions judged |
| `toolsmith_a_proper_kit` | toolsmith | COMPLETABLE | craft iron pickaxe |
| `toolsmith_deep_delve` | toolsmith | COMPLETABLE | break iron ore tag x5 |
| `toolsmith_sharp_and_ready` | toolsmith | COMPLETABLE | craft iron axe |
| `toolsmith_temper_in_battle` | toolsmith | COMPLETABLE | zombie kill x4 trivial |
| `toolsmith_tools_of_the_trade` | toolsmith | COMPLETABLE | iron ingot delivery trivial |
| `townstead_a_balanced_day` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_state True objective, no vanilla issue |
| `townstead_a_proper_nights_rest` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_change 0 objective |
| `townstead_a_real_workshop` | townstead | COMPLETABLE | blast_furnace x2 near the location is unusual but achievable; Townstead side UNVERIFIABLE(townstead) |
| `townstead_a_week_kept_well` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_state thresholds 55/10/8 depend on Townstead's scale |
| `townstead_apprentice_first_full_shift` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): schedule_streak, chain stage2/4 |
| `townstead_apprentice_masterwork` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): profession_progress+streak, chain stage4/4 |
| `townstead_apprentice_tools_of_calling` | townstead | COMPLETABLE | clock/book/bread deliveries fair, chain stage1/4 |
| `townstead_apprentice_trusted_hand` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): profession_progress+streak, chain stage3/4 |
| `townstead_bookkeepers_census` | townstead | COMPLETABLE | paper x32/book x8/talk_to_profession x3 all vanilla+MCA guard, fair |
| `townstead_breakfast_before_bells` | townstead | COMPLETABLE | Cosmetic: townstead_value condition duplicated twice in all_of |
| `townstead_care_for_the_young` | townstead | COMPLETABLE | item_delivery cooked_beef x8 is fair |
| `townstead_character_choose_our_name` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_state settlement, chain stage2/4 |
| `townstead_character_first_mark` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): spirit_progress, chain stage1/4 |
| `townstead_character_living_legacy` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): spirit_progress+healthy_residents, chain stage4/4 |
| `townstead_character_strength_of_place` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): spirit_progress, [not]spirit cond plausibly gates on threshold not yet met |
| `townstead_commission_bells_for_old_names` | townstead | RISKY | defend_location zombie x8+skeleton x4 near a location is spawn/luck dependent |
| `townstead_commission_breadth_of_the_fields` | townstead | COMPLETABLE | Breed+build objectives fair and event-driven |
| `townstead_commission_ink_and_index` | townstead | COMPLETABLE | craft/delivery/build counts reasonable |
| `townstead_commission_iron_sings` | townstead | COMPLETABLE | craft/build counts reasonable |
| `townstead_commission_market_bells` | townstead | COMPLETABLE | trade+delivery achievable via trading hall |
| `townstead_commission_salt_and_lanterns` | townstead | COMPLETABLE | fish+build counts fair |
| `townstead_commission_watch_at_the_gate` | townstead | RISKY | defend_location depends on unguaranteed pillager spawns near location |
| `townstead_commission_welcome_lights` | townstead | COMPLETABLE | pure build_near_location counts fair |
| `townstead_day_off_means_day_off` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_change/state depend on Townstead schedule internals |
| `townstead_deep_water_days` | townstead | COMPLETABLE | fish x12 fine; UNVERIFIABLE(townstead) for schedule_streak+building cond |
| `townstead_dockside_catch` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): building_registered dock x1 |
| `townstead_fill_the_wool_shed` | townstead | COMPLETABLE | wool x24 fair, cond townstead_building plausible |
| `townstead_first_shift` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_state work |
| `townstead_first_workday_as_an_adult` | townstead | COMPLETABLE | simple delivery+reach+state objectives |
| `townstead_founding_character` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): spirit_progress |
| `townstead_fuel_the_smoker` | townstead | COMPLETABLE | charcoal x16 fair |
| `townstead_full_granary` | townstead | COMPLETABLE | wheat x32 fair |
| `townstead_growing_community` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): dock+spirit_progress |
| `townstead_harbor_deep_water` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): building_registered/spirit_progress depend on Townstead state |
| `townstead_harbor_first_piling` | townstead | COMPLETABLE | offer gated on building not yet registered matches objective to register it |
| `townstead_harbor_lantern_line` | townstead | COMPLETABLE | build objectives fair |
| `townstead_harbor_working_tide` | townstead | COMPLETABLE | fish+schedule_streak fair |
| `townstead_harvest_under_gold` | townstead | COMPLETABLE | delivery counts fair for stage 3 chain |
| `townstead_healthy_workforce` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): healthy_residents |
| `townstead_heat_over_the_fields` | townstead | COMPLETABLE | 3 potions in 3 slots is fine; plain minecraft:potion id also matches water bottles (no NBT match) |
| `townstead_infirmary_before_frost` | townstead | COMPLETABLE | offer requires building not yet registered matching registration objective |
| `townstead_lanterns_for_late_shift` | townstead | COMPLETABLE | delivery+state objectives fair |
| `townstead_lanterns_for_the_departed` | townstead | RISKY | defend_location depends on unguaranteed zombie spawns near location |
| `townstead_leatherworkers_order` | townstead | COMPLETABLE | leather x16 fair |
| `townstead_master_of_the_trade` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): profession_progress+track cond |
| `townstead_mend_the_nets` | townstead | COMPLETABLE | string x12 fair |
| `townstead_names_in_the_family_book` | townstead | COMPLETABLE | delivery+talk_to_profession straightforward |
| `townstead_pantry_run` | townstead | COMPLETABLE | bread x12 fair |
| `townstead_pasture_first_fence` | townstead | COMPLETABLE | missing not(townstead_building) guard is covered by the isTriviallySatisfied offer filter; consistency nit only |
| `townstead_pasture_keeper_of_the_flock` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): profession/spirit progress depend on Townstead internals |
| `townstead_pasture_lambing_day` | townstead | COMPLETABLE | breed+delivery counts fair |
| `townstead_pasture_wool_under_roof` | townstead | COMPLETABLE | missing not(townstead_building) guard is covered by the isTriviallySatisfied offer filter; consistency nit only |
| `townstead_plan_the_fields` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_state work |
| `townstead_rest_after_the_alarm` | townstead | RISKY | protect target death fails the quest cleanly (onProtectedDeath); inherent escort/protect fragility |
| `townstead_rooms_for_the_road` | townstead | COMPLETABLE | offer requires building not yet registered matching registration objective |
| `townstead_shears_and_shelter` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): building_registered pen x1 |
| `townstead_smokehouse_first_fire` | townstead | COMPLETABLE | offer requires building not yet registered matching registration objective |
| `townstead_smokehouse_honest_shift` | townstead | COMPLETABLE | delivery+schedule_streak fair |
| `townstead_smokehouse_legacy` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): profession/spirit progress depend on Townstead internals |
| `townstead_smokehouse_winter_stores` | townstead | COMPLETABLE | delivery counts fair |
| `townstead_spring_bells_and_blossoms` | townstead | COMPLETABLE | build_near_location tag-based flowers + bell fair |
| `townstead_spring_in_the_furrows` | townstead | COMPLETABLE | delivery+schedule_streak fair |
| `townstead_stock_the_smokehouse` | townstead | COMPLETABLE | cooked_porkchop x16 fair, cond townstead_building plausible |
| `townstead_stores_against_winter` | townstead | COMPLETABLE | delivery counts fair |
| `townstead_tanned_and_ready` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_state work |
| `townstead_the_elders_old_route` | townstead | RISKY | escort/protect target death fails cleanly; long multi-stage escort is fragile by design |
| `townstead_the_long_harvest` | townstead | COMPLETABLE | Cosmetic: duplicate townstead_value condition in all_of, harmless |
| `townstead_the_master_tanner` | townstead | COMPLETABLE | leather x32 large but doable; UNVERIFIABLE(townstead) streak |
| `townstead_the_whole_flock` | townstead | COMPLETABLE | Cosmetic: duplicate townstead_value condition in all_of, harmless |
| `townstead_the_winter_cure` | townstead | COMPLETABLE | Cosmetic: duplicate townstead_value condition in all_of, harmless |
| `townstead_water_bearers_rounds` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): townstead_healthy_residents depends on Townstead internals |
| `townstead_water_for_the_weary` | townstead | COMPLETABLE | single potion delivery |
| `townstead_winter_at_the_table` | townstead | COMPLETABLE | UNVERIFIABLE(townstead): healthy_residents/spirit_progress depend on Townstead internals |
| `unemployed_a_splash_of_color` | unemployed | COMPLETABLE | small_flowers tag common |
| `unemployed_apple_a_day` | unemployed | COMPLETABLE | apple delivery via oak tree drops |
| `unemployed_berry_picking` | unemployed | COMPLETABLE | sweet berries common in taiga |
| `unemployed_egg_hunt` | unemployed | COMPLETABLE | egg delivery via chicken farming |
| `unemployed_helping_hand` | unemployed | COMPLETABLE | bread craftable from wheat |
| `unemployed_kindling` | unemployed | COMPLETABLE | stick trivial |
| `unemployed_lend_a_blade` | unemployed | COMPLETABLE | zombie kill x3 trivial |
| `weaponsmith_bone_collector` | weaponsmith | COMPLETABLE | skeleton kill x6 trivial |
| `weaponsmith_creeper_cull` | weaponsmith | COMPLETABLE | creeper kill x3 trivial |
| `weaponsmith_drowned_depths` | weaponsmith | COMPLETABLE | drowned only ocean/river biome but no structure/dimension needed; title hints water |
| `weaponsmith_proving_the_steel` | weaponsmith | COMPLETABLE | zombie kill x6 trivial |
| `weaponsmith_temper_the_blade` | weaponsmith | COMPLETABLE | iron ingot delivery trivial |
| `weaponsmith_the_horde` | weaponsmith | COMPLETABLE | zombie kill x8 trivial |

## 4 Findings

Ids: F-B (bug), F-D (design smell), F-C (cosmetic). No Blocker-severity finding was confirmed: nothing
in the shipped pack is impossible to complete and nothing found crashes. F-B01 is the closest thing.

### Bugs

| Id | Where | What is wrong | Smallest fix | Affects |
|---|---|---|---|---|
| F-B01 | `quests/relations/cure_infected_kin.json` `conditions` | Offer gate is `related_villager_status{relation:any,status:same_village}`; the objective is `cure_villager` on that kin. Nothing requires the kin to be infected, and `CureVillagerObjective` has no `unofferableReason` guard, so the quest is offered against a healthy relative and can only advance if they later happen to be zombified. `relations_cure_my_spouse` gets this right with an `infected` gate. | Set `"status": "infected"` on the condition (and on the objective's `require`), mirroring `cure_my_spouse`. | 1 quest (the only BROKEN verdict) |
| F-B02 | `quest/QuestManager.java:500-503`, `quest/OfferFilters.java:123` | "Already active" is checked per villager (`hasActive(id, villagerUuid)`), not per player. A non-chain quest offered by several professions or by any villager can be accepted from two givers at once; kill/craft/break events credit every active copy (`event/QuestProgressEvents.java:863-891`), so one set of kills completes both copies and pays twice. | For definitions without a `chain`, gate on `hasActive(id)` across all villagers in both the offer filter and `accept()`. Chain quests are per-villager arcs by design and keep the current check. | 115 quests (51 with no profession gate, 64 multi-profession) |
| F-B03 | `event/QuestProgressEvents.java:655`, login handler `:96-103` | `onGiverDeath` iterates online players only. A player who is offline when their giver dies is never reconciled: the quest stays active against a dead villager, and login does not re-check. With `fail_on_giver_death` set, the failure branch of a chain never fires for them. | On login, scan `data.active()` for givers that no longer resolve and run the same fail-or-keep logic, or keep a small SavedData set of dead giver UUIDs the login handler consults. | any ORIGINAL_GIVER quest; 17 with `fail_on_giver_death` |
| F-B04 | `project/ProjectManager.java:67,111-117` | The anti-spam map `lastContributeTick` is keyed by player only, so contributing to one project locks the player out of every other project for `projectContributeMinIntervalTicks`. | Key by `(player, project instance)`. | all 21 projects |
| F-B05 | `project/ProjectRewardDistributor.java:37-39,282-286` | When a project completes after the sponsor died (`sponsor.on_death` turn-in-to-village or fail), `HeartsWithSponsorReward` sees a null villager and no-ops; it is also excluded from offline queueing. The reward is silently lost. `HeartsReward` and `HeartsWithParticipantsReward` have fallbacks. | Fall back to the village/participants delivery path when the sponsor is gone, or bank it as a `PendingReward`. | latent; no bundled project uses `hearts_with_sponsor` |
| F-B06 | `event/QuestProgressEvents.java:106-124` | `kill_entity` credits `getSource().getEntity()` only: melee and player-fired projectiles. Kills by tamed wolves, TNT, lava, or fall damage after a hit are not counted, although quest text says "kill". | If unintended, fall back to `LivingEntity#getKillCredit()` / `lastHurtByPlayer` when the source entity is not a player. | 40+ kill quests, minor |
| F-B07 | `quest/objective/DefendVillagerObjective.java:90-99` | A kill only counts if the defended villager resolves at that instant; if their chunk is unloaded (render-distance desync near a village edge) the kill is silently dropped with no feedback. | Accept the kill when the villager was resolved within the last N ticks, or surface an `unavailableReason`. | `defend_villager` quests |
| F-B08 | `quest/QuestManager.java:817-824` | `grantSafely` swallows any reward exception after `rewardClaimed` is already true and delivery items are consumed (`:855,862-863`). The quest completes and is removed; the player gets no message that a reward failed. | Send the player a system message naming the failed reward; keep the swallow so turn-in stays atomic. | all quests, only on a broken reward |

### Design smells

| Id | Where | What is wrong | Smallest fix |
|---|---|---|---|
| F-D01 | `event/QuestProgressEvents.java:345-359,693-712`; `quest/objective/BreakBlockObjective.java:88-90` | `break_block` and `place_block` have no placed-block memory, so placing and breaking the same block farms both objectives for free. `build_near_location` already tracks placed positions. | Record player-placed positions per active objective and skip credit for breaking one of them (and vice versa). |
| F-D02 | `quests/adventurer/last_banner_home.json` `objectives[1]` | `vindicator x4` needs a raid or a woodland mansion; title and dialogue give no hint. | Hint the mansion/raid, or use a more common illager. |
| F-D03 | `quests/mercenary/witch_hunt.json` `objectives[0]` | `witch x3` without a swamp-hut hint; witches otherwise appear only via lightning or raids. | Add a swamp hint or lower the count. |
| F-D04 | `quests/fisherman/rain_catch.json` `failure` | `require_weather` fails the quest the moment rain stops, before 5 salmon may be caught. Works as documented, but it is the only quest whose failure is pure weather luck. | Drop `require_weather` from `failure` (keep it as an offer condition) or add a generous deadline instead. |
| F-D05 | `quests/townstead/{townstead_commission_bells_for_old_names,townstead_commission_watch_at_the_gate,townstead_lanterns_for_the_departed}.json` | `defend_location` asks for 6-12 kills of specific hostiles near a fixed spot with nothing summoning them; progress depends on natural spawns at that spot. | Lower counts, widen the radius, or pair with a situation that spawns the threat. |
| F-D06 | `quests/chains/{bell_when_the_horns_answer,remedy_the_returning_voice,road_caravan_through}.json` `failure` | Chain finales set `fail_on_giver_death` with no `retry_after` or fail branch. Arcs are per-villager, so losing the giver at stage 4 means restarting the whole arc with another villager. In `the_ashen_remedy` the giver is the infected villager the chain is about, so death is plausible. | Add `retry_after`, or a `quest_failed` branch that re-opens the finale. |
| F-D07 | `quest/QuestManager.java:1204` docstring vs `event/QuestProgressEvents.java:374-411` | `isSuspended` claims a suspended quest "does not poll", but the poll passes run before the suspension check; it only works because every polling objective self-guards. A future objective that does not would accrue progress while suspended. | Gate the poll passes on `!isSuspended` centrally, or fix the docstring. |
| F-D08 | `quest/QuestManager.java:838-924` vs `failQuest` `:1133` | `failQuest` calls `syncLog` itself because the per-tick resync misses a player whose last active quest just changed; `completeQuest` relies on its callers to sync. Correct today, fragile for the next caller. | Move `syncLog` into `completeQuest`. |
| F-D09 | `project/ProjectManager.java:665-669`, `project/scope/ScopeResolver.java:44-46` | Stale-scope detection only notices a scope-type change in the pack, not an MCA village that was deleted or merged; such `ProjectState` entries live forever in `ProjectSavedData` with no admin-visible marker. | Extend `isScopeStale` to check village existence for village/profession scopes. |
| F-D10 | `project/ProjectManager.java:570-622` | One kill or placement is matched against every objective of every active project; two objectives in one phase that match the same entity both get credit. Intentional per the comments, but unguarded. | `ProjectValidator` warning for overlapping kill/place targets inside one phase. |
| F-D11 | `quests/townstead/townstead_heat_over_the_fields.json` `objectives[0]`; `quest/target/ItemTarget.java:26-28` | `item_delivery minecraft:potion x3` matches by item id only, so three water bottles satisfy it. All item targets are NBT-blind; this is the one quest where it changes the meaning. | Use an item that identifies itself (e.g. honey bottle), or add optional potion matching to `ItemTarget`. |
| F-D12 | `quests/townstead/townstead_a_real_workshop.json` `objectives[2]`, `townstead_a_week_kept_well.json` `objectives[1-3]` | `build_near_location blast_furnace x2` is the only non-1 build count in the pack; `townstead_state` targets 55/10/8 have no documented scale. | Confirm intent; document the state scale in `TOWNSTEAD.md`. |
| F-D13 | `quests/townstead/townstead_pasture_{first_fence,wool_under_roof}.json` `conditions` | Sibling building-registration quests gate on `not(townstead_building)`; these two do not. The `isTriviallySatisfied` offer filter still suppresses them once the building exists, so this is consistency only. | Add the same `not` guard. |
| F-D14 | `quests/adventurer/drowned_ledger.json` | 8 prismarine crystals are obtainable from the sea lanterns in the ocean ruins the quest already targets, but nothing says so; a reader assumed drowned drops. | One dialogue line pointing at the lanterns. |

### Cosmetic

| Id | Where | What is wrong | Smallest fix |
|---|---|---|---|
| F-C01 | `quests/townstead/{the_long_harvest,the_whole_flock,the_winter_cure,townstead_breakfast_before_bells}.json` `conditions.all_of` | The same `townstead_value` condition appears twice in the `all_of`. Harmless. | Remove the duplicate. |
| F-C02 | `quest/objective/KillEntityObjective.java:74-81`, `BreakBlockObjective.java:73-81` | The raw counter keeps growing past `required`; only `current()` clamps. No consumer reads the raw value. | Clamp on write in `ObjectiveProgress`, or leave. |
| F-C03 | `quest/QuestManager.java:1737-1793` | `checkReadyTransitions` and `syncLog` each recompute `isComplete`/`isSuspended` in the same tick. They agree today. | Compute once per quest per pass. |
| F-C04 | `state/ActiveQuest.java:117` | `RECONCILED_QUESTS` is a static set keyed by quest id only; it dedupes one log line across all players. Not gameplay state. | Comment that it is intentionally global. |
| F-C05 | census script | `zero_rewards` fired on the 7 templates because rewards sit under `template.rewards`. Recorded here so the flag is not mistaken for a bug. | none |
| F-C06 | 28 registered types unused by the pack (inventory section 6) | `sleep_or_rest` objective, the `effect` / `xp_levels` / `command` / `unlock` rewards, and 12 conditions (`biome`, `dimension`, `time`, `item_held`, `advancement`, `random_chance`, `quest_not_completed`, `quest_declined`, `personality`, `mood`, `village_reputation`, `profession`) ship with no bundled quest exercising them, so their runtime paths are covered only by unit tests. Add-on API surface, not a defect. | Consider one bundled quest per core vanilla type, or accept. |

Reader claims checked and rejected: hearts thresholds alongside `is_player_spouse` are not redundant
(hearts can drop after marriage); potions x3 is not a stack problem (three slots); protect/escort target
death fails the quest via `onProtectedDeath` / `onEscortTargetDeath` (`QuestProgressEvents.java:132,186`),
it does not soft-lock; a failed chain finale is re-offerable (correction 6), it does not kill the chain.

## 5 Highest-value fixes, in order

1. **F-B01** cure_infected_kin gate: one-line data fix; the only quest a player can hold and never finish.
2. **F-B02** global "already active" for non-chain quests: closes a double-reward exploit on 115 quests.
3. **F-B03** reconcile dead givers on login: makes `fail_on_giver_death` and the config toggle honest in multiplayer.
4. **F-B04** project rate limit per (player, project): one-line key change, affects every project.
5. **F-D01** placed-block memory for break/place: the only progress-farming loop in the objective set.
6. **F-D06** `retry_after` on the three chain finales: cheap data fix that stops a 4-stage arc evaporating.
7. **F-B08** tell the player when a reward fails: small, and the rest of turn-in is already atomic.
8. **F-B06** kill credit for indirect kills: decide intent, then either document or widen.

## 6 Detection and marker plumbing, per objective type

Full table with file:line is in [inventory.md section 5](inventory.md#5-type-registries). What was verified:

| Type | Progress source | Fires when the text implies? | Guards |
|---|---|---|---|
| `obtain_item`, `item_delivery`, `deliver_to_villager` | possession: live inventory scan (main + armor + offhand), any source | yes: crafted, traded, looted, withdrawn all count; not shulkers/bundles; NBT-blind (F-D11) | delivery consumed once at turn-in with a `delivered` marker (`QuestManager.java:772-787,859-861`) |
| `craft_item` | `ItemCraftedEvent` (`QuestProgressEvents.java:680`) | yes; shift-click fires per crafted stack with the real count | server, capped |
| `kill_entity`, `defend_villager`, `defend_location` | `LivingDeathEvent` (`:105`) | direct and projectile kills only (F-B06); defend needs the villager resolvable (F-B07) | server, capped |
| `break_block`, `place_block` | `BlockEvent.BreakEvent` / `EntityPlaceEvent` (`:345,693`) | yes, but farmable (F-D01) | server, capped |
| `fish_item` | `ItemFishedEvent` (`:769`) | yes; junk and treasure count, id or tag | server, capped |
| `trade_with_villager`, `tame_animal`, `breed_animals`, `sleep_or_rest`, `talk_to_profession`, `heal_entity`, `cure_villager` | Forge events (`:714-792`) | yes; talk is deduped per hand and UUID; sleep attributed per sleeping player | server, capped |
| `visit_biome`, `visit_dimension`, `reach_location`, `enter_structure`, `build_near_location` | tick poll (`:361`), sticky latch | yes; never un-latches, so the toast cannot re-fire | server |
| `escort_entity`, `protect_entity`, `find_missing_relative` | tick poll + death events (`:132,186`) | pause while target unloaded, fail on death, never spawn twice | server |
| `townstead_*` (8), `ftbq_complete_quest` | `PollingObjective` pass (`:405-407`) on `townsteadPollIntervalTicks`; FTBQ via a Forge-bus bridge | UNVERIFIABLE(townstead); the FTBQ bridge is server-only, not a network handler | server |

Cross-cutting: every handler starts with a `ServerPlayer` / `!isClientSide` guard; every objective class
clamps `current()` with `Math.min(count, required)` and `ObjectiveProgress` floors at 0; no C2S packet
writes progress. The one client-driven input, `ProjectContributeC2SPacket`, is re-validated against the
live inventory and the registry (`project/ProjectManager.java:91-162`).

## 7 Lifecycle and state

| Concern | Verdict | Evidence |
|---|---|---|
| Relog / server restart | OK | every UI-visible field (`ActiveQuest` progress, offers, tracked quest, `readyNotified`, `suspendedTicks`, `dispatchedPhases`) round-trips through `PlayerQuestData.save/load`; only throttle maps are memory-only |
| Death / respawn | OK | `QuestCapabilityEvents.onClone` (`:33`) handles both death and portal clones with `reviveCaps`, copy, `invalidateCaps`, matching the note at `PlayerQuestDataProvider.java:39-44` |
| Dimension change | OK | only highlight/guidance dedupe maps clear (`QuestProgressEvents.java:326`), intentionally |
| Giver `/kill` | caveat | `LivingDeathEvent` fires for `/kill`; online players are handled, offline players are not (F-B03) |
| Multiplayer independence | OK | no static per-player state; one harmless static log-dedupe set (F-C04) |
| Ready transition once | OK | `readyNotified` set at `QuestManager.java:1743-1751`, cleared only if the quest regresses; single send site for `QuestReadyToastS2CPacket` |
| Toast / HUD / log agreement | OK | `syncLog` follows every state change (turn-in, abandon, fail, giver death); `failQuest` syncs inline (`:1133`) |
| Turn-in idempotent | OK | `completeQuest` returns at `:840` if `rewardClaimed`; the flag flips at `:855` before consumption and rewards; a second packet in the same tick is a no-op |
| Accept validated | OK | live offer session, distance/interactability, caps, cooldown re-checked (`QuestManager.java:112,183,243,255`); per-villager active check only (F-B02) |
| Project banking | OK | `contributeFromPacket` runs contribute, bank, `checkPhaseAdvance`, `setDirty` in one server-thread call (`ProjectManager.java:148-158`); phase advance cannot double-fire |
| Project rewards offline | OK | `PendingReward` / `BankedReward` delivered on login (`ProjectLifecycleEvents.java:34`), amounts frozen per phase |

## 8 Chains and projects

Chains (11: eight named arcs plus `the_bell_at_dawn`, `the_ashen_remedy`, `the_broken_road`): every
stage 1 is offerable cold; every stage above 1 carries `prerequisites` or an outcome condition; all
referenced ids exist; no cycles; `stage_total` equals the distinct stage count in each chain (branch
stages such as 2a/2b share a number, which is why the census counts distinct stages). `quest_declined`
is permanent per player but no bundled quest branches on it. The only chain-level risk is F-D06. All
eleven chains judged COMPLETABLE at chain level.

Projects (21): all objective types have advancing code; scope resolution and sponsor-death handling
(`sponsor.on_death`) resolve recipients without the sponsor except for `hearts_with_sponsor` (F-B05,
unused by the bundled pack). The last phase of `festival_preparation` has no objectives by design
(celebration phase; `ProjectValidator` allows an objective-less final phase). Open items: F-B04, F-D09, F-D10.

## 9 What `/mcaquests validate` catches today, and what to add

`validateQuests` (`command/McaQuestsCommand.java:636-666`) reports `QuestRegistry.lastErrors/lastWarnings`
(everything `QuestDataLoader` ran at reload: `QuestChainValidator`, `TemplateValidator`, `FailureValidator`,
`ObjectiveValidator`, `AgeEligibilityValidator`, `TargetGateValidator`) plus `ProgressionValidator`,
`TownsteadContentValidator`, and `TranslationKeyValidator` (client-side only). `ProjectValidator` runs
from `/mcaquests project validate`. The load-time pipeline is asserted over the bundled pack by
`BuiltinPackValidatesTest`.

| Blocker class | Caught today? | Where | Proposed guard |
|---|---|---|---|
| Unknown objective / condition / reward type | yes | codec dispatch rejects the file (`ObjectiveTypes.java:107-117`), reload and validate | none needed |
| Unresolved item / block / entity id | yes | `ItemTarget` / `BlockTarget` / `EntityTarget` use `byNameCodec` (`quest/target/*.java:19-22`) | none needed |
| Unresolved biome / dimension / structure id | **no** | `BiomeTarget.java:29` is a plain `ResourceLocation` | resolve against `Registries.BIOME` / `LEVEL_STEM` / `STRUCTURE` in `validateQuests` (server registries are available there); a JUnit over the vanilla id lists extracted in this audit |
| Empty or unknown tag on a plain quest | **no** (templates only, `TemplateValidator.java:113-117`) | none | the same check in `ObjectiveValidator` for `tag` fields |
| Chain: dangling target, cycle, unreachable stage, stage gap | yes | `QuestChainValidator.java:42-183`, `BuiltinPackValidatesTest.chainsAreContiguous` | none needed |
| `stage_total` disagreement | warning only (`:82-86`) | none | promote to error for the bundled pack via a JUnit |
| Missing lang key | partly | `TranslationKeyValidator` (client only); `LocaleParityTest` for all locales | none needed |
| Objective whose gate cannot establish the required target state (F-B01 class) | **no** | `TargetGateValidator` checks existence and relation, not status | `CureVillagerObjective.validate` requires a `related_villager_status ... infected` (or `infected`) gate on the same relation; generalise so each villager-targeting objective declares the status it needs |
| Quest with no rewards (non-template) | **no** | none | warning in `QuestDataLoader` when `rewards` and `template.rewards` are both empty |
| Delivery count above the item's max stack, or above a sanity bound | **no** | none | warning in `ObjectiveValidator` (max stack from `Item#getMaxStackSize`) |
| Same quest active from two givers (F-B02) | code, not data | none | `QuestLogicTest`: accept from villager A, then an offer from villager B must fail with `ALREADY_ACTIVE` |
| Offline giver death (F-B03) | code | none | `ActiveQuestReconcileTest`: login with a dead giver UUID fails or keeps the quest per config |
| Template reward parse after substitution | yes | `TemplateValidator.java:96-98` | none needed |

## 10 Unverifiable items and open questions

- Townstead building families, needs, skills, profession tracks and spirit values (73 quests, 12 projects)
  are checked only by `TownsteadContentValidator` at runtime and `BuiltinTownsteadAchievabilityTest`. No
  `townstead:` resource locations appear in JSON (integration is by string key), so nothing was found
  unresolved, and nothing could be.
- MCA profession assignment: existence is proven from the jar; whether an `mca:guard` naturally appears
  in a given village is MCA's spawn logic and is not statically provable. All four MCA professions the
  pack uses are in `ProfessionsMCA`.
- Hearts thresholds: the four `hearts` conditions (values recorded in inventory section 6) fit MCA's
  0-100 tiers; MCA itself does not clamp, so nothing can exceed what MCA can reach.
- Mob-in-biome and structure-hint judgments use vanilla spawner data plus the eight village biomes; they
  are heuristics and were only used for RISKY, never BROKEN.
- Runtime behaviours (packet spam, relog, clone ordering) were reviewed from source, not executed.
- `mcareputation:` and `ftbquests:` types are registered but not used by the bundled pack.
