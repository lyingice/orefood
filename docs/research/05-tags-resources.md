# 05 · 物品标签约定、资源与数据包目录结构、26.1.2 API 改名清单

> 调研范围：`.mc-src/1.20.1`（MC 1.20.1 + Forge 47.4.5）、`.mc-src/1.21.1`（MC 1.21.1 + NeoForge 21.1.250）、
> `.mc-src/26.1.2`（MC 26.1.2 + NeoForge 26.1.2.109），以及项目自身的 `common/`、`forge-1.20.1/`、
> `neoforge-1.21.1/`、`neoforge-26.1.2/` 三个平台模块。
>
> 所有结论都给出 `文件:行号` 证据。**凡未在反编译源码中找到实证的，一律写「未找到」并列出搜过的关键字**，
> 不做推测。反编译源码中的 `net/neoforged`（NeoForge 本体）也不总是带 `data/` 数据目录，凡涉及
> 「数据文件是否真的存在于某个路径」的结论，若只有代码常量佐证，会明确标注为「由代码常量推导」。

---

## 0. 结论速查表

| 项目 | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 |
|---|---|---|---|
| 通用食物标签 id | **不存在**（`forge:food` 不是 Forge 定义的） | `c:foods` | `c:foods` |
| 标签常量 | 无 | `net.neoforged.neoforge.common.Tags.Items.FOODS` | 同左 |
| 标签文件路径 | —（自建时：`data/<ns>/tags/items/<name>.json`） | `data/c/tags/item/foods.json` | `data/c/tags/item/foods.json`（同 1.21.1） |
| 标签根目录 | `tags/items`（复数） | `tags/item`（单数） | `tags/item`（单数） |
| 物品模型 JSON | `assets/<ns>/models/item/<id>.json` | 同左 | `assets/<ns>/items/<id>.json` **+** `assets/<ns>/models/item/<id>.json` |
| 贴图 | `assets/<ns>/textures/item/<id>.png` | 同左 | 同左 |
| pack_format | 15（资源=数据） | 34（资源）/ 48（数据） | 84（资源，与 84.0 同）/ 101（数据，101.1） |
| 附魔描述键 | `enchantment.<ns>.<path>`（代码推导） | `enchantment.<ns>.<path>`（写在附魔 JSON 的 `description` 里） | 同左 |

---

## 1. 通用食物标签到底叫什么

### 1.1 NeoForge 1.21.1：`c:foods`，由 NeoForge 提供，常量是 `Tags.Items.FOODS`

**是 NeoForge 提供的。** 证据链：

1. 常量定义：
   `.mc-src/1.21.1/net/neoforged/neoforge/common/Tags.java:499`
   ```java
   public static final TagKey<Item> FOODS = tag("foods");
   ```
2. 命名空间由 helper 决定：
   `.mc-src/1.21.1/net/neoforged/neoforge/common/Tags.java:923-925`
   ```java
   private static TagKey<Item> tag(String name) {
       return ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", name));
   }
   ```
   → 完整 id = `c:foods`。
   （同一文件 `:283-284` 另有 `neoforgeTag(...)` 用 `"neoforge"` 命名空间，即 `neoforge:*`，
   这是 NeoForge「自己实现的功能性标签」，与 common 约定不同，不要混用。）
3. 子标签常量：`Tags.java:504-563`，`FOODS_FRUIT = tag("foods/fruit")`、`FOODS_RAW_MEAT = tag("foods/raw_meat")`
   等，共 16 个子标签 + `ANIMAL_FOODS = tag("animal_foods")`（`:563`）。
4. 实际数据文件**已存在于反编译产物中**（最硬的证据）：
   - `.mc-src/1.21.1/data/c/tags/item/foods.json`
   - `.mc-src/1.21.1/data/c/tags/item/foods/fruit.json` 等 16 个子文件
   内容（`data/c/tags/item/foods.json`）：
   ```json
   {
     "values": [
       "minecraft:baked_potato",
       "minecraft:pumpkin_pie",
       "minecraft:honey_bottle",
       "minecraft:ominous_bottle",
       "minecraft:dried_kelp",
       "#c:foods/fruit",
       "#c:foods/vegetable",
       "#c:foods/berry",
       "#c:foods/bread",
       "#c:foods/cookie",
       "#c:foods/dough",
       "#c:foods/raw_meat",
       "#c:foods/raw_fish",
       "#c:foods/cooked_meat",
       "#c:foods/cooked_fish",
       "#c:foods/soup",
       "#c:foods/candy",
       "#c:foods/pie",
       "#c:foods/golden",
       "#c:foods/edible_when_placed",
       "#c:foods/food_poisoning"
     ]
   }
   ```
   注意这个文件**没有 `replace` 字段**（`TagFile` 的 `replace` 默认 `false`）。
5. 生成器（datagen）来源：`.mc-src/1.21.1/net/neoforged/neoforge/common/data/internal/NeoForgeItemTagsProvider.java:124-146`
   —— `c:foods` 本体由 16 个子标签 `addTags(...)` 聚合而成（`:140-145`）。
6. 语言键格式佐证目录名是**单数 `item`**：
   `.mc-src/1.21.1/assets/c/lang/en_us.json:248` → `"tag.item.c.foods": "Foods"`
   （同文件 `:148` `"tag.item.c.animal_foods"`，`:249-264` 各子标签）。

### 1.2 NeoForge 26.1.2：同样是 `c:foods`，常量与路径不变

1. `.mc-src/26.1.2/net/neoforged/neoforge/common/Tags.java:593`
   ```java
   public static final TagKey<Item> FOODS = tag("foods");
   ```
   子标签 `:598-657`，与 1.21.1 一一对应（`FOODS_FRUIT = tag("foods/fruit")` … `ANIMAL_FOODS = tag("animal_foods")`）。
2. helper 只把 `ResourceLocation` 换成了 `Identifier`：
   `.mc-src/26.1.2/net/neoforged/neoforge/common/Tags.java:1051-1053`
   ```java
   private static TagKey<Item> tag(String name) {
       return ItemTags.create(Identifier.fromNamespaceAndPath("c", name));
   }
   ```
3. 生成器：`.mc-src/26.1.2/net/neoforged/neoforge/common/data/internal/NeoForgeItemTagsProvider.java:133-154`
   —— 与 1.21.1 完全同构。
4. **26.1.2 的反编译产物里不含 NeoForge 的 `data/c/` 目录**（`.mc-src/26.1.2/data` 下只有 `minecraft`），
   因此「文件确实落在 `data/c/tags/item/foods.json`」这一点在 26.1.2 是**由代码常量推导**的
   （`Registries.tagsDirPath` + `Identifier("c","foods")`，见 §2.1），而不是从现成文件读到的。
   考虑到生成器代码与目录常量都没变，这个推导的可信度很高。
5. 26.1.2 的 `TagConventionLogWarning`（1.21.1 里那个 legacy `forge:*` 迁移警告表）在
   `.mc-src/26.1.2` 下**未找到**（只找到 `net/neoforged/neoforge/client/TagConventionLogWarningClient.java`）。
   搜过的关键字：`TagConvention*`（文件名通配）、`"food"`、`"foods"`、`FOODS)`。

### 1.3 Forge 1.20.1：`forge:food` **不存在**

**明确结论：`forge:food` 不是 Forge 定义的标签，Forge 1.20.1 也没有任何「通用食物标签」。**

证据（三种搜法都为空）：

1. `net/minecraftforge/common/Tags.java` 的 `Items` 内部类里**没有 FOODS 常量**。
   逐行核对 F 段：
   `.mc-src/1.20.1/net/minecraftforge/common/Tags.java:265-270`
   ```
   265: FEATHERS = tag("feathers")
   266: FENCE_GATES
   267: FENCE_GATES_WOODEN
   268: FENCES
   269: FENCES_NETHER_BRICK
   270: FENCES_WOODEN
   ```
   紧接着是 `:271 GEMS`。按字母序 `FOODS` 应夹在这两者之间，**没有**。
   同样，`Select-String -CaseSensitive 'FOOD'` 在整个 `Tags.java` 上**零匹配**。
2. 实际数据目录 `data/forge/tags/items/` 下**没有 `food.json` / `foods.json`**。
   （该目录实际列出：`armors, barrels, bones, bookshelves, chests, cobblestone, crops, dusts, dyes,
   eggs, enchanting_fuels, ender_pearls, end_stones, feathers, fences, fence_gates, gems, glass,
   glass_panes, gravel, gunpowder, heads, ingots, leather, mushrooms, netherrack, nether_stars,
   nuggets, obsidian, ores, raw_materials, rods, sand, sandstone, seeds, shears, slimeballs,
   stained_glass, stained_glass_panes, stone, storage_blocks, string, tools …`）
