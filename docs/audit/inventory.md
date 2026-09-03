# MCA: Quests content inventory

Generated ground truth for [QUEST_AUDIT.md](QUEST_AUDIT.md). Produced by a census script over the
bundled datapack, the Java type registries, and the Forge / MCA jars in the Gradle cache; the
"Progress sources" tables at the end of section 5 come from a code-review pass. Nothing here was
hand-edited except this preamble. Regenerate rather than patch.

Reading notes:
- `zero_rewards` on the seven `templates/` quests is an artefact: template rewards live under
  `template.rewards` and are substituted at accept time.
- `objective_type_unused`, `zero_conditions` and `hearts_threshold` are informational.
- `count_outlier` compares a delivery count with the item's max stack; non-stackable items held in
  several slots still count, so the two rows are not defects.
- Townstead, MCA: Reputation and FTB Quests references are string keys, not resource locations, so
  there is nothing for id resolution to check; achievability against a live Townstead is covered by
  `TownsteadContentValidator` / `BuiltinTownsteadAchievabilityTest`.
## 1 Census summary

| folder | quests |
| --- | --- |
| adventurer | 10 |
| archer | 4 |
| armorer | 4 |
| butcher | 4 |
| cartographer | 5 |
| chains | 42 |
| cleric | 8 |
| farmer | 4 |
| fisherman | 5 |
| fletcher | 5 |
| guard | 14 |
| leatherworker | 4 |
| librarian | 4 |
| mason | 5 |
| mercenary | 4 |
| relations | 38 |
| shepherd | 4 |
| templates | 7 |
| toolsmith | 5 |
| townstead | 73 |
| unemployed | 7 |
| weaponsmith | 6 |
| **total** | **262** |

- Quests: 262 (expected 262) — match
- Projects: 21 (expected 21) — match
- Townstead quests: 73 (expected 73) — match

### Ground-truth sources

| source | path | notes |
| --- | --- | --- |
| Forge mapped sources | `C:\Users\crims\.gradle\caches\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.10_mapped_official_1.20.1\forge-1.20.1-47.4.10_mapped_official_1.20.1-sources.jar` | vanilla registry ids (items, blocks, entities, biomes, effects, enchantments, sounds, professions, structures, code-side tags) |
| Vanilla data | `C:\Users\crims\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar` | loot tables, advancements, data tags, biome spawners |
| MCA Reborn | `C:\Users\crims\.gradle\caches\forge_gradle\deobf_dependencies\maven\modrinth\minecraft-comes-alive-reborn\7.6.20+1.20.1_mapped_official_1.20.1\minecraft-comes-alive-reborn-7.6.20+1.20.1_mapped_official_1.20.1.jar` | version 7.6.20+1.20.1; lang keys, class-constant `mca:` ids, data/assets listing |
| Townstead / mcareputation / ftbquests | (no jar) | ids in these namespaces are classified UNVERIFIABLE |

Ground-truth set sizes: advancement=1271, biome=64, biome_tag=69, block=1003, block_tag=170, dimension=3, effect=33, enchantment=39, entity_type=124, entity_type_tag=14, item=1255, item_tag=101, loot_table=1091, mca_ids=1076, mca_lang_keys=954, mca_professions=13, profession=28, sound=1393, structure=33, structure_tag=14

### MCA_PROFESSIONS

`mca:adventurer`, `mca:archer`, `mca:baker`, `mca:child`, `mca:cultist`, `mca:guard`, `mca:jeweler`, `mca:mercenary`, `mca:miner`, `mca:none`, `mca:outlaw`, `mca:pillager`, `mca:warrior`

## 2 Quests

