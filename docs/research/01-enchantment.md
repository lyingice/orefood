# 三版本附魔（Enchantment）系统技术对照报告

> 目标：为「食物附魔」模组（`com.lyingice.orefood`，工作目录 `D:\0CreaterMods\OreFood`）在
> 1.20.1-Forge / 1.21.1-NeoForge / 26.1.2-NeoForge 三平台上各注册 5 个只对食物生效的附魔做前置调研。
>
> 所有结论均来自 `.mc-src/<版本>/` 下的反编译源码与随 jar 附带的数据包文件，证据格式为 `源文件路径:行号`。
> 未找到的内容明确标注「未找到」。

## 0. 调研范围与证据来源

| 版本 | 加载器 | 源码根 | 附魔相关包路径 |
|---|---|---|---|
| 1.20.1 | Forge 47.4.5 | `.mc-src/1.20.1/` | `net/minecraft/world/item/enchantment/` |
| 1.21.1 | NeoForge 21.1.250 | `.mc-src/1.21.1/` | `net/minecraft/world/item/enchantment/` |
| 26.1.2 | NeoForge 26.1.2.109 | `.mc-src/26.1.2/` | `net/minecraft/world/item/enchantment/` |

补充说明：
- `.mc-src/26.1.2/data/` 内含**完整的原版数据包**（含 `data/minecraft/enchantment/*.json` 共 43 个附魔定义文件），
  因此 26.1.2 的 JSON 结论可直接由真实文件佐证。
- `.mc-src/1.21.1/data/` 只含 NeoForge 自己生成的数据（`c/`、`neoforge/`、部分 `minecraft/`），
  **不含 `data/minecraft/enchantment/`**；`.mc-src/1.20.1/data/` 只含 `forge/` 与 `minecraft/loot_tables|recipes`。
  1.21.1 的 JSON 键名结论由 `Enchantment.DIRECT_CODEC` 源码推导（与 26.1.2 的真实 JSON 完全一致，可交叉验证）。

---

## 1. `Enchantment` 类型的定义

### 1.1 1.20.1（Forge）：抽象 class，非 record

```java
public abstract class Enchantment implements IForgeEnchantment
```
证据：`.mc-src/1.20.1/net/minecraft/world/item/enchantment/Enchantment.java:20`

**构造器签名**（`Enchantment.java:32`）：
```java
protected Enchantment(Enchantment.Rarity p_44676_, EnchantmentCategory p_44677_, EquipmentSlot[] p_44678_)
```

**关键字段**：

| 字段 | 类型 | 可见性 | 证据 |
|---|---|---|---|
| `slots` | `EquipmentSlot[]` | `private final` | `Enchantment.java:21` |
| `rarity` | `Enchantment.Rarity` | `private final` | `Enchantment.java:22` |
| `category` | `EnchantmentCategory` | `public final` | `Enchantment.java:23` |
| `descriptionId` | `@Nullable String` | `protected` | `Enchantment.java:25` |

**没有** `maxLevel` / `supportedItems` / `treasure` / `weight` 字段。这些都是**方法**，由子类覆写：
- `getMinLevel()` → 默认 `1`（`Enchantment.java:55-57`）
- `getMaxLevel()` → 默认 `1`（`Enchantment.java:59-61`）
- `getMinCost(int level)` → 默认 `1 + level*10`（`Enchantment.java:63-65`）
- `getMaxCost(int level)` → 默认 `getMinCost(level)+5`（`Enchantment.java:67-69`）
- `isTreasureOnly()` → 默认 `false`（`Enchantment.java:125-127`）
- `isCurse()` → 默认 `false`（`Enchantment.java:129-131`）
- `isTradeable()` → 默认 `true`（`Enchantment.java:133-135`）
- `isDiscoverable()` → 默认 `true`（`Enchantment.java:137-139`）
- `canApplyAtEnchantingTable(ItemStack)` → 默认委托 `stack.canApplyAtEnchantingTable(this)`（`Enchantment.java:141-143`）
- `isAllowedOnBooks()` → 默认 `true`（`Enchantment.java:145-147`）
- `canEnchant(ItemStack)` → 默认委托 `canApplyAtEnchantingTable(stack)`（`Enchantment.java:115-117`）

**`Rarity` 枚举**（`Enchantment.java:149-153`）：`COMMON(10)` / `UNCOMMON(5)` / `RARE(2)` / `VERY_RARE(1)`，权重由 `getWeight()` 返回（`Enchantment.java:161-163`）。

**显示名**：`getDescriptionId()`（`Enchantment.java:96-98`）→ `getOrCreateDescriptionId()`（`Enchantment.java:88-94`），
内部调用 `Util.makeDescriptionId("enchantment", BuiltInRegistries.ENCHANTMENT.getKey(this))`，
即 lang 键 `enchantment.<命名空间>.<路径>`。`getFullname(int level)`（`Enchantment.java:100-113`）负责加等级后缀与颜色。

### 1.2 1.21.1（NeoForge）：record，4 个组件字段

```java
public record Enchantment(
    Component description,
    Enchantment.EnchantmentDefinition definition,
    HolderSet<Enchantment> exclusiveSet,
    DataComponentMap effects
)
```
证据：`.mc-src/1.21.1/net/minecraft/world/item/enchantment/Enchantment.java:61`

**嵌套 `EnchantmentDefinition` record**（`Enchantment.java:650-659`）：
```java
public static record EnchantmentDefinition(
    HolderSet<Item> supportedItems,
    Optional<HolderSet<Item>> primaryItems,
    int weight,
    int maxLevel,
    Enchantment.Cost minCost,
    Enchantment.Cost maxCost,
    int anvilCost,
    List<EquipmentSlotGroup> slots
)
```

**嵌套 `Cost` record**（`Enchantment.java:636-643`）：
```java
public static record Cost(int base, int perLevelAboveFirst)
```
`calculate(int level)` = `base + perLevelAboveFirst * (level - 1)`（`Enchantment.java:645-647`）。

**常量**：`public static final int MAX_LEVEL = 255;`（`Enchantment.java:61`）

**便捷工厂**（`Enchantment.java:77-110`）：
- `static Cost constantCost(int cost)` → `new Cost(cost, 0)`
- `static Cost dynamicCost(int base, int perLevel)` → `new Cost(base, perLevel)`
- `static EnchantmentDefinition definition(HolderSet<Item> supportedItems, HolderSet<Item> primaryItems, int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, EquipmentSlotGroup... slots)`
- `static EnchantmentDefinition definition(HolderSet<Item> supportedItems, int weight, int maxLevel, Cost minCost, Cost maxCost, int anvilCost, EquipmentSlotGroup... slots)`
- `static Enchantment.Builder enchantment(Enchantment.EnchantmentDefinition definition)`（`Enchantment.java:533-535`）

**codec**：
- `Codec<Enchantment> DIRECT_CODEC`（`Enchantment.java:63-73`）
- `Codec<Holder<Enchantment>> CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT)`（`Enchantment.java:74`）
- `StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> STREAM_CODEC`（`Enchantment.java:75`）

**取值方法**：`getWeight()`（`:163`）、`getAnvilCost()`（`:167`）、`getMinLevel()` 恒为 1（`:171`）、`getMaxLevel()` → `definition.maxLevel()`（`:175`）、
`getMinCost(level)` → `definition.minCost().calculate(level)`（`:182`）、`getMaxCost(level)`（`:186`）、
`matchingSlot(EquipmentSlot)`（`:140`）、`getSupportedItems()` @Deprecated（`:136`）、`isSupportedItem(ItemStack)` @Deprecated（`:159`）、`isPrimaryItem(ItemStack)` @Deprecated（`:151`）、
`canEnchant(ItemStack)` @Deprecated（`:221`）、`getEffects(DataComponentType<List<T>>)`（`:225`）、
`static areCompatible(Holder<Enchantment>, Holder<Enchantment>)`（`:195`）、`static getFullname(Holder<Enchantment>, int)`（`:199`）。

> 注意：1.21.1 中 `Enchantment` 的 `treasure` / `curse` / `tradeable` 等语义**不再是字段**，改为 `minecraft:treasure` / `minecraft:curse` 等**附魔标签**（见 §5）。

### 1.3 26.1.2（NeoForge）：同样是 record，签名与 1.21.1 逐字一致

```java
public record Enchantment(
    Component description,
    Enchantment.EnchantmentDefinition definition,
    HolderSet<Enchantment> exclusiveSet,
    DataComponentMap effects
)
```
证据：`.mc-src/26.1.2/net/minecraft/world/item/enchantment/Enchantment.java:60`
`EnchantmentDefinition`：`Enchantment.java:658-667`；`Cost`：`Enchantment.java:644-651`；`MAX_LEVEL = 255`：`Enchantment.java:61`。

**26.1.2 相对 1.21.1 的改动**（全部有据可查）：