3. 全量搜 `data/` 下任何含 `food` 的文件名：零结果。

**旁证：`forge:food` 是社区自发的旧约定，NeoForge 专门为它写了迁移警告。**
`.mc-src/1.21.1/net/neoforged/neoforge/common/TagConventionLogWarning.java:41-45` 的注释写着：

> `Old 'forge' tags that we migrated to a new tag under a new convention.`
> `May also contain commonly used 'forge' tags that are not following convention.`

而 `:378-379`：
```java
createForgeMapEntry(Registries.ITEM, "food", Tags.Items.FOODS),
createForgeMapEntry(Registries.ITEM, "foods", Tags.Items.FOODS),
```
即 NeoForge 认为 `forge:food` / `forge:foods` 是「不遵循约定的、但被社区常用」的旧标签，
应当迁移到 `c:foods`。1.21.1 里作者**没有**把它列进「We migrated」（那部分是 Forge 真的定义过的），
而是列进了「commonly used but not following convention」。

#### 1.20.1 的正确做法

三条路，按推荐顺序：

**方案 A（推荐）：自建一个模组自己的食物标签，附魔用 `supported` 判断。**
1.20.1 没有数据驱动附魔，附魔的「可附魔物品」是**代码判断**：覆写 `Enchantment#canEnchant(ItemStack)`
（`.mc-src/1.20.1/net/minecraft/world/item/enchantment/Enchantment.java:115-116`：
`canEnchant` 默认委托给 `canApplyAtEnchantingTable`）并在里面检查自建标签：

```json
// forge-1.20.1/src/main/resources/data/orefood/tags/items/foods.json
{
  "replace": false,
  "values": [
    "minecraft:apple",
    "minecraft:bread",
    "#forge:raw_materials/beef"
  ]
}
```
```java
private static final TagKey<Item> FOODS =
        ItemTags.create(new ResourceLocation("orefood", "foods"));

@Override
public boolean canEnchant(ItemStack stack) {
    return stack.is(FOODS);
}
```
> 注意：`ItemTags.create(ResourceLocation)` 在 1.20.1 存在，
> `.mc-src/1.20.1/net/minecraft/tags/ItemTags.java:115-116`。

**方案 B：借用 Forge 已有的宽口径标签。** 1.20.1 Forge 里语义最接近「食物」的是
`Tags.Items.CROPS`（`:234`）——但它只覆盖作物，不含肉/鱼/熟食，语义不匹配，不建议。

**方案 C：用原版标签拼。** 见 §1.4，原版**没有**涵盖全部食物的标签，只能自己 `#minecraft:fox_food` 等拼，
仍然要自建一层。所以本质还是方案 A。

**不要**写 `forge:food`：它在 1.20.1 解析不出来（不存在的标签引用会导致标签加载报错，除非用
`{"id": "forge:food", "required": false}` 形式；而即便加载成功，也没有任何原版/Forge 代码会往里面填内容）。

### 1.4 1.20.1 的原版食物标签有哪些

`.mc-src/1.20.1/net/minecraft/tags/ItemTags.java` 中食物相关的常量只有 3 个：

| 行号 | 常量 | id |
|---|---|---|
| `:56` | `PIGLIN_FOOD` | `minecraft:piglin_food` |
| `:57` | `FOX_FOOD` | `minecraft:fox_food` |
| `:96` | `SNIFFER_FOOD` | `minecraft:sniffer_food` |

**没有任何覆盖全部食物的原版标签。** 全量 F/FISH/MEAT/FOOD 关键字检索（`FOODS|food`）在
`ItemTags.java` 上只返回上述 3 行。

对照另外两版本（原版也都没有「全部食物」标签，只有「喂动物的食物」类）：

* 1.21.1 `.mc-src/1.21.1/net/minecraft/tags/ItemTags.java:56-101`：多了 `MEAT`（`:56`）、
  `COW_FOOD/GOAT_FOOD/SHEEP_FOOD/WOLF_FOOD/CAT_FOOD/HORSE_FOOD/CAMEL_FOOD/ARMADILLO_FOOD/BEE_FOOD/
  CHICKEN_FOOD/FROG_FOOD/HOGLIN_FOOD/LLAMA_FOOD/OCELOT_FOOD/PANDA_FOOD/PIG_FOOD/RABBIT_FOOD/
  STRIDER_FOOD/TURTLE_FOOD/PARROT_FOOD/PARROT_POISONOUS_FOOD/AXOLOTL_FOOD`（`:60-84`）、`FISHES`（`:101`）。
* 26.1.2 `.mc-src/26.1.2/net/minecraft/tags/ItemTags.java:80-129`：同上并新增
  `ZOMBIE_HORSE_FOOD`、`HAPPY_GHAST_FOOD`、`CAMEL_HUSK_FOOD`、`NAUTILUS_FOOD`、`NAUTILUS_BUCKET_FOOD`。

**结论：三版本都不该指望原版标签，「食物」这件事必须靠 `c:foods`（NeoForge）或自建标签（Forge 1.20.1）。**

---

## 2. 标签 JSON 的目录与格式

### 2.1 目录：1.20.1 是 `tags/items`（复数），1.21.1 / 26.1.2 是 `tags/item`（单数）

**Forge 1.20.1** —— `tags/items`
`.mc-src/1.20.1/net/minecraft/tags/TagManager.java:21-32`
```java
private static final Map<ResourceKey<? extends Registry<?>>, String> CUSTOM_REGISTRY_DIRECTORIES = Map.of(
    Registries.BLOCK,       "tags/blocks",
    Registries.ENTITY_TYPE, "tags/entity_types",
    Registries.FLUID,       "tags/fluids",
    Registries.GAME_EVENT,  "tags/game_events",
    Registries.ITEM,        "tags/items"      // ← 复数
);
```
`:44-47`
```java
public static String getTagDir(ResourceKey<? extends Registry<?>> p_203919_) {
    String s = CUSTOM_REGISTRY_DIRECTORIES.get(p_203919_);
    return s != null ? s : "tags/" + ForgeHooks.prefixNamespace(p_203919_.location());
}
```
→ 物品标签根目录 = `data/<ns>/tags/items/`。

**NeoForge 1.21.1** —— `tags/item`
`.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:251-257`
```java
public static String elementsDirPath(ResourceKey<? extends Registry<?>> registryKey) {
    return net.neoforged.neoforge.common.CommonHooks.prefixNamespace(registryKey.location());
}

public static String tagsDirPath(ResourceKey<? extends Registry<?>> registryKey) {
    return "tags/" + net.neoforged.neoforge.common.CommonHooks.prefixNamespace(registryKey.location());
}
```
被 `TagManager` 使用：`.mc-src/1.21.1/net/minecraft/tags/TagManager.java:54`
```java
TagLoader<Holder<T>> tagloader = new TagLoader<>(registry::getHolder, Registries.tagsDirPath(resourcekey));
```
`prefixNamespace` 定义：`.mc-src/1.21.1/net/neoforged/neoforge/common/CommonHooks.java:1340`
`Registries.ITEM = createRegistryKey("item")`（`:159`）→ 目录 = `tags/item`。
→ 物品标签根目录 = `data/<ns>/tags/item/`。
**实证**：`.mc-src/1.21.1/data/c/tags/item/foods.json` 实际存在。

**NeoForge 26.1.2** —— 同样是 `tags/item`
`.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:325-335`
```java
private static String registryDirPath(ResourceKey<? extends Registry<?>> registryKey) {
    return net.neoforged.neoforge.common.CommonHooks.prefixNamespace(registryKey.identifier());
}
...
public static String tagsDirPath(ResourceKey<? extends Registry<?>> registryKey) {
    return "tags/" + registryDirPath(registryKey);
}
```
`prefixNamespace` 定义：`.mc-src/26.1.2/net/neoforged/neoforge/common/CommonHooks.java:1410-1411`
```java
public static String prefixNamespace(Identifier registryKey) {
    return registryKey.getNamespace().equals("minecraft") ? registryKey.getPath()
                                                          : registryKey.getNamespace() + "/" + registryKey.getPath();
}
```
`Registries.ITEM = createRegistryKey("item")`（`:199`）→ `tags/item`。
**实证**：`.mc-src/26.1.2/data/minecraft/tags/item/` 目录实际存在，内含
`acacia_logs.json`、`armadillo_food.json`、`arrows.json`、`axes.json`、`enchantable/` … 以及
`.mc-src/1.21.1/assets/c/lang/en_us.json:248` 的 `tag.item.c.foods` 键名。

