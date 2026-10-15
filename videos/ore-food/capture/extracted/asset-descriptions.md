# Asset inventory — OreFood / 矿物牛排

Canonical asset inventory for this film. Downstream steps read **only this file**;
never re-derive paths from raw directories.

Two roots:

- `MOD` = `D:\0CreaterMods\OreFood\common\src\main\resources\assets\orefood\textures\item\`
- `VAN` = `C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\` (user granted free use)

Project-relative copies for the mod item textures live at `assets/orefood/`.
Vanilla textures are referenced in place at the absolute `VAN` path.

**The frame palette is strictly two-colour (cream paper + cobalt ink).**
`scripts/pixelate.mjs` converts any sprite into a hard-edged cobalt ink mask:
`node scripts/pixelate.mjs --in <png> --out <png> [--grid N] [--threshold T] [--invert]`.
It decodes colour types 0/2/3/4/6 at bit depth 1/2/4/8 (the library is
palette- and 4-bit-heavy), resamples nearest-neighbour to N×N, and never smooths.
**All vanilla sprites and every mod texture go through it.** No bilinear scaling anywhere.

---

## A. Project assets — mod item textures (5)

16×16 RGBA pixel art, the mod's own sprites. **Must appear as-is; scale only with
`image-rendering: pixelated`**, never smooth-interpolated.

| Project path | Source | One-line description |
|---|---|---|
| `assets/orefood/copper_beef.png` | `MOD\copper_beef.png` | 铜牛排 item sprite — steak silhouette with a pale copper-tone mineral crust; the tier-1 form. |
| `assets/orefood/iron_beef.png` | `MOD\iron_beef.png` | 铁牛排 item sprite — same steak shape, cool grey iron plating and rivet speckle; tier 2. |
| `assets/orefood/golden_beef.png` | `MOD\golden_beef.png` | 金牛排 item sprite — bright yellow-gold banding across the steak face; tier 3. |
| `assets/orefood/diamond_beef.png` | `MOD\diamond_beef.png` | 钻石牛排 item sprite — cyan-white crystalline facets set into the steak; tier 4. |
| `assets/orefood/netherite_beef.png` | `MOD\netherite_beef.png` | 下界合金牛排 item sprite — dark purple-brown netherite plate with a violet sheen; the 128-bite top tier and the hook frame's hero sprite. |

Every one of these five is also the correct **inventory slot**, **crafting-grid cell**,
**JEI recipe result** and **hotbar icon** sprite for its tier — there is no separate
32×32 inventory variant in the mod repository.

---

## B. Vanilla texture library — items (Tier and crafting vocabulary)

All `item/*` sprites are 16×16. Paths below are relative to `VAN`.

### B1. Mineral ingots, gems and the smithing chain

| Path | One-line description |
|---|---|
| `item/copper_ingot.png` | Copper ingot — the 8× ring material for 铜牛排, and the "copper" chip in the five-tier ladder. |
| `item/iron_ingot.png` | Iron ingot — 8× ring material for 铁牛排. |
| `item/gold_ingot.png` | Gold ingot — 8× ring material for 金牛排. |
| `item/diamond.png` | Diamond gem — 8× ring material for 钻石牛排 (the ring uses the gem, not a block). |
| `item/netherite_ingot.png` | Netherite ingot — one of the two smithing-table inputs for the 下界合金牛排 step. |
| `item/netherite_scrap.png` | Netherite scrap — shown beside the ingot when the smithing step is explained (scrap is what netherite is made of). |
| `item/netherite_upgrade_smithing_template.png` | Netherite upgrade smithing template — the template slot of the 锻造台 recipe that turns 钻石牛排 into 下界合金牛排. |
| `item/dune_armor_trim_smithing_template.png` | Dune armor trim smithing template — a spare "other template" sprite, useful as a visual contrast when explaining that only the **netherite upgrade** template works. |
| `item/lapis_lazuli.png` | Lapis lazuli — the datapack frame's example custom tier (青金石牛排). |
| `item/redstone.png` | Redstone dust — the datapack frame's second example custom tier (红石牛排). |
| `item/emerald.png` | Emerald — spare gem sprite for a "tier ladder" lineup if five gems are shown side by side. |
| `item/quartz.png` | Nether quartz — spare white gem sprite, same purpose. |
| `item/amethyst_shard.png` | Amethyst shard — spare crystal sprite, same purpose. |

### B2. Food, base material and the vanilla-dish round trip

| Path | One-line description |
|---|---|
| `item/cooked_beef.png` | Cooked beef (熟牛排) — the **middle slot** of the 铜牛排 recipe, and the start of the whole upgrade chain. |
| `item/beef.png` | Raw beef — shown as the "before cooking" state in the crafting frame. |
| `item/porkchop.png` | Raw porkchop — a generic vanilla-food reference sprite. |
| `item/cooked_porkchop.png` | Cooked porkchop — same, cooked variant. |
| `item/bread.png` | Bread — plain vanilla food sprite for the "普通食物 vs 矿物牛排" contrast beat. |
| `item/golden_apple.png` | Golden apple — the classic "vanilla item that grants potion effects" comparison point. |
| `item/golden_carrot.png` | Golden carrot — second vanilla effect-food comparison sprite. |

### B3. Stations, books and effects

| Path | One-line description |
|---|---|
| `item/book.png` | Book — plain book sprite, pairs with the enchanted book. |
| `item/enchanted_book.png` | Enchanted book — the 附魔台 / 宝藏附魔 vocabulary; the visual for "附魔" in the five-enchantment frame. |
| `item/writable_book.png` | Book and quill — spare book sprite. |
| `item/experience_bottle.png` | Bottle o' enchanting — the 经验修补-style acquisition channel for 盛宴 (treasure enchantment). |
| `item/nether_star.png` | Nether star — the star-quality marker for 盛宴 being a treasure enchantment. |
| `item/potion.png` | Potion bottle — the item the tooltip format is borrowed from ("显示格式与原版药水完全一致"). |
| `item/splash_potion.png` | Splash potion — spare potion sprite. |
| `item/diamond_sword.png` | Diamond sword — plain equipment sprite for the "耐久 is NOT equipment durability" contrast. |
| `item/netherite_sword.png` | Netherite sword — the second half of that contrast (equipment durability vs bites). |
| `item/diamond_chestplate.png` | Diamond chestplate — armor sprite for the same equipment-durability beat. |
| `item/netherite_chestplate.png` | Netherite chestplate — armor sprite, same beat. |
| `item/ender_pearl.png` | Ender pearl — spare item sprite if a sixth object is needed in a lineup. |

> **No item sprite exists for crafting table / furnace / enchanting table / smithing
> table / anvil / grindstone / bookshelf in this library.** Use the `block/*` sprites in
> section D for those stations.

---

## C. Vanilla texture library — mob effect HUD icons (18×18)

`mob_effect/*.png`, all 18×18. These are the exact icons that pop in the HUD when a
bite fires an enchantment — the five-enchantment frame shows them one per bite.

| Path | One-line description |
|---|---|
| `mob_effect/instant_health.png` | Instant health icon — the burst half of 强效恢复 (瞬间治疗). |
| `mob_effect/regeneration.png` | Regeneration icon — the duration half of 强效恢复 (生命恢复, 等级×5 秒). |
| `mob_effect/strength.png` | Strength icon — 肾上腺素's 力量 component (等级×5 秒). |
| `mob_effect/speed.png` | Speed icon — 肾上腺素's 速度 component (等级×10 秒). |
| `mob_effect/absorption.png` | Absorption icon — 强身健体's 生命吸收, the effect that **does** follow the enchantment level. |
| `mob_effect/resistance.png` | Resistance icon — 强身健体's 抗性提升, **fixed at II = 40% damage reduction** at every level; the frame's key icon. |
| `mob_effect/saturation.png` | Saturation icon — 盛宴's effect, 1 minute per level. |
| `mob_effect/hunger.png` | Hunger icon — the food-system reference icon for 饱腹's "额外回 等级×2 点饱食度". |
| `mob_effect/health_boost.png` | Health boost icon — spare heart-adjacent effect icon; do **not** use it to stand in for 生命吸收 (that is `absorption.png`). |

---

## D. Vanilla texture library — block sprites (16×16)

`block/*.png`, all 16×16. These stand in for the stations and for the mineral blocks
that frame the crafting beats.

| Path | One-line description |
|---|---|
| `block/crafting_table_front.png` | Crafting table front face — the 2×2/3×3 crafting-grid station for the four mineral + cooked-beef tiers. |
| `block/crafting_table_top.png` | Crafting table top face — the grid surface itself; the cleanest sprite to lay a 3×3 ring over. |
| `block/crafting_table_side.png` | Crafting table side face — third face if the station is shown in 3D. |
| `block/furnace_front.png` | Furnace front — the "one tier to the next" smelting flavour station. |
| `block/furnace_front_on.png` | Lit furnace front — alt state for the same station. |
| `block/enchanting_table_top.png` | Enchanting table top — the 附魔台 station; used to show 盛宴 **cannot** be rolled here. |
| `block/enchanting_table_side.png` | Enchanting table side. |
| `block/enchanting_table_bottom.png` | Enchanting table bottom. |
| `block/smithing_table_front.png` | Smithing table front — the 锻造台 station for the netherite step. |
| `block/smithing_table_top.png` | Smithing table top — the three-slot smithing surface layout. |
| `block/smithing_table_side.png` | Smithing table side. |
| `block/smithing_table_bottom.png` | Smithing table bottom. |
| `block/anvil.png` | Anvil — spare "workstation" sprite. |
| `block/grindstone_side.png` | Grindstone side — spare workstation sprite. |
| `block/grindstone_round.png` | Grindstone wheel — spare workstation sprite. |
| `block/bookshelf.png` | Bookshelf — the enchantment-power context for the 附魔台 beat. |
| `block/copper_block.png` | Block of copper — the copper tier's block-level backing plate. |
| `block/ancient_debris_top.png` | Ancient debris top — the netherite-tier block sprite (this library has no separate netherite block texture). |
| `block/lapis_block.png` | Lapis block — datapack-frame backing for 青金石牛排. |
| `block/redstone_block.png` | Redstone block — datapack-frame backing for 红石牛排. |

---

## E. Vanilla texture library — GUI sprites

### E1. Container sheets (256×256)

| Path | One-line description |
|---|---|
| `gui/container/crafting_table.png` | Crafting table GUI sheet (256×256, 4-bit palette) — the real 3×3 grid + player inventory, used for every crafting-chain beat. |
| `gui/container/smithing.png` | Smithing table GUI sheet (256×256) — template / base / addition slot layout for the netherite step. |
| `gui/container/enchanting_table.png` | Enchanting table GUI sheet — the three offer slots with the lapis + level cost UI. |
| `gui/container/furnace.png` | Furnace GUI sheet — input / fuel / output for the smelting-flavour beat. |
| `gui/container/inventory.png` | Player inventory GUI sheet — the 一格一块 (no stacking) demonstration surface. |
| `gui/container/generic_54.png` | Generic 54-slot chest GUI sheet — a clean big grid if a "many steaks, all unstackable" tile is wanted. |
| `gui/container/anvil.png` | Anvil GUI sheet — spare workstation sheet. |
| `gui/container/creative_inventory/tab_items.png` | Creative inventory "Items" tab sheet — the 创造模式物品栏 surface (creation-mode source of all five steaks). |
| `gui/container/creative_inventory/tab_inventory.png` | Creative inventory tab strip sheet — pairs with the above. |

### E2. Slot, HUD and container sprites

Sizes as measured: `slot.png` 18×18, `hotbar.png` 182×22, hearts 9×9, all others 16×16
unless noted.

| Path | One-line description |
|---|---|
| `gui/sprites/container/slot.png` | Inventory slot frame (18×18) — the single unit of the 一格一块 / 不可堆叠 demonstration. |
| `gui/sprites/container/slot_highlight_back.png` | Slot highlight, back layer — hover state for the tooltip beat. |
| `gui/sprites/container/slot_highlight_front.png` | Slot highlight, front layer — pairs with the above. |
| `gui/sprites/container/slot/smithing_template_netherite_upgrade.png` | Netherite-upgrade ghost icon for the smithing template slot. |
| `gui/sprites/container/slot/ingot.png` | Ingot ghost icon for the smithing base slot. |
| `gui/sprites/container/slot/diamond.png` | Diamond ghost icon for the smithing base slot. |
| `gui/sprites/container/smithing/error.png` | Smithing error X — the "wrong middle slot" gotcha marker. |
| `gui/sprites/container/anvil/error.png` | Anvil error X — spare error marker. |
| `gui/sprites/container/cartography_table/error.png` | Cartography error X — spare error marker. |
| `gui/sprites/container/grindstone/error.png` | Grindstone error X — spare error marker. |
| `gui/sprites/container/loom/error.png` | Loom error X — spare error marker. |
| `gui/sprites/container/furnace/burn_progress.png` | Furnace burn arrow — the "one tier becomes the next" progress arrow. |
| `gui/sprites/container/furnace/lit_progress.png` | Furnace flame — pairs with the burn arrow. |
| `gui/sprites/container/enchanting_table/enchantment_slot.png` | Enchantment offer slot — the offer row on the 附魔台 sheet. |
| `gui/sprites/container/enchanting_table/level_3.png` | Level-3 offer glyph — the highest-cost enchant offer, for the "盛宴 永远刷不出" beat. |
| `gui/sprites/hud/hotbar.png` | Hotbar (182×22) — the held-steak bar for right-click-and-hold eating. |
| `gui/sprites/hud/hotbar_selection.png` | Hotbar selection frame — marks the held slot. |
| `gui/sprites/hud/effect_background.png` | Effect chip background — the HUD backing behind every mob-effect icon. |
| `gui/sprites/hud/food_full.png` | Full hunger drumstick — the 饱食度 read-out (6 / 8 / 8 / 10 / 12 per steak). |
| `gui/sprites/hud/food_empty.png` | Empty hunger drumstick — pairs with the above for a segmented hunger bar. |
| `gui/sprites/hud/experience_bar_progress.png` | XP bar fill — 附魔台 / 经验修补 context. |
| `gui/sprites/hud/experience_bar_background.png` | XP bar background — pairs with the above. |
| `gui/sprites/hud/heart/full.png` | Full heart (9×9) — 饱食度 / 生命恢复 read-out. |
| `gui/sprites/hud/heart/half.png` | Half heart (9×9) — halves the read-out. |
| `gui/sprites/hud/heart/container.png` | Heart container frame (9×9) — the empty pip behind the hearts. |
| `gui/sprites/hud/heart/absorbing_full.png` | **Absorption heart** (9×9) — the yellow extra-heart pip; the exact HUD feedback for 强身健体's 生命吸收. |
| `gui/sprites/container/inventory/effect_background.png` | Effect background, inventory variant — alt chip backing. |

---

## F. Vanilla texture library — enchant glint and particles

| Path | One-line description |
|---|---|
| `misc/enchanted_glint_item.png` | Item enchantment glint sheet — the shimmer laid over an enchanted steak in the tooltip beat. |
| `misc/enchanted_glint_armor.png` | Armor enchantment glint sheet — spare glint layer. |
| `misc/shadow.png` | Entity shadow blob — soft grounding for a floating item. |
| `particle/glint.png` | Glint particle — sparkle on the enchant-line text. |
| `particle/spark_0.png` | Spark particle frame 0 (8×8) — a bite's impact pop. |
| `particle/critical_hit.png` | Critical-hit particle — a bite's damage-flavoured pop. |
| `particle/effect_0.png` | Effect swirl frame 0 — potion-effect burst. |
| `particle/heart.png` | Heart particle — 饱腹 / 生命恢复 feedback. |

---

## G. Notes, gaps and gotchas

- **No netherite-block, iron-block, gold-block or diamond-block texture exists in this
  library.** Use `block/ancient_debris_top.png` for the netherite tier and
  `block/copper_block.png` / `block/lapis_block.png` / `block/redstone_block.png` where a
  block plate is needed; for iron / gold / diamond, use the ingot / gem item sprites
  (`item/iron_ingot.png`, `item/gold_ingot.png`, `item/diamond.png`) instead.
- **No station item sprites** (crafting table, furnace, enchanting table, smithing table,
  anvil, grindstone, bookshelf) — see section D for the block sprites.
- The library has no JEI-rendered screenshot; JEI is reconstructed from
  `gui/container/crafting_table.png` + `gui/sprites/container/slot.png` + the tier sprites.
- **Pixel discipline:** every sprite above is nearest-neighbour only. At frame scale use
  `image-rendering: pixelated` on raw sprites, or `scripts/pixelate.mjs` to render the
  cobalt ink mask. No bilinear filtering, no anti-aliasing, no smoothing anywhere.
- All 2031 PNGs in the library were enumerated; every path listed in this file was
  individually confirmed to exist on disk.