| 项目 | 1.21.1 | 26.1.2 | 证据 |
|---|---|---|---|
| 标识符类型 | `ResourceLocation` | **`Identifier`** | 1.21.1 `Enchantment.java:34`；26.1.2 `Enchantment.java:30` |
| `exclusive_set` 默认值 | `HolderSet.direct()` | `HolderSet.empty()` | 1.21.1 `:67-69`；26.1.2 `:66` |
| `primary_items` codec 写法 | 分两行 `.optionalFieldOf(...)` | 单行 `.optionalFieldOf(...)` | 1.21.1 `:663-665`；26.1.2 `:671` |
| `attributes` 组件 codec | `EnchantmentAttributeEffect.CODEC.codec()` | `EnchantmentAttributeEffect.MAP_CODEC.codec()` | 1.21.1 `:56`；26.1.2 `:114` |
| 效果列表校验 | 直接 `.listOf()` | 新增 `validatedListCodec(codec, ContextKeySet)` 包装 | 26.1.2 `EnchantmentEffectComponents.java:127-129` |
| 上下文参数类型 | `LootContextParamSets.*` | `ContextKeySet`（`net.minecraft.util.context`） | 26.1.2 `EnchantmentEffectComponents.java:13,21` |
| 新增效果组件 | — | `minecraft:post_piercing_attack` | 26.1.2 `EnchantmentEffectComponents.java:51-54` |
| 新增实体效果 | — | `change_item_damage` / `apply_impulse` / `apply_exhaustion` | 26.1.2 `effects/EnchantmentEntityEffect.java:21,25,26` |
| 移除实体效果 | `damage_item` | 无（被 `change_item_damage` 取代） | 1.21.1 `effects/EnchantmentEntityEffect.java:22` |
| 新增 LevelBasedValue | — | `minecraft:exponent` | 26.1.2 `LevelBasedValue.java:26` |
| 物品附魔相关数据组件 | 无 | 新增 `minecraft:enchantable`(`Enchantable`) 与 `minecraft:repairable`(`Repairable`) | 26.1.2 `core/component/DataComponents.java:189,195` |
| 物品实例抽象 | `ItemStack` | 引入 `ItemInstance` 接口，附魔读取下移到 `ItemInstanceExtension` | 26.1.2 `ItemInstanceExtension.java:56`；`IItemExtension.java:420` |
| 食物消耗 | `FoodProperties`（`Item.finishUsingItem` 调 `livingEntity.eat`） | 新增 `Consumable` 数据组件 + `ConsumeEffect` 注册表 | 1.21.1 `Item.java:183-186`；26.1.2 `Item.java:232-235` |

---

## 2. 附魔的注册方式

### 2.1 1.20.1：**静态注册**（不是数据驱动）

原版：`Registry.register(BuiltInRegistries.ENCHANTMENT, name, enchantment)`
证据：`.mc-src/1.20.1/net/minecraft/world/item/enchantment/Enchantments.java:71-73`

```java
private static Enchantment register(String p_44993_, Enchantment p_44994_) {
    return Registry.register(BuiltInRegistries.ENCHANTMENT, p_44993_, p_44994_);
}
```

注册表键：`Registries.ENCHANTMENT = createRegistryKey("enchantment")`（`.mc-src/1.20.1/net/minecraft/core/registries/Registries.java:109`）。
Forge 包装：`ForgeRegistries.ENCHANTMENTS`（`.mc-src/1.20.1/net/minecraftforge/registries/ForgeRegistries.java:55`），
其键为 `ForgeRegistries.Keys.ENCHANTMENTS = key("enchantment")`（同文件 `:139`）。
模组侧标准做法：`DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID)`。

`.mc-src/1.20.1/data/` 下**未找到**任何 `enchantment` 数据目录（只有 `data/forge/**` 与 `data/minecraft/loot_tables|recipes`）。

### 2.2 1.21.1 / 26.1.2：**datapack registry**（数据驱动）

证据：`.mc-src/1.21.1/net/minecraft/resources/RegistryDataLoader.java:96`（`WORLDGEN_REGISTRIES`）
与 `:113`（`SYNCHRONIZED_REGISTRIES`）：
```java
new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
```
26.1.2 对应：`.mc-src/26.1.2/net/minecraft/resources/RegistryDataLoader.java:118` 与 `:154`。

注册表键：`Registries.ENCHANTMENT = createRegistryKey("enchantment")`（`.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:214`）。

**数据包路径**：`data/<命名空间>/enchantment/<路径>.json`（目录名是**单数** `enchantment`）。
证据：`.mc-src/26.1.2/data/minecraft/enchantment/` 下存在 43 个原版文件，如
`sharpness.json`、`fire_aspect.json`、`bane_of_arthropods.json`、`binding_curse.json`。

> 因此 1.21.1/26.1.2 上「注册一个自定义附魔」= 在 mod jar 里放 `data/<modid>/enchantment/<id>.json`，
> **不需要也不应该**写 `DeferredRegister`。若确需在代码里构造，可用 `Enchantment.enchantment(definition)` 得到 `Builder`
> （1.21.1 `Enchantment.java:533`，26.1.2 `Enchantment.java:539`）——但这只生成对象，仍需注册进 datapack registry。

### 2.3 JSON 完整字段清单（从 codec 读出）

顶层 `Enchantment.DIRECT_CODEC`（1.21.1 `Enchantment.java:63-73`；26.1.2 `Enchantment.java:62-70`）：

| JSON 键 | 类型 | 必填？ | 默认值 | 证据 |
|---|---|---|---|---|
| `description` | `Component`（`ComponentSerialization.CODEC`） | **必填** | — | 1.21.1 `:65`；26.1.2 `:64` |
| `exclusive_set` | `HolderSet<Enchantment>`（附魔标签/列表） | 可选 | `HolderSet.direct()`（1.21.1）/ `HolderSet.empty()`（26.1.2） | 1.21.1 `:67-69`；26.1.2 `:66` |
| `effects` | `DataComponentMap` | 可选 | `DataComponentMap.EMPTY` | 1.21.1 `:70`；26.1.2 `:67` |

内联的 `EnchantmentDefinition.CODEC`（`MapCodec`，字段被**扁平化**到顶层，1.21.1 `Enchantment.java:660-674`；26.1.2 `:668-680`）：

| JSON 键 | 类型/取值范围 | 必填？ | 证据 |
|---|---|---|---|
| `supported_items` | `HolderSet<Item>`（`RegistryCodecs.homogeneousList(Registries.ITEM)`） | **必填** | 1.21.1 `:662`；26.1.2 `:670` |
| `primary_items` | `HolderSet<Item>` | 可选 | 1.21.1 `:663-665`；26.1.2 `:671` |
| `weight` | int，`intRange(1, 1024)` | **必填** | 1.21.1 `:666`；26.1.2 `:672` |
| `max_level` | int，`intRange(1, 255)` | **必填** | 1.21.1 `:667`；26.1.2 `:673` |
| `min_cost` | `Cost` 对象 `{base, per_level_above_first}` | **必填** | 1.21.1 `:668`；26.1.2 `:674` |
| `max_cost` | `Cost` 对象 | **必填** | 1.21.1 `:669`；26.1.2 `:675` |
| `anvil_cost` | int，非负 | **必填** | 1.21.1 `:670`；26.1.2 `:676` |
| `slots` | `List<EquipmentSlotGroup>` | **必填** | 1.21.1 `:671`；26.1.2 `:677` |

`Cost` codec（1.21.1 `Enchantment.java:637-643`；26.1.2 `:645-651`）：键 `base`、`per_level_above_first`，均为 `Codec.INT` 且**必填**。

### 2.4 可粘贴的完整示例 JSON

以下示例按 26.1.2 的真实文件结构写成（字段顺序与 `sharpness.json` 一致），可直接放到
`src/main/resources/data/<modid>/enchantment/<id>.json`。1.21.1 完全通用（唯一差别是命名空间解析细节，键名一致）。

**示例 A：一个「食用后获得力量」的食物附魔（纯 JSON，见 §7 的重要结论——它无法真正生效，此处仅示范字段完整性）**

```json
{
  "anvil_cost": 2,
  "description": {
    "translate": "enchantment.orefood.nourishing"
  },
  "effects": {
    "minecraft:post_attack": [
      {
        "affected": "victim",
        "effect": {
          "type": "minecraft:apply_mob_effect",
          "to_apply": "minecraft:strength",
          "min_duration": {
            "type": "minecraft:linear",
            "base": 5.0,
            "per_level_above_first": 5.0
          },
          "max_duration": {
            "type": "minecraft:linear",
            "base": 5.0,
            "per_level_above_first": 5.0
          },
          "min_amplifier": 0.0,
          "max_amplifier": 0.0
        },
        "enchanted": "attacker",
        "requirements": {
          "condition": "minecraft:damage_source_properties",
          "predicate": {
            "is_direct": true
          }
        }
      }
    ]
  },
  "exclusive_set": "#orefood:exclusive_set/food",
  "max_cost": {
    "base": 41,
    "per_level_above_first": 10
  },
  "max_level": 3,
  "min_cost": {
    "base": 11,
    "per_level_above_first": 10
  },
  "primary_items": "#c:foods",
  "slots": [
    "any"
  ],
  "supported_items": "#c:foods",
  "weight": 5
}
```

**示例 B：最小的、字段最少的合法附魔 JSON**（对照 `binding_curse.json` 的写法）

```json
{
  "anvil_cost": 4,
  "description": {
    "translate": "enchantment.orefood.satiating"
  },
  "effects": {
    "minecraft:prevent_equipment_drop": {}
  },
  "max_cost": {
    "base": 50,
    "per_level_above_first": 0
  },
  "max_level": 1,
  "min_cost": {
    "base": 25,
    "per_level_above_first": 0
  },
  "slots": [
    "any"
  ],
  "supported_items": "#c:foods",
  "weight": 1
}
```
（`binding_curse.json` 原文见 `.mc-src/26.1.2/data/minecraft/enchantment/binding_curse.json:1-23`；
`prevent_equipment_drop` 的组件注册名见 `EnchantmentEffectComponents.java:121`。）

---

## 3. 从 `ItemStack` 读取附魔等级 / 全部附魔 / 是否有附魔

### 3.1 1.20.1

`net.minecraft.world.item.enchantment.EnchantmentHelper`（`.mc-src/1.20.1/net/minecraft/world/item/enchantment/EnchantmentHelper.java`）：