> **踩坑点**：从 1.21.1 往 26.1.2 迁移标签文件时路径不变（都 `tags/item`），
> 但从 1.20.1 往 1.21.1 迁移必须把 `tags/items` 改成 `tags/item`。这是一个**静默失败**：
> 目录名错了，标签文件不会被加载（既不会报「找不到标签」，也不会被任何东西引用）。

### 2.2 格式：三版本一致（`values` + 可选 `replace` / `remove`）

**1.20.1** —— `.mc-src/1.20.1/net/minecraft/tags/TagFile.java:7-15`
```java
public record TagFile(List<TagEntry> entries, boolean replace, List<TagEntry> remove) {
    public static final Codec<TagFile> CODEC = RecordCodecBuilder.create(
        p_215967_ -> p_215967_.group(
                TagEntry.CODEC.listOf().fieldOf("values").forGetter(TagFile::entries),
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(TagFile::replace),
                TagEntry.CODEC.listOf().optionalFieldOf("remove", List.of()).forGetter(TagFile::remove)
            ).apply(p_215967_, TagFile::new));
}
```

**1.21.1** —— `.mc-src/1.21.1/net/minecraft/tags/TagFile.java:8-16`：**逐字相同**。

**26.1.2** —— `.mc-src/26.1.2/net/minecraft/tags/TagFile.java:7-15`：**逐字相同**（只是 lambda 参数改名）。

**单条 entry 的两种写法**（1.20.1 起就支持，三版本相同）：

1.20.1 `.mc-src/1.20.1/net/minecraft/tags/TagEntry.java:14-25`：
```java
private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create(
    p_215937_ -> p_215937_.group(
            ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagEntry::elementOrTag),
            Codec.BOOL.optionalFieldOf("required", true).forGetter(p_215952_ -> p_215952_.required)
        ).apply(p_215937_, TagEntry::new));
public static final Codec<TagEntry> CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, FULL_CODEC)
    .xmap(..., p_215931_ -> p_215931_.required ? Either.left(...) : Either.right(p_215931_));
```
1.21.1 同构：`.mc-src/1.21.1/net/minecraft/tags/TagEntry.java:15-26`。
`ExtraCodecs.TAG_OR_ELEMENT_ID` 定义：1.21.1 `.mc-src/1.21.1/net/minecraft/util/ExtraCodecs.java:147-152`
（前缀 `#` 表示引用标签，否则是物品）。

**所以**：
- 必填条目：直接写字符串 `"minecraft:apple"` 或 `"#c:foods/raw_meat"`。
- 可选条目（引用的标签/物品可能不存在时）：写对象 `{"id": "forge:food", "required": false}`。
- `replace` 缺省 `false`（合并而非覆盖）。

### 2.3 可粘贴示例

**NeoForge 1.21.1 / 26.1.2：往 `c:foods` 里加自己的物品**
`data/c/tags/item/foods.json`（注意：这会**覆盖** NeoForge 自带的同名文件——
NeoForge jar 与本 mod jar 同属一个数据包栈，两个同名标签文件是**合并**关系而不是覆盖关系，
`replace` 缺省 `false` 时安全）：

```json
{
  "values": [
    "orefood:iron_beef"
  ]
}
```
> 但更干净、也**更推荐**的做法是**不要**去写 `c:foods` 的增量文件，而是给自己的物品加一个
> 自定义标签，再让附魔引用 `#c:foods`。写 `c:foods` 的增量文件在别的模组也这么干时容易互相打架。

**模组自建标签（三版本通用写法，注意 1.20.1 换目录名）**
```json
{
  "replace": false,
  "values": [
    "orefood:iron_beef",
    "orefood:golden_beef",
    "#c:foods"
  ]
}
```
路径：
- 1.20.1 → `forge-1.20.1/src/main/resources/data/orefood/tags/items/foods.json`
- 1.21.1 → `neoforge-1.21.1/src/main/resources/data/orefood/tags/item/foods.json`
- 26.1.2 → `neoforge-26.1.2/src/main/resources/data/orefood/tags/item/foods.json`

**带可选引用的写法**（推荐用于「别的模组的标签可能不存在」的场合）
```json
{
  "replace": false,
  "values": [
    { "id": "orefood:iron_beef", "required": false },
    { "id": "#c:foods", "required": false }
  ]
}
```
> 注意 `"#c:foods"` 里的 `#` 写在**字符串内部**；写成 `{"id": "c:foods", "required": false}` 会被当成**物品** `c:foods` 而不是标签。

---

## 3. 数据包目录结构与 `pack.mcmeta`

### 3.1 三版本的 pack_format（源码常量）

| 版本 | 常量位置 | 资源包 | 数据包 |
|---|---|---|---|
| 1.20.1 | `.mc-src/1.20.1/net/minecraft/SharedConstants.java:31,33` | `RESOURCE_PACK_FORMAT = 15` | `DATA_PACK_FORMAT = 15` |
| 1.21.1 | `.mc-src/1.21.1/net/minecraft/SharedConstants.java:85,93` | `RESOURCE_PACK_FORMAT = 34` | `DATA_PACK_FORMAT = 48` |
| 26.1.2 | `.mc-src/26.1.2/net/minecraft/SharedConstants.java:27-33` | `RESOURCE_PACK_FORMAT_MAJOR = 84` / `MINOR = 0` | `DATA_PACK_FORMAT_MAJOR = 101` / `MINOR = 1` |

26.1.2 的 `version.json` 交叉验证：`.mc-src/26.1.2/version.json:7-12`
```json
"pack_version": {
    "resource_major": 84,
    "resource_minor": 0,
    "data_major": 101,
    "data_minor": 1
}
```
> 26.1.2 起 pack format 变成 `major.minor` 两段式。`version.json:15` 显示 Java 版本要求 25，
> 与 `neoforge-26.1.2/build.gradle:26` 的 `JavaLanguageVersion.of(25)` 一致。

另可参考 NeoForge 自带的 pack.mcmeta：
- `.mc-src/1.20.1/pack.mcmeta`：`"pack_format": 15`
- `.mc-src/1.21.1/pack.mcmeta`：`"pack_format": 48`，`"supported_formats": [0, 2147483647]`
  （这是 NeoForge 作为**数据包**的元数据；它能用 48 是因为它 `supported_formats` 通吃）

### 3.2 `supported_formats` / `min_format` / `max_format` 的演变

**1.21.1**：`PackMetadataSection` 的 codec
`.mc-src/1.21.1/net/minecraft/server/packs/metadata/pack/PackMetadataSection.java:16-17`
```java
Codec.INT.fieldOf("pack_format").forGetter(PackMetadataSection::packFormat),
InclusiveRange.codec(Codec.INT).lenientOptionalFieldOf("supported_formats").forGetter(...)
```
→ `pack_format` **必填**，`supported_formats` 可选（`[min, max]` 数组）。

NeoForge 对**模组**的 pack.mcmeta 用更宽松的 codec：
`.mc-src/1.21.1/net/neoforged/neoforge/resource/ResourcePackLoader.java:144-149`
```java
public static final MetadataSectionType<PackMetadataSection> OPTIONAL_FORMAT = MetadataSectionType.fromCodec("pack",
    RecordCodecBuilder.create(in -> in.group(
        ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty())...,
        Codec.INT.optionalFieldOf("pack_format", -1)...,
        InclusiveRange.codec(Codec.INT).optionalFieldOf("supported_formats")...)
    .apply(in, PackMetadataSection::new)));
```
→ NeoForge 1.21.1 下，模组的 `pack.mcmeta` 里 `pack_format` **可以省略**（默认 -1），不会炸。

**26.1.2：⚠️ 这里有一个真实的坑。**

