# NAV.md — OreFood / 矿物牛排 · frame preset navigation layer

Reconnaissance inventory for the six frames of the 105 s / 1920×1080 / Bilibili promo.
**Motion is out of scope here** — this file is inventory facts only: which pixel texture each beat
needs, where that file physically is, and which hero figure + frame.md type token carries it.

Preset: **cobalt-grid** (`frame.md`). Paper `#F0EBDE`, ink `#1F2BE0` (the ONLY ink), permanent
~2cqw graph-paper grid, top + bottom cobalt hairlines, 0 radius, 0 shadow, no second hue.
Vanilla sprites are **never copied into this project**: the frame workers rasterize them to cobalt
ink masks straight from the library path with `scripts/pixelate.mjs`.

---

## 0 · Sources and path status (verified 2026-10-02, Asia/Shanghai)

| # | Source | Root |
|---|---|---|
| S1 | mod's own textures | project-relative `assets/orefood/` |
| S1v | S1 verified originals (byte-identical, in the mod repo) | `D:\0CreaterMods\OreFood\common\src\main\resources\assets\orefood\textures\item\` |
| S2 | user's vanilla texture library | `C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\` |

**Path convention.** S1 paths are project-relative as written. S2 paths in §3 are written
`…\textures\<…>` — the ellipsis is exactly the S2 root above and nothing else, so
`…\textures\item\copper_ingot.png` means
`C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\copper_ingot.png`.

**STATUS — `assets/orefood/` is staged and verified.** It appeared during this recon; all five
sprites are now present at the project-relative paths below, 16×16, byte-identical to the S1v
originals in the mod repo. A builder can read the project paths directly; the S1v column is kept as
the provenance trail. (At the start of recon the directory did not exist, so any earlier note saying
so is superseded by this line.)

The five mod sprites (all **16×16, 8-bit RGBA**, colour type 6):

| project-relative target (S1) | S1v original (verified) | bytes |
|---|---|---|
| `assets/orefood/copper_beef.png` | `common/src/main/resources/assets/orefood/textures/item/copper_beef.png` | 420 |
| `assets/orefood/iron_beef.png` | `…textures/item/iron_beef.png` | 398 |
| `assets/orefood/golden_beef.png` | `…textures/item/golden_beef.png` | 400 |
| `assets/orefood/diamond_beef.png` | `…textures/item/diamond_beef.png` | 396 |
| `assets/orefood/netherite_beef.png` | `…textures/item/netherite_beef.png` | 372 |

The mod ships **no other textures** — `assets/orefood/` contains only these five PNGs plus
`lang/en_us.json`, `lang/zh_cn.json` and six item models. There are no mod GUI, block or logo
textures. Every other sprite in this video comes from S2.

---

## 1 · Fixed system tokens (from `frame.md`)

Type tokens available to carry a hero figure, with their authored size:

| token | size | family |
|---|---|---|
| `vbig-numeral` | 12.5cqw | Newsreader 400 |
| `display-hero` | 10.4cqw | Newsreader 400 |
| `display-closing` | 9.4cqw | Newsreader 400 |
| `display-chapter` | 6.8cqw | Newsreader 400 |
| `headline` | 4.6cqw | Newsreader 400 |
| `table-name` | 1.5cqw | Newsreader 400 |
| `mono-tag` / `mono-chrome` | 0.78cqw / 13px | DM Mono |

Fit rule from `frame.md`: ≤3 words → `vbig-numeral`/`display-hero`; 4–6 → `display-chapter`;
7+ → `headline`. Newsreader is 400 only, negative-tracked; labels are Hanken uppercase 0.16em.

All figures below trace to the mod README (`D:\0CreaterMods\OreFood\README.md`) — none are invented.

---

## 2 · Library facts that change how the frames get built

1. **The library is PNG-optimised; it is not plain 8-bit RGBA.** Census of all 3503 PNGs:

   | colour type / bit depth | count | example |
   |---|---|---|
   | 3 / 8 (palette) | 1641 | `block/acacia_log_top.png` |
   | 3 / 4 (palette) | 561 | `block/acacia_planks.png` |
   | 6 / 8 (RGBA) | 519 | `block/acacia_trapdoor.png` |
   | 4 / 8 (gray+alpha) | 382 | `block/black_stained_glass.png` |
   | 0 / 8 (grayscale) | 152 | `block/anvil.png` |
   | 2 / 8 (RGB) | 130 | `block/black_concrete_powder.png` |
   | 3 / 2 (palette) | 91 | `block/black_concrete.png` |
   | 3 / 1, 0 / 1, 0 / 2, 0 / 4 | 15 / 6 / 5 / 1 | `entity/llama/spit.png`, `effect/dither.png` |

   Many sprites a frame will want are **palette**, e.g. `item/copper_ingot.png` (type 3 / 8) and
   `mob_effect/strength.png` (type 3 / 8); `gui/container/crafting_table.png` is **type 3 / 4**.
   `scripts/pixelate.mjs` decodes all of these exactly (see §4). All are non-interlaced.

2. **Exact sprite sizes are not uniform 16×16.** Resample with `--grid` accordingly:
   - mod steaks, most `item/` + `block/` sprites: 16×16
   - `mob_effect/*.png`: **18×18** (all of them)
   - `gui/container/*.png`: **256×256** sheets — 21 of the 23 top-level files; the two
     exceptions are `gamemode_switcher.png` (128×128) and `villager.png` (512×256). Every sheet
     cited in §3 (crafting_table, smithing, enchanting_table, generic_54, inventory, anvil) is 256×256.
   - `gui/sprites/tooltip/background.png`, `…/frame.png`: **100×100**, each with a `.mcmeta`
     (nine-slice — do not slice naively; resample whole and crop)
   - `gui/sprites/widget/slot_frame.png`: 80×80 · `gui/sprites/container/slot.png`: 18×18
   - `gui/sprites/hud/food_full.png` / `food_half.png` / `food_empty.png`: **9×9**
   - `gui/sprites/recipe_book/slot_craftable.png`: 25×25 · `overlay_recipe.png`: 32×32 (+mcmeta)

3. **Four `item/` textures named in the brief do not exist in this library.** Verified absent:
   `item/crafting_table.png`, `item/enchanting_table.png`, `item/smithing_table.png`,
   `item/anvil.png`. Use the verified block/GUI equivalents instead:
   - `block/crafting_table_top.png`, `block/crafting_table_front.png`, `block/crafting_table_side.png` (16×16)
   - `block/enchanting_table_top.png`, `block/enchanting_table_side.png`, `block/enchanting_table_bottom.png` (16×16)
   - `block/smithing_table_top.png`, `block/smithing_table_front.png`, `block/smithing_table_side.png` (16×16)
   - `block/anvil.png`, `block/anvil_top.png` (16×16) — both are grayscale type 0
   Also absent: `block/iron_block.png`, `block/gold_block.png`, `block/diamond_block.png`,
   `block/netherite_block.png`. The only mineral block present is `block/copper_block.png`.

4. **No loader logos exist in the library.** There is no Forge / NeoForge / MIT mark as a texture.
   Per `frame.md` "Approved Entities", render those as type (or a placeholder), never as a found mark.

5. **Mod official Chinese names** (`lang/zh_cn.json`, the names the frames must set):
   铜牛排 · 铁牛排 · 金牛排 · 钻石牛排 · 下界合金牛排 ·
   强效恢复 · 肾上腺素 · 强身健体 · 饱腹 · 盛宴.
   The shared item id is `orefood:ore_food`.

---

## 3 · The six frames

Numbering follows the run order. Each frame lists: the beats it must cover, its one hero number +
carrying token, then every pixel texture the beats need.

### Frame 1 — 0–3 s · 钩子 (hook)

**Beats.** Open on the netherite steak being bitten; a burst of buff icons fires at once. Hard
conflict hook, no preamble.

**Hero number and token.** `128` — 下界合金牛排 can be eaten 128 times / 128 口.
Carrier: **`vbig-numeral`** (12.5cqw; a bare numeral à la `frame.md` Hero Cover).

**Textures.**

| beat | texture (source) | size |
|---|---|---|
| the bitten hero item | `assets/orefood/netherite_beef.png` (S1) | 16×16 |
| material echo behind the bite | `…\textures\item\netherite_ingot.png` (S2) | 16×16 |
| buff burst (强效恢复) | `…\textures\mob_effect\instant_health.png` (S2) | 18×18 |
| buff burst (强效恢复) | `…\textures\mob_effect\regeneration.png` (S2) | 18×18 |
| buff burst (肾上腺素) | `…\textures\mob_effect\strength.png` (S2) | 18×18 |
| buff burst (肾上腺素) | `…\textures\mob_effect\speed.png` (S2) | 18×18 |
| buff burst (强身健体) | `…\textures\mob_effect\absorption.png` (S2) | 18×18 |
| buff burst (强身健体) | `…\textures\mob_effect\resistance.png` (S2) | 18×18 |
| buff burst (盛宴/饱腹) | `…\textures\mob_effect\saturation.png` (S2) | 18×18 |

### Frame 2 — 3–20 s · 五种牛排 + 耐久机制 (product intro)

**Beats.** One item id, five mineral forms; eating costs exactly 1 durability per bite; the item
only disappears at 0 durability. Per-tier durability 8 / 16 / 32 / 64 / 128; per-tier hunger
6 / 8 / 8 / 10 / 12. Not stackable.

**Hero number and token.** `1` — one durability point per bite (一口 = 1 点耐久).
Carrier: **`display-hero`**. The five per-tier values 8/16/32/64/128 are ledger data, not the
hero: carry them per row in **`table-name`** (1.5cqw) inside the Index Ledger treatment.

**Textures.**

| beat | texture (source) | size |
|---|---|---|
| 铜牛排 | `assets/orefood/copper_beef.png` (S1) | 16×16 |
| 铁牛排 | `assets/orefood/iron_beef.png` (S1) | 16×16 |
| 金牛排 | `assets/orefood/golden_beef.png` (S1) | 16×16 |
| 钻石牛排 | `assets/orefood/diamond_beef.png` (S1) | 16×16 |
| 下界合金牛排 | `assets/orefood/netherite_beef.png` (S1) | 16×16 |
| the vanilla base the chain starts from | `…\textures\item\cooked_beef.png` (S2) | 16×16 |
| 铜 tier material | `…\textures\item\copper_ingot.png` (S2) | 16×16 |
| 铁 tier material | `…\textures\item\iron_ingot.png` (S2) | 16×16 |
| 金 tier material | `…\textures\item\gold_ingot.png` (S2) | 16×16 |
| 钻石 tier material | `…\textures\item\diamond.png` (S2) | 16×16 |
| 下界合金 tier material | `…\textures\item\netherite_ingot.png` (S2) | 16×16 |
| grounded mineral tile (only mineral block in the library) | `…\textures\block\copper_block.png` (S2) | 16×16 |

### Frame 3 — 20–50 s · 五个食物附魔 (feature showcase)

**Beats.** Five food-only enchantments firing on a bite. 强效恢复 (max IV, instant health +
regeneration), 肾上腺素 (max III, strength + speed), 强身健体 (max V, absorption follows level,
**resistance fixed at II = 40% 减伤**), 饱腹 (max III, extra hunger), 盛宴 (max III, saturation,
treasure enchantment). Level I enchanted = level I potion. The 40% is the emphasised claim.

**Hero number and token.** `40%` — 强身健体's fixed Resistance II damage reduction, which does not
scale with the enchantment level.
Carrier: **`display-hero`**. The section count "5" belongs in a `display-chapter`/`mono-tag`
index, not in the hero slot.

**Textures.**

| beat | texture (source) | size |
|---|---|---|
| 强效恢复 | `…\textures\mob_effect\instant_health.png` (S2) | 18×18 |
| 强效恢复 | `…\textures\mob_effect\regeneration.png` (S2) | 18×18 |
| 肾上腺素 | `…\textures\mob_effect\strength.png` (S2) | 18×18 |
| 肾上腺素 | `…\textures\mob_effect\speed.png` (S2) | 18×18 |
| 强身健体 | `…\textures\mob_effect\absorption.png` (S2) | 18×18 |
| 强身健体 (the 40% claim) | `…\textures\mob_effect\resistance.png` (S2) | 18×18 |
| 饱腹 (extra hunger points) | `…\textures\gui\sprites\hud\food_full.png` (S2) | 9×9 |
| 饱腹 (half-point variant) | `…\textures\gui\sprites\hud\food_half.png` (S2) | 9×9 |
| 盛宴 (saturation) | `…\textures\mob_effect\saturation.png` (S2) | 18×18 |
| enchantment is food-only | `…\textures\item\enchanted_book.png` (S2) | 16×16 |
| 盛宴 is a treasure enchantment | `…\textures\item\book.png` (S2) | 16×16 |
| enchanting cost / XP | `…\textures\item\experience_bottle.png` (S2) | 16×16 |
| 附魔台 (block face) | `…\textures\block\enchanting_table_top.png` (S2) | 16×16 |
| 附魔台 (block side) | `…\textures\block\enchanting_table_side.png` (S2) | 16×16 |
| 附魔台 (GUI sheet, downsample) | `…\textures\gui\container\enchanting_table.png` (S2) | 256×256 |
| 附魔台 surround (optional) | `…\textures\block\bookshelf.png`, `…\textures\block\obsidian.png` (S2) | 16×16 |

### Frame 4 — 50–80 s · 合成升级链 (feature showcase)

**Beats.** Tier-by-tier crafting: each tier = 8 of the matching mineral around the *previous* tier's
steak (copper starts from vanilla cooked beef), then the netherite step moves to the smithing table
(netherite upgrade template + netherite ingot). The centre slot must be exactly the right tier.

**Hero number and token.** `8` — every crafting tier takes exactly 8 identical minerals.
Carrier: **`vbig-numeral`**. The chain itself is ledger/index content in `table-name` +
`mono-tag` (materials).

**Textures.**

| beat | texture (source) | size |
|---|---|---|
| chain nodes | `assets/orefood/copper_beef.png`, `iron_beef.png`, `golden_beef.png`, `diamond_beef.png`, `netherite_beef.png` (S1) | 16×16 |
| chain entry point | `…\textures\item\cooked_beef.png` (S2) | 16×16 |
| tier material ×4 | `…\textures\item\copper_ingot.png`, `iron_ingot.png`, `gold_ingot.png`, `diamond.png` (S2) | 16×16 |
| smithing inputs | `…\textures\item\netherite_ingot.png`, `…\textures\item\netherite_scrap.png` (S2) | 16×16 |
| smithing template | `…\textures\item\netherite_upgrade_smithing_template.png` (S2) | 16×16 |
| crafting grid sheet | `…\textures\gui\container\crafting_table.png` (S2) | 256×256 |
| smithing grid sheet | `…\textures\gui\container\smithing.png` (S2) | 256×256 |
| slot chrome | `…\textures\gui\sprites\container\slot.png` (S2) | 18×18 |
| framed slot | `…\textures\gui\sprites\widget\slot_frame.png` (S2) | 80×80 |
| ghost slot icons | `…\textures\gui\sprites\container\slot\ingot.png`, `…\slot\diamond.png`, `…\slot\smithing_template_netherite_upgrade.png` (S2) | 16×16 |
| crafting table block | `…\textures\block\crafting_table_top.png`, `…\_front.png`, `…\_side.png` (S2) | 16×16 |
| smithing table block | `…\textures\block\smithing_table_top.png`, `…\_front.png`, `…\_side.png` (S2) | 16×16 |
| forge/upgrade mark | `…\textures\block\anvil.png`, `…\block\anvil_top.png` (S2) | 16×16 |
| recipe-book chrome (optional) | `…\textures\gui\sprites\recipe_book\slot_craftable.png` (25×25), `…\overlay_recipe.png` (32×32, +mcmeta) | — |

### Frame 5 — 80–100 s · 数据包自定义 (benefit highlight)

**Beats.** All numbers live in a data pack — change one line and it takes effect on world reload.
The item tooltip lists each enchantment's effect, level and duration like a vanilla potion. With
JEI installed, the five steaks are five separate entries (products **and** ingredients keep the
right tier's sprite and name), never merged into one unnamed "矿物食品".

**Hero number and token.** `1` — one data-pack line to change a value / one item id behind all
five forms.
Carrier: **`display-hero`**. *Alternate if two frames showing "1" reads as repetition:* `5`
(five independent JEI entries) on `display-hero`; the data-pack line then drops to `mono-tag`.

**Textures.**

| beat | texture (source) | size |
|---|---|---|
| the five JEI entries | `assets/orefood/copper_beef.png`, `iron_beef.png`, `golden_beef.png`, `diamond_beef.png`, `netherite_beef.png` (S1) | 16×16 |
| tooltip ground | `…\textures\gui\sprites\tooltip\background.png` (S2) | 100×100 + `.mcmeta` (nine-slice) |
| tooltip frame | `…\textures\gui\sprites\tooltip\frame.png` (S2) | 100×100 + `.mcmeta` (nine-slice) |
| JEI / inventory panel | `…\textures\gui\container\generic_54.png`, `…\textures\gui\container\inventory.png` (S2) | 256×256 |
| slot chrome | `…\textures\gui\sprites\container\slot.png` (18×18), `…\widget\slot_frame.png` (80×80) (S2) | — |
| the data-pack file itself | `…\textures\item\paper.png`, `…\textures\item\writable_book.png`, `…\textures\item\written_book.png` (S2) | 16×16 |
| tooltip effect rows | `…\textures\mob_effect\instant_health.png`, `strength.png`, `absorption.png`, `resistance.png`, `saturation.png` (S2) | 18×18 |

### Frame 6 — 100–105 s · 收尾 (branding)

**Beats.** Loaders supported (Forge 1.20.1 / NeoForge 1.21.1 / NeoForge 26.1.2), MIT licence,
author lyingice, the ask (关注 / 投币 / 收藏). Colophon: right-aligned close, mono credit columns.

**Hero number and token.** `3` — three supported loaders.
Carrier: **`display-closing`** (9.4cqw — `frame.md` Treatment 6 "Colophon" names this token as the
frame's focal; it is not in the six-token shortlist above, so use `display-hero` if the build
insists on one of the six).

**Textures.**

| beat | texture (source) | size |
|---|---|---|
| sign-off product mark | `assets/orefood/netherite_beef.png` (S1) | 16×16 |
| MIT licence / credits column | `…\textures\item\book.png` (S2) | 16×16 |
| material echo (optional) | `…\textures\item\netherite_ingot.png` (S2) | 16×16 |

No loader logo texture exists (see §2.4) — loader names are type only.

---

## 4 · `scripts/pixelate.mjs` contract

```
node scripts/pixelate.mjs --in <png> --out <png> [--grid 16] [--threshold 0.5] [--invert]
```

Turns any of the S1/S2 sprites into a two-colour cobalt ink mask on transparent. Ink is exactly
`#1F2BE0` at full alpha; everything else is fully transparent; alpha-0 source pixels stay
transparent. `--grid N` nearest-neighbour resamples to N×N **before** thresholding (so a 16×16
sprite is untouched and a 256×256 GUI sheet is downsampled deterministically); `--invert` flips
which luminance side becomes ink (for light-on-dark sprites such as the light `mob_effect` icons).
Output is 8-bit RGBA, non-interlaced, filter type 0 on every row. No anti-aliasing, no smoothing,
no colour. It prints one line: input size → output size, ink pixel count, ink coverage %.

The decoder is dependency-free (`node:zlib` only) and handles every format the library actually
contains — colour types 0 / 2 / 3 / 4 / 6, bit depths 1 / 2 / 4 / 8, filters 0–4, `tRNS` — all
decoded exactly. It fails loudly (non-zero exit, no output file) on interlaced / Adam7, 16-bit
samples, unknown colour types, a palette without `PLTE`, CRC damage, truncation or bad arguments.
Because the library is palette-heavy (see §2.1), a decoder limited to 8-bit RGB/RGBA alone would
have rejected most real sprites; `item/copper_ingot.png` and `mob_effect/strength.png` are both
palette.

Verified runs (default `--grid 16`): `item/copper_ingot.png` 37.9% ink ·
`mob_effect/strength.png` 17.2% · `gui/container/crafting_table.png` 9.0% ·
`netherite_beef.png` 41.0% · `copper_beef.png` 34.8% · `iron_beef.png` 28.9% ·
`golden_beef.png` 34.8% · `diamond_beef.png` 34.8%.