| 用途 | 签名 | 证据 |
|---|---|---|
| 取指定附魔等级（已弃用） | `@Deprecated public static int getItemEnchantmentLevel(Enchantment, ItemStack)` | `:62-65` |
| 取指定附魔等级（NBT） | `public static int getTagEnchantmentLevel(Enchantment, ItemStack)` | `:67-84` |
| 取全部附魔 | `public static Map<Enchantment, Integer> getEnchantments(ItemStack)` | `:86-89` |
| 反序列化 | `public static Map<Enchantment, Integer> deserializeEnchantments(ListTag)` | `:91-100` |
| 写入全部附魔 | `public static void setEnchantments(Map<Enchantment, Integer>, ItemStack)` | `:102` |
| 取实体身上某附魔等级 | `public static int getEnchantmentLevel(Enchantment, LivingEntity)` | `:168` |
| 随机取带某附魔的物品 | `public static Map.Entry<EquipmentSlot, ItemStack> getRandomItemWith(Enchantment, LivingEntity[, Predicate<ItemStack>])` | `:259`, `:264` |
| 是否有绑定诅咒 | `public static boolean hasBindingCurse(ItemStack)` | `:234` |
| 是否有消失诅咒 | `public static boolean hasVanishingCurse(ItemStack)` | `:238` |
| 是否有精准采集 | `public static boolean hasSilkTouch(ItemStack)` | `:242` |
| 是否有引雷 | `public static boolean hasChanneling(ItemStack)` | `:254` |

**键类型**：直接用 `Enchantment` 对象（静态注册表的单例），**不是** `Holder`，**不是** `ResourceKey`。
底层存储是 `ItemStack` 上的 `ListTag`（`EnchantmentHelper.java:72` `p_44845_.getEnchantmentTags()`），
每条 `{id: "ns:path", lvl: n}`（`storeEnchantment`/`getEnchantmentId`，`:37-58`）。

**「是否有任意附魔」**：1.20.1 的 `EnchantmentHelper` **未找到** `hasAnyEnchantments`。
可用的替代：`!EnchantmentHelper.getEnchantments(stack).isEmpty()`。

### 3.2 1.21.1

`EnchantmentHelper`（`.mc-src/1.21.1/net/minecraft/world/item/enchantment/EnchantmentHelper.java`）：

| 用途 | 签名 | 证据 |
|---|---|---|
| 取指定附魔等级 | `@Deprecated public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemStack stack)` | `:55-59` |
| 取指定附魔等级（NBT） | `public static int getTagEnchantmentLevel(Holder<Enchantment> p_346179_, ItemStack p_44845_)` | `:64-67` |
| 取「合成用」附魔集合 | `public static ItemEnchantments getEnchantmentsForCrafting(ItemStack stack)` | `:91-93` |
| 组件类型选择 | `public static DataComponentType<ItemEnchantments> getComponentType(ItemStack stack)` | `:95-97` |
| **是否有任意附魔** | `public static boolean hasAnyEnchantments(ItemStack stack)` | `:99-102` |
| 取实体身上某附魔等级 | `public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity)` | `:288` |
| 是否在附魔标签中 | `public static boolean hasTag(ItemStack stack, TagKey<Enchantment> tag)` | `:441` |
| 是否带某效果组件 | `public static boolean has(ItemStack stack, DataComponentType<?> componentType)` | `:460` |
| 取最高等级的效果 | `public static <T> Optional<T> pickHighestLevel(ItemStack, DataComponentType<List<T>>)` | `:470` |
| 取最高等级 + 等级 | `public static <T> Pair<T, Integer> getHighestLevel(ItemStack, DataComponentType<T>)` | `:482` |
| 随机取带某组件的物品 | `public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?>, LivingEntity, Predicate<ItemStack>)` | `:495` |
| 遍历物品上的附魔 | `public static void runIterationOnItem(ItemStack stack, EnchantmentHelper.EnchantmentVisitor visitor)` | `:136` |
| 遍历装备上的附魔 | `public static void runIterationOnEquipment(LivingEntity entity, EnchantmentHelper.EnchantmentInSlotVisitor visitor)` | `:172` |

**键类型**：**`Holder<Enchantment>`**（不是 `ResourceKey`，不是裸 `Enchantment`）。
`getTagEnchantmentLevel` 内部：`stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder)`（`:65-66`）。

**取「全部附魔」**：`EnchantmentHelper` 里**未找到**返回 `Map` 的 `getEnchantments(ItemStack)`。正确做法：

- 原版：`ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);`
  然后 `ench.keySet()` → `Set<Holder<Enchantment>>`（`ItemEnchantments.java:107`），
  `ench.entrySet()` → `Set<Object2IntMap.Entry<Holder<Enchantment>>>`（`:111`），`ench.getLevel(Holder<Enchantment>)`（`:66`）。
  注意 `stack.getEnchantments()` 在 NeoForge 下已 `@Deprecated`（`ItemStack.java:969-972`），推荐 `getTagEnchantments()`（`:962`）。
- NeoForge 推荐（含事件与自定义逻辑）：`stack.getEnchantmentLevel(Holder<Enchantment>)`（`IItemStackExtension.java:162`）、
  `stack.getAllEnchantments(RegistryLookup<Enchantment> lookup)`（`IItemStackExtension.java:177`）、
  `stack.supportsEnchantment(Holder<Enchantment>)`（`:147`）、`stack.isPrimaryItemFor(Holder<Enchantment>)`（`:140`）。

### 3.3 26.1.2

`EnchantmentHelper`（`.mc-src/26.1.2/net/minecraft/world/item/enchantment/EnchantmentHelper.java`）：

| 用途 | 签名 | 证据 |
|---|---|---|
| 取指定附魔等级 | `public static int getItemEnchantmentLevel(Holder<Enchantment> enchantment, ItemInstance piece)` | `:54` |
| 取指定附魔等级（NBT） | `public static int getTagEnchantmentLevel(Holder<Enchantment> enchantment, ItemInstance piece)` | `:62` |
| 取「合成用」附魔集合 | `public static ItemEnchantments getEnchantmentsForCrafting(ItemStack itemStack)` | `:89` |
| 组件类型选择 | `public static DataComponentType<ItemEnchantments> getComponentType(ItemStack itemStack)` | `:93` |
| **是否有任意附魔** | `public static boolean hasAnyEnchantments(ItemStack itemStack)` | `:97` |
| 取实体身上某附魔等级 | `public static int getEnchantmentLevel(Holder<Enchantment> enchantment, LivingEntity entity)` | `:292` |
| 是否在附魔标签中 | `public static boolean hasTag(ItemStack item, TagKey<Enchantment> tag)` | `:452` |
| 是否带某效果组件 | `public static boolean has(ItemStack item, DataComponentType<?> effectType)` | `:471` |
| 取最高等级的效果 | `public static <T> Optional<T> pickHighestLevel(ItemStack, DataComponentType<List<T>>)` | `:481` |
| 取最高等级 + 等级 | `public static <T> Pair<T, Integer> getHighestLevel(ItemStack, DataComponentType<T>)` | `:492` |
| 随机取带某组件的物品 | `public static Optional<EnchantedItemInUse> getRandomItemWith(DataComponentType<?>, LivingEntity, Predicate<ItemStack>)` | `:505` |
| 创建附魔书 | `public static ItemStack createBook(EnchantmentInstance enchant)` | `:133` |
| 遍历物品上的附魔 | `public static void runIterationOnItem(ItemStack piece, EnchantmentHelper.EnchantmentVisitor method)` | `:139` |

**关键差异**：`getItemEnchantmentLevel` / `getTagEnchantmentLevel` 的参数类型从 `ItemStack` 变为 **`ItemInstance`**（26.1.2 新引入的接口，`ItemStack` 实现它）。

**实例级 API**（NeoForge 扩展）：
- `ItemInstanceExtension.getEnchantmentLevel(Holder<Enchantment>)`（`.mc-src/26.1.2/net/neoforged/neoforge/common/extensions/ItemInstanceExtension.java:56`）
  —— 注意它**已从 `IItemStackExtension` 移出**（1.21.1 在 `IItemStackExtension.java:162`，26.1.2 该处已无此方法）。
- `ItemInstanceExtension.getTagEnchantments()`（`ItemInstanceExtension.java:41`）
- `IItemStackExtension.getAllEnchantments(RegistryLookup<Enchantment>)`（`IItemStackExtension.java:126`）
- `IItemStackExtension.supportsEnchantment(Holder<Enchantment>)`（`:112`）、`isPrimaryItemFor(Holder<Enchantment>)`（`:105`）
- `IItemExtension.getEnchantmentLevel(ItemInstance, Holder<Enchantment>)`（`IItemExtension.java:420`）

**取「全部附魔」**：同样**未找到** `EnchantmentHelper.getEnchantments(ItemStack)` 返回 `Map` 的版本；
用 `ItemEnchantments`（26.1.2 `ItemEnchantments.java:54` `getLevel`、`:89` `keySet`、`:93` `entrySet`）。
26.1.2 的 `ItemEnchantments.CODEC` 已简化为纯 `unboundedMap`（`ItemEnchantments.java:33`），
不再有 `show_in_tooltip` 字段（1.21.1 有，`ItemEnchantments.java:36-43`）。

---

## 4. 「可附魔物品」如何指定

### 4.1 1.20.1：**没有 JSON**，靠 `EnchantmentCategory` + `canEnchant` 覆写

- 分类枚举：`net.minecraft.world.item.enchantment.EnchantmentCategory`
  （`.mc-src/1.20.1/net/minecraft/world/item/enchantment/EnchantmentCategory.java:18-102`），
  取值：`ARMOR` / `ARMOR_FEET` / `ARMOR_LEGS` / `ARMOR_CHEST` / `ARMOR_HEAD` / `WEAPON` / `DIGGER` / `FISHING_ROD` /
  `TRIDENT` / `BREAKABLE` / `BOW` / `WEARABLE` / `CROSSBOW` / `VANISHABLE`。
  **没有 `FOOD` 之类的食物分类。**
- 该枚举实现 `IExtensibleEnum`（`:18`），并提供 `public static EnchantmentCategory create(String name, Predicate<Item> delegate)`（`:113-115`），
  模组可用它新增自定义分类（Forge 的枚举扩展机制）。