`PackFormat.IntermediaryFormat.PACK_CODEC`：`.mc-src/26.1.2/net/minecraft/server/packs/metadata/pack/PackFormat.java:104-112`
```java
PackFormat.BOTTOM_CODEC.optionalFieldOf("min_format")...,
PackFormat.TOP_CODEC.optionalFieldOf("max_format")...,
Codec.INT.optionalFieldOf("pack_format")...,
InclusiveRange.codec(Codec.INT).optionalFieldOf("supported_formats")...
```
`PackFormat.lastPreMinorVersion(...)`：`:63-68`
```java
public static int lastPreMinorVersion(PackType type) {
    return switch (type) {
        case CLIENT_RESOURCES -> 64;
        case SERVER_DATA -> 81;
    };
}
```
校验逻辑 `:158-170`
```java
} else if (hasPackFormatField && this.format.isPresent()) {
    int mainFormat = this.format.get();
    return mainFormat > lastPreMinorVersion
        ? DataResult.error(() -> context + " declares support for version newer than "
            + lastPreMinorVersion + ", but is missing mandatory fields min_format and max_format")
        : DataResult.success(new InclusiveRange<>(PackFormat.of(mainFormat)));
}
```

**因此：只写 `"pack_format": 84` 在 26.1.2 会解析失败**（84 > 64），报
`"declares support for version newer than 64, but is missing mandatory fields min_format and max_format"`。

NeoForge 读模组 pack.mcmeta 时会捕获并回退：
`.mc-src/26.1.2/net/neoforged/neoforge/resource/ResourcePackLoader.java:198-206`
```java
try {
    metadata = primaryResources.getMetadataSection(metadataTypeForPackType(type));
} catch (JsonParseException exception) {
    LOGGER.warn("Error reading optional pack metadata for {}, attempting fallback type", location.id(), exception);
    metadata = primaryResources.getMetadataSection(PackMetadataSection.FALLBACK_TYPE);
}
```
`FALLBACK_TYPE` 只认 `description`：`.mc-src/26.1.2/net/minecraft/server/packs/metadata/pack/PackMetadataSection.java:12-18`
→ **不会崩溃**，但日志里会刷 warning，而且格式范围退化成「上限 = Integer.MAX_VALUE」。

**26.1.2 的正确写法**（三选一）：
```json
{ "pack": { "description": { "text": "..." }, "min_format": 84, "max_format": 84 } }
```
```json
{ "pack": { "description": { "text": "..." }, "min_format": [84, 0], "max_format": [84, 0] } }
```
```json
{ "pack": { "description": { "text": "..." } } }
```
（第三种什么都不写 → `UNLIMITED_SUPPORT`，见
`.mc-src/26.1.2/net/neoforged/neoforge/resource/ResourcePackLoader.java:155-173`：
`min/max/format/supported` 全空时返回 `UNLIMITED_SUPPORT`。）
`BOTTOM_CODEC` 的 `minor` 默认 0、`TOP_CODEC` 默认 `Integer.MAX_VALUE`
（`PackFormat.java:22-23` + `:25-31`），所以 `"min_format": 84, "max_format": 84`
等价于 `[84.0, 84.MAX]`，覆盖 84.x，够用。

> **本项目现状（需要修）**：`neoforge-26.1.2/src/main/templates/pack.mcmeta` 目前只有
> `"pack_format": 84`，正落在上面那个坑里。
> 建议改为 `min_format` / `max_format`（或干脆删掉格式字段）。

### 3.3 项目现有三个模块的 pack.mcmeta 内容

**`forge-1.20.1/src/main/resources/pack.mcmeta`**（8 行，`${mod_id}` 由
`forge-1.20.1/build.gradle:104-123` 的 `processResources` 展开）
```json
{
  "pack": {
    "description": {
      "text": "${mod_id} resources"
    },
    "pack_format": 15
  }
}
```

**`neoforge-1.21.1/src/main/templates/pack.mcmeta`**（8 行，由
`neoforge-1.21.1/build.gradle:126-147` 的 `generateModMetadata` 展开到
`build/generated/sources/modMetadata` 并作为资源目录参与打包）
```json
{
  "pack": {
    "description": {
      "text": "${mod_id} resources"
    },
    "pack_format": 34
  }
}
```
> 用的是**资源包**格式 34。作为数据包它其实需要 48；但 NeoForge 1.21.1 的
> `OPTIONAL_FORMAT`（§3.2）把 `pack_format` 设成可选，所以这里写 34 不会报错。
> 若要严谨，可用 `"supported_formats": [34, 48]`。

**`neoforge-26.1.2/src/main/templates/pack.mcmeta`**（8 行，同上经 `generateModMetadata` 展开）
```json
{
  "pack": {
    "description": {
      "text": "${mod_id} resources"
    },
    "pack_format": 84
  }
}
```
> ⚠️ 见 §3.2，需要改成 `min_format`/`max_format`。

### 3.4 数据包/资源包的目录树（本项目落地用）

```
<module>/src/main/resources/
├─ pack.mcmeta                      ← 仅 forge-1.20.1 放这里（另两个在 src/main/templates）
├─ META-INF/mods.toml               ← 仅 forge-1.20.1（另两个在 src/main/templates）
├─ assets/<mod_id>/
│   ├─ lang/{en_us,zh_cn}.json
│   ├─ models/item/<id>.json
│   ├─ textures/item/<id>.png
│   └─ items/<id>.json              ← 仅 26.1.2
└─ data/<mod_id>/
    └─ tags/{items|item}/<name>.json
```

---

## 4. 26.1.2 的 API 改名清单

### 4.1 `ResourceLocation` → `Identifier`（硬改名，无别名类）

| 1.21.1 | 26.1.2 |
|---|---|
| `net.minecraft.resources.ResourceLocation`（`.mc-src/1.21.1/net/minecraft/resources/ResourceLocation.java`） | `net.minecraft.resources.Identifier`（`.mc-src/26.1.2/net/minecraft/resources/Identifier.java:17`） |
| `net.minecraft.ResourceLocationException` | `net.minecraft.IdentifierException` |
| — | `net.minecraft.IdentifierException` 在新位置，旧类**已删除** |

**核实方式**：在 `.mc-src/26.1.2/net/minecraft` 下按文件名递归搜 `ResourceLocation.java` → **NOT FOUND**；
搜 `Identifier.java` → 命中 `net/minecraft/resources/Identifier.java`。包路径**没变**，只是类名变了。

`Identifier` 的静态工厂与 1.21.1 的 `ResourceLocation` 一一对应
（`.mc-src/26.1.2/net/minecraft/resources/Identifier.java`）：

| 1.21.1（`ResourceLocation`） | 26.1.2（`Identifier`） | 行号 |
|---|---|---|
| `fromNamespaceAndPath(String, String)` | `fromNamespaceAndPath(String, String)` | `:41` |
| `parse(String)` | `parse(String)` | `:45` |
| `withDefaultNamespace(String)` | `withDefaultNamespace(String)` | `:49` |
| `tryParse(String)` | `tryParse(String)` | `:53` |
| `tryBuild(String, String)` | `tryBuild(String, String)` | `:57` |
| `bySeparator(String, char)` | `bySeparator(String, char)` | `:61` |
| `read(String)` | `read(String)` | `:93` |
| `getPath()` / `getNamespace()` | 同名 | `:101` / `:105` |
| `CODEC` / `STREAM_CODEC` | 同名 | `:18` / `:19` |
| — | `toLanguageKey()`（新增，把 `ns:path` 转成 `ns.path`） | `:167` |
| — | `toLanguageKey(String prefix)` / `toLanguageKey(String, String)` | `:179` / `:183` |

> 注意：**只改类名，方法名全都不变**。批量替换 `ResourceLocation` → `Identifier`
> 并把 import 改成 `net.minecraft.resources.Identifier` 就够。

### 4.2 `ResourceKey`：类没改名，但**取值方法改名了**

`ResourceKey` 仍在 `net.minecraft.resources.ResourceKey`，但：

| 1.21.1 | 26.1.2 | 证据 |
|---|---|---|
| `ResourceKey#location()` | `ResourceKey#identifier()` | `.mc-src/1.21.1/.../ResourceKey.java:82` vs `.mc-src/26.1.2/.../ResourceKey.java:55` |
| `ResourceKey#registry()` | `ResourceKey#registry()`（**返回类型**从 `ResourceLocation` 变 `Identifier`） | `:86` vs `:59` |
| `ResourceKey.create(ResourceKey, ResourceLocation)` | `ResourceKey.create(ResourceKey, Identifier)` | `:44` vs `:25` |
| `ResourceKey.createRegistryKey(ResourceLocation)` | `ResourceKey.createRegistryKey(Identifier)` | `:51` vs `:29` |

真实调用点佐证：`.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:326`
用了 `registryKey.identifier()`。

### 4.3 其他已核实的改名 / 移包

