# Ore Food

Turn minerals into edible food. **50 mineral foods** — five mineral tiers × ten food types — plus five food enchantments that grant potion effects based on enchantment level. Eating consumes durability instead of the item, so one food can be eaten many times.

**Platforms:** Forge 1.20.1, NeoForge 1.21.1, NeoForge 26.1.2

## What it adds

### 50 mineral foods

Ten foods: Beef, Bread, Chicken, Cod, Mutton, Porkchop, Pufferfish, Rabbit, Rotten Flesh, Salmon.
Five mineral tiers: Copper, Iron, Golden, Diamond, Netherite.

Item IDs are `<mineral>_<food>` — `orefood:copper_beef`, `orefood:diamond_bread`, `orefood:netherite_salmon`.

**Hunger and saturation** come from the matching vanilla food, multiplied by the mineral tier:

| Tier | Hunger | Saturation |
|---|---|---|
| Copper | ×0.75 | ×0.75 |
| Iron | ×1.0 | ×1.0 |
| Golden | ×1.0 | ×1.25 |
| Diamond | ×1.25 | ×1.5625 |
| Netherite | ×1.5 | ×1.875 |

For beef (vanilla cooked beef is 8 / 12.8): 6 / 9.6, 8 / 12.8, 8 / 16.0, 10 / 20.0, 12 / 24.0.
For bread (vanilla bread is 5 / 6.0): 4 / 4.5, 5 / 6.0, 5 / 7.5, 6 / 9.4, 8 / 11.3.

**Durability and enchantability** depend only on the mineral tier, not on the food:

| Tier | Bites | Enchantability |
|---|---|---|
| Copper | 8 | 1 |
| Iron | 16 | 1 |
| Golden | 32 | 5 |
| Diamond | 64 | 10 |
| Netherite | 128 | 15 |

Durability means **remaining bites**. Each bite costs 1 durability; when it reaches 0, the food is consumed. Iron tier can be eaten 16 times; Netherite tier, 128 times. They do not stack. In Creative mode, eating does not consume durability.

### Pufferfish and Rotten Flesh: the debuffs are intentional

These two carry their vanilla debuffs, and the duration scales with the mineral tier. Effect levels stay at vanilla values (Pufferfish: Poison II, Hunger III, Nausea I; Rotten Flesh: Hunger I, 80% chance) with one exception — the **Netherite** tier also raises the effects that are still level I to level II.

| Tier | Pufferfish: Poison / Hunger / Nausea | Rotten Flesh: Hunger (80%) |
|---|---|---|
| Copper | 30 / 7.5 / 7.5 s | 15 s |
| Iron (vanilla) | 60 / 15 / 15 s | 30 s |
| Golden | 90 / 22.5 / 22.5 s | 45 s |
| Diamond | 120 / 30 / 30 s | 60 s |
| Netherite | 150 / 37.5 / 37.5 s | 75 s |

Netherite raises Nausea (I → II) on Pufferfish and Hunger (I → II) on Rotten Flesh. Poison II and Hunger III on Pufferfish are already above level I, so they are not raised further. The other eight foods have no debuffs.

### Five food enchantments

These apply only to edible items. Effects trigger when the food is eaten.

| Enchantment | Max Level | Effect |
|---|---|---|
| Powerful Recovery | 4 | Instant Health + Regeneration. Effect level equals enchantment level; Regeneration lasts level × 5 seconds. |
| Adrenaline | 3 | Strength (level × 5s) + Speed (level × 10s). |
| Fitness | 5 | Absorption (scales with enchantment level) + Resistance II (40% damage reduction), duration level × 20s. |
| Satiety | 3 | Restores level × 2 extra hunger. |
| Feast | 3 | Saturation, 1 minute per level. **Treasure enchantment.** |

### Crafting

Every food has its own five-tier upgrade chain — 50 recipes in total. The four lower tiers are crafted at a crafting table; the fifth is smithed.

| Result | Materials (8) | Center |
|---|---|---|
| Copper <food> | Copper Ingots | the matching vanilla food |
| Iron <food> | Iron Ingots | Copper <food> |
| Golden <food> | Gold Ingots | Iron <food> |
| Diamond <food> | Diamonds | Golden <food> |
| Netherite <food> | Smithing Table: Netherite Upgrade Template + Netherite Ingot | Diamond <food> |

Example for bread: 8 Copper Ingots + Bread → Copper Bread; 8 Iron Ingots + Copper Bread → Iron Bread; 8 Gold Ingots + Iron Bread → Golden Bread; 8 Diamonds + Golden Bread → Diamond Bread; Smithing Table: Template + Netherite Ingot + Diamond Bread → Netherite Bread.

The center slot must be the correct previous tier — using the wrong tier produces nothing.

## How to use

1. In the Creative **Food & Drinks** tab, every mineral food sits **directly after its vanilla counterpart**: Cooked Beef, then Copper / Iron / Golden / Diamond / Netherite Beef, then the bread group, and so on. In Survival, start from the copper tier and craft upwards.
2. Enchant:
   - Powerful Recovery, Adrenaline, Fitness and Satiety are normal enchantments (enchanting table, villagers, loot).
   - Feast is treasure-only (loot, librarian, similar to Mending).
3. Eat: hold right-click. Each bite consumes 1 durability and triggers the effects.
4. Hover the food to see effects, levels and durations in the tooltip, formatted like vanilla potions. Debuffs carried by pufferfish and rotten flesh variants are listed there too.

## Adding your own mineral food (KubeJS)

With KubeJS installed you can register your own, inheriting every behaviour — eating, durability, enchantments and tooltip:

```js
StartupEvents.registry('item', event => {
  event.create('mypack:redstone_beef', 'orefood:ore_food_item')
    .definition({ nutrition: 6, saturation: 9.6, durability: 12, enchantment_value: 2 })
    .tag('c:foods')   // NeoForge only: required, or the food cannot be enchanted
})
```

`definition` fields: `nutrition`, `saturation`, `durability` (bites), `enchantment_value`, and the optional `effects` array with `{ effect, duration, amplifier, probability }` entries.

## Upgrading from 1.x

**1.2.0** replaced the single `orefood:ore_food` item with one real item per beef (five items); **2.0.0** expanded that to 50 foods.
Items from 1.1.0 and earlier no longer exist, so `orefood:ore_food` stacks in existing worlds become invalid. Start a new world, or accept the one-off registry warnings when loading an old one.

## Development notes

The source repository's `AGENT.md` documents the three-platform API differences, the build setup, and the pitfalls hit while porting.