- 实际判定：`Enchantment.canEnchant(ItemStack)`（`Enchantment.java:115-117`）→ `canApplyAtEnchantingTable(ItemStack)`（`:141-143`）→ `ItemStack.canApplyAtEnchantingTable`；
  附魔台判定用 `EnchantmentCategory.canEnchant(Item)`（`EnchantmentCategory.java:117-119`）。
- 因此 1.20.1 想「只对食物生效」，必须**写代码**：自定义 `EnchantmentCategory`（`Item` 的 predicate 用 `item.isEdible()` / `item.getFoodProperties() != null`）
  或覆写 `canEnchant` / `canApplyAtEnchantingTable`。
  1.20.1 的食物判定 API（`.mc-src/1.20.1/net/minecraft/world/item/Item.java`）：
  - `public boolean isEdible()` → `this.foodProperties != null`（`:362-364`）
  - `@Deprecated @Nullable public FoodProperties getFoodProperties()`（`:366-370`）
  - `ItemStack#isEdible()` / `getFoodProperties(LivingEntity)` 也存在（`Item.java:149-150` 有使用示例）
  - 因此 predicate 可写：`EnchantmentCategory.create("FOOD", item -> item.isEdible())`
  - 注意 `create()` 抛 `IllegalStateException("Enum not extended")`（`:113-115`），依赖 Forge 的 `IExtensibleEnum` 运行时 ASM 改写，**必须在 mod 构造前（类加载期）调用**。

### 4.2 1.21.1 / 26.1.2：JSON 字段 `supported_items` + `primary_items`

字段名（**都是这两个，不是 `primary_items` 单独存在**）：
- `supported_items`（必填）——「能被附魔」的集合，铁砧/命令/`ItemStack#supportsEnchantment` 用它
  （`Enchantment.java:662` / 26.1.2 `:670`；语义见 `IItemExtension.java:477-479` / 26.1.2 `:404-406`）。
- `primary_items`（可选）——「附魔台随机池」的集合，`EnchantmentHelper.getAvailableEnchantmentResults` 用它
  （`Enchantment.java:663-665` / 26.1.2 `:671`；语义见 `IItemExtension.java:453-459` / 26.1.2 `:380-386`）。

**支持的写法**（由 `HolderSetCodec` 决定，`.mc-src/1.21.1/net/minecraft/resources/HolderSetCodec.java`）：

| 写法 | 示例 | 解析结果 | 证据 |
|---|---|---|---|
| 标签 | `"#c:foods"` | `HolderSet` = 该标签 | `:46` `Codec.either(TagKey.hashedCodec(registryKey), homogenousListCodec)`；`:67` `lookupTag` |
| 单个物品 id | `"minecraft:apple"` | `HolderSet.direct(...)`（单元素） | `:31-35` `Codec.either(codec, holderCodec).xmap(... List::of)` |
| 物品 id 列表 | `["minecraft:apple", "minecraft:bread"]` | `HolderSet.direct(...)` | `:27-28` `holderCodec.listOf()` |
| NeoForge 自定义 holder set | `{"type": "neoforge:any"}` / `{"type": "neoforge:and", "values": [...]}` / `{"type": "neoforge:or", "values": [...]}` / `{"type": "neoforge:not", "value": ...}` | 对应实现 | `:48-50` dispatch codec；`NeoForgeMod.java:344,349,354,360`；`AndHolderSet.java:75-79`（字段 `values`）；`NotHolderSet.java:154-159`（字段 `value`） |

**重要限制**：
- 列表必须是**同质**的——不能混用标签引用与直接物品 id（`HolderSetCodec.java:28` `ExtraCodecs.ensureHomogenous(Holder::kind)`）。
- **不存在 `{"items": [...]}` 这种写法**。源码中 `HolderSetCodec` 的分支只有「标签 / 元素列表 / NeoForge holder set 对象」三类，
  `AndHolderSet` / `OrHolderSet` 的字段名是 `values` 而不是 `items`（`AndHolderSet.java:79`、`OrHolderSet.java:67`），
  `NotHolderSet` 是 `value`（`NotHolderSet.java:159`）。搜索整个 `.mc-src/` 也**未找到**任何 `"items"` 作为 holder set 字段名的 codec。
- 1.20.1 的 `HolderSetCodec` 也有同样的标签/列表结构（`.mc-src/1.20.1/net/minecraft/resources/HolderSetCodec.java`），
  但 1.20.1 的附魔根本不读它。

### 4.3 食物标签：能否直接写 `#c:foods` / `#forge:food`？

| 标签 | 1.20.1 (Forge) | 1.21.1 (NeoForge) | 26.1.2 (NeoForge) |
|---|---|---|---|
| `#c:foods` | **不存在** | **存在** ✅ | **存在** ✅（由 NeoForge 数据生成器产出） |
| `#forge:food` / `#forge:foods` | **不存在** ❌ | 不存在（已迁移到 `c:`） | 不存在（已迁移到 `c:`） |
| `#neoforge:foods` | — | **未找到** ❌ | **未找到** ❌ |
| `#c:animal_foods` | — | 存在 ✅ | 存在 ✅ |

证据：
- 1.21.1 真实标签文件：`.mc-src/1.21.1/data/c/tags/item/foods.json:1-25`
  （`values` 含 `minecraft:baked_potato`、`#c:foods/fruit`、`#c:foods/vegetable` … 共 21 项）。
  子标签文件：`data/c/tags/item/foods/edible_when_placed.json`、`data/c/tags/item/foods/food_poisoning.json`；
  另有 `data/c/tags/item/animal_foods.json`。
- 26.1.2 的 `c:` 标签由代码生成（反编译 jar 里没有 `data/c/` 目录）：
  `Tags.Items.FOODS = tag("foods")`（`.mc-src/26.1.2/net/neoforged/neoforge/common/Tags.java:593`），
  而 `tag(String)` 的命名空间是 `c`：`ItemTags.create(Identifier.fromNamespaceAndPath("c", name))`（同文件 `:1051-1053`）。
  生成逻辑：`NeoForgeItemTagsProvider.java:133-154`（`FOODS` 汇总 `FOODS_FRUIT`/`FOODS_VEGETABLE`/… 16 个子标签 + 5 个直接物品）；
  `ANIMAL_FOODS`：`:155-160`。语言键：`NeoForgeLanguageProvider.java:243-260`。
- 1.20.1：`grep FOOD|foods` 在 `.mc-src/1.20.1/net/minecraftforge/**/*.java` 只命中
  `RecipeBookManager.java:53,57` 与 `VanillaGuiOverlay.java:82`，与物品标签无关；
  `.mc-src/1.20.1/data/forge/tags/items/` 下**没有**任何名为 `food`/`foods` 的文件。
  → **1.20.1 不存在加载器通用食物标签。**

**结论（第 4 条要点）**：
- 1.21.1 与 26.1.2：`"supported_items": "#c:foods"` **可以直接写**，无需自己建标签。
  ⚠️ 但要注意 `c:foods` 是 NeoForge 生成的原版+模组食物汇总，**不含** `minecraft:cake` 之类的「放置型食物」
  之外的边角情况——`cake` 在 `c:foods/edible_when_placed`（`NeoForgeItemTagsProvider.java:147`）里，
  而该子标签已被汇总进 `c:foods`（`:151-154`），所以 `cake` 也在 `c:foods` 中。
- 1.20.1：**不能**。必须自己建物品标签（例如 `data/orefood/tags/items/foods.json`）并在代码里
  `stack.is(orefoodFoodsTag)` 判定，或者用 `EnchantmentCategory.create(...)` + `Item#isEdible`/`getFoodProperties` 判定。

### 4.4 26.1.2 的额外注意：`minecraft:enchantable` 数据组件

26.1.2 新增了 `minecraft:enchantable` 组件（`DataComponents.java:189`，类型 `Enchantable`，`Enchantable.java:10-23`，
codec 键 `value`，正数）。它与 `supported_items` 不是同一机制：`supported_items` 决定「这个附魔能不能上这个物品」，
`enchantable` 决定「这个物品的附魔能力值（enchantability）」。两者都会影响附魔结果，注册食物附魔时不要混淆。

---

## 5. `max_level` / `treasure` / `rarity` / `anvil_cost` / `weight` / `slots` / `description` 的确切键名与格式

### 5.1 逐字段对照（1.21.1 与 26.1.2 **完全一致**，除 `exclusive_set` 默认值）

| 概念 | JSON 键名 | 取值格式 | 1.21.1 证据 | 26.1.2 证据 |
|---|---|---|---|---|
| 最大等级 | `max_level` | int，`1..255` | `Enchantment.java:667` | `Enchantment.java:673` |
| 稀有度 | **无 `rarity` 键** | — | — | — |
| 宝藏 | **无 `treasure` 键** | 用附魔标签 `#minecraft:treasure` 表示 | `EnchantmentTags.java:28` | `EnchantmentTags.java:28` |
| 铁砧代价 | `anvil_cost` | int，非负（`ExtraCodecs.NON_NEGATIVE_INT`） | `Enchantment.java:670` | `Enchantment.java:676` |
| 权重 | `weight` | int，`1..1024` | `Enchantment.java:666` | `Enchantment.java:672` |
| 生效槽位 | `slots` | `List<EquipmentSlotGroup>`，如 `["mainhand"]`、`["any"]`、`["armor"]` | `Enchantment.java:671` | `Enchantment.java:677` |
| 显示名 | `description` | `Component`（对象 `{"translate": ...}`/`{"text": ...}`，或纯字符串） | `Enchantment.java:65` | `Enchantment.java:64` |
| 互斥集合 | `exclusive_set` | `HolderSet<Enchantment>`（如 `"#minecraft:exclusive_set/damage"`），可选 | `Enchantment.java:67-69` | `Enchantment.java:66` |
| 最低代价 | `min_cost` | `{"base": int, "per_level_above_first": int}` | `Enchantment.java:668`, `:637-643` | `Enchantment.java:674`, `:645-651` |
| 最高代价 | `max_cost` | 同上 | `Enchantment.java:669` | `Enchantment.java:675` |
| 效果 | `effects` | `DataComponentMap`，可选，默认 `{}` | `Enchantment.java:70` | `Enchantment.java:67` |