在 `.mc-src/26.1.2/net/minecraft` 下逐个按文件名核验（并对照 `.mc-src/1.21.1`）：

| 类 | 1.21.1 位置 | 26.1.2 位置 | 判定 |
|---|---|---|---|
| `Util` | `net.minecraft.Util` | **`net.minecraft.util.Util`** | **移包**（`.mc-src/26.1.2/net/minecraft/util/Util.java:133` `makeDescriptionId`） |
| `ChatFormatting` | `net.minecraft.ChatFormatting` | `net.minecraft.ChatFormatting` | 不变 |
| `Component` | `net.minecraft.network.chat.Component` | 同 | 不变 |
| `ComponentUtils` / `CommonComponents` | `net.minecraft.network.chat.*` | 同 | 不变 |
| `ItemStack` | `net.minecraft.world.item.ItemStack` | 同 | 不变 |
| `Item` | `net.minecraft.world.item.Item` | 同 | 不变 |
| `Items` | `net.minecraft.world.item.Items` | 同 | 不变 |
| `Rarity` | `net.minecraft.world.item.Rarity` | 同 | 不变 |
| `DataComponents` | `net.minecraft.core.component.DataComponents` | 同 | 不变 |
| `DataComponentType` | `net.minecraft.core.component.DataComponentType` | 同 | 不变 |
| `InteractionHand` | `net.minecraft.world.InteractionHand` | 同 | 不变 |
| `InteractionResult` | `net.minecraft.world.InteractionResult` | 同 | 不变 |
| `Level` | `net.minecraft.world.level.Level` | 同 | 不变 |
| `LivingEntity` | `net.minecraft.world.entity.LivingEntity` | 同 | 不变 |
| `Player` | `net.minecraft.world.entity.player.Player` | 同 | 不变 |
| `MobEffect` / `MobEffectInstance` / `MobEffects` | `net.minecraft.world.effect.*` | 同 | 不变 |
| `Holder` / `HolderSet` / `HolderLookup` | `net.minecraft.core.*` | 同 | 不变 |
| `Registry` / `RegistryAccess` | `net.minecraft.core.*` | 同 | 不变 |
| `BuiltInRegistries` / `Registries` | `net.minecraft.core.registries.*` | 同 | 不变 |
| `TagKey` / `ItemTags` | `net.minecraft.tags.*` | 同 | 不变 |
| `CreativeModeTab` / `CreativeModeTabs` | `net.minecraft.world.item.*` | 同 | 不变 |
| `FoodProperties` | `net.minecraft.world.food.FoodProperties` | 同 | 不变 |
| `Consumable` | `net.minecraft.world.item.component.Consumable` | 同 | 不变 |
| `ItemEnchantments` | `net.minecraft.world.item.enchantment.ItemEnchantments` | 同 | 不变 |
| `Enchantment` / `Enchantments` / `EnchantmentHelper` | `net.minecraft.world.item.enchantment.*` | 同 | 不变 |
| `EnchantmentTags` | `net.minecraft.tags.EnchantmentTags` | 同 | 不变 |
| `EquipmentSlot` / `EquipmentSlotGroup` | `net.minecraft.world.entity.*` | 同 | 不变 |
| `ItemAttributeModifiers` | `net.minecraft.world.item.component.*` | 同 | 不变 |
| **`TrimMaterial`** | `net.minecraft.world.item.armortrim.TrimMaterial` | **`net.minecraft.world.item.equipment.trim.TrimMaterial`** | **移包** |
| **`TrimPattern`** | `net.minecraft.world.item.armortrim.TrimPattern` | **`net.minecraft.world.item.equipment.trim.TrimPattern`** | **移包** |
| **`ArmorMaterial` / `ArmorMaterials`** | `net.minecraft.world.item.*` | **`net.minecraft.world.item.equipment.*`** | **移包** |
| **`Equipable` → `Equippable`** | `net.minecraft.world.item.Equipable`（**接口**，单 `p`；`ArmorItem implements Equipable`，见 `.mc-src/1.21.1/net/minecraft/world/item/ArmorItem.java:29`） | `net.minecraft.world.item.equipment.Equippable`（**数据组件记录**，双 `p`；`.mc-src/26.1.2/net/minecraft/core/component/DataComponents.java:97,192-193`） | **改名 + 移包 + 语义变化**（接口 → 数据组件） |
| `DataComponents.EQUIPPABLE` | **不存在**（在 1.21.1 的 `DataComponents.java` 里 grep `equip` 大小写不敏感 = 0 匹配） | 存在：`.mc-src/26.1.2/net/minecraft/core/component/DataComponents.java:192-193` | **新增组件**（文件也从 249 行涨到 435 行） |
| **`ArmorItem`** | `net.minecraft.world.item.ArmorItem` | **已删除**（递归搜 `ArmorItem.java` = NOT FOUND）→ 用 `Item` + `DataComponents.EQUIPPABLE` 表达；另有 `net.minecraft.world.item.equipment.ArmorType` 枚举（`.mc-src/26.1.2/.../equipment/ArmorType.java`） | **移除** |
| **`UseAnim`** | `net.minecraft.world.item.UseAnim` | **已删除**（NOT FOUND）→ `net.minecraft.world.item.ItemUseAnimation` | **改名** |
| **`Tiers`** | `net.minecraft.world.item.Tiers` | **已删除**（NOT FOUND）→ `net.minecraft.world.item.ToolMaterial`（枚举式常量 `WOOD/STONE/COPPER/IRON/DIAMOND/GOLD/NETHERITE`，`.mc-src/26.1.2/net/minecraft/world/item/ToolMaterial.java:20-29`）。**注意 `ToolMaterials.java` 也 NOT FOUND**，只有一个 `ToolMaterial` record | **改名** |
| **`advancements.critereon`** | `net.minecraft.advancements.critereon.*` | **`net.minecraft.advancements.criterion.*`** | **包改名**（拼写修正） |

搜索方式与命令：
```powershell
# 按文件名在 26.1.2 树里递归找
Get-ChildItem -Recurse -File .mc-src/26.1.2/net/minecraft -Filter <ClassName>.java
# 目录级差异比对
$a = ...1.21.1 全部 *.java 相对路径 ; $b = ...26.1.2 全部 *.java 相对路径
Compare-Object $a $b
```
目录级 `Compare-Object` 结果里，只在 1.21.1 存在的包有：
`\advancements\critereon`、`\world\item\armortrim`、`\data\models`（改到 `client\data\models`）、
`\obfuscate`、`\world\entity\animal\horse`、`\world\entity\boss`、`\world\entity\projectile\windcharge`、
`\client\model\dragon`、`\client\renderer\block\model\multipart`。
> 与「只提食物/附魔物品」这个需求直接相关的只有前三项。

**NeoForge 侧（`net/neoforged`）对本需求相关的改名**：

| 1.21.1 | 26.1.2 | 说明 |
|---|---|---|
| `DeferredRegister.createItems(String)` | 同名（`.mc-src/26.1.2/.../DeferredRegister.java:145`） | 不变 |
| `DeferredRegister.Items#registerItem(...)` | 同名（`:679/:696/:713`） | 不变 |
| `DeferredItem` | 同名 | 不变 |
| `neoforge.client.model.generators.ItemModelProvider` 等 | **已删除** | 26.1.2 的 NeoForge client model 包被重写成 `client/data/internal/VanillaModelProvider` 等，旧的 `client/model/generators/*` 全部不在 |
| `AddAttributeTooltipsEvent` / `GatherSkippedAttributeTooltipsEvent` | 已删除 | 与 tooltip 相关，若将来要做 tooltip 注入需另找方案 |
| `IClientItemExtensions` | 仍在 `client/extensions/common/` | 不变 |

### 4.4 26.1.2 的物品模型系统：`assets/<ns>/items/<id>.json`（1.21.4+ 新格式）

**结论：26.1.2 用新格式，且 `models/item/<id>.json` 仍然必需**（两者是不同层次的东西）。

证据：

1. 加载器：`.mc-src/26.1.2/net/minecraft/client/resources/model/ClientItemInfoLoader.java:32`
   ```java
   private static final FileToIdConverter LISTER = FileToIdConverter.json("items");
   ```
   `:36` `LISTER.listMatchingResources(manager)`，`:44` `LISTER.fileToId(resourceId)`，
   `:51` `ClientItem.CODEC.parse(...)`。
   → 扫描 `assets/<ns>/items/*.json`，每个文件名映射到一个**物品 id**。