| id | folder | givers | objectives | conditions | rewards | chain | repeat | failure | turn_in | flags |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| mcaquests:atlas_of_four_horizons | adventurer | minecraft:cartographer | visit_biome #minecraft:is_forest; visit_biome #minecraft:is_mountain; visit_biome #minecr… | [all_of[0]]player_level | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:drowned_ledger | adventurer | minecraft:fisherman, minecraft:cartographer | enter_structure #mcaquests:ocean_ruins; kill_entity minecraft:drowned x10; obtain_item mi… | - | currency; xp(amount=55); hearts(amount=14) | - | cooldown | - | DEFAULT | mob_not_in_village_biome,zero_conditions |
| mcaquests:echoes_below | adventurer | minecraft:librarian, minecraft:cleric | enter_structure minecraft:ancient_city; obtain_item minecraft:echo_shard x4; craft_item m… | [all_of[0]]player_level | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:adventurer_into_the_deep | adventurer | mca:adventurer | visit_dimension minecraft:the_nether | - | currency; xp(amount=40); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:last_banner_home | adventurer | mca:guard, minecraft:fletcher, minecraft:armorer | kill_entity minecraft:pillager x12; kill_entity minecraft:vindicator x4; deliver_to_villa… | - | currency; xp(amount=55); hearts(amount=14) | - | cooldown | - | DEFAULT | count_outlier,zero_conditions |
| mcaquests:nether_relay | adventurer | minecraft:cleric, minecraft:weaponsmith | visit_dimension minecraft:the_nether; kill_entity minecraft:blaze x8; deliver_to_villager… | - | currency; xp(amount=55); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:relic_beneath_the_well | adventurer | minecraft:mason, minecraft:librarian | enter_structure #mcaquests:trail_ruins; obtain_item #mcaquests:pottery_sherds x2; craft_i… | - | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | zero_conditions |
| mcaquests:adventurer_relic_hunt | adventurer | mca:adventurer | enter_structure minecraft:ancient_city | - | currency; xp(amount=40); hearts(amount=14) | - | once | - | DEFAULT | zero_conditions |
| mcaquests:adventurer_trailblazer | adventurer | mca:adventurer | visit_biome #minecraft:is_mountain | - | currency; xp(amount=25); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:trial_by_fire | adventurer | minecraft:weaponsmith | visit_dimension minecraft:the_nether; kill_entity minecraft:blaze x6; craft_item minecraf… | [all_of[0]]player_level | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:archer_cull_from_afar | archer | mca:archer | kill_entity minecraft:skeleton x8 | - | currency; xp(amount=28); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:archer_fletchings | archer | mca:archer | item_delivery minecraft:feather x16 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:archer_marksman | archer | mca:archer | kill_entity minecraft:skeleton x6 | - | currency; xp(amount=28); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:archer_spider_nest | archer | mca:archer | kill_entity minecraft:spider x6 | time | currency; xp(amount=26); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:armorer_coal_for_winter | armorer | minecraft:armorer | obtain_item minecraft:coal x24 | - | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:armorer_copper_plating | armorer | minecraft:armorer | item_delivery minecraft:copper_ingot x16 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:armorer_forge_ahead | armorer | minecraft:armorer | craft_item minecraft:shield x2 | - | currency; xp(amount=25); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:armorer_iron_for_the_forge | armorer | minecraft:armorer | item_delivery minecraft:iron_ingot x6 | - | currency; xp(amount=25); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:butcher_fresh_cuts | butcher | minecraft:butcher | obtain_item minecraft:beef x12 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:butcher_hearty_meal | butcher | minecraft:butcher | item_delivery minecraft:cooked_beef x6 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:butcher_poultry_run | butcher | minecraft:butcher | item_delivery minecraft:cooked_chicken x6 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:butcher_smoked_supply | butcher | minecraft:butcher | item_delivery minecraft:cooked_porkchop x8 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cartographer_edge_of_the_world | cartographer | minecraft:cartographer | visit_dimension minecraft:the_end | - | currency; xp(amount=50); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cartographer_guide_the_surveyor | cartographer | minecraft:cartographer | escort_entity | time | currency; xp(amount=26); hearts(amount=8) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:cartographer_high_ground | cartographer | minecraft:cartographer | visit_biome #minecraft:is_mountain | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cartographer_scout_the_land | cartographer | minecraft:cartographer | visit_biome #minecraft:is_forest | - | item(item=minecraft:map,count=1); xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cartographer_spare_parchment | cartographer | minecraft:cartographer | item_delivery minecraft:paper x12 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:aging_parent_1_warm_meal | chains | any | deliver_to_villager minecraft:bread x6 | related_villager_status | currency; xp(amount=18); hearts(amount=8) | mcaquests:aging_parent 1/3 | once | - | DEFAULT | - |
| mcaquests:aging_parent_2_remedy | chains | any | heal_entity minecraft:golden_carrot x1 | related_villager_status | currency; xp(amount=26); hearts(amount=8) | mcaquests:aging_parent 2/3 | once | - | DEFAULT | - |
| mcaquests:aging_parent_3_last_walk | chains | any | escort_entity | related_villager_status | currency; xp(amount=38); hearts(amount=14) | mcaquests:aging_parent 3/3 | once | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:bell_shields_for_neighbors | chains | mca:guard, minecraft:armorer, minecraft:fletcher | deliver_to_villager minecraft:shield x3; deliver_to_villager minecraft:bow x2 | [all_of[0]]has_home | currency; xp(amount=34); hearts(amount=8) | the_bell_at_dawn 1/4 | once | - | DEFAULT | - |
| mcaquests:bell_the_lantern_line | chains | mca:guard, minecraft:armorer, minecraft:fletcher | build_near_location minecraft:lantern x12; build_near_location minecraft:cobblestone_wall… | - | currency; xp(amount=55); hearts(amount=14) | the_bell_at_dawn 2/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:bell_watch_before_dawn | chains | mca:guard, minecraft:armorer, minecraft:fletcher | defend_location minecraft:pillager x8; defend_location minecraft:skeleton x8 | [all_of[0]]time | currency; xp(amount=55); hearts(amount=14) | the_bell_at_dawn 3/4 | once | - | DEFAULT | - |
| mcaquests:bell_when_the_horns_answer | chains | mca:guard, minecraft:armorer, minecraft:fletcher | kill_entity minecraft:ravager x1; defend_location minecraft:pillager x12; protect_entity | - | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=12); gran… | the_bell_at_dawn 4/4 | once | fail_on_giver_death | DEFAULT | zero_conditions |
| mcaquests:courting_1_first_gift | chains | any | item_delivery minecraft:poppy x3 | [all_of[0]]relationship_state; [all_of[1]]hearts | currency; xp(amount=16); hearts(amount=8) | mcaquests:courting 1/4 | once | - | DEFAULT | hearts_threshold |
| mcaquests:courting_2_walk_together | chains | any | escort_entity | - | currency; xp(amount=24); hearts(amount=8) | mcaquests:courting 2/4 | once | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | zero_conditions |
| mcaquests:courting_3_meet_the_family | chains | any | deliver_to_villager minecraft:cake x1 | related_villager_status | currency; xp(amount=26); hearts(amount=14) | mcaquests:courting 3/4 | once | - | DEFAULT | - |
| mcaquests:courting_4_the_proposal | chains | any | item_delivery minecraft:gold_ingot x4 | - | currency; xp(amount=40); hearts(amount=14) | mcaquests:courting 4/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:farmer_family_1_wheat | chains | minecraft:farmer | item_delivery minecraft:wheat x16 | - | currency; xp(amount=20); hearts(amount=8) | mcaquests:farmer_family 1/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:farmer_family_2_expand | chains | minecraft:farmer | item_delivery minecraft:bone_meal x12 | - | currency; xp(amount=25); hearts(amount=8) | mcaquests:farmer_family 2/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:farmer_family_3_apprentice | chains | minecraft:farmer | deliver_to_villager minecraft:bread x6 | related_villager_status | currency; xp(amount=25); hearts(amount=8) | mcaquests:farmer_family 3/4 | once | deadline_ticks,retry_after | DEFAULT | - |
| mcaquests:farmer_family_4_feast | chains | minecraft:farmer | deliver_to_villager minecraft:cake x1 | related_villager_status | currency; item(item=minecraft:golden_apple,count=1); xp(amount=40); hearts(amou… | mcaquests:farmer_family 4/4 | once | - | DEFAULT | - |
| mcaquests:guard_safety_1_clear | chains | mca:guard | kill_entity minecraft:zombie x6 | - | currency; xp(amount=25); hearts(amount=8) | mcaquests:guard_safety 1/3 | once | - | DEFAULT | zero_conditions |
| mcaquests:guard_safety_2_patrol | chains | mca:guard | kill_entity minecraft:spider x4 | - | currency; xp(amount=28); hearts(amount=8) | mcaquests:guard_safety 2/3 | once | deadline_ticks,retry_after | DEFAULT | zero_conditions |
| mcaquests:guard_safety_2b_amends | chains | mca:guard | item_delivery minecraft:torch x16 | quest_failed | currency; xp(amount=20); hearts(amount=4) | mcaquests:guard_safety 2/3 | once | - | DEFAULT | - |
| mcaquests:guard_safety_3_militia | chains | mca:guard | item_delivery minecraft:iron_sword x2 | [any_of[0]]quest_completed; [any_of[1]]quest_completed | currency; xp(amount=38); hearts(amount=14) | mcaquests:guard_safety 3/3 | once | - | DEFAULT | - |
| mcaquests:jobless_friendship_1_errand | chains | minecraft:none, minecraft:nitwit | obtain_item minecraft:apple x6 | - | currency; xp(amount=12); hearts(amount=4) | mcaquests:jobless_friendship 1/3 | once | - | DEFAULT | zero_conditions |
| mcaquests:jobless_friendship_2_favor | chains | minecraft:none, minecraft:nitwit | item_delivery minecraft:poppy x3 | - | currency; xp(amount=18); hearts(amount=8) | mcaquests:jobless_friendship 2/3 | once | - | DEFAULT | zero_conditions |
| mcaquests:jobless_friendship_2b_understanding | chains | minecraft:none, minecraft:nitwit | obtain_item minecraft:sweet_berries x4 | quest_abandoned | currency; xp(amount=14); hearts(amount=8) | mcaquests:jobless_friendship 2/3 | once | - | DEFAULT | - |
| mcaquests:jobless_friendship_3_friend | chains | minecraft:none, minecraft:nitwit | item_delivery minecraft:cookie x8 | [any_of[0]]quest_completed; [any_of[1]]quest_completed | currency; item(item=minecraft:music_disc_cat,count=1); xp(amount=30); hearts(am… | mcaquests:jobless_friendship 3/3 | once | - | DEFAULT | - |
| mcaquests:librarian_knowledge_1_ink | chains | minecraft:librarian | obtain_item minecraft:ink_sac x4 | - | currency; xp(amount=18); hearts(amount=8) | mcaquests:librarian_knowledge 1/3 | once | - | DEFAULT | zero_conditions |
| mcaquests:librarian_knowledge_2_lost_book | chains | minecraft:librarian | obtain_item minecraft:book x3 | - | currency; xp(amount=24); hearts(amount=8) | mcaquests:librarian_knowledge 2/3 | once | - | DEFAULT | zero_conditions |
| mcaquests:librarian_knowledge_3_share | chains | minecraft:librarian | item_delivery minecraft:bookshelf x3 | hearts | currency; item(item=minecraft:enchanted_book,count=1); xp(amount=36); hearts(am… | mcaquests:librarian_knowledge 3/3 | once | - | DEFAULT | hearts_threshold |
| mcaquests:lost_child_1_trail | chains | any | visit_biome #minecraft:is_forest | related_villager_status | currency; xp(amount=22); hearts(amount=8) | mcaquests:lost_child 1/3 | once | - | DEFAULT | - |
| mcaquests:lost_child_2_deeper | chains | any | find_missing_relative; escort_entity | related_villager_status | currency; xp(amount=30); hearts(amount=14) | mcaquests:lost_child 2/3 | once | deadline_ticks,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:lost_child_2b_cold_trail | chains | any | item_delivery minecraft:candle x3 | quest_failed | currency; xp(amount=24); hearts(amount=8) | mcaquests:lost_child 2/3 | once | - | DEFAULT | - |
| mcaquests:lost_child_3_homecoming | chains | any | item_delivery minecraft:cake x1 | [any_of[0]]quest_completed; [any_of[1]]quest_completed | currency; xp(amount=38); hearts(amount=14) | mcaquests:lost_child 3/3 | once | - | DEFAULT | - |
| mcaquests:mapmaker_expedition_1_survey | chains | minecraft:cartographer | item_delivery minecraft:paper x8 | - | currency; xp(amount=18); hearts(amount=4) | mcaquests:mapmaker_expedition 1/3 | once | - | DEFAULT | zero_conditions |
| mcaquests:mapmaker_expedition_2_expedition | chains | minecraft:cartographer | item_delivery minecraft:compass x1; item_delivery minecraft:glass_pane x4 | - | currency; xp(amount=26); hearts(amount=8) | mcaquests:mapmaker_expedition 2/3 | once | deadline_time,retry_after | DEFAULT | zero_conditions |
| mcaquests:mapmaker_expedition_2b_salvage | chains | minecraft:cartographer | item_delivery minecraft:paper x6 | quest_failed | currency; xp(amount=20); hearts(amount=8) | mcaquests:mapmaker_expedition 2/3 | once | - | DEFAULT | - |
| mcaquests:mapmaker_expedition_3_frontier | chains | minecraft:cartographer | item_delivery minecraft:map x2 | [any_of[0]]quest_completed; [any_of[1]]quest_completed | currency; item(item=minecraft:map,count=1); xp(amount=38); hearts(amount=14) | mcaquests:mapmaker_expedition 3/3 | once | - | DEFAULT | - |
| mcaquests:remedy_embers_and_wart | chains | any | visit_dimension minecraft:the_nether; obtain_item minecraft:nether_wart x6; kill_entity m… | - | currency; xp(amount=55); hearts(amount=14) | the_ashen_remedy 2/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:remedy_gold_against_ash | chains | any | craft_item minecraft:golden_apple x1; obtain_item minecraft:fermented_spider_eye x2; obta… | - | currency; xp(amount=34); hearts(amount=8) | the_ashen_remedy 3/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:remedy_the_fevered_word | chains | any | deliver_to_villager minecraft:spider_eye x2; deliver_to_villager minecraft:glass_bottle x… | [all_of[0]]infected | currency; xp(amount=34); hearts(amount=8) | the_ashen_remedy 1/4 | once | - | DEFAULT | - |
| mcaquests:remedy_the_returning_voice | chains | any | cure_villager; protect_entity | - | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=12); gran… | the_ashen_remedy 4/4 | once | fail_on_giver_death | DEFAULT | zero_conditions |
| mcaquests:road_caravan_through | chains | minecraft:cartographer | escort_entity; protect_entity | - | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=10); gran… | the_broken_road 4/4 | once | fail_on_giver_death | DEFAULT | zero_conditions |
| mcaquests:road_clear_the_cut | chains | minecraft:cartographer | defend_location minecraft:pillager x8; defend_location minecraft:spider x6 | - | currency; xp(amount=55); hearts(amount=14) | the_broken_road 2/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:road_raise_the_waystation | chains | minecraft:cartographer | build_near_location minecraft:gravel x32; build_near_location minecraft:lantern x8; build… | - | currency; xp(amount=55); hearts(amount=14) | the_broken_road 3/4 | once | - | DEFAULT | zero_conditions |
| mcaquests:road_the_missing_mile | chains | minecraft:cartographer | reach_location; talk_to_profession minecraft:cartographer x1 | [all_of[0]]has_home | currency; xp(amount=34); hearts(amount=8) | the_broken_road 1/4 | once | - | DEFAULT | - |
| mcaquests:cleric_brave_the_nether | cleric | minecraft:cleric | visit_dimension minecraft:the_nether | - | currency; xp(amount=40); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cleric_glow_in_the_dark | cleric | minecraft:cleric | item_delivery minecraft:glowstone_dust x8 | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cleric_grim_harvest | cleric | minecraft:cleric | item_delivery minecraft:rotten_flesh x10 | - | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cleric_night_pilgrimage | cleric | minecraft:cleric | escort_entity | time | currency; xp(amount=24); hearts(amount=8) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:cleric_tend_the_flock | cleric | minecraft:cleric | talk_to_profession minecraft:none x3 | - | currency; xp(amount=18); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:cleric_tend_the_wounded | cleric | minecraft:cleric | heal_entity minecraft:golden_carrot x1 | health_below | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:cleric_urgent_medicine | cleric | minecraft:cleric | item_delivery minecraft:glistering_melon_slice x4 | - | currency; xp(amount=30); hearts(amount=14) | - | cooldown | deadline_ticks,failure_hearts,retry_after | DEFAULT | zero_conditions |
| mcaquests:cleric_urgent_medicine_recovery | cleric | minecraft:cleric | item_delivery minecraft:sweet_berries x6; item_delivery minecraft:honey_bottle x1 | quest_failed | currency; xp(amount=28); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:farmer_build_the_hayloft | farmer | minecraft:farmer | place_block minecraft:hay_block x8 | - | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:farmer_root_cellar | farmer | minecraft:farmer | item_delivery minecraft:carrot x16 | - | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:farmer_sow_before_the_rain | farmer | minecraft:farmer | obtain_item minecraft:wheat_seeds x16 | weather | currency; xp(amount=14); hearts(amount=4) | - | cooldown | - | DEFAULT | - |
| mcaquests:farmer_wheat_request | farmer | minecraft:farmer | item_delivery minecraft:wheat x24 | - | currency; xp(amount=25); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fisherman_a_taste_of_the_sea | fisherman | minecraft:fisherman | obtain_item minecraft:pufferfish x2 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fisherman_fresh_catch | fisherman | minecraft:fisherman | item_delivery minecraft:cod x8 | - | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fisherman_rain_catch | fisherman | minecraft:fisherman | fish_item minecraft:salmon x5 | weather | currency; xp(amount=24); hearts(amount=8) | - | cooldown | require_weather,retry_after | DEFAULT | - |
| mcaquests:fisherman_salmon_run | fisherman | minecraft:fisherman | fish_item minecraft:salmon x5 | - | currency; xp(amount=25); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fisherman_storm_catch | fisherman | minecraft:fisherman | fish_item minecraft:cod x4 | weather | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:fletcher_a_fine_bow | fletcher | minecraft:fletcher | craft_item minecraft:bow x1 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fletcher_feather_light | fletcher | minecraft:fletcher | item_delivery minecraft:feather x10 | - | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fletcher_feathers_for_fighters | fletcher | minecraft:fletcher | item_delivery minecraft:arrow x16 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fletcher_full_quiver | fletcher | minecraft:fletcher | item_delivery minecraft:arrow x32 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:fletcher_knapping | fletcher | minecraft:fletcher | item_delivery minecraft:flint x12 | - | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:guard_carry_word_home | guard | mca:guard | reach_location | [any_of[0]]giver_distance_from_village; [any_of[1]]time | currency; xp(amount=24); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_clear_the_night | guard | mca:guard | kill_entity minecraft:zombie x5 | - | currency; xp(amount=35); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:guard_dawn_defense | guard | mca:guard | kill_entity minecraft:zombie x6 | time | currency; xp(amount=32); hearts(amount=14) | - | cooldown | deadline_time,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:guard_defend_the_captain | guard | mca:guard | defend_villager x5 | time | currency; xp(amount=30); hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_hold_the_gate | guard | mca:guard | defend_location x8 | time | currency; xp(amount=30); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_last_stand | guard | mca:guard | defend_location x12 | time | currency; xp(amount=38); hearts(amount=14) | - | cooldown | deadline_time,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:guard_night_watch | guard | mca:guard | kill_entity minecraft:zombie x4 | time | currency; xp(amount=28); hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_phantom_vigil | guard | mca:guard | kill_entity minecraft:phantom x3 | time | currency; xp(amount=28); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_post_raid_sweep | guard | mca:guard | kill_entity minecraft:zombie x6 | village_member | currency; xp(amount=26); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_skeleton_patrol | guard | mca:guard | kill_entity minecraft:skeleton x5 | - | currency; xp(amount=28); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:guard_spider_season | guard | mca:guard | kill_entity minecraft:spider x4 | - | currency; xp(amount=30); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:guard_the_creeper_problem | guard | mca:guard | kill_entity minecraft:creeper x4 | - | currency; xp(amount=26); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:guard_torch_the_dark | guard | mca:guard | build_near_location minecraft:torch x12 | time | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:guard_wolf_at_the_door | guard | mca:guard | kill_entity minecraft:zombie x8 | - | currency; xp(amount=30); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:leatherworker_cobblers_apprentice | leatherworker | minecraft:leatherworker | craft_item minecraft:leather_boots x1 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:leatherworker_rabbit_run | leatherworker | minecraft:leatherworker | obtain_item minecraft:rabbit_hide x6 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:leatherworker_the_tannery | leatherworker | minecraft:leatherworker | item_delivery minecraft:leather x12 | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:leatherworker_tough_hide | leatherworker | minecraft:leatherworker | item_delivery minecraft:leather x8 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:librarian_ink_and_quill | librarian | minecraft:librarian | obtain_item minecraft:ink_sac x4 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:librarian_paper_trail | librarian | minecraft:librarian | item_delivery minecraft:paper x20 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:librarian_take_the_census | librarian | minecraft:librarian | talk_to_profession minecraft:farmer x3 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:librarian_well_read | librarian | minecraft:librarian | item_delivery minecraft:book x3 | - | currency; xp(amount=25); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mason_clay_works | mason | minecraft:mason | obtain_item minecraft:clay_ball x16 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mason_lay_the_path | mason | minecraft:mason | place_block minecraft:stone_bricks x32 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mason_quarry_work | mason | minecraft:mason | break_block minecraft:stone x48 | - | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mason_rebuild_the_wall | mason | minecraft:mason | build_near_location minecraft:stone_bricks x16 | village_member | currency; xp(amount=24); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:mason_solid_foundations | mason | minecraft:mason | item_delivery minecraft:stone x24 | - | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mercenary_bounty | mercenary | mca:mercenary | kill_entity minecraft:creeper x5 | - | currency; xp(amount=32); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mercenary_contract_killing | mercenary | mca:mercenary | kill_entity minecraft:zombie x8 | - | currency; xp(amount=30); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mercenary_pillager_bounty | mercenary | mca:mercenary | kill_entity #minecraft:raiders x6 | - | currency; xp(amount=35); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:mercenary_witch_hunt | mercenary | mca:mercenary | kill_entity minecraft:witch x3 | - | currency; xp(amount=35); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:relations_a_meal_for_mother | relations | any | deliver_to_villager minecraft:bread x6 | related_villager_status | currency; xp(amount=18); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_anniversary_gift | relations | any | item_delivery minecraft:cake x1 | [all_of[0]]is_player_spouse; [all_of[1]]hearts | currency; xp(amount=24); hearts(amount=14) | - | cooldown | - | DEFAULT | hearts_threshold |
| mcaquests:relations_child_treat | relations | any | item_delivery minecraft:cookie x6 | [all_of[0]]is_family_member; [all_of[1]]age_group | currency; xp(amount=15); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_childs_first_toy | relations | any | deliver_to_villager minecraft:white_wool x3 | related_villager_status | currency; xp(amount=16); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_cure_infected_kin | relations | any | cure_villager | related_villager_status | currency; xp(amount=40); hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_cure_my_spouse | relations | any | cure_villager | [all_of[0]]is_player_spouse; [all_of[1]]infected | currency; xp(amount=30); hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_escort_me_home | relations | any | escort_entity | [all_of[0]]giver_distance_from_village; [all_of[1]]time | currency; xp(amount=20); hearts(amount=8) | - | cooldown | deadline_time,fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_escort_to_market | relations | any | escort_entity | giver_distance_from_village | currency; xp(amount=30); hearts(amount=8) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_family_reunion_feast | relations | any | deliver_to_villager minecraft:cooked_beef x8 | [all_of[0]]is_family_member; [all_of[1]]related_villager_status | currency; xp(amount=24); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:feast_of_many_tables | relations | minecraft:farmer, minecraft:butcher, minecraft:cleric | deliver_to_villager minecraft:bread x12; deliver_to_villager minecraft:cooked_beef x8; de… | - | currency; xp(amount=55); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:relations_grandparents_blanket | relations | any | item_delivery minecraft:white_bed x1 | is_family_member | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_guard_village_patrol | relations | mca:guard | kill_entity minecraft:zombie x5 | village_member | currency; xp(amount=30); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_heal_my_beloved | relations | any | heal_entity minecraft:golden_carrot x1 | [all_of[0]]is_player_spouse; [all_of[1]]health_below | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:honey_for_the_healer | relations | minecraft:cleric, minecraft:farmer | breed_animals minecraft:bee x2; craft_item minecraft:honey_block x4; deliver_to_villager … | - | currency; xp(amount=34); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:honored_envoy | relations | mca:guard, minecraft:none, minecraft:nitwit | obtain_item minecraft:paper x8 | reputation_tier | currency; xp(amount=30); hearts(amount=4); grant_title(title=mcaquests:honored_… | - | once | - | DEFAULT | - |
| mcaquests:horse_for_the_courier | relations | minecraft:cartographer, mca:adventurer | tame_animal minecraft:horse x1; reach_location | - | currency; xp(amount=34); hearts(amount=8) | - | once | - | DEFAULT | zero_conditions |
| mcaquests:relations_lead_me_home | relations | any | escort_entity | [any_of[0]]giver_distance_from_village; [any_of[1]/all_of[0]]time; [any_of[1]/all_of[1]]g… | currency; xp(amount=24); hearts(amount=8) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_letter_to_brother | relations | any | deliver_to_villager minecraft:paper x1 | related_villager_status | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_light_the_beacon | relations | any | build_near_location minecraft:lantern x6 | [all_of[0]]village_member; [all_of[1]]time | currency; xp(amount=18); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:long_way_home | relations | minecraft:cartographer, mca:adventurer, minecraft:none, minecraft:nitwit | escort_entity; protect_entity | - | currency; xp(amount=55); hearts(amount=14) | - | cooldown | fail_on_giver_death | DEFAULT | zero_conditions |
| mcaquests:relations_mend_the_quarrel | relations | any | deliver_to_villager minecraft:poppy x1 | related_villager_status | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_missing_child_search | relations | any | find_missing_relative | related_villager_status | currency; hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:monument_of_names | relations | minecraft:cleric, minecraft:librarian | build_near_location minecraft:stone_bricks x24; build_near_location minecraft:candle x8; … | - | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=8) | - | once | - | DEFAULT | zero_conditions |
| mcaquests:relations_protect_my_child | relations | any | protect_entity | related_villager_status | currency; xp(amount=24); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_repair_the_well | relations | any | build_near_location minecraft:stone_bricks x8 | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:relations_reunite_with_spouse | relations | any | escort_entity | [all_of[0]]related_villager_status; [all_of[1]]time | currency; xp(amount=26); hearts(amount=14) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_search_the_ruins | relations | any | find_missing_relative | related_villager_status | currency; xp(amount=26); hearts(amount=8) | - | once | - | DEFAULT | - |
| mcaquests:relations_see_my_child_home | relations | any | escort_entity | [all_of[0]]related_villager_status; [all_of[1]/any_of[0]]giver_distance_from_village; [al… | currency; xp(amount=30); hearts(amount=14) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_sick_villager_remedy | relations | any | item_delivery minecraft:golden_apple x1 | [any_of[0]]health_below; [any_of[1]]infected | currency; hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_spouse_flowers | relations | any | item_delivery minecraft:poppy x5 | [all_of[0]]is_player_spouse; [all_of[1]]hearts | currency; hearts(amount=8) | - | cooldown | - | DEFAULT | hearts_threshold |
| mcaquests:relations_stranded_at_dusk | relations | any | escort_entity | [all_of[0]]giver_distance_from_village; [all_of[1]]time | currency; xp(amount=32); hearts(amount=14) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_sweetheart_cocoa | relations | any | item_delivery minecraft:cookie x6 | is_player_spouse | currency; hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_teach_them_to_fish | relations | any | deliver_to_villager minecraft:cooked_cod x4 | related_villager_status | currency; xp(amount=18); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_long_road_back | relations | any | escort_entity | giver_distance_from_village | currency; xp(amount=36); hearts(amount=14) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_trade_with_blacksmith | relations | any | trade_with_villager minecraft:weaponsmith x2 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:relations_walk_me_to_bed | relations | any | escort_entity | time | currency; xp(amount=22); hearts(amount=8) | - | cooldown | fail_on_giver_death,failure_hearts,retry_after | DEFAULT | - |
| mcaquests:relations_walk_the_walls | relations | any | kill_entity minecraft:zombie x4 | [all_of[0]]village_member; [all_of[1]]time | currency; xp(amount=18); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:relations_widow_memorial | relations | any | deliver_to_villager minecraft:poppy x3 | [all_of[0]]related_villager_status; [all_of[1]]related_villager_status | currency; hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:shepherd_a_dyers_dozen | shepherd | minecraft:shepherd | item_delivery minecraft:black_wool x12 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:shepherd_spin_a_yarn | shepherd | minecraft:shepherd | obtain_item minecraft:string x16 | - | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:shepherd_warm_blankets | shepherd | minecraft:shepherd | craft_item minecraft:white_carpet x6 | - | currency; xp(amount=16); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:shepherd_wool_gathering | shepherd | minecraft:shepherd | item_delivery minecraft:white_wool x12 | - | currency; xp(amount=15); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:template_cartographer_survey | templates | minecraft:cartographer | - | - | - | - | cooldown | - | {"mode":"self_complete"} | zero_conditions,zero_rewards |
| mcaquests:template_farmer_crop_request | templates | minecraft:farmer | - | - | - | - | cooldown | - | DEFAULT | zero_conditions,zero_rewards |
| mcaquests:template_fisherman_catch | templates | minecraft:fisherman | - | - | - | - | cooldown | - | DEFAULT | zero_conditions,zero_rewards |
| mcaquests:template_guard_mob_cull | templates | mca:guard | - | - | - | - | cooldown | - | DEFAULT | zero_conditions,zero_rewards |
| mcaquests:template_kin_errand | templates | any | - | related_villager_status | - | - | cooldown | - | DEFAULT | zero_rewards |
| mcaquests:template_librarian_knowledge | templates | minecraft:librarian | - | - | - | - | cooldown | - | DEFAULT | zero_conditions,zero_rewards |
| mcaquests:template_mercenary_bounty | templates | mca:mercenary | - | - | - | - | cooldown | - | DEFAULT | zero_conditions,zero_rewards |
| mcaquests:toolsmith_a_proper_kit | toolsmith | minecraft:toolsmith | craft_item minecraft:iron_pickaxe x1 | - | currency; xp(amount=20); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:toolsmith_deep_delve | toolsmith | minecraft:toolsmith | break_block #minecraft:iron_ores x5 | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:toolsmith_sharp_and_ready | toolsmith | minecraft:toolsmith | craft_item minecraft:iron_axe x1 | - | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:toolsmith_temper_in_battle | toolsmith | minecraft:toolsmith | kill_entity minecraft:zombie x4 | - | currency; xp(amount=20); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:toolsmith_tools_of_the_trade | toolsmith | minecraft:toolsmith | item_delivery minecraft:iron_ingot x5 | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:townstead_a_balanced_day | townstead | minecraft:farmer, minecraft:librarian, minecraft:cleric | townstead_state True | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=32); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_a_proper_nights_rest | townstead | minecraft:farmer, minecraft:librarian, minecraft:toolsmith, minecraft:mason | townstead_change 0 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=30); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_care_for_the_young | townstead | minecraft:farmer, minecraft:cleric, minecraft:butcher | item_delivery minecraft:cooked_beef x8 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=24); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_deep_water_days | townstead | minecraft:fisherman | fish_item #mcaquests:harbor_catch x12; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_building | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_dockside_catch | townstead | minecraft:fisherman | townstead_building_registered dock x1 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=45); hearts(amount=14); xp(amount=6); townstead_reaction(ta… | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_fill_the_wool_shed | townstead | minecraft:shepherd | item_delivery minecraft:white_wool x24 | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_building | currency; xp(amount=34); hearts(amount=8); townstead_profession_xp(amount=45) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_first_shift | townstead | minecraft:farmer, minecraft:fisherman, minecraft:shepherd, minecraft:butcher | townstead_state work | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=28); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_founding_character | townstead | minecraft:cleric, minecraft:librarian, minecraft:mason | townstead_spirit_progress | [all_of[0]]townstead_available | currency; xp(amount=50); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_fuel_the_smoker | townstead | minecraft:butcher | item_delivery minecraft:charcoal x16 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=20); hearts(amount=4); townstead_profession_xp(amount=20) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_full_granary | townstead | minecraft:farmer | item_delivery minecraft:wheat x32 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=34); hearts(amount=8); townstead_profession_xp(amount=45) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_growing_community | townstead | minecraft:mason, minecraft:cleric | townstead_building_registered dock x1; townstead_spirit_progress | [all_of[0]]townstead_available | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_healthy_workforce | townstead | minecraft:farmer, minecraft:cleric | townstead_healthy_residents | [all_of[0]]townstead_available | currency; xp(amount=45); hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_leatherworkers_order | townstead | minecraft:leatherworker | item_delivery minecraft:leather x16 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=20); hearts(amount=4); xp(amount=6); townstead_reaction(tas… | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_master_of_the_trade | townstead | minecraft:farmer, minecraft:shepherd, minecraft:butcher | townstead_profession_progress | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_professi… | currency; xp(amount=60); hearts(amount=14); townstead_reaction(task=mcaquests:t… | - | once | - | DEFAULT | - |
| mcaquests:townstead_mend_the_nets | townstead | minecraft:fisherman | item_delivery minecraft:string x12 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=20); hearts(amount=4); xp(amount=6); townstead_reaction(tas… | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_pantry_run | townstead | minecraft:farmer, minecraft:butcher, minecraft:shepherd | item_delivery minecraft:bread x12 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=20); hearts(amount=4) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_plan_the_fields | townstead | minecraft:farmer | townstead_state work | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=20); hearts(amount=4); townstead_profession_xp(amount=20) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_shears_and_shelter | townstead | minecraft:shepherd | townstead_building_registered pen x1 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=30); hearts(amount=8); townstead_profession_xp(amount=35) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_stock_the_smokehouse | townstead | minecraft:butcher | item_delivery minecraft:cooked_porkchop x16 | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_building | currency; xp(amount=34); hearts(amount=8); townstead_profession_xp(amount=45) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_tanned_and_ready | townstead | minecraft:leatherworker | townstead_state work | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=32); hearts(amount=8); xp(amount=6); townstead_reaction(tas… | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_the_long_harvest | townstead | minecraft:farmer | townstead_profession_progress minecraft:farmer | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value; [… | currency; xp(amount=60); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_the_master_tanner | townstead | minecraft:leatherworker | townstead_schedule_streak; item_delivery minecraft:leather x32 | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_building | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_the_whole_flock | townstead | minecraft:shepherd | townstead_profession_progress minecraft:shepherd | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value; [… | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_the_winter_cure | townstead | minecraft:butcher | townstead_profession_progress minecraft:butcher | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value; [… | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_a_real_workshop | townstead | minecraft:toolsmith, minecraft:weaponsmith | townstead_building_registered blacksmith x1; build_near_location minecraft:anvil x1; buil… | [all_of[0]]townstead_available; [all_of[1]/not]townstead_building | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_a_week_kept_well | townstead | minecraft:farmer, minecraft:shepherd, minecraft:butcher | townstead_schedule_streak; townstead_state 55; townstead_state 10; townstead_state 8 | [all_of[0]]townstead_available | currency; xp(amount=55); hearts(amount=14) | - | period | - | DEFAULT | - |
| mcaquests:townstead_apprentice_first_full_shift | townstead | minecraft:farmer, minecraft:shepherd, minecraft:butcher | townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_profession_track | currency; xp(amount=34); hearts(amount=8) | apprenticeship_pact 2/4 | once | - | DEFAULT | - |
| mcaquests:townstead_apprentice_masterwork | townstead | minecraft:farmer, minecraft:shepherd, minecraft:butcher | townstead_profession_progress; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_profession_track | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=10); gran… | apprenticeship_pact 4/4 | once | - | DEFAULT | - |
| mcaquests:townstead_apprentice_tools_of_calling | townstead | minecraft:farmer, minecraft:shepherd, minecraft:butcher | item_delivery minecraft:clock x1; item_delivery minecraft:book x1; item_delivery minecraf… | [all_of[0]]townstead_available; [all_of[1]]townstead_profession_track; [all_of[2]]townste… | currency; xp(amount=20); hearts(amount=4) | apprenticeship_pact 1/4 | once | - | DEFAULT | - |
| mcaquests:townstead_apprentice_trusted_hand | townstead | minecraft:farmer, minecraft:shepherd, minecraft:butcher | townstead_profession_progress; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_profession_track | currency; xp(amount=55); hearts(amount=14) | apprenticeship_pact 3/4 | once | - | DEFAULT | - |
| mcaquests:townstead_bookkeepers_census | townstead | minecraft:librarian, minecraft:cartographer | item_delivery minecraft:paper x32; item_delivery minecraft:book x8; talk_to_profession mi… | [all_of[0]]townstead_available; [all_of[1]]townstead_building | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_breakfast_before_bells | townstead | minecraft:farmer, minecraft:mason, minecraft:fisherman, minecraft:shepherd | item_delivery minecraft:bread x8; townstead_change | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value | currency; xp(amount=20); hearts(amount=4) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_character_choose_our_name | townstead | minecraft:cleric, minecraft:librarian, minecraft:mason, minecraft:cartographer | townstead_state settlement | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | village_with_a_name 2/4 | once | - | DEFAULT | - |
| mcaquests:townstead_character_first_mark | townstead | minecraft:cleric, minecraft:librarian, minecraft:mason, minecraft:cartographer | townstead_spirit_progress | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | village_with_a_name 1/4 | once | - | DEFAULT | - |
| mcaquests:townstead_character_living_legacy | townstead | minecraft:cleric, minecraft:librarian, minecraft:mason, minecraft:cartographer | townstead_spirit_progress; townstead_healthy_residents | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=12); gran… | village_with_a_name 4/4 | once | - | DEFAULT | - |
| mcaquests:townstead_character_strength_of_place | townstead | minecraft:cleric, minecraft:librarian, minecraft:mason, minecraft:cartographer | townstead_spirit_progress | [all_of[0]]townstead_available; [all_of[1]/not]townstead_spirit | currency; xp(amount=55); hearts(amount=14) | village_with_a_name 3/4 | once | - | DEFAULT | - |
| mcaquests:townstead_commission_bells_for_old_names | townstead | minecraft:cleric, minecraft:librarian | build_near_location minecraft:candle x12; defend_location minecraft:zombie x8; defend_loc… | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=55); hearts(amount=14) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_breadth_of_the_fields | townstead | minecraft:farmer, minecraft:shepherd | breed_animals minecraft:sheep x6; build_near_location minecraft:hay_block x12 | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_ink_and_index | townstead | minecraft:librarian, minecraft:cartographer | craft_item minecraft:book x12; item_delivery minecraft:paper x24; build_near_location min… | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_iron_sings | townstead | minecraft:toolsmith, minecraft:weaponsmith, minecraft:mason | craft_item minecraft:anvil x1; build_near_location minecraft:blast_furnace x2; build_near… | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=55); hearts(amount=14) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_market_bells | townstead | minecraft:farmer, minecraft:butcher, minecraft:none, minecraft:nitwit | trade_with_villager x12; item_delivery minecraft:emerald x16 | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_salt_and_lanterns | townstead | minecraft:fisherman, minecraft:cartographer | fish_item minecraft:cod x12; build_near_location minecraft:lantern x6 | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_watch_at_the_gate | townstead | mca:guard, minecraft:armorer, minecraft:weaponsmith | defend_location minecraft:pillager x8; build_near_location minecraft:lantern x4 | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=55); hearts(amount=14) | - | period | - | DEFAULT | - |
| mcaquests:townstead_commission_welcome_lights | townstead | minecraft:cartographer, minecraft:cleric, minecraft:none, minecraft:nitwit | build_near_location minecraft:lantern x8; build_near_location minecraft:white_bed x4 | [all_of[0]]townstead_available; [all_of[1]]townstead_spirit | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_day_off_means_day_off | townstead | any | townstead_change; townstead_state work | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_first_workday_as_an_adult | townstead | any | item_delivery minecraft:compass x1; item_delivery minecraft:book x1; reach_location; town… | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value; [… | currency; xp(amount=34); hearts(amount=8) | - | once | - | DEFAULT | - |
| mcaquests:townstead_harbor_deep_water | townstead | minecraft:fisherman, minecraft:cartographer | townstead_building_registered dock x1; townstead_spirit_progress | [all_of[0]]townstead_available | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=8); grant… | harbor_of_hands 4/4 | once | - | DEFAULT | - |
| mcaquests:townstead_harbor_first_piling | townstead | minecraft:fisherman | townstead_building_registered dock x1 | [all_of[0]]townstead_available; [all_of[1]/not]townstead_building | currency; xp(amount=34); hearts(amount=8) | harbor_of_hands 1/4 | once | - | DEFAULT | - |
| mcaquests:townstead_harbor_lantern_line | townstead | minecraft:fisherman | build_near_location minecraft:lantern x6; build_near_location minecraft:chain x12 | [all_of[0]]townstead_available; [all_of[1]]townstead_building | currency; xp(amount=55); hearts(amount=14) | harbor_of_hands 3/4 | once | - | DEFAULT | - |
| mcaquests:townstead_harbor_working_tide | townstead | minecraft:fisherman | fish_item minecraft:cod x8; fish_item minecraft:salmon x8; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_building | currency; xp(amount=34); hearts(amount=8) | harbor_of_hands 2/4 | once | - | DEFAULT | - |
| mcaquests:townstead_harvest_under_gold | townstead | minecraft:farmer | item_delivery minecraft:wheat x64; item_delivery minecraft:carrot x32; item_delivery mine… | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=55); hearts(amount=14) | seasons_of_the_soil 3/4 | once | - | DEFAULT | - |
| mcaquests:townstead_heat_over_the_fields | townstead | minecraft:farmer | item_delivery minecraft:potion x3; townstead_change; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=34); hearts(amount=8) | seasons_of_the_soil 2/4 | once | - | DEFAULT | count_outlier |
| mcaquests:townstead_infirmary_before_frost | townstead | minecraft:cleric, minecraft:mason | townstead_building_registered infirmary x1; item_delivery minecraft:honey_bottle x12; ite… | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]/not]townstead_buil… | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_lanterns_for_late_shift | townstead | minecraft:fisherman, minecraft:butcher, minecraft:toolsmith, mca:guard | item_delivery minecraft:lantern x6; townstead_state work | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]time | currency; xp(amount=34); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_lanterns_for_the_departed | townstead | minecraft:cleric | build_near_location minecraft:candle x8; build_near_location minecraft:lantern x4; defend… | [all_of[0]]townstead_available; [all_of[1]]townstead_building | currency; xp(amount=55); hearts(amount=14) | - | period | - | DEFAULT | - |
| mcaquests:townstead_names_in_the_family_book | townstead | any | deliver_to_villager minecraft:book x1; talk_to_profession minecraft:librarian x1 | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]is_family_member; … | currency; xp(amount=34); hearts(amount=8) | - | once | - | DEFAULT | - |
| mcaquests:townstead_pasture_first_fence | townstead | minecraft:shepherd | townstead_building_registered pen x1 | [all_of[0]]townstead_available | currency; xp(amount=34); hearts(amount=8) | wool_and_winter 1/4 | once | - | DEFAULT | - |
| mcaquests:townstead_pasture_keeper_of_the_flock | townstead | minecraft:shepherd | townstead_profession_progress; townstead_spirit_progress | [all_of[0]]townstead_available; [all_of[1]]townstead_profession_track | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=8) | wool_and_winter 4/4 | once | - | DEFAULT | - |
| mcaquests:townstead_pasture_lambing_day | townstead | minecraft:shepherd | breed_animals minecraft:sheep x6; item_delivery minecraft:shears x2 | [all_of[0]]townstead_available; [all_of[1]]townstead_building | currency; xp(amount=34); hearts(amount=8) | wool_and_winter 2/4 | once | - | DEFAULT | - |
| mcaquests:townstead_pasture_wool_under_roof | townstead | minecraft:shepherd | townstead_building_registered wool_shed x1; item_delivery minecraft:white_wool x32; towns… | [all_of[0]]townstead_available | currency; xp(amount=55); hearts(amount=14) | wool_and_winter 3/4 | once | - | DEFAULT | - |
| mcaquests:townstead_rest_after_the_alarm | townstead | mca:guard, minecraft:cleric, minecraft:farmer | townstead_change; protect_entity | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value | currency; xp(amount=34); hearts(amount=8) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_rooms_for_the_road | townstead | minecraft:cleric, minecraft:cartographer | townstead_building_registered inn x1; build_near_location minecraft:white_bed x4; build_n… | [all_of[0]]townstead_available; [all_of[1]/not]townstead_building | currency; xp(amount=55); hearts(amount=14) | - | once | - | DEFAULT | - |
| mcaquests:townstead_smokehouse_first_fire | townstead | minecraft:butcher | townstead_building_registered butcher_shop x1 | [all_of[0]]townstead_available; [all_of[1]/not]townstead_building | currency; xp(amount=34); hearts(amount=8) | smokehouse_legacy 1/4 | once | - | DEFAULT | - |
| mcaquests:townstead_smokehouse_honest_shift | townstead | minecraft:butcher | item_delivery minecraft:charcoal x16; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_building | currency; xp(amount=34); hearts(amount=8) | smokehouse_legacy 2/4 | once | - | DEFAULT | - |
| mcaquests:townstead_smokehouse_legacy | townstead | minecraft:butcher | townstead_profession_progress; townstead_spirit_progress | [all_of[0]]townstead_available; [all_of[1]]townstead_profession_track | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=8) | smokehouse_legacy 4/4 | once | - | DEFAULT | - |
| mcaquests:townstead_smokehouse_winter_stores | townstead | minecraft:butcher | item_delivery minecraft:cooked_beef x16; item_delivery minecraft:cooked_porkchop x16; ite… | [all_of[0]]townstead_available; [all_of[1]/any_of[0]]townstead_value; [all_of[1]/any_of[1… | currency; xp(amount=55); hearts(amount=14) | smokehouse_legacy 3/4 | once | - | DEFAULT | - |
| mcaquests:townstead_spring_bells_and_blossoms | townstead | minecraft:farmer, minecraft:cleric | build_near_location #minecraft:flowers x12; build_near_location minecraft:bell x1 | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_spring_in_the_furrows | townstead | minecraft:farmer | item_delivery minecraft:wheat_seeds x32; townstead_schedule_streak | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=34); hearts(amount=8) | seasons_of_the_soil 1/4 | once | - | DEFAULT | - |
| mcaquests:townstead_stores_against_winter | townstead | minecraft:farmer, minecraft:butcher, minecraft:cleric | item_delivery minecraft:coal x32; item_delivery minecraft:bread x24; item_delivery minecr… | [all_of[0]]townstead_available; [all_of[1]/any_of[0]]townstead_value; [all_of[1]/any_of[1… | currency; xp(amount=34); hearts(amount=8) | - | period | - | DEFAULT | - |
| mcaquests:townstead_the_elders_old_route | townstead | any | escort_entity; escort_entity; protect_entity | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=55); hearts(amount=14) | - | once | fail_on_giver_death | DEFAULT | - |
| mcaquests:townstead_water_bearers_rounds | townstead | minecraft:farmer, minecraft:cleric | townstead_healthy_residents | [all_of[0]]townstead_available | currency; xp(amount=55); hearts(amount=14) | - | cooldown | - | DEFAULT | - |
| mcaquests:townstead_winter_at_the_table | townstead | minecraft:farmer | townstead_healthy_residents; townstead_spirit_progress | [all_of[0]]townstead_available; [all_of[1]]townstead_value | currency; xp(amount=55); hearts(amount=14); village_reputation(amount=8); grant… | seasons_of_the_soil 4/4 | once | - | DEFAULT | - |
| mcaquests:townstead_water_for_the_weary | townstead | minecraft:farmer, minecraft:fisherman, minecraft:mason | item_delivery minecraft:potion x1 | [all_of[0]]townstead_available; [all_of[1]]townstead_value; [all_of[2]]townstead_value | currency; xp(amount=18); hearts(amount=4) | - | cooldown | - | DEFAULT | - |
| mcaquests:unemployed_a_splash_of_color | unemployed | minecraft:none, minecraft:nitwit | obtain_item #minecraft:small_flowers x8 | - | currency; xp(amount=12); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:unemployed_apple_a_day | unemployed | minecraft:none, minecraft:nitwit | item_delivery minecraft:apple x6 | - | currency; xp(amount=12); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:unemployed_berry_picking | unemployed | minecraft:none, minecraft:nitwit | obtain_item minecraft:sweet_berries x12 | - | currency; xp(amount=13); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:unemployed_egg_hunt | unemployed | minecraft:none, minecraft:nitwit | obtain_item minecraft:egg x6 | - | currency; xp(amount=12); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:unemployed_helping_hand | unemployed | minecraft:none, minecraft:nitwit | item_delivery minecraft:bread x6 | - | currency; xp(amount=12); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:unemployed_kindling | unemployed | minecraft:none, minecraft:nitwit | obtain_item minecraft:stick x16 | - | currency; xp(amount=10); hearts(amount=4) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:unemployed_lend_a_blade | unemployed | minecraft:none, minecraft:nitwit | kill_entity minecraft:zombie x3 | - | currency; xp(amount=16); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:weaponsmith_bone_collector | weaponsmith | minecraft:weaponsmith | kill_entity minecraft:skeleton x6 | - | currency; xp(amount=30); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:weaponsmith_creeper_cull | weaponsmith | minecraft:weaponsmith | kill_entity minecraft:creeper x3 | - | currency; xp(amount=28); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:weaponsmith_drowned_depths | weaponsmith | minecraft:weaponsmith | kill_entity minecraft:drowned x5 | - | currency; xp(amount=28); hearts(amount=8) | - | cooldown | - | DEFAULT | mob_not_in_village_biome,zero_conditions |
| mcaquests:weaponsmith_proving_the_steel | weaponsmith | minecraft:weaponsmith | kill_entity minecraft:zombie x6 | - | item(item=minecraft:iron_sword,count=1); xp(amount=26); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:weaponsmith_temper_the_blade | weaponsmith | minecraft:weaponsmith | item_delivery minecraft:iron_ingot x6 | - | currency; xp(amount=22); hearts(amount=8) | - | cooldown | - | DEFAULT | zero_conditions |
| mcaquests:weaponsmith_the_horde | weaponsmith | minecraft:weaponsmith | kill_entity minecraft:zombie x8 | - | currency; xp(amount=28); hearts(amount=14) | - | cooldown | - | DEFAULT | zero_conditions |

## 3 Templates

| id | variables | kinds | objectives |
| --- | --- | --- | --- |
| mcaquests:template_cartographer_survey | place | place:biome | - |
| mcaquests:template_farmer_crop_request | crop, count, bounty | crop:item, count:int, bounty:int | - |
| mcaquests:template_fisherman_catch | catch, count | catch:item, count:int | - |
| mcaquests:template_guard_mob_cull | foe, count | foe:entity, count:int | - |
| mcaquests:template_kin_errand | gift, count | gift:item, count:int | - |
| mcaquests:template_librarian_knowledge | supply, count | supply:item, count:int | - |
| mcaquests:template_mercenary_bounty | foe, count, bounty | foe:entity, count:int, bounty:int | - |

## 4 Projects

| id | scope | sponsors | on_death | phases | objectives | rewards (type→target) | reputation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| mcaquests:after_raid_recovery | village | mca:guard | transfer | 2 | project_kill_entity minecraft:zombie x8; donate_item minecraft:oak_planks x64; donate_ite… | xp→contributors; hearts_with_participants→all_participants; loot_table→all_participants | {"on_phase_complete":3,"on_project_complete":10} |
| mcaquests:festival_preparation | village | any | pause | 3 | donate_item minecraft:bread x32; donate_item minecraft:cooked_beef x16; project_place_blo… | currency→contributors; item→contributors; hearts_with_participants→all_participants; vill… | {"on_project_complete":10} |
| mcaquests:guardhouse_stockpile | village | mca:guard | transfer | 2 | donate_item minecraft:iron_ingot x32; donate_item minecraft:arrow x64; donate_item minecr… | currency→contributors; hearts_with_participants→all_participants; currency→top_contributo… | {"on_phase_complete":2,"on_project_complete":8} |
| mcaquests:library_restoration | {'scope': 'profession', 'professions': ['minecraft:librarian']} | minecraft:librarian | pause | 2 | donate_item minecraft:paper x64; donate_item minecraft:book x16; project_talk_to_professi… | xp→contributors; loot_table→all_participants; hearts_with_participants→all_participants | {"on_project_complete":6} |
| mcaquests:missing_villager_search | village | any | pause | 2 | project_talk_to_profession minecraft:cartographer x3; donate_item minecraft:bread x16; do… | xp→contributors; hearts_with_participants→all_participants; currency→contributors | - |
| mcaquests:muster_the_militia | {'scope': 'village', 'fallback_radius': 64} | mca:guard | transfer | 2 | donate_item minecraft:iron_ingot x24; donate_item minecraft:arrow x64; project_kill_entit… | currency→contributors; hearts_with_participants→all_participants; currency→top_contributo… | {"on_phase_complete":3,"on_project_complete":12} |
| mcaquests:rebuild_the_walls | {'scope': 'village', 'fallback_radius': 64} | minecraft:mason | transfer | 2 | donate_item minecraft:stone_bricks x96; donate_item minecraft:torch x16; project_place_bl… | xp→contributors; hearts_with_participants→all_participants; currency→top_contributor; vil… | {"on_phase_complete":2,"on_project_complete":10} |
| mcaquests:roads_and_lanterns | {'scope': 'village', 'fallback_radius': 64} | minecraft:cartographer, minecraft:mason | transfer | 3 | donate_item minecraft:gravel x256; donate_item minecraft:stone_bricks x128; donate_item m… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":18} |
| mcaquests:townstead_a_working_village | {'scope': 'village', 'fallback_radius': 64} | minecraft:mason, minecraft:cleric | transfer | 2 | donate_item minecraft:iron_ingot x24; donate_item minecraft:bread x32; townstead_workforc… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":16} |
| mcaquests:townstead_find_our_character | {'scope': 'village', 'fallback_radius': 64} | minecraft:cleric, minecraft:librarian | transfer | 2 | donate_item minecraft:stone_bricks x64; donate_item minecraft:glass x16; townstead_spirit… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":18} |
| mcaquests:townstead_pastures_and_wool | {'scope': 'village', 'fallback_radius': 64} | minecraft:shepherd | transfer | 2 | donate_item minecraft:oak_fence x48; donate_item minecraft:hay_block x8; townstead_buildi… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":12} |
| mcaquests:townstead_raise_the_docks | {'scope': 'village', 'fallback_radius': 64} | minecraft:fisherman | transfer | 2 | donate_item minecraft:oak_planks x96; donate_item minecraft:oak_log x32; townstead_buildi… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":14} |
| mcaquests:townstead_apprentices_guild | {'scope': 'village', 'fallback_radius': 64} | minecraft:farmer, minecraft:shepherd, minecraft:butcher, minecraft:librarian, minecraft:mason | transfer | 3 | donate_item minecraft:book x24; donate_item minecraft:clock x6; donate_item minecraft:iro… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":24} |
| mcaquests:townstead_civic_quarter | {'scope': 'village', 'fallback_radius': 64} | minecraft:cleric, minecraft:librarian, minecraft:mason | transfer | 3 | donate_item minecraft:stone_bricks x128; donate_item minecraft:glass x48; donate_item min… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":24} |
| mcaquests:townstead_four_seasons_larder | {'scope': 'village', 'fallback_radius': 64} | minecraft:farmer, minecraft:butcher | transfer | 4 | donate_item minecraft:wheat_seeds x128; donate_item minecraft:carrot x64; donate_item min… | xp→contributors; currency→top_contributor; xp→contributors; currency→top_contributor; xp→… | {"on_project_complete":20} |
| mcaquests:townstead_harbor_quarter | {'scope': 'village', 'fallback_radius': 64} | minecraft:fisherman, minecraft:cartographer | transfer | 3 | donate_item minecraft:oak_planks x128; donate_item minecraft:oak_log x48; donate_item min… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":22} |
| mcaquests:townstead_known_far_and_wide | {'scope': 'village', 'fallback_radius': 64} | minecraft:cartographer, minecraft:cleric, minecraft:none | transfer | 3 | donate_item minecraft:white_bed x12; donate_item minecraft:lantern x24; donate_item minec… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":22} |
| mcaquests:townstead_rest_for_all | {'scope': 'village', 'fallback_radius': 64} | minecraft:cleric, minecraft:farmer, minecraft:shepherd | transfer | 2 | donate_item minecraft:white_bed x12; donate_item minecraft:bread x96; donate_item minecra… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":18} |
| mcaquests:townstead_well_fed_townstead | {'scope': 'village', 'fallback_radius': 64} | minecraft:farmer, minecraft:butcher | transfer | 2 | donate_item minecraft:bread x48; donate_item minecraft:cooked_beef x24; townstead_residen… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":12} |
| mcaquests:walls_before_winter | {'scope': 'village', 'fallback_radius': 64} | mca:guard, minecraft:mason, minecraft:armorer, minecraft:weaponsmith | transfer | 3 | donate_item minecraft:cobblestone x256; donate_item minecraft:iron_ingot x64; donate_item… | xp→contributors; currency→top_contributor; hearts_with_participants→all_participants; vil… | {"on_project_complete":20} |
| mcaquests:well_repair | {'scope': 'village', 'fallback_radius': 64} | minecraft:mason | transfer | 2 | donate_item minecraft:stone x64; donate_item minecraft:bucket x2; project_place_block min… | xp→contributors; hearts_with_participants→all_participants; currency→top_contributor; vil… | {"on_project_complete":10} |

## 5 Type registries

### Objective types

| id | class | uses | progress source |
| --- | --- | --- | --- |
| mcaquests:item_delivery | ItemDeliveryObjective | 89 | see handlers table |
| mcaquests:townstead_state | TownsteadStateObjective | 11 | see handlers table |
| mcaquests:townstead_change | TownsteadChangeObjective | 5 | see handlers table |
| mcaquests:townstead_profession_progress | TownsteadProfessionProgressObjective | 8 | see handlers table |
| mcaquests:townstead_building_registered | TownsteadBuildingRegisteredObjective | 11 | see handlers table |
| mcaquests:townstead_spirit_progress | TownsteadSpiritProgressObjective | 9 | see handlers table |
| mcaquests:townstead_healthy_residents | TownsteadHealthyResidentsObjective | 5 | see handlers table |
| mcaquests:townstead_schedule_streak | TownsteadScheduleStreakObjective | 12 | see handlers table |
| mcaquests:obtain_item | ObtainItemObjective | 23 | see handlers table |
| mcaquests:kill_entity | KillEntityObjective | 34 | see handlers table |
| mcaquests:break_block | BreakBlockObjective | 2 | see handlers table |
| mcaquests:visit_biome | VisitBiomeObjective | 8 | see handlers table |
| mcaquests:visit_dimension | VisitDimensionObjective | 6 | see handlers table |
| mcaquests:craft_item | CraftItemObjective | 13 | see handlers table |
| mcaquests:place_block | PlaceBlockObjective | 2 | see handlers table |
| mcaquests:fish_item | FishItemObjective | 7 | see handlers table |
| mcaquests:talk_to_profession | TalkToProfessionObjective | 9 | see handlers table |
| mcaquests:escort_entity | EscortEntityObjective | 17 | see handlers table |
| mcaquests:protect_entity | ProtectEntityObjective | 7 | see handlers table |
| mcaquests:defend_villager | DefendVillagerObjective | 1 | see handlers table |
| mcaquests:trade_with_villager | TradeWithVillagerObjective | 2 | see handlers table |
| mcaquests:heal_entity | HealEntityObjective | 3 | see handlers table |
| mcaquests:cure_villager | CureVillagerObjective | 3 | see handlers table |
| mcaquests:breed_animals | BreedAnimalsObjective | 3 | see handlers table |
| mcaquests:tame_animal | TameAnimalObjective | 1 | see handlers table |
| mcaquests:sleep_or_rest | SleepOrRestObjective | 0 | see handlers table |
| mcaquests:build_near_location | BuildNearLocationObjective | 31 | see handlers table |
| mcaquests:enter_structure | EnterStructureObjective | 4 | see handlers table |
| mcaquests:deliver_to_villager | DeliverToVillagerObjective | 25 | see handlers table |
| mcaquests:find_missing_relative | FindMissingRelativeObjective | 3 | see handlers table |
| mcaquests:reach_location | ReachLocationObjective | 4 | see handlers table |
| mcaquests:defend_location | DefendLocationObjective | 11 | see handlers table |
| mcaquests:ftbq_complete_quest | FtbqCompleteQuestObjective | 0 | see handlers table |

### Condition types

| id | class | uses | progress source |
| --- | --- | --- | --- |
| mcareputation:has_incident | HasIncidentCondition | 0 | see handlers table |
| mcaquests:townstead_available | TownsteadAvailableCondition | 73 | see handlers table |
| mcaquests:townstead_value | TownsteadValueCondition | 49 | see handlers table |
| mcaquests:townstead_building | TownsteadBuildingCondition | 15 | see handlers table |
| mcaquests:townstead_spirit | TownsteadSpiritCondition | 12 | see handlers table |
| mcaquests:townstead_skill | TownsteadSkillCondition | 0 | see handlers table |
| mcaquests:townstead_profession_track | TownsteadProfessionTrackCondition | 10 | see handlers table |
| mcaquests:hearts | HeartsCondition | 4 | see handlers table |
| mcaquests:profession | ProfessionCondition | 0 | see handlers table |
| mcaquests:biome | BiomeCondition | 0 | see handlers table |
| mcaquests:dimension | DimensionCondition | 0 | see handlers table |
| mcaquests:time | TimeCondition | 21 | see handlers table |
| mcaquests:weather | WeatherCondition | 3 | see handlers table |
| mcaquests:item_held | ItemHeldCondition | 0 | see handlers table |
| mcaquests:advancement | AdvancementCondition | 0 | see handlers table |
| mcaquests:player_level | PlayerLevelCondition | 3 | see handlers table |
| mcaquests:random_chance | RandomChanceCondition | 0 | see handlers table |
| mcaquests:quest_completed | QuestCompletedCondition | 8 | see handlers table |
| mcaquests:quest_not_completed | QuestNotCompletedCondition | 0 | see handlers table |
| mcaquests:quest_failed | QuestFailedCondition | 4 | see handlers table |
| mcaquests:quest_abandoned | QuestAbandonedCondition | 1 | see handlers table |
| mcaquests:quest_declined | QuestDeclinedCondition | 0 | see handlers table |
| mcaquests:is_player_spouse | IsPlayerSpouseCondition | 5 | see handlers table |
| mcaquests:relationship_state | RelationshipStateCondition | 1 | see handlers table |
| mcaquests:is_family_member | IsFamilyMemberCondition | 4 | see handlers table |
| mcaquests:age_group | AgeGroupCondition | 1 | see handlers table |
| mcaquests:personality | PersonalityCondition | 0 | see handlers table |
| mcaquests:mood | MoodCondition | 0 | see handlers table |
| mcaquests:village_member | VillageMemberCondition | 5 | see handlers table |
| mcaquests:has_home | HasHomeCondition | 2 | see handlers table |
| mcaquests:health_below | HealthBelowCondition | 3 | see handlers table |
| mcaquests:infected | InfectedCondition | 3 | see handlers table |
| mcaquests:related_villager_status | RelatedVillagerStatusCondition | 24 | see handlers table |
| mcaquests:village_reputation | VillageReputationCondition | 0 | see handlers table |
| mcaquests:reputation_tier | ReputationTierCondition | 1 | see handlers table |
| mcaquests:giver_distance_from_village | GiverDistanceFromVillageCondition | 8 | see handlers table |
| mcaquests:ftbq_quest_completed | FtbqQuestCompletedCondition | 0 | see handlers table |
| mcaquests:ftbq_chapter_completed | FtbqChapterCompletedCondition | 0 | see handlers table |
| mcaquests:ftbq_task_completed | FtbqTaskCompletedCondition | 0 | see handlers table |

### Reward types

| id | class | uses | progress source |
| --- | --- | --- | --- |
| mcaquests:item | ItemReward | 7 | see handlers table |
| mcaquests:xp | XpReward | 274 | see handlers table |
| mcaquests:hearts | HeartsReward | 255 | see handlers table |
| mcaquests:xp_levels | XpLevelsReward | 0 | see handlers table |
| mcaquests:effect | EffectReward | 0 | see handlers table |
| mcaquests:loot_table | LootTableReward | 2 | see handlers table |
| mcaquests:command | CommandReward | 0 | see handlers table |
| mcaquests:hearts_with_sponsor | HeartsWithSponsorReward | 0 | see handlers table |
| mcaquests:hearts_with_participants | HeartsWithParticipantsReward | 27 | see handlers table |
| mcareputation:resolve_incident | ResolveIncidentReward | 0 | see handlers table |
| mcareputation:record_incident | RecordIncidentReward | 0 | see handlers table |
| mcaquests:village_reputation | VillageReputationReward | 34 | see handlers table |
| mcaquests:unlock | UnlockReward | 0 | see handlers table |
| mcaquests:townstead_needs | TownsteadNeedsReward | 0 | see handlers table |
| mcaquests:townstead_profession_xp | TownsteadProfessionXpReward | 6 | see handlers table |
| mcaquests:townstead_skill | TownsteadSkillReward | 0 | see handlers table |
| mcaquests:townstead_reaction | TownsteadReactionReward | 5 | see handlers table |
| mcaquests:grant_title | GrantTitleReward | 8 | see handlers table |
| mcaquests:currency | CurrencyReward | 276 | see handlers table |
| mcaquests:ftbq_progress | FtbqProgressReward | 0 | see handlers table |

### Project objective types

| id | class | uses | progress source |
| --- | --- | --- | --- |
| mcaquests:donate_item | DonateItemObjective | 61 | see handlers table |
| mcaquests:townstead_building_project | TownsteadBuildingProjectObjective | 10 | see handlers table |
| mcaquests:townstead_spirit_project | TownsteadSpiritProjectObjective | 8 | see handlers table |
| mcaquests:townstead_workforce_project | TownsteadWorkforceProjectObjective | 4 | see handlers table |
| mcaquests:townstead_resident_wellbeing_project | TownsteadResidentWellbeingProjectObjective | 3 | see handlers table |
| mcaquests:project_kill_entity | ProjectKillObjective | 5 | see handlers table |
| mcaquests:project_place_block | ProjectPlaceBlockObjective | 7 | see handlers table |
| mcaquests:project_talk_to_profession | ProjectTalkObjective | 5 | see handlers table |

### Progress sources (handler audit)


#### 1. Objective types (33) -- quest/objective/ObjectiveTypes.java

General pattern: accumulation objectives implement plain QuestObjective, are event-driven
(isEventDriven()), and are advanced by @SubscribeEvent handlers in event/QuestProgressEvents.java
via the forActiveObjectives(player, Type.class, action) helper (defined at
QuestProgressEvents.java:863-891). Every handler checked began with a server-side guard (confirmed
at lines 98,107,111,136,189,335,351,363,645,682,699,716,720,730,734,744,748,771,798,801). Progress
cap: each type's current(ServerPlayer, ObjectiveProgress) clamps with Math.min(progress.count(),
required) (confirmed pattern at KillEntityObjective.java:75); the raw counter is floored at 0 in
ObjectiveProgress.java:53. This pattern is uniform, so per-row cap cells cite it rather than
re-verifying all 20 individually.

Townstead-family objectives plus find_missing_relative and ftbq_complete_quest implement
PollingObjective and are polled generically at QuestProgressEvents.java:405
(forActiveObjectives(player, PollingObjective.class, ...)), gated by a TownsteadObjective due-check
at line 407. item_delivery and obtain_item are POSSESSION objectives (QuestObjective.java:16-18
doc): no counter is ever incremented -- current()/isSatisfied() read live inventory state each
check, so they have no event handler by design, not by omission.

| id | class | kind | advancing code | event/bridge | server guard | capped | notes |
|---|---|---|---|---|---|---|---|
| item_delivery | ItemDeliveryObjective | possession | current/isSatisfied read live inventory; delivered at turn-in QuestManager.java:859-861 | n/a (live-state) | n/a | n/a | canDeliver/deliver gate at QuestManager.java:772-787 |
| townstead_state | TownsteadStateObjective | polling | QuestProgressEvents.java:405-407 | PollingObjective poll via TownsteadObjective due-check | loop at 405 | clamp pattern | |
| townstead_change | TownsteadChangeObjective | polling | QuestProgressEvents.java:405-407 | same | same | same | |
| townstead_profession_progress | TownsteadProfessionProgressObjective | polling | QuestProgressEvents.java:405-407 | same | same | same | |
| townstead_building_registered | TownsteadBuildingRegisteredObjective | polling | QuestProgressEvents.java:405-407 | same | same | same | |
| townstead_spirit_progress | TownsteadSpiritProgressObjective | polling | QuestProgressEvents.java:405-407 | same | same | same | |
| townstead_healthy_residents | TownsteadHealthyResidentsObjective | polling | QuestProgressEvents.java:405-407 | same | same | same | |
| townstead_schedule_streak | TownsteadScheduleStreakObjective | polling | QuestProgressEvents.java:405-407 | same | same | same | counts whole shifts, not hold_ticks (ObjectiveTypes.java:41-45) |
| obtain_item | ObtainItemObjective | possession | current/isSatisfied read live inventory; validated at load ObjectiveValidator.java:126 | n/a (live-state) | n/a | n/a | no QuestManager/QuestProgressEvents advancing code found -- by design, not a gap |
| kill_entity | KillEntityObjective | event | QuestProgressEvents.java:114 | LivingDeathEvent handler ~98-111 | line 98/107 | Math.min pattern (KillEntityObjective.java:75) | |
| break_block | BreakBlockObjective | event | QuestProgressEvents.java:352 | BlockEvent.BreakEvent handler ~351 | line 351 | pattern | |
| visit_biome | VisitBiomeObjective | event | QuestProgressEvents.java:374 | tick handler ~363 | line 363 | pattern | |
| visit_dimension | VisitDimensionObjective | event | QuestProgressEvents.java:380 | tick handler ~363 | line 363 | pattern | |
| craft_item | CraftItemObjective | event | QuestProgressEvents.java:684 | ItemCraftedEvent handler ~682 | line 682 | pattern | |
| place_block | PlaceBlockObjective | event | QuestProgressEvents.java:703 | BlockEvent.EntityPlaceEvent handler ~699 | line 699 | pattern | |
| fish_item | FishItemObjective | event | QuestProgressEvents.java:775 | ItemFishedEvent handler ~771 | line 771 | pattern | |
| talk_to_profession | TalkToProfessionObjective | event | QuestProgressEvents.java:830 | interact handler ~798-801 | line 798/801 | pattern | |
| escort_entity | EscortEntityObjective | event | QuestProgressEvents.java:206-209, 369, 386 | staged tick + PlayerTickEvent | line 208/363 | pattern | isStaged guard at line 208 |
| protect_entity | ProtectEntityObjective | event | QuestProgressEvents.java:156-157, 388 | death event ~136 | line 136 | pattern | |
| defend_villager | DefendVillagerObjective | event | QuestProgressEvents.java:120 | death event ~111 | line 111 | pattern | |
| trade_with_villager | TradeWithVillagerObjective | event | QuestProgressEvents.java:724 | trade event ~716-720 | line 716/720 | pattern | |
| heal_entity | HealEntityObjective | event | QuestProgressEvents.java:807 | heal handler | not individually re-checked | pattern | |
| cure_villager | CureVillagerObjective | event | QuestProgressEvents.java:390, 809 | cure event ~645 | line 645 | pattern | |
| breed_animals | BreedAnimalsObjective | event | QuestProgressEvents.java:752 | BabyEntitySpawnEvent handler ~744-748 | line 744/748 | pattern | |
| tame_animal | TameAnimalObjective | event | QuestProgressEvents.java:738 | AnimalTameEvent handler ~730-734 | line 730/734 | pattern | |
| sleep_or_rest | SleepOrRestObjective | event | QuestProgressEvents.java:763 | sleep handler | not individually re-checked | pattern | |
| build_near_location | BuildNearLocationObjective | event | QuestProgressEvents.java:709 | BlockEvent.EntityPlaceEvent ~699 | line 699 | pattern | |
| enter_structure | EnterStructureObjective | event | QuestProgressEvents.java:392 | tick handler ~363 | line 363 | pattern | |
| deliver_to_villager | DeliverToVillagerObjective | event | QuestProgressEvents.java:811 | villager interact ~798-801 | line 798/801 | pattern | |
| find_missing_relative | FindMissingRelativeObjective | polling | QuestProgressEvents.java:405-407 (also VillagerTargeted) | PollingObjective generic poll | loop at 405 | pattern | |
| reach_location | ReachLocationObjective | event | QuestProgressEvents.java:394 | tick handler ~363 | line 363 | pattern | |
| defend_location | DefendLocationObjective | event | QuestProgressEvents.java:122 | death event ~111 | line 111 | pattern | |
| ftbq_complete_quest | FtbqCompleteQuestObjective | polling | QuestProgressEvents.java:405-407 | PollingObjective poll, fed by compat/ftbq bridge | loop at 405 | pattern | zero FTB imports (ObjectiveTypes.java:102) |

#### 2. Condition types -- quest/condition/ConditionTypes.java (35 leaf + 3 composite)

Entry points located but NOT individually walked per id (budget ran out before this table):
condition evaluation happens through offer filtering (OfferFilters, referenced in
QuestObjective.unofferableReason doc at QuestObjective.java:111), QuestManager accept/turn-in
paths, and a tick pass. NOT VERIFIED this pass: whether any condition is re-evaluated at turn-in in
a way that could block an already-earned completion -- open, not confirmed either way.

Leaf ids present: mcareputation:has_incident, townstead_available, townstead_value,
townstead_building, townstead_spirit, townstead_skill, townstead_profession_track, hearts,
profession, biome, dimension, time, weather, item_held, advancement, player_level, random_chance,
quest_completed, quest_not_completed, quest_failed, quest_abandoned, quest_declined,
is_player_spouse, relationship_state, is_family_member, age_group, personality, mood,
village_member, has_home, health_below, infected, related_villager_status, village_reputation,
reputation_tier, giver_distance_from_village, ftbq_quest_completed, ftbq_chapter_completed,
ftbq_task_completed. Composites: all_of, any_of, not.

Row-level "evaluated when" / per-id anomaly detail: NOT COMPLETED this pass.

#### 3. Reward types -- quest/reward/RewardTypes.java (20 ids)

item, xp, hearts, xp_levels, effect, loot_table, command, hearts_with_sponsor,
hearts_with_participants, mcareputation:resolve_incident, mcareputation:record_incident,
village_reputation, unlock, townstead_needs, townstead_profession_xp, townstead_skill,
townstead_reaction, grant_title, currency, ftbq_progress.

Grant-code file:line, load-time validation, and config-gate columns NOT COMPLETED this pass
(budget exhausted after the objective and condition inventories). Do not assume any config gate
(e.g. for the command reward) without separate verification.

#### 4. Project objective types -- project/objective/ProjectObjectiveTypes.java (8 ids)

donate_item, townstead_building_project, townstead_spirit_project, townstead_workforce_project,
townstead_resident_wellbeing_project, project_kill_entity, project_place_block,
project_talk_to_profession. Implementing classes exist as separate files (DonateItemObjective,
TownsteadBuildingProjectObjective, TownsteadSpiritProjectObjective,
TownsteadWorkforceProjectObjective, TownsteadResidentWellbeingProjectObjective,
ProjectKillObjective, ProjectPlaceBlockObjective, ProjectTalkObjective), plus a
PollingProjectObjective interface mirroring PollingObjective. Advancing code and
SharedObjectiveProgress wiring NOT TRACED this pass -- open.

#### 5. Interfaces / storage

- Progress method names: QuestObjective.current(ServerPlayer, ObjectiveProgress)
  (QuestObjective.java:65), QuestObjective.isSatisfied(ServerPlayer, ObjectiveProgress)
  (QuestObjective.java:68), QuestObjective.isEventDriven() (QuestObjective.java:75),
  QuestObjective.consumeOnTurnIn (QuestObjective.java:71).
- Raw counter storage: ObjectiveProgress.java -- count field floored via Math.max(0, value)
  (line 53); elapsedTicks floored similarly (line 67). Exact declared type of count not
  re-confirmed this pass (inferred int from usage, not opened directly).
- Per-type display clamp: Math.min(progress.count(), count) pattern, confirmed in
  KillEntityObjective.java:75; assumed uniform across the other accumulation types by
  convention, not individually re-verified for all 20.



#### Condition evaluation entry points
QuestCondition.test(QuestContext) at quest/condition/QuestCondition.java:17. The only two call
sites that gate anything against `def.effectiveConditions()` are:
- OfferFilters.java:143 - offer-time gate (menu construction).
- QuestManager.java:330 - inside whyNothingIsOffered, a diagnostic-only "why is this locked"
  helper for flavour text, not a blocker.
No call to effectiveConditions().test(...) or conditions().test(...) exists in accept() (line 460)
or in canTurnInAt (line 1248) or completeQuest (line 838). ConditionTypes.testAll (line 238-239) is
unused by QuestManager itself (only referenced by validators/tests grep did not surface a caller in
QuestManager). There is no separate turn_in.conditions block in QuestDefinition (turnIn is a
TurnInMode/villager-matching record, not a condition holder).

**Answer: `conditions` are evaluated ONLY at offer time**, never re-evaluated at accept or at
turn-in. Once a quest is offered and accepted, nothing re-checks time/weather/biome/hearts/etc.
So a condition true at offer and false later (weather changed, player left the biome, hearts
dropped) CANNOT block turn-in or fail an already-accepted quest via the `conditions` mechanism.
The only project-side analog is ProjectManager.conditionsPass (ProjectManager.java:357-365), and a
comment at ProjectManager.java:214 explicitly documents the same choice for projects: "Deliberately
NOT re-tested against conditions. A project already under way must stay [under way]."

#### Table 2 - condition ids
35 leaf ids + all_of/any_of/not, all under quest/condition/leaf|composite/. Per-id class/mod-gate
table not itemized (budget) but the load-bearing fact above (offer-only evaluation) answers the
key risk question for all of them uniformly - none can retroactively block turn-in.

#### Table 3 - Rewards, notable findings
- **command reward** (quest/reward/CommandReward.java): gated by
  `McaQuestsConfig.COMMON.allowCommandRewards.get()` (CommandReward.java:37). Runs via
  `player.createCommandSourceStack()` (line 44) at permission level 2 with output suppressed
  (per class doc comment line 17); `@s` resolves to the player, i.e. it runs as/through the player's
  own command source stack, not the console.
- **Failure handling for all rewards**: uniform, not per-type. QuestManager.grantSafely
  (QuestManager.java:817-824) wraps every `reward.grant(...)` call in try/catch, logs and
  continues. No reward type has bespoke failure handling and none is missing coverage - grantSafely
  wraps every call site (lines 876, 880, 884).
- Did not individually re-verify grant-code line numbers, load-time validation, or item/hearts/
  xp_levels/loot_table clamp specifics for all 20 ids this pass - open if needed.

#### Cap verification (accumulation objectives, current()/isSatisfied())
Checked all 30 classes in quest/objective/*.java for Math.min/required() clamps in current():
every one clamps (BreakBlock, BreedAnimals, BuildNearLocation, CraftItem, CureVillager,
DefendLocation, DefendVillager, DeliverToVillager, EnterStructure, EscortEntity,
FindMissingRelative, FishItem, FtbqCompleteQuest, HealEntity, ItemDelivery, KillEntity,
ObtainItem, PlaceBlock, ProtectEntity, ReachLocation, SleepOrRest, TalkToProfession, TameAnimal,
TownsteadBuildingRegistered, TownsteadChange, TownsteadHealthyResidents,
TownsteadProfessionProgress, TownsteadScheduleStreak, TownsteadSpiritProgress, TownsteadState,
TradeWithVillager, VisitBiome, VisitDimension).
**No class lacking a cap was found.**

#### Table 4 - Project objectives
All 8 ids have confirmed implementing classes with advancing code:
- donate_item -> DonateItemObjective.contribute (project/objective/DonateItemObjective.java:58)
- project_kill_entity -> ProjectKillObjective.matches, advanced at ProjectManager.java:508
- project_place_block -> ProjectPlaceBlockObjective.matches, advanced at ProjectManager.java:519
- project_talk_to_profession -> ProjectTalkObjective, advanced at ProjectManager.java:538
- townstead_building_project -> TownsteadBuildingProjectObjective, PollingProjectObjective poll
  method at line 54
- townstead_spirit_project -> TownsteadSpiritProjectObjective, poll method at line 69
- townstead_workforce_project -> TownsteadWorkforceProjectObjective, poll method at line 73
- townstead_resident_wellbeing_project -> TownsteadResidentWellbeingProjectObjective, poll method
  at line 93
All write into project/state/SharedObjectiveProgress (server-owned, shared across contributors).
**No project objective type found with missing advancing code.**


## 6 Flags

### count_outlier (2)

| quest_id | detail |
| --- | --- |
| mcaquests:last_banner_home | minecraft:crossbow x2 > max stack 1 (deliver_to_villager) |
| mcaquests:townstead_heat_over_the_fields | minecraft:potion x3 > max stack 1 (item_delivery) |

### hearts_threshold (4)

| quest_id | detail |
| --- | --- |
| mcaquests:courting_1_first_gift | info: all_of[1] min=15 max=None |
| mcaquests:librarian_knowledge_3_share | info: root min=40 max=None |
| mcaquests:relations_anniversary_gift | info: all_of[1] min=40 max=None |
| mcaquests:relations_spouse_flowers | info: all_of[1] min=50 max=None |

### mob_not_in_village_biome (2)

| quest_id | detail |
| --- | --- |
| mcaquests:drowned_ledger | info: minecraft:drowned spawns in 12 biomes, none with villages |
| mcaquests:weaponsmith_drowned_depths | info: minecraft:drowned spawns in 12 biomes, none with villages |

### objective_type_unused (28)

| quest_id | detail |
| --- | --- |
| - | objective:mcaquests:sleep_or_rest |
| - | objective:mcaquests:ftbq_complete_quest |
| - | condition:mcareputation:has_incident |
| - | condition:mcaquests:townstead_skill |
| - | condition:mcaquests:profession |
| - | condition:mcaquests:biome |
| - | condition:mcaquests:dimension |
| - | condition:mcaquests:item_held |
| - | condition:mcaquests:advancement |
| - | condition:mcaquests:random_chance |
| - | condition:mcaquests:quest_not_completed |
| - | condition:mcaquests:quest_declined |
| - | condition:mcaquests:personality |
| - | condition:mcaquests:mood |
| - | condition:mcaquests:village_reputation |
| - | condition:mcaquests:ftbq_quest_completed |
| - | condition:mcaquests:ftbq_chapter_completed |
| - | condition:mcaquests:ftbq_task_completed |
| - | reward:mcaquests:xp_levels |
| - | reward:mcaquests:effect |
| - | reward:mcaquests:command |
| - | reward:mcaquests:hearts_with_sponsor |
| - | reward:mcareputation:resolve_incident |
| - | reward:mcareputation:record_incident |
| - | reward:mcaquests:unlock |
| - | reward:mcaquests:townstead_needs |
| - | reward:mcaquests:townstead_skill |
| - | reward:mcaquests:ftbq_progress |

### project_phase_zero_total (1)

| quest_id | detail |
| --- | --- |
| mcaquests:festival_preparation | phase celebrate has no objectives |

### zero_conditions (114)

| quest_id | detail |
| --- | --- |
| mcaquests:drowned_ledger | info: no conditions |
| mcaquests:adventurer_into_the_deep | info: no conditions |
| mcaquests:last_banner_home | info: no conditions |
| mcaquests:nether_relay | info: no conditions |
| mcaquests:relic_beneath_the_well | info: no conditions |
| mcaquests:adventurer_relic_hunt | info: no conditions |
| mcaquests:adventurer_trailblazer | info: no conditions |
| mcaquests:archer_cull_from_afar | info: no conditions |
| mcaquests:archer_fletchings | info: no conditions |
| mcaquests:archer_marksman | info: no conditions |
| mcaquests:armorer_coal_for_winter | info: no conditions |
| mcaquests:armorer_copper_plating | info: no conditions |
| mcaquests:armorer_forge_ahead | info: no conditions |
| mcaquests:armorer_iron_for_the_forge | info: no conditions |
| mcaquests:butcher_fresh_cuts | info: no conditions |
| mcaquests:butcher_hearty_meal | info: no conditions |
| mcaquests:butcher_poultry_run | info: no conditions |
| mcaquests:butcher_smoked_supply | info: no conditions |
| mcaquests:cartographer_edge_of_the_world | info: no conditions |
| mcaquests:cartographer_high_ground | info: no conditions |
| mcaquests:cartographer_scout_the_land | info: no conditions |
| mcaquests:cartographer_spare_parchment | info: no conditions |
| mcaquests:bell_the_lantern_line | info: no conditions |
| mcaquests:bell_when_the_horns_answer | info: no conditions |
| mcaquests:courting_2_walk_together | info: no conditions |
| mcaquests:courting_4_the_proposal | info: no conditions |
| mcaquests:farmer_family_1_wheat | info: no conditions |
| mcaquests:farmer_family_2_expand | info: no conditions |
| mcaquests:guard_safety_1_clear | info: no conditions |
| mcaquests:guard_safety_2_patrol | info: no conditions |
| mcaquests:jobless_friendship_1_errand | info: no conditions |
| mcaquests:jobless_friendship_2_favor | info: no conditions |
| mcaquests:librarian_knowledge_1_ink | info: no conditions |
| mcaquests:librarian_knowledge_2_lost_book | info: no conditions |
| mcaquests:mapmaker_expedition_1_survey | info: no conditions |
| mcaquests:mapmaker_expedition_2_expedition | info: no conditions |
| mcaquests:remedy_embers_and_wart | info: no conditions |
| mcaquests:remedy_gold_against_ash | info: no conditions |
| mcaquests:remedy_the_returning_voice | info: no conditions |
| mcaquests:road_caravan_through | info: no conditions |
| mcaquests:road_clear_the_cut | info: no conditions |
| mcaquests:road_raise_the_waystation | info: no conditions |
| mcaquests:cleric_brave_the_nether | info: no conditions |
| mcaquests:cleric_glow_in_the_dark | info: no conditions |
| mcaquests:cleric_grim_harvest | info: no conditions |
| mcaquests:cleric_tend_the_flock | info: no conditions |
| mcaquests:cleric_urgent_medicine | info: no conditions |
| mcaquests:farmer_build_the_hayloft | info: no conditions |
| mcaquests:farmer_root_cellar | info: no conditions |
| mcaquests:farmer_wheat_request | info: no conditions |
| mcaquests:fisherman_a_taste_of_the_sea | info: no conditions |
| mcaquests:fisherman_fresh_catch | info: no conditions |
| mcaquests:fisherman_salmon_run | info: no conditions |
| mcaquests:fletcher_a_fine_bow | info: no conditions |
| mcaquests:fletcher_feather_light | info: no conditions |
| mcaquests:fletcher_feathers_for_fighters | info: no conditions |
| mcaquests:fletcher_full_quiver | info: no conditions |
| mcaquests:fletcher_knapping | info: no conditions |
| mcaquests:guard_clear_the_night | info: no conditions |
| mcaquests:guard_skeleton_patrol | info: no conditions |
| mcaquests:guard_spider_season | info: no conditions |
| mcaquests:guard_the_creeper_problem | info: no conditions |
| mcaquests:guard_wolf_at_the_door | info: no conditions |
| mcaquests:leatherworker_cobblers_apprentice | info: no conditions |
| mcaquests:leatherworker_rabbit_run | info: no conditions |
| mcaquests:leatherworker_the_tannery | info: no conditions |
| mcaquests:leatherworker_tough_hide | info: no conditions |
| mcaquests:librarian_ink_and_quill | info: no conditions |
| mcaquests:librarian_paper_trail | info: no conditions |
| mcaquests:librarian_take_the_census | info: no conditions |
| mcaquests:librarian_well_read | info: no conditions |
| mcaquests:mason_clay_works | info: no conditions |
| mcaquests:mason_lay_the_path | info: no conditions |
| mcaquests:mason_quarry_work | info: no conditions |
| mcaquests:mason_solid_foundations | info: no conditions |
| mcaquests:mercenary_bounty | info: no conditions |
| mcaquests:mercenary_contract_killing | info: no conditions |
| mcaquests:mercenary_pillager_bounty | info: no conditions |
| mcaquests:mercenary_witch_hunt | info: no conditions |
| mcaquests:feast_of_many_tables | info: no conditions |
| mcaquests:honey_for_the_healer | info: no conditions |
| mcaquests:horse_for_the_courier | info: no conditions |
| mcaquests:long_way_home | info: no conditions |
| mcaquests:monument_of_names | info: no conditions |
| mcaquests:relations_repair_the_well | info: no conditions |
| mcaquests:relations_trade_with_blacksmith | info: no conditions |
| mcaquests:shepherd_a_dyers_dozen | info: no conditions |
| mcaquests:shepherd_spin_a_yarn | info: no conditions |
| mcaquests:shepherd_warm_blankets | info: no conditions |
| mcaquests:shepherd_wool_gathering | info: no conditions |
| mcaquests:template_cartographer_survey | info: no conditions |
| mcaquests:template_farmer_crop_request | info: no conditions |
| mcaquests:template_fisherman_catch | info: no conditions |
| mcaquests:template_guard_mob_cull | info: no conditions |
| mcaquests:template_librarian_knowledge | info: no conditions |
| mcaquests:template_mercenary_bounty | info: no conditions |
| mcaquests:toolsmith_a_proper_kit | info: no conditions |
| mcaquests:toolsmith_deep_delve | info: no conditions |
| mcaquests:toolsmith_sharp_and_ready | info: no conditions |
| mcaquests:toolsmith_temper_in_battle | info: no conditions |
| mcaquests:toolsmith_tools_of_the_trade | info: no conditions |
| mcaquests:unemployed_a_splash_of_color | info: no conditions |
| mcaquests:unemployed_apple_a_day | info: no conditions |
| mcaquests:unemployed_berry_picking | info: no conditions |
| mcaquests:unemployed_egg_hunt | info: no conditions |
| mcaquests:unemployed_helping_hand | info: no conditions |
| mcaquests:unemployed_kindling | info: no conditions |
| mcaquests:unemployed_lend_a_blade | info: no conditions |
| mcaquests:weaponsmith_bone_collector | info: no conditions |
| mcaquests:weaponsmith_creeper_cull | info: no conditions |
| mcaquests:weaponsmith_drowned_depths | info: no conditions |
| mcaquests:weaponsmith_proving_the_steel | info: no conditions |
| mcaquests:weaponsmith_temper_the_blade | info: no conditions |
| mcaquests:weaponsmith_the_horde | info: no conditions |

### zero_rewards (7)

| quest_id | detail |
| --- | --- |
| mcaquests:template_cartographer_survey | no rewards block |
| mcaquests:template_farmer_crop_request | no rewards block |
| mcaquests:template_fisherman_catch | no rewards block |
| mcaquests:template_guard_mob_cull | no rewards block |
| mcaquests:template_kin_errand | no rewards block |
| mcaquests:template_librarian_knowledge | no rewards block |
| mcaquests:template_mercenary_bounty | no rewards block |

### Unverifiable ids by namespace