### 5.2 关于 `rarity`

**1.21.1 / 26.1.2 的附魔 JSON 里没有 `rarity` 字段。**
搜索 `Enchantment.java` 全文未找到 `rarity`（1.20.1 有 `Enchantment.Rarity`，见 §1.1）。
原版用 `weight` 承担「稀有度」作用（`weight` 越小越稀有），并用 `#minecraft:treasure` 标记宝藏。

原版 43 个附魔的 `weight` 实测分布（遍历 `.mc-src/26.1.2/data/minecraft/enchantment/*.json` 的 `"weight"` 字段）：

| weight | 附魔（示例） |
|---|---|
| 1 | `binding_curse`、`vanishing_curse`、`silk_touch`、`soul_speed`、`swift_sneak`、`thorns`、`channeling`、`infinity` |
| 2 | `mending`、`frost_walker`、`fire_aspect`、`fortune`、`looting`、`multishot`、`respiration`、`riptide`、`depth_strider`、`aqua_affinity`、`blast_protection`、`breach`、`flame`、`luck_of_the_sea`、`lure`、`punch`、`sweeping_edge`、`wind_burst` |
| 5 | `sharpness` 之外的多数中档附魔：`bane_of_arthropods`、`smite`、`knockback`、`loyalty`、`lunge`、`unbreaking`、`density`、`feather_falling`、`fire_protection`、`projectile_protection`、`quick_charge` |
| 10 | `sharpness`、`protection`、`efficiency`、`power`、`piercing` |

（`sharpness.json:35` 的 `"weight": 10`；`mending.json` 的 `"weight": 2` 且 `"max_level": 1`。）

### 5.3 `slots` 的合法取值

`EquipmentSlotGroup` 枚举（`.mc-src/1.21.1/net/minecraft/world/entity/EquipmentSlotGroup.java:12-22`；
26.1.2 同文件 `:14-26`），`CODEC = StringRepresentable.fromEnum(...)`（1.21.1 `:25`；26.1.2 `:28`）：

`"any"` / `"mainhand"` / `"offhand"` / `"hand"` / `"feet"` / `"legs"` / `"chest"` / `"head"` / `"armor"` / `"body"`

> ⚠️ 食物附魔的关键陷阱：`slots` 决定「附魔在哪些槽位生效」。食物一般拿在手上，
> 所以要么写 `"mainhand"`，要么写 `"any"`。但**即使写了 `any`，`EnchantmentHelper` 的 `tick` / `post_attack`
> 等效果也只在「装备槽位扫描」路径里被触发**（`Enchantment.getSlotItems` 只遍历 `EquipmentSlot.values()`，
> 见 1.21.1 `Enchantment.java:117-130`），吃食物的瞬间手上物品并不是通过该路径扫描的。

### 5.4 `treasure` 附魔怎么表示

用**附魔标签**，不是 JSON 字段：
- 常量：`EnchantmentTags.TREASURE = create("treasure")`（`.mc-src/1.21.1/net/minecraft/tags/EnchantmentTags.java:28`；26.1.2 同文件 `:28`），
  命名空间为 `minecraft`（`create()` 用 `ResourceLocation.withDefaultNamespace`，1.21.1 `:46`；26.1.2 用 `Identifier.withDefaultNamespace`，`:39`）。
- 数据文件：`.mc-src/26.1.2/data/minecraft/tags/enchantment/treasure.json:1-11`，内容为
  `binding_curse` / `vanishing_curse` / `swift_sneak` / `soul_speed` / `frost_walker` / `mending` / `wind_burst`。
- 对应还有 `non_treasure`（`.mc-src/26.1.2/data/minecraft/tags/enchantment/non_treasure.json`）。
- 自定义附魔要「是宝藏」：在你的数据包里加
  `data/<modid>/tags/enchantment/treasure.json`（NeoForge 会自动合并进 `minecraft:treasure`），
  或直接 `data/minecraft/tags/enchantment/treasure.json` 追加你的 id。
- 相关判定方法：`EnchantmentHelper.hasTag(ItemStack, TagKey<Enchantment>)`（1.21.1 `:441`；26.1.2 `:452`）。
- 1.21.1 的完整附魔标签常量表见 `.mc-src/1.21.1/net/minecraft/tags/EnchantmentTags.java:8-43`（含 `curse`、`tradeable`、
  `in_enchanting_table`、`on_random_loot`、`exclusive_set/*` 等）；26.1.2 见 `EnchantmentTags.java:8-36`。
  26.1.2 的 `EnchantmentTags` **删除了** `TRADES_*_SPECIAL` 系列（对比 1.21.1 `:37-43`）。

---

## 6. `EnchantmentEffectComponents` 内置效果组件完整清单

### 6.1 注册机制

`EnchantmentEffectComponents` 是一个 **interface**，每个组件通过 `Registry.register(BuiltInRegistries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, name, ...)` 注册
（1.21.1 `.mc-src/1.21.1/net/minecraft/world/item/enchantment/EnchantmentEffectComponents.java:131-133`；
26.1.2 同文件 `:135-137`）。
JSON 中的键 = `minecraft:<注册名>`（原版全部在 `minecraft` 命名空间，见 `:22` `BuiltInRegistries...byNameCodec()`）。

### 6.2 1.21.1 全部组件（共 30 个）

| # | JSON 键（`minecraft:` 前缀省略） | Java 常量 | 值类型 | 行号 |
|---|---|---|---|---|
| 1 | `damage_protection` | `DAMAGE_PROTECTION` | `List<ConditionalEffect<EnchantmentValueEffect>>` | `:24-27` |
| 2 | `damage_immunity` | `DAMAGE_IMMUNITY` | `List<ConditionalEffect<DamageImmunity>>` | `:28-30` |
| 3 | `damage` | `DAMAGE` | `List<ConditionalEffect<EnchantmentValueEffect>>` | `:31-33` |
| 4 | `smash_damage_per_fallen_block` | `SMASH_DAMAGE_PER_FALLEN_BLOCK` | 同上 | `:34-37` |
| 5 | `knockback` | `KNOCKBACK` | 同上 | `:38-40` |
| 6 | `armor_effectiveness` | `ARMOR_EFFECTIVENESS` | 同上 | `:41-44` |
| 7 | `post_attack` | `POST_ATTACK` | `List<TargetedConditionalEffect<EnchantmentEntityEffect>>` | `:45-48` |
| 8 | `hit_block` | `HIT_BLOCK` | `List<ConditionalEffect<EnchantmentEntityEffect>>` | `:49-51` |
| 9 | `item_damage` | `ITEM_DAMAGE` | `List<ConditionalEffect<EnchantmentValueEffect>>` | `:52-54` |
| 10 | `attributes` | `ATTRIBUTES` | `List<EnchantmentAttributeEffect>` | `:55-57` |
| 11 | `equipment_drops` | `EQUIPMENT_DROPS` | `List<TargetedConditionalEffect<EnchantmentValueEffect>>` | `:58-63` |
| 12 | `location_changed` | `LOCATION_CHANGED` | `List<ConditionalEffect<EnchantmentLocationBasedEffect>>` | `:64-67` |
| 13 | `tick` | `TICK` | `List<ConditionalEffect<EnchantmentEntityEffect>>` | `:68-70` |
| 14 | `ammo_use` | `AMMO_USE` | `List<ConditionalEffect<EnchantmentValueEffect>>` | `:71-73` |
| 15 | `projectile_piercing` | `PROJECTILE_PIERCING` | 同上 | `:74-77` |
| 16 | `projectile_spawned` | `PROJECTILE_SPAWNED` | `List<ConditionalEffect<EnchantmentEntityEffect>>` | `:78-81` |
| 17 | `projectile_spread` | `PROJECTILE_SPREAD` | `List<ConditionalEffect<EnchantmentValueEffect>>` | `:82-85` |
| 18 | `projectile_count` | `PROJECTILE_COUNT` | 同上 | `:86-89` |
| 19 | `trident_return_acceleration` | `TRIDENT_RETURN_ACCELERATION` | 同上 | `:90-93` |
| 20 | `fishing_time_reduction` | `FISHING_TIME_REDUCTION` | 同上 | `:94-97` |
| 21 | `fishing_luck_bonus` | `FISHING_LUCK_BONUS` | 同上 | `:98-101` |
| 22 | `block_experience` | `BLOCK_EXPERIENCE` | 同上 | `:102-105` |
| 23 | `mob_experience` | `MOB_EXPERIENCE` | 同上 | `:106-109` |
| 24 | `repair_with_xp` | `REPAIR_WITH_XP` | 同上 | `:110-113` |
| 25 | `crossbow_charge_time` | `CROSSBOW_CHARGE_TIME` | `EnchantmentValueEffect`（单个，非 list） | `:114-116` |
| 26 | `crossbow_charging_sounds` | `CROSSBOW_CHARGING_SOUNDS` | `List<CrossbowItem.ChargingSounds>` | `:117-119` |
| 27 | `trident_sound` | `TRIDENT_SOUND` | `List<Holder<SoundEvent>>` | `:120` |
| 28 | `prevent_equipment_drop` | `PREVENT_EQUIPMENT_DROP` | `Unit`（`{}`） | `:121` |
| 29 | `prevent_armor_change` | `PREVENT_ARMOR_CHANGE` | `Unit`（`{}`） | `:122` |
| 30 | `trident_spin_attack_strength` | `TRIDENT_SPIN_ATTACK_STRENGTH` | `EnchantmentValueEffect` | `:123-125` |