2. 数据模型：`.mc-src/26.1.2/net/minecraft/client/renderer/item/ClientItem.java:12-16`
   ```java
   public record ClientItem(ItemModel.Unbaked model, ClientItem.Properties properties, ...) {
       public static final Codec<ClientItem> CODEC = RecordCodecBuilder.create(
           i -> i.group(ItemModels.CODEC.fieldOf("model")..., ClientItem.Properties.MAP_CODEC...)
       );
   }
   ```
3. datagen 路径：`.mc-src/26.1.2/net/minecraft/client/data/models/ModelProvider.java:47`
   ```java
   this.itemInfoPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
   ```
4. 原版实例：`.mc-src/26.1.2/assets/minecraft/items/apple.json`
   ```json
   { "model": { "type": "minecraft:model", "model": "minecraft:item/apple" } }
   ```
   对应模型文件 `.mc-src/26.1.2/assets/minecraft/models/item/apple.json`
   ```json
   { "parent": "minecraft:item/generated", "textures": { "layer0": "minecraft:item/apple" } }
   ```
   `item/generated` 模板仍存在：`.mc-src/26.1.2/assets/minecraft/models/item/generated.json` 存在。
5. **没有回退**：`ClientItemInfoLoader` 只把解析成功的条目放进
   `LoadedClientInfos(Map<Identifier, ClientItem>)`（`:78-88`）；`items/<id>.json` 缺失时
   物品 id 在 map 里没有条目，渲染层拿不到模型（`MissingItemModel` / 网格方块）。
   代码里**不存在**「找不到 items/<id>.json 就去 models/item 找」的分支
   （搜过 `ClientItemInfoLoader.java`、`ItemModelResolver.java`、`ModelManager.java`）。
6. **贴图图集变了**：26.1.2 有独立的 items 图集
   `.mc-src/26.1.2/net/minecraft/client/resources/model/ModelManager.java:141`
   `pendingStitches.get(AtlasIds.ITEMS)`；
   图集源自动收集各命名空间的 `textures/item/`：
   `.mc-src/26.1.2/assets/minecraft/atlases/items.json:3-7`
   ```json
   { "type": "minecraft:directory", "prefix": "item/", "source": "item" }
   ```
   → 模组只要把 png 放到 `assets/<ns>/textures/item/`，**不需要**额外注册图集。
   （1.21.1 侧物品贴图进的是 `minecraft:blocks` 图集：
   `.mc-src/1.21.1/net/minecraft/client/resources/model/ModelManager.java:63-64`
   `TextureAtlas.LOCATION_BLOCKS, ResourceLocation.withDefaultNamespace("blocks")`。
   1.21.1 反编译里 `assets/minecraft/atlases/` 只剩 `blocks.json`，且被 NeoForge 补丁成只有
   `neoforge:white`，**无法从反编译产物里读到原版目录源定义**——这一点标注为「未从反编译证实」，
   但 1.21.1 的模组实践中物品贴图进 blocks 图集是既定事实。）

**项目现状对照**：
`neoforge-26.1.2/src/main/resources/assets/orefood/items/example_item.json`
```json
{
  "model": {
    "type": "minecraft:model",
    "model": "orefood:item/example_item"
  }
}
```
配套模型在共享层：`common/src/main/resources/assets/orefood/models/item/example_item.json`
```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "orefood:item/example_item" }
}
```
→ **这套分层是对的**：`items/<id>.json` 只放 26.1.2 模块，`models/item/<id>.json` + 贴图 + lang 放共享层。
`neoforge-26.1.2/src/main/java/com/lyingice/orefood/init/OreFoodItems.java:30-34`
的类注释也明确写了这一点。

---

## 5. 附魔的语言文件键

### 5.1 1.21.1 / 26.1.2：`enchantment.<namespace>.<path>`，但**是写在附魔 JSON 的 `description` 字段里**

1.21 起附魔变成**数据驱动注册表**（`Registries.ENCHANTMENT`，`ResourceKey<Registry<Enchantment>>`），
名称不再靠 `Util.makeDescriptionId` 拼，而是 JSON 里显式给一个 Component：

`.mc-src/1.21.1/net/minecraft/world/item/enchantment/Enchantment.java:61`
```java
public record Enchantment(Component description, Enchantment.EnchantmentDefinition definition,
                          HolderSet<Enchantment> exclusiveSet, DataComponentMap effects) {
```
`:65`
```java
ComponentSerialization.CODEC.fieldOf("description").forGetter(Enchantment::description),
```
`:199-212` tooltip 用这个名字：
```java
public static Component getFullname(Holder<Enchantment> enchantment, int level) {
    MutableComponent mutablecomponent = enchantment.value().description.copy();
    if (enchantment.is(EnchantmentTags.CURSE)) { ... ChatFormatting.RED } else { ... ChatFormatting.GRAY }
    if (level != 1 || enchantment.value().getMaxLevel() != 1) {
        mutablecomponent.append(CommonComponents.SPACE)
                       .append(Component.translatable("enchantment.level." + level));
    }
    return mutablecomponent;
}
```
26.1.2 完全同构：`.mc-src/26.1.2/net/minecraft/world/item/enchantment/Enchantment.java:64`（`description` 字段）
+ `:185-195`（`getFullname`）。

**原版实例**：`.mc-src/26.1.2/data/minecraft/enchantment/sharpness.json:3-5`
```json
"description": {
  "translate": "enchantment.minecraft.sharpness"
},
```
→ 语言文件里的键就是 `enchantment.minecraft.sharpness`。

**所以本模组要写**：
```json
// data/orefood/enchantment/<enchant_id>.json
{
  "description": { "translate": "enchantment.orefood.<enchant_id>" },
  "supported_items": "#c:foods",
  "primary_items": "#c:foods",
  "weight": 5, "max_level": 3,
  "min_cost": { "base": 1, "per_level_above_first": 11 },
  "max_cost": { "base": 21, "per_level_above_first": 11 },
  "anvil_cost": 1,
  "slots": [ "mainhand" ],
  "effects": { }
}
```
```json
// assets/orefood/lang/zh_cn.json
{ "enchantment.orefood.<enchant_id>": "……" }
```

**「只对食物生效」怎么落地**：靠 `supported_items`（可写标签引用 `"#c:foods"`）。
`.mc-src/1.21.1/net/minecraft/world/item/enchantment/Enchantment.java:662`：
```java
RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(...)
```
`:664` `primary_items`（可选）。
26.1.2 同：`.mc-src/26.1.2/net/minecraft/world/item/enchantment/Enchantment.java:670-671`。
`homogeneousList` 接受 `"#tag"` 形式（`RegistryCodecs.homogeneousList` 用 `HolderSet` codec，
支持 `#` 前缀的标签引用）。
`canEnchant` 的默认实现就是查 `supported_items`：
`.mc-src/1.21.1/net/minecraft/world/item/enchantment/Enchantment.java:221-223`
```java
public boolean canEnchant(ItemStack stack) {
    return this.definition.supportedItems().contains(stack.getItemHolder());
}
```
附加检查（附魔台 vs 铁砧）在 NeoForge 扩展里：
`.mc-src/26.1.2/net/neoforged/neoforge/common/extensions/IItemExtension.java:385`
```java
return this.supportsEnchantment(stack, enchantment) && (primaryItems.isEmpty() || stack.is(primaryItems.get()));
```
→ 想让「附魔台 + 铁砧」都只对食物生效，**同时**填 `supported_items` 和 `primary_items` 为 `#c:foods`。

**等级数字键**：`enchantment.level.1` … `enchantment.level.N`，由原版提供
（1.21.1 `Enchantment.java:208`、26.1.2 `:194`），模组不用管。

### 5.2 Forge 1.20.1：`enchantment.<namespace>.<path>`（由代码拼出）

`.mc-src/1.20.1/net/minecraft/world/item/enchantment/Enchantment.java:89-101`
```java
if (this.descriptionId == null) {
    this.descriptionId = Util.makeDescriptionId("enchantment", BuiltInRegistries.ENCHANTMENT.getKey(this));
}
...
public Component getFullname(int p_44701_) {
    MutableComponent mutablecomponent = Component.translatable(this.getDescriptionId());
```
`Util.makeDescriptionId(prefix, id)` 的规则 = `prefix + "." + namespace + "." + path`。
→ 键就是 **`enchantment.<namespace>.<path>`**，与 1.21+ **格式相同**（只是产生方式从「代码拼」变成「JSON 里写」）。