### 6.3 26.1.2 全部组件（共 31 个）

与 1.21.1 的差异：**新增** `post_piercing_attack`（`:51-54`），其余 30 个同名同义。
`bootstrap` 返回 `DAMAGE_PROTECTION`（`:131-133`）。

### 6.4 与「施加状态效果」相关的组件：`apply_mob_effect`

**重要澄清**：`apply_mob_effect` **不是一个 effect component**——它是 **`EnchantmentEntityEffect` 的一种类型**，
必须挂在某个 effect 集合里（`post_attack` / `tick` / `projectile_spawned` / `hit_block` / `location_changed` 等）才能生效。

**注册名**：`minecraft:apply_mob_effect`
证据：1.21.1 `.mc-src/1.21.1/net/minecraft/world/item/enchantment/effects/EnchantmentEntityEffect.java:20`
（`Registry.register(registry, "apply_mob_effect", ApplyMobEffect.CODEC);`）；
26.1.2 同文件 `:20`。
注册表：`Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE`（1.21.1 `BuiltInRegistries.java:279`；26.1.2 `:316`）。

**数据结构**（1.21.1 `effects/ApplyMobEffect.java:22-34`；26.1.2 `:21-33`）：
```java
public record ApplyMobEffect(
    HolderSet<MobEffect> toApply,
    LevelBasedValue minDuration,
    LevelBasedValue maxDuration,
    LevelBasedValue minAmplifier,
    LevelBasedValue maxAmplifier
) implements EnchantmentEntityEffect
```
JSON 键（全部必填，`RecordCodecBuilder.mapCodec`，1.21.1 `:25-34`；26.1.2 `:24-33`）：

| JSON 键 | 类型 | 说明 |
|---|---|---|
| `to_apply` | `HolderSet<MobEffect>` | 如 `"minecraft:slowness"` 或 `["minecraft:speed","minecraft:strength"]` 或 `"#minecraft:..."` |
| `min_duration` | `LevelBasedValue` | **单位：秒**（代码里 `* 20.0F` 转 tick，1.21.1 `:42`；26.1.2 `:42`） |
| `max_duration` | `LevelBasedValue` | 秒 |
| `min_amplifier` | `LevelBasedValue` | 0 起（amplifier 0 = I 级） |
| `max_amplifier` | `LevelBasedValue` | |

应用逻辑：随机取 `toApply` 中一个效果，`Mth.randomBetween(random, min, max)` 取持续/等级，
`livingentity.addEffect(new MobEffectInstance(...))`（1.21.1 `:37-47`；26.1.2 `:36-50`）。

**`LevelBasedValue` 的类型**（`Registries.ENCHANTMENT_LEVEL_BASED_VALUE_TYPE`）：
1.21.1（`LevelBasedValue.java:27-31`）：`minecraft:clamped`、`minecraft:fraction`、`minecraft:levels_squared`、`minecraft:linear`、`minecraft:lookup`，
另外裸浮点数直接解析为 `Constant`（`:18` `Codec.either(LevelBasedValue.Constant.CODEC, DISPATCH_CODEC)`）。
26.1.2（`LevelBasedValue.java:22-27`）：同上 **+ `minecraft:exponent`**。

常用写法：
- 常量：直接写数字，如 `"min_duration": 1.5`（对照 `bane_of_arthropods.json:38`）
- 线性：`{"type": "minecraft:linear", "base": 1.5, "per_level_above_first": 0.5}`（对照 `bane_of_arthropods.json:32-36`）

**`TargetedConditionalEffect` / `ConditionalEffect` 结构**（挂在哪个集合里决定了外层形状）：

- `ConditionalEffect<T>`：`{"effect": <T>, "requirements": <LootItemCondition>?}`
  （`requirements` 可选；定义见 `.mc-src/1.21.1/net/minecraft/world/item/enchantment/ConditionalEffect.java:30`）
- `TargetedConditionalEffect<T>`：`{"enchanted": "attacker"|"victim", "affected": "attacker"|"victim", "effect": <T>, "requirements": <condition>?}`
  仅用于 `post_attack` 与 `equipment_drops`（定义见 `TargetedConditionalEffect.java:16,31`）。
  真实例子：`.mc-src/26.1.2/data/minecraft/enchantment/bane_of_arthropods.json:26-61`
  （`affected: "victim"`、`enchanted: "attacker"`、`effect.type: "minecraft:apply_mob_effect"`、`requirements.condition: "minecraft:all_of"`）。
- `EnchantmentAttributeEffect`：`attributes` 组件专用（`effects/EnchantmentAttributeEffect.java`）。

**与「施加状态效果」相关的其他可用类型**（同为 `EnchantmentEntityEffect`，可挂在同样集合里）：
- `minecraft:ignite`（`ignite`，1.21.1 `EnchantmentEntityEffect.java:24`）—— 施加燃烧
- `minecraft:apply_exhaustion`（**仅 26.1.2**，26.1.2 `EnchantmentEntityEffect.java:26`）—— 施加饥饿消耗
- `minecraft:apply_impulse`（**仅 26.1.2**，`:25`）
- `minecraft:damage_entity`（1.21.1 `:21`；26.1.2 `:22`）
- `minecraft:all_of`（1.21.1 `:19`；26.1.2 `:19`）
- `minecraft:run_function`（1.21.1 `:28`；26.1.2 `:30`）—— **理论上可以用 `/effect give` 命令绕过一切限制**
- `minecraft:explode` / `minecraft:play_sound` / `minecraft:spawn_particles` / `minecraft:summon_entity` /
  `minecraft:replace_block` / `minecraft:replace_disk` / `minecraft:set_block_properties`（两版都有）
- `minecraft:damage_item`（**仅 1.21.1**，`:22`）/ `minecraft:change_item_damage`（**仅 26.1.2**，`:21`）

---

## 7. **关键问题**：是否存在「物品被食用 / 使用完成」的触发点 effect component？

### 7.1 结论（明确回答）

**不存在。**

- 1.21.1 `EnchantmentEffectComponents` 的 30 个组件里，**没有**任何 `eat` / `consume` / `use` / `food` / `finish_using` 相关的组件（完整列表见 §6.2）。
- 26.1.2 `EnchantmentEffectComponents` 的 31 个组件里，同样**没有**（完整列表见 §6.3）。
- 对 `.mc-src/1.21.1/net/minecraft/world/item/enchantment/` 全包做 `(?i)eat|consum|food|use_item|on_use` 检索，
  命中的全部是无关内容（`Consumer` 泛型、`createFire`、`FALL`、`Consumer` 参数等），**没有任何食物/食用语义的组件或效果**。
- 对 `.mc-src/26.1.2/net/minecraft/world/item/enchantment/` 做同样检索，唯一与「吃」沾边的是
  `effects/ApplyExhaustion.java:20` 的 `livingEntity.causeFoodExhaustion(...)`——那是「增加饥饿消耗」，
  **不是**「被食用时触发」。
- 对 `.mc-src/1.20.1/net/minecraft/world/item/enchantment/` 检索，命中只有 `getOrCreateDescriptionId`、`EnchantmentCategory.create`、`FALL`、`BlockSnapshot.create`，同样无关。

### 7.2 为什么纯数据驱动做不到

1.21.1/26.1.2 的所有 effect component 的**触发时机**是硬编码在 Java 调用点的，共 5 类上下文：

| LootContext 参数集 | 触发点 | 组件 |
|---|---|---|
| `ENCHANTED_DAMAGE` | 攻击/受伤 | `damage`、`damage_protection`、`damage_immunity`、`knockback`、`armor_effectiveness`、`smash_damage_per_fallen_block`、`post_attack`、`equipment_drops` |
| `ENCHANTED_ITEM` | 物品耐久/弹药等 | `item_damage`、`ammo_use`、`projectile_piercing`、`block_experience`、`repair_with_xp` |
| `ENCHANTED_LOCATION` | 移动/换位置 | `location_changed` |
| `ENCHANTED_ENTITY` | 实体每 tick / 弹射物 | `tick`、`projectile_spawned`、`projectile_spread`、`projectile_count`、`trident_return_acceleration`、`fishing_time_reduction`、`fishing_luck_bonus`、`mob_experience` |
| `HIT_BLOCK` | 击中方块 | `hit_block` |

（参数集见 1.21.1 `EnchantmentEffectComponents.java:24-125` 每行的 `LootContextParamSets.*`；
26.1.2 同结构，类型为 `ContextKeySet`。）
**没有任何一个参数集对应「物品使用完成 / 食用」。**
而 `Item.finishUsingItem` 的执行路径（1.21.1 `.mc-src/1.21.1/net/minecraft/world/item/Item.java:183-186`）：
```java
public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
    FoodProperties foodproperties = stack.getFoodProperties(livingEntity);
    return foodproperties != null ? livingEntity.eat(level, stack, foodproperties) : stack;
}
```
—— 全程**不查询任何附魔**。

26.1.2 的路径（`.mc-src/26.1.2/net/minecraft/world/item/Item.java:232-235`）：
```java
public ItemStack finishUsingItem(ItemStack itemStack, Level level, LivingEntity entity) {
    Consumable consumable = itemStack.get(DataComponents.CONSUMABLE);
    return consumable != null ? consumable.onConsume(level, entity, itemStack) : itemStack;
}
```
`Consumable.onConsume` 遍历 `onConsumeEffects`（`.mc-src/26.1.2/net/minecraft/world/item/component/Consumable.java:87`），
而 `ConsumeEffect` 的类型只有 5 种（`.mc-src/26.1.2/net/minecraft/world/item/consume_effects/ConsumeEffect.java:25-39`）：
`apply_effects` / `remove_effects` / `clear_all_effects` / `teleport_randomly` / `play_sound`。
**这些是「物品组件」而不是「附魔效果」**——它们的取值在物品定义里固定，**不能读取附魔等级**，
也不能按附魔等级缩放（`apply_effects` 用的是固定的 `MobEffectInstance` 列表）。
也就是说：26.1.2 可以纯数据驱动地做到「吃这个物品 → 获得药水效果」，
但**做不到**「吃这个物品 → 如果它有 X 附魔 → 按附魔等级 Y 施药效」。

### 7.3 三版本「食用时施药效」的正确实现路径

**共同结论：三版本都必须写代码。** 好消息是**三版本都有同一个现成的事件钩子**
`LivingEntityUseItemEvent.Finish`，它正是「物品使用完成（含吃完食物）」的触发点：

| 版本 | 事件类路径 | `Finish` 内部类行号 | 事件触发点 |
|---|---|---|---|
| 1.20.1 | `net.minecraftforge.event.entity.living.LivingEntityUseItemEvent` | `.mc-src/1.20.1/net/minecraftforge/event/entity/living/LivingEntityUseItemEvent.java:31-47` | `ForgeEventFactory.onItemUseFinish`（`.mc-src/1.20.1/net/minecraftforge/event/ForgeEventFactory.java:302`）由 `LivingEntity.java:3064` 调用 |
| 1.21.1 | `net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent` | `.mc-src/1.21.1/net/neoforged/neoforge/event/entity/living/LivingEntityUseItemEvent.java:115-123` | `EventHooks.onItemUseFinish`（`.mc-src/1.21.1/net/neoforged/neoforge/event/EventHooks.java:472-476`）由 `LivingEntity.java:3280` 调用 |
| 26.1.2 | `net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent` | `.mc-src/26.1.2/net/neoforged/neoforge/event/entity/living/LivingEntityUseItemEvent.java:107-122` | `EventHooks.onItemUseFinish` 由 `LivingEntity.java:3610` 调用 |

**关键 API**：
- `LivingEntityUseItemEvent.getItem()` → `ItemStack`（**正在被使用的原始物品**，仍带附魔），
  1.20.1 `:18-21`；1.21.1 `:29`；26.1.2 `:29`。
- `LivingEntityUseItemEvent.getEntity()` → `LivingEntity`（继承自 `LivingEvent`；1.20.1 `:8`，1.21.1 `:13`，26.1.2 `:13`）。
- `Finish.getResultStack()` / `setResultStack(ItemStack)` → 使用完成后放回背包的物品
  （1.20.1 `:39-46`；26.1.2 `:115-121`）。
- 继承链：`Finish extends LivingEntityUseItemEvent extends LivingEvent extends EntityEvent`（1.20.1 `:8`）。

**触发顺序（重要）**：以 26.1.2 为例（`LivingEntity.java:3608-3615`）：
```java
ItemStack copy = this.useItem.copy();
ItemStack result = EventHooks.onItemUseFinish(this, copy, getUseItemRemainingTicks(),
                                             this.useItem.finishUsingItem(this.level(), this));
```
—— `finishUsingItem(...)`（即原版吃食物的逻辑：恢复饥饿、施加 `FoodProperties.effects`）**先执行**，
事件**后触发**，事件拿到的是 `copy`（原物品的副本，附魔仍在）。
1.20.1 完全同构（`LivingEntity.java:3062-3066`）。所以在这个事件里读附魔 + `addEffect` 是安全且语义正确的。

**因此推荐实现（三版本统一）**：

| 版本 | 附魔本体 | 「食用时施药效」 |
|---|---|---|
| **1.20.1** | 代码：`DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID)`（键 `ForgeRegistries.Keys.ENCHANTMENTS`，`ForgeRegistries.java:139`）+ `Enchantment` 子类，`canEnchant` 用自建食物标签或 `Item#isEdible` | `@SubscribeEvent void onUseFinish(LivingEntityUseItemEvent.Finish e)` → `EnchantmentHelper.getItemEnchantmentLevel(myEnch, e.getItem())`（`EnchantmentHelper.java:63`）→ `e.getEntity().addEffect(...)` |
| **1.21.1** | 数据：`data/<modid>/enchantment/<id>.json`，`"supported_items": "#c:foods"`，`"slots": ["any"]` | `@SubscribeEvent void onUseFinish(LivingEntityUseItemEvent.Finish e)` → `e.getItem().getEnchantmentLevel(holder)`（`IItemStackExtension.java:162`）或 `EnchantmentHelper.getTagEnchantmentLevel(holder, e.getItem())`（`:64`）→ `e.getEntity().addEffect(...)` |
| **26.1.2** | 数据：同上（`Identifier` 而非 `ResourceLocation`，`c:foods` 由 NeoForge 生成） | 同上，但读等级用 `e.getItem().getEnchantmentLevel(holder)`（`ItemInstanceExtension.java:56`，`ItemStack` 实现了 `ItemInstance`） |

**备选方案（若不想依赖事件）**：覆写自己的 `Item#finishUsingItem(ItemStack, Level, LivingEntity)`
（签名三版本一致：1.21.1 `Item.java:183`、26.1.2 `Item.java:232`；1.20.1 同），在 `super.finishUsingItem(...)` 之后读附魔并施药效。

**不可行的方案（已排除）**：
- ❌ 用 `minecraft:tick` 组件 + 条件判定：`tick` 只对**装备槽位**扫描到的物品生效（`Enchantment.getSlotItems` 遍历 `EquipmentSlot.values()`，
  1.21.1 `Enchantment.java:117-130`；26.1.2 `:107-120`），食物拿在手上时若 `slots` 不含 `mainhand` 就完全不会被 tick。
  即使 `slots: ["mainhand"]`，`EnchantmentHelper.tickEffects` 也是按 tick 触发的，无法精确对齐「吃下的那一刻」，
  且无法阻止它在吃之前/之后反复触发。
- ❌ 纯 JSON：`effects` 里没有任何食用触发点（§7.1）。
- ❌ 26.1.2 的 `Consumable.on_consume_effects`：它是**物品组件**，`ConsumeEffect` 的 5 种类型
  （`apply_effects` / `remove_effects` / `clear_all_effects` / `teleport_randomly` / `play_sound`，
  `ConsumeEffect.java:25-39`）取值固定、**不读附魔等级**，无法按附魔等级缩放。

---

## 8. 三版本附魔 API 主要差异清单

| 维度 | 1.20.1 (Forge 47.4.5) | 1.21.1 (NeoForge 21.1.250) | 26.1.2 (NeoForge 26.1.2.109) |
|---|---|---|---|
| `Enchantment` 类型 | `abstract class`（`Enchantment.java:20`） | `record`，4 字段（`Enchantment.java:61`） | `record`，4 字段（`Enchantment.java:60`） |
| 构造/定义 | `protected Enchantment(Rarity, EnchantmentCategory, EquipmentSlot[])`（`:32`） | `record` 规范构造器 + `EnchantmentDefinition`（`:650`） | 同 1.21.1（`:658`） |
| 等级上限来源 | 覆写 `getMaxLevel()`（`:59`） | `definition.maxLevel()`（`:175`） | 同（`:164`） |
| 可附魔物品 | `EnchantmentCategory` 枚举 + `canEnchant` 覆写（`EnchantmentCategory.java:18-102`） | JSON `supported_items` / `primary_items`（`Enchantment.java:662-665`） | 同（`:670-671`） |
| 权重 | `Rarity` 枚举权重（`Enchantment.java:149-163`） | JSON `weight`（`:666`） | 同（`:672`） |
| 稀有度概念 | `Enchantment.Rarity` 枚举 | 无 `rarity`，用 `weight` | 无 `rarity`，用 `weight` |
| 宝藏/诅咒 | 方法 `isTreasureOnly()` / `isCurse()`（`:125,129`） | 附魔标签 `#minecraft:treasure` / `#minecraft:curse`（`EnchantmentTags.java:28,22`） | 同（`EnchantmentTags.java:28,22`） |
| 注册机制 | 静态：`BuiltInRegistries.ENCHANTMENT`（`Enchantments.java:72`）/ `ForgeRegistries.ENCHANTMENTS`（`ForgeRegistries.java:55`） | **datapack registry**：`data/<ns>/enchantment/*.json`（`RegistryDataLoader.java:96,113`） | 同（`RegistryDataLoader.java:118,154`） |
| 效果系统 | 硬编码在 `Enchantment` 子类方法里（`doPostAttack`/`doPostHurt` 等） | `DataComponentMap effects` + `EnchantmentEffectComponents`（30 个） | 同 + `post_piercing_attack`（31 个） |
| 施加药效 | 子类里 `entity.addEffect(...)` | 数据：`minecraft:apply_mob_effect` 实体效果，挂 `post_attack`/`tick` 等 | 同 |
| 读取等级 | `EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)`（`EnchantmentHelper.java:63`） | `getItemEnchantmentLevel(Holder<Enchantment>, ItemStack)`（`:56`）；Neo: `stack.getEnchantmentLevel(Holder)`（`IItemStackExtension.java:162`） | `getItemEnchantmentLevel(Holder<Enchantment>, ItemInstance)`（`:54`）；Neo: `ItemInstanceExtension.getEnchantmentLevel(Holder)`（`ItemInstanceExtension.java:56`） |
| 附魔键类型 | `Enchantment`（裸对象） | `Holder<Enchantment>` | `Holder<Enchantment>` |
| 全部附魔 | `EnchantmentHelper.getEnchantments(ItemStack)` → `Map<Enchantment,Integer>`（`:86`） | `ItemEnchantments`（`ItemStack#getTagEnchantments`，`ItemStack.java:962`）；无 Map 版本 | 同（`ItemEnchantments.java:54,89,93`） |
| `hasAnyEnchantments` | **未找到** | `EnchantmentHelper.java:99` | `EnchantmentHelper.java:97` |
| 标识符类 | `ResourceLocation` | `ResourceLocation` | **`Identifier`** |
| 物品抽象 | `ItemStack` | `ItemStack` | `ItemStack` + **`ItemInstance`** |
| 食物数据 | `FoodProperties`（`FoodProperties.java`） | `FoodProperties`（`minecraft:food` 组件，键 `nutrition`/`saturation`/`can_always_eat`/`eat_seconds`/`using_converts_to`/`effects`，`FoodProperties.java:17-28`） | `FoodProperties` + **`Consumable`**（`consume_seconds`/`animation`/`sound`/`has_consume_particles`/`on_consume_effects`，`Consumable.java:32-47`） |
| 食物通用标签 | **无** `forge:food(s)` | **`#c:foods`**（`data/c/tags/item/foods.json`） | **`#c:foods`**（`Tags.java:593` + `NeoForgeItemTagsProvider.java:149`） |
| 附魔能力 | `Item#getEnchantmentValue` | 同 | 新增 `minecraft:enchantable` 组件（`DataComponents.java:189`） |
| 附魔 JSON 目录 | 不存在 | `data/<ns>/enchantment/` | `data/<ns>/enchantment/` |
| 「食用完成」钩子 | `LivingEntityUseItemEvent.Finish`（`net.minecraftforge.event.entity.living`，`:31-47`） | `LivingEntityUseItemEvent.Finish`（`net.neoforged.neoforge.event.entity.living`，`:115-123`） | `LivingEntityUseItemEvent.Finish`（`net.neoforged.neoforge.event.entity.living`，`:107-122`） |
| 「食用完成」触发点 | `LivingEntity.java:3064`（`ForgeEventFactory.onItemUseFinish`） | `LivingEntity.java:3280`（`EventHooks.onItemUseFinish`） | `LivingEntity.java:3610`（`EventHooks.onItemUseFinish`） |