**「只对食物生效」怎么落地（1.20.1 没有数据驱动）**：
- `Enchantment` 的构造器签名：`.mc-src/1.20.1/.../Enchantment.java:32`
  `protected Enchantment(Enchantment.Rarity, EnchantmentCategory, EquipmentSlot[])`
- `EnchantmentCategory` 是一组枚举 + `canEnchant(Item)`：`.mc-src/1.20.1/.../EnchantmentCategory.java:21-117`
  —— 里面**没有**任何「食物」类别。
- `canEnchant` 默认委托：`.mc-src/1.20.1/.../Enchantment.java:115-116`
  ```java
  public boolean canEnchant(ItemStack p_44689_) { return this.canApplyAtEnchantingTable(p_44689_); }
  ```
  `:141-142` `canApplyAtEnchantingTable(stack)` → `stack.canApplyAtEnchantingTable(this)`，
  最终走到 Forge 扩展 `.mc-src/1.20.1/net/minecraftforge/common/extensions/IForgeItemStack.java:89-90`。
- **所以 1.20.1 必须自定义 `Enchantment` 子类并覆写 `canEnchant` / `canApplyAtEnchantingTable`**，
  在里面用自建标签（§1.3 方案 A）判断。`EnchantmentCategory` 传一个任意的（例如 `EnchantmentCategory.WEAPON`
  或自定义一个）只影响附魔台的候选列表，真正生效的是覆写的方法。

---

## 6. 注册自定义物品（`orefood:iron_beef`）所需的资源/数据文件完整清单

通用命名规则（三版本一致，由 `Util.makeDescriptionId("item", id)` 决定）：

| 用途 | 键 / id |
|---|---|
| 物品 id | `orefood:iron_beef`（注册名 `iron_beef`，全小写 + 下划线） |
| 语言键 | `item.orefood.iron_beef` |
| 模型引用 id | `orefood:item/iron_beef` |
| 贴图 id | `orefood:item/iron_beef` |

证据：1.21.1 `.mc-src/1.21.1/net/minecraft/world/item/Item.java:265-266`
`this.descriptionId = Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));`；
1.20.1 `.mc-src/1.20.1/.../Item.java:230`；26.1.2 `.mc-src/26.1.2/.../Item.java:403-404`
（`ITEM_DESCRIPTION_ID = id -> Util.makeDescriptionId("item", id.identifier())`）。

### 6.1 Forge 1.20.1

| # | 文件（相对 `forge-1.20.1/src/main/resources/`，除非注明） | 内容 |
|---|---|---|
| 1 | `assets/orefood/models/item/iron_beef.json` | `{"parent":"minecraft:item/generated","textures":{"layer0":"orefood:item/iron_beef"}}` |
| 2 | `assets/orefood/textures/item/iron_beef.png` | 16×16 PNG |
| 3 | `assets/orefood/lang/en_us.json` | `"item.orefood.iron_beef": "Iron Beef"` |
| 4 | `assets/orefood/lang/zh_cn.json` | `"item.orefood.iron_beef": "铁牛排"` |
| 5 | `data/orefood/tags/items/<food_tag>.json` | **自建**食物标签（§1.3 方案 A），注意 **`items` 复数** |
| 6 | （附魔）`java/.../init/ModEnchantments.java` | `Enchantment` 子类 + `canEnchant` 覆写 |
| — | 不需要 `items/*.json` | 不存在这个概念 |

**新增物品本体（1.20.1）**：
```java
public static final RegistryObject<Item> IRON_BEEF = ITEMS.register("iron_beef",
        () -> new Item(new Item.Properties()
                .food(new FoodProperties.Builder().nutrition(8).saturationMod(0.8F).build())));
```
`Item.Properties#food(FoodProperties)`：`.mc-src/1.20.1/net/minecraft/world/item/Item.java:428`。

### 6.2 NeoForge 1.21.1

| # | 文件（相对 `neoforge-1.21.1/src/main/resources/`，除非注明） | 内容 |
|---|---|---|
| 1 | `assets/orefood/models/item/iron_beef.json` | 同 6.1 |
| 2 | `assets/orefood/textures/item/iron_beef.png` | 同 6.1 |
| 3-4 | `assets/orefood/lang/{en_us,zh_cn}.json` | 同 6.1 |
| 5 | `data/orefood/tags/item/<food_tag>.json` | **`item` 单数** |
| 6 | `data/c/tags/item/foods.json`（**可选**） | 把 `orefood:iron_beef` 直接塞进 `c:foods` |
| 7 | （附魔）`data/orefood/enchantment/<id>.json` | `description` / `supported_items: "#c:foods"` / `primary_items` |
| 8 | （附魔）lang 加 `"enchantment.orefood.<id>"` | |
| — | 不需要 `items/*.json` | 1.21.1 仍用 `models/item` 自动映射 |

**新增物品本体（1.21.1 风格，注意是 `FoodProperties` 而非 `food` 组件）**：
```java
public static final DeferredItem<Item> IRON_BEEF = ITEMS.registerItem("iron_beef",
        props -> new Item(props.food(new FoodProperties.Builder()
                .nutrition(8).saturationModifier(0.8F).build())));
```
`Item.Properties#food(FoodProperties)`：`.mc-src/1.21.1/net/minecraft/world/item/Item.java:448`。

### 6.3 NeoForge 26.1.2

| # | 文件 | 内容 |
|---|---|---|
| 1 | `neoforge-26.1.2/src/main/resources/assets/orefood/items/iron_beef.json` | **26.1.2 新增，必须**：`{"model":{"type":"minecraft:model","model":"orefood:item/iron_beef"}}` |
| 2 | `assets/orefood/models/item/iron_beef.json` | **仍需保留**（被 #1 引用） |
| 3 | `assets/orefood/textures/item/iron_beef.png` | 同前 |
| 4-5 | `assets/orefood/lang/{en_us,zh_cn}.json` | 同前 |
| 6 | `data/orefood/tags/item/<food_tag>.json` | **`item` 单数**（与 1.21.1 相同） |
| 7 | `data/c/tags/item/foods.json`（可选） | 同 1.21.1 |
| 8 | `data/orefood/enchantment/<id>.json` | 同 1.21.1（26.1.2 的字段名一致，见 §5.1） |
| 9 | `pack.mcmeta` 修正 | 见 §3.2，改 `min_format`/`max_format` |

**新增物品本体（26.1.2）**：
```java
public static final DeferredItem<Item> IRON_BEEF = ITEMS.registerItem("iron_beef",
        props -> new Item(props.food(new FoodProperties.Builder()
                .nutrition(8).saturationModifier(0.8F).build())));
```
`Item.Properties#food(FoodProperties)`：`.mc-src/26.1.2/net/minecraft/world/item/Item.java:420`
（另有 `:424` 的 `food(FoodProperties, Consumable)` 重载）。
`FoodProperties` 仍是 record：`.mc-src/26.1.2/net/minecraft/world/food/FoodProperties.java:20`
`public record FoodProperties(int nutrition, float saturation, boolean canAlwaysEat)`，
Builder 在 `:51-73`（`nutrition(int)` / `saturationModifier(float)`）。
> 26.1.2 里**没有** `FoodProperties#saturationMod`（1.20.1 的名字），是 `saturationModifier`——
> 与 1.21.1 一致。

### 6.4 共享层 vs 平台层：各自该放什么

项目现状（三个 `build.gradle` 一致）：
- `forge-1.20.1/build.gradle:28` / `neoforge-1.21.1/build.gradle:36` / `neoforge-26.1.2/build.gradle:35`
  → 三个模块**都**把 `../common/src/main/resources` 加进 `sourceSets.main.resources`。
- `forge-1.20.1/build.gradle:120-122` → `${...}` 展开作用于 `META-INF/mods.toml` 与 `pack.mcmeta`。
- `neoforge-1.21.1/build.gradle:139-142` / `neoforge-26.1.2/build.gradle:139-142`
  → `src/main/templates` 经 `generateModMetadata` 展开到 `build/generated/sources/modMetadata`。

`common/src/main/resources/` 现有内容（就是「平台无关」的样板）：
```
assets/orefood/lang/en_us.json
assets/orefood/lang/zh_cn.json
assets/orefood/models/item/example_item.json
assets/orefood/textures/item/example_item.png
```

**分层规则表**：