---

## 9. 自定义附魔的 lang 键与 `description` 组件

### 9.1 1.21.1 / 26.1.2：`enchantment.<ns>.<id>` **不是强制要求**

- `description` 在 codec 里是**必填**字段（`ComponentSerialization.CODEC.fieldOf("description")`，
  1.21.1 `Enchantment.java:65`；26.1.2 `Enchantment.java:64`）。
- **显示名完全来自 `description` 组件**，不看注册 id。
  证据：`Enchantment.getFullname(Holder<Enchantment>, int level)`（1.21.1 `:199-212`；26.1.2 `:185-198`）第一行就是
  `enchantment.value().description.copy()`，之后只做「按 `#minecraft:curse` 染色（红/灰）」与「追加等级后缀」。
- 所以：
  - 若 `description` 写成 `{"translate": "enchantment.orefood.nourishing"}`，你**必须**在
    `assets/<modid>/lang/zh_cn.json` / `en_us.json` 里提供该键（这是原版惯例，也是推荐做法）。
  - 若写成 `{"text": "滋养"}` 或 `{"translate": "orefood.enchantment.nourishing"}`，则**不需要** `enchantment.<ns>.<id>` 这个特定键，
    只需要提供你实际引用的那个键（或干脆内联文本）。
- `ComponentSerialization.CODEC` 接受纯字符串（`.mc-src/1.21.1/net/minecraft/network/chat/ComponentSerialization.java:118`
  `Codec.either(Codec.either(Codec.STRING, ...), codec)`），所以 `"description": "Nourishing"` 也是合法的
  （等价于 `{"text": "Nourishing"}`，但**无法本地化**）。
- 等级后缀固定为 `enchantment.level.<n>`（1.21.1 `Enchantment.java:208`；26.1.2 `:194`），当 `level != 1` 或 `maxLevel != 1` 时追加。

### 9.2 1.20.1：lang 键是**强制**的（除非覆写）

`Enchantment.getDescriptionId()`（`Enchantment.java:96-98`）→ `getOrCreateDescriptionId()`（`:88-94`）：
```java
this.descriptionId = Util.makeDescriptionId("enchantment", BuiltInRegistries.ENCHANTMENT.getKey(this));
```
`Util.makeDescriptionId("enchantment", <id>)` 生成 `enchantment.<命名空间>.<路径>`。
`getFullname(int level)`（`:100-113`）用 `Component.translatable(this.getDescriptionId())` 取名字。
→ 1.20.1 上**必须**提供 `enchantment.<modid>.<id>` 的 lang 键；要改显示名只能覆写 `getDescriptionId()`。

### 9.3 代码路径的对比（NeoForge `Enchantment.Builder`）

若在 1.21.1/26.1.2 用代码构造附魔：
`Enchantment.Builder.build(ResourceLocation|Identifier)` 默认用
`Component.translatable(Util.makeDescriptionId("enchantment", location))`（1.21.1 `Enchantment.java:627-633`），
即默认回到 `enchantment.<ns>.<id>`；可用 `withCustomName(UnaryOperator<MutableComponent>)` 覆盖
（1.21.1 `:614-617`，NeoForge 新增，注释见 `:609-613`）。

---

## 10. 未找到 / 未验证项（明确声明）

1. `.mc-src/1.20.1/data/` 下**未找到**任何附魔数据文件（1.20.1 本来就没有该机制）。
2. `.mc-src/1.21.1/data/minecraft/enchantment/` **不存在**（1.21.1 的 jar 解包不含原版附魔 JSON）；
   1.21.1 的 JSON 键名结论来自 codec 源码，已用 26.1.2 的真实 JSON 交叉验证（键名完全一致）。
3. `.mc-src/1.21.1/data/minecraft/tags/item/` 目录为空，**未找到** 1.21.1 的原版物品标签 JSON
   （`#minecraft:enchantable/*` 在 1.21.1 的 JSON 里**无法直接验证**；26.1.2 有
   `.mc-src/26.1.2/data/minecraft/tags/item/enchantable/` 共 21 个文件）。
4. 26.1.2 **未找到** `data/c/` 目录——`c:` 标签由 NeoForge 数据生成器在运行时/构建时产出，
   源码证据为 `Tags.java:593,1051-1053` 与 `NeoForgeItemTagsProvider.java:133-160`。
5. `{"items": [...]}` 形式的物品集合写法：**未找到**任何支持它的 codec（见 §4.2）。
6. 1.20.1 的 `EnchantmentHelper.hasAnyEnchantments`：**未找到**。
7. 1.21.1 / 26.1.2 的 `EnchantmentHelper.getEnchantments(ItemStack)` 返回 `Map` 的版本：**未找到**（改用 `ItemEnchantments`）。
8. 三版本的附魔包里**未找到**任何 `eat` / `consume` / `use` / `food` 相关的 effect component（§7.1）。
9. ~~NeoForge 26.1.2 的 `LivingEntityUseItemEvent` 未检索~~ → **已补充验证**：
   `.mc-src/26.1.2/net/neoforged/neoforge/event/entity/living/LivingEntityUseItemEvent.java:107-122` 存在 `Finish`，
   包名与 1.21.1 相同（`net.neoforged.neoforge.event.entity.living`）。三版本该事件均可用（见 §7.3）。
10. `.mc-src/1.21.1/net/minecraft/world/item/component/` 目录**不完整**（只有 26 个文件，且**未找到** `Consumable.java`），
    推测该反编译源为裁剪集；1.21.1 的食物机制结论（`FoodProperties`）来自
    `.mc-src/1.21.1/net/minecraft/world/food/FoodProperties.java` 与 `Item.java:183-186`，可信。
11. 1.20.1 的 `Enchantment#getEnchantmentValue`（物品附魔能力）与 `ItemStack#getEnchantmentValue` 本次未逐行核对
    （26.1.2 改为 `minecraft:enchantable` 组件，见 §4.4）。
12. 1.21.1 的 `Registries.ENCHANTMENT` 是否同时出现在 `DIMENSION_REGISTRIES` 中未逐一核对；
    已确认在 `WORLDGEN_REGISTRIES`（`:96`）与 `SYNCHRONIZED_REGISTRIES`（`:113`）中，即**服务端数据包加载 + 网络同步**。

---

## 11. 给「食物附魔」实现的直接结论（TL;DR）

1. **食物标签**：
   - 1.21.1 / 26.1.2 → `"supported_items": "#c:foods"` 可直接用（NeoForge 通用食物标签，两版都存在）。
   - 1.20.1 → **不能**，`forge:food` / `forge:foods` 不存在；必须自建物品标签 + 代码判定（或 `EnchantmentCategory.create`）。
2. **注册方式**：
   - 1.20.1 → 代码静态注册（`DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, MODID)`）。
   - 1.21.1 / 26.1.2 → `data/<modid>/enchantment/<id>.json` 数据驱动（**不要**写 DeferredRegister）。
3. **「食用时施药效」**：三版本**都必须写代码**。
   纯数据驱动在 1.21.1/26.1.2 只能做到「固定物品 → 固定药效」（1.21.1 `FoodProperties.effects` / 26.1.2 `Consumable.on_consume_effects`），
   无法读附魔等级。**三版本统一用 `LivingEntityUseItemEvent.Finish` 事件**
   （1.20.1 在 `net.minecraftforge.event.entity.living`，1.21.1/26.1.2 在 `net.neoforged.neoforge.event.entity.living`），
   在事件里用 `event.getItem()` 读附魔等级、`event.getEntity()` 施药效。
   备选：覆写 `Item#finishUsingItem`（三版本签名一致）。
4. **`slots` 要写 `"any"` 或 `"mainhand"`**，否则 `matchingSlot` 判定会失败。
5. **`description` 必填**，1.21.1/26.1.2 里 `enchantment.<ns>.<id>` 只是惯例；1.20.1 里是硬性要求。
6. **`rarity` / `treasure` 在 1.21.1/26.1.2 的 JSON 里不存在**——用 `weight` 与 `#minecraft:treasure` 标签表达。