| 资源种类 | 放哪 | 理由 |
|---|---|---|
| `assets/<ns>/lang/*.json` | **共享层** `common/src/main/resources/` | 三版本语言键格式一致（`item.<ns>.<path>`、`enchantment.<ns>.<path>`），只有 26.1.2 多了 tag 相关键可选 |
| `assets/<ns>/textures/item/*.png` | **共享层** | 三版本路径完全一致 |
| `assets/<ns>/models/item/*.json` | **共享层** | 三版本路径与内容一致（26.1.2 仍然需要它） |
| `assets/<ns>/items/*.json` | **仅 26.1.2 模块** `neoforge-26.1.2/src/main/resources/` | 1.20.1/1.21.1 没有这个目录概念；放共享层只会被无意义地打进另两个 jar |
| `data/<ns>/tags/items/*.json` | **仅 1.20.1 模块** | 目录名 `items` 与其他两版本不同 |
| `data/<ns>/tags/item/*.json` | **1.21.1 与 26.1.2 模块各一份**（或共享层放 `item` 版 + 1.20.1 单独放 `items` 版） | 1.21.1 与 26.1.2 目录名相同（`item`），所以这一份**可以**放共享层；但要接受它同时被打进 1.20.1 的 jar 而完全不生效（无害，但容易误导） |
| `data/<ns>/enchantment/*.json` | **仅 1.21.1 与 26.1.2**（可放共享层） | 1.20.1 没有数据驱动附魔，这份数据对它是垃圾 |
| `pack.mcmeta` | 1.20.1 → `src/main/resources/`；1.21.1/26.1.2 → `src/main/templates/` | 平台格式号不同，不能共享 |
| `META-INF/mods.toml` / `neoforge.mods.toml` | 同上 | |
| 物品/附魔的 Java 注册代码 | **各平台模块各自一份** | `DeferredRegister` vs `RegistryObject`、`createItems` vs `create`、`putAfter` vs `insertAfter`（见 `forge-1.20.1/.../OreFoodItems.java:22-32` 与 `neoforge-1.21.1/.../OreFoodItems.java:14-28` 的对照注释） |

**推荐做法（为了「一处改，三处生效」且不误导）**：
1. `common/src/main/resources/` 放：`lang/`、`textures/item/`、`models/item/`。
2. `common` 里**不放**任何 `data/**`（因为 `tags/items` vs `tags/item` 的分歧会让它至少在一个版本上失效）。
3. 三个平台模块各自放自己的 `data/`：
   - `forge-1.20.1/src/main/resources/data/orefood/tags/items/*.json`
   - `neoforge-1.21.1/src/main/resources/data/orefood/tags/item/*.json` + `data/orefood/enchantment/*.json`
   - `neoforge-26.1.2/src/main/resources/data/orefood/tags/item/*.json` + `data/orefood/enchantment/*.json`
4. `neoforge-26.1.2/src/main/resources/assets/orefood/items/*.json` 只放 26.1.2。
5. 如果嫌 data 文件要写两遍（1.21.1 与 26.1.2 内容相同），可以把它们放到
   `common/src/main/resources/data/...`，代价是 1.20.1 的 jar 里会多出一份不生效的 `tags/item/`。
   ——**这是权衡，不是错误**，但必须写进注释，否则后人会以为 1.20.1 也在用它。

---

## 7. 给「5 个只对食物生效的附魔」的具体建议

| 版本 | 附魔定义位置 | 「可附魔物品」表达 |
|---|---|---|
| 1.20.1 | Java（`Enchantment` 子类） | 覆写 `canEnchant(ItemStack)`，`return stack.is(MOD_FOODS_TAG)`；`MOD_FOODS_TAG` 指向自建 `orefood:foods`，其 JSON 在 `data/orefood/tags/items/foods.json` |
| 1.21.1 | `data/orefood/enchantment/<id>.json` | `"supported_items": "#c:foods"` + `"primary_items": "#c:foods"` |
| 26.1.2 | `data/orefood/enchantment/<id>.json` | 同上，字段名未变 |

**要不要额外建 `orefood:foods`？**
- 只关心「原版 + 本模组的食物」→ 直接引 `#c:foods`，**不用**自建标签。但要把 `orefood:iron_beef` 加进
  `c:foods`，就得写增量文件 `data/c/tags/item/foods.json`。
- 担心别的模组也写 `c:foods` 增量、或者想自己控制白名单 → 自建 `orefood:foods`，
  内容 `{"values": ["#c:foods", "orefood:iron_beef", { "id": "#c:foods/dough", "required": false }]}`。
- **推荐后者**：附魔只依赖自己的标签，`c:foods` 只作为来源之一，互不干扰。

---

## 8. 「未找到」与未核实项（诚实清单）

| 事项 | 状态 | 搜过的关键字 / 方式 |
|---|---|---|
| 1.20.1 的 `forge:food` 标签 | **确认不存在** | `Tags.java` 内 `FOOD`（大小写敏感）、`TagKey<Item> F`；`data/forge/tags/items/` 目录列表；`data/**/*food*` 文件名 |
| 1.20.1 原版「全部食物」标签 | **确认不存在** | `ItemTags.java` 内 `FOODS|food` → 只有 `piglin_food`/`fox_food`/`sniffer_food` |
| 26.1.2 的 `TagConventionLogWarning.java`（1.21.1 中那张 legacy 迁移表） | **未找到** | 文件名通配 `TagConvention*`；该文件内 `"food"` / `"foods"` / `FOODS)` |
| 26.1.2 NeoForge 实际产出的 `data/c/tags/item/foods.json` | **反编译产物里没有**（`.mc-src/26.1.2/data` 只有 `minecraft`）；路径由 `Registries.tagsDirPath` + `CommonHooks.prefixNamespace` + `NeoForgeItemTagsProvider` 推导 | 目录列表 `.mc-src/26.1.2/data` |
| 1.20.1 / 1.21.1 原版 `assets/minecraft/atlases/*.json` 的完整目录源定义 | **读不到**：反编译产物里这两个文件已被 Forge/NeoForge 补丁替换成只有 `forge:white` / `neoforge:white` | 读出两文件全文 |
| 26.1.2 的 `pack_format` 单独出现时是否**真的**回退成功（不崩） | 代码路径成立（`ResourcePackLoader.readMeta` 的 `catch (JsonParseException)` → `FALLBACK_TYPE`），但未实际跑过 | `ResourcePackLoader.java:198-206`、`PackMetadataSource.java`、`PackFormat.java:142-171` |
| NeoForge 26.1.2 的 `DeferredRegister` 是否有 1.21.1 之外的破坏性改动（对本需求范围外） | 未全面核对 | 只在 `DeferredRegister.java` 内核对 `createItems` / `registerItem` |

---

## 9. 行动清单（按优先级）

1. **修 `neoforge-26.1.2/src/main/templates/pack.mcmeta`**：`pack_format: 84` → `min_format`/`max_format`（§3.2）。
2. 确定食物标签策略（§7 推荐自建 `orefood:foods`），据此决定要不要写 `data/c/tags/item/foods.json`。
3. 5 个附魔：
   - 1.20.1 → Java 子类 + `canEnchant` 覆写 + `data/orefood/tags/items/foods.json`
   - 1.21.1 / 26.1.2 → `data/orefood/enchantment/*.json`，`supported_items` + `primary_items` = `#c:foods`（或自建标签）
4. 4 种矿物牛排物品：
   - 共享层：`models/item/*.json`、`textures/item/*.png`、`lang/*.json`
   - 1.20.1：注册代码 + `data/orefood/tags/items/foods.json` 纳入
   - 1.21.1：注册代码 + `data/orefood/tags/item/foods.json` 纳入
   - 26.1.2：注册代码 + **`assets/orefood/items/*.json`（4 个）** + `data/orefood/tags/item/foods.json` 纳入
5. 26.1.2 编译前全局检查改名：`ResourceLocation` → `Identifier`、`.location()` → `.identifier()`、
   `net.minecraft.Util` → `net.minecraft.util.Util`、`advancements.critereon` → `advancements.criterion`、
   `UseAnim` → `ItemUseAnimation`、`Tiers` → `ToolMaterial`、`armortrim` → `equipment.trim`、
   `Equipable` → `equipment.Equippable`（接口 → 数据组件）、`ArmorItem` 已被移除。
6. 数据包（默认注册 4 种牛排）：三版本放 `data/orefood/<registry>/`；注意 26.1.2 的
   `Registries.elementsDirPath` 走 `prefixNamespace`（`.mc-src/26.1.2/.../Registries.java:329-331`），
   与 1.21.1 一致（`.mc-src/1.21.1/.../Registries.java:251-253`）。
