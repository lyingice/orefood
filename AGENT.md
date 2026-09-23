# AGENT.md — 开发与维护备忘

给接手这个仓库的 AI / 开发者看的。玩家向介绍在 [README.md](README.md)，
数据包作者看 [docs/datapack-guide.md](docs/datapack-guide.md)。

`docs/research/` 下有五份**逐条带 `文件:行号` 证据**的 MC 源码对照报告
（附魔 / 食物与耐久 / 动态注册表 / 提示框 / 标签与资源），
改任何与版本差异相关的代码前先读它们，比读代码快得多。

---

## 一、项目结构

```
common/                       共享层：纯 JDK，由三个平台各自编译进 jar
  src/main/java/com/lyingice/orefood/
    OreFoodConstants.java       常量
    FoodEnchantment.java        五个附魔的 id 与最大等级
    FoodEnchantmentRules.java   所有数值公式（含 duration / amplifier 换算）
  src/main/resources/assets/orefood/
    lang/ models/item/ textures/item/    三个平台共用的资源
forge-1.20.1/                 Forge 47.x  + MC 1.20.1 + Java 17 + Gradle 8.8
neoforge-1.21.1/              NeoForge 21.1.x + MC 1.21.1 + Java 21 + Gradle 8.14.2
neoforge-26.1.2/              NeoForge 26.1.2.x + MC 26.1.2 + Java 25 + Gradle 9.1.0
docs/research/                源码对照报告
docs/datapack-guide.md        数据包教程
tools/                        源码提取 / 反编译 / 贴图生成脚本
```

各平台模块内的包结构一致：

```
<platform>/src/main/java/com/lyingice/orefood/
  <loader>/OreFood<Loader>.java   @Mod 主类，只做装配
  init/OreFoodItems.java          单例物品注册 + 创造栏 + stackFor
  init/OreFoodComponents.java     数据组件（仅两个 NeoForge 平台）
  init/OreFoodEnchantments.java   附魔：1.20.1 真注册，1.21+ 只声明 key
  item/OreFoodItem.java           物品类，按定义提供耐久/附魔能力
  data/OreFoodDefinition.java     定义记录 + Codec
  data/PotionEffectSpec.java      单条食用效果 + Codec
  logic/OreFoodConsume.java       食用结算（三件事）+ 消耗逻辑
  logic/FoodEnchantmentEffects.java  附魔 → 效果列表（提示框与结算共用）
  logic/FoodEnchantmentLevels.java   读物品栈上的五个附魔等级
  crafting/OreFoodIngredient.java    自定义配料：按定义 id 匹配（不是按物品类型）
  crafting/OreFoodIngredients.java   配料类型注册
  crafting/OreFoodRecipeSugar.java   配方语法糖：反序列化前把定义 id 展开成原版写法
  mixin/*.java                    食用注入 + 配方 JSON 改写
                                  （26.1.2 走官方 ModifyRecipeJsonsEvent，没有 mixin）
  client/OreFoodTooltip.java      提示框
```

**改数值只改 `FoodEnchantmentRules`**，三个平台自动同步。
**改附魔等级上限只改 `FoodEnchantment`**（1.20.1 的代码注册和 1.21+ 的 JSON 都必须与它对齐，
JSON 里的 `max_level` 是手写的，不会自动跟随）。

---

## 二、构建

三个模块的 Gradle 版本互不兼容，所以根构建**刻意不用 composite build**，只转调：

```bash
./gradlew buildAll            # 三个平台依次构建
cd <module> && ./gradlew build runClient runServer runData
```

`common/` 没有构建文件，靠各平台 `sourceSets.main.java.srcDir '../common/src/main/java'` 编译。
**共享层只能放纯 JDK 代码**——一旦 import 游戏类，三个平台里任一缺符号就全崩。

### 需要更宽的文件权限才能构建

Gradle 要写工作区外的 `~/.gradle`（锁文件、依赖缓存）。若沙箱只允许写工作区，
构建会以 `FileNotFoundException: ...gradle-8.8-bin.zip.lck (拒绝访问)` 失败。

---

## 三、源码查阅工具

```bash
pwsh tools/extract-mc-src.ps1          # 1.21.1 / 26.1.2：从 Gradle 缓存解压到 .mc-src/
pwsh tools/decompile-forge-1.20.1.ps1  # 1.20.1：ForgeGradle 只缓存 class，需 vineflower 反编译
```

`.mc-src/` 已在 `.gitignore` 里（两万多个文件）。
`tools/rename-mod.ps1` 保留着，但**必须**排除 `.mc-src`——MC 源码里遍布
`StructureTemplate` 之类的标识，被改名规则碰到就直接毁掉。

---

## 四、三版本差异（改代码前必看）

### 附魔

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 形态 | `abstract class Enchantment` | `record Enchantment` | 同 1.21.1 |
| 注册 | `DeferredRegister` + `ForgeRegistries.ENCHANTMENTS` | 数据包 `data/orefood/enchantment/*.json` | 同 1.21.1 |
| 「只对食物生效」 | `EnchantmentCategory.create(..., Item::isEdible)` | `"supported_items": "#c:foods"` | 同 1.21.1 |
| 获取途径 | 覆写 `isTreasureOnly()` / `isDiscoverable()` / `isTradeable()` | 数据包附魔标签（见下） | 同 1.21.1 |
| 等级读取 | `EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)` | `stack.getEnchantmentLevel(Holder<Enchantment>)` | 同 1.21.1 |

### 配方：定义 id 语法糖

配方里可以直接写**定义 id**（如 `orefood:copper_beef`——它**不是**物品 id，
物品注册表里没有它），加载时由本模组在**反序列化之前**改写成原版写法。
实现见三个平台的 `crafting/OreFoodRecipeSugar`。

**为什么必须自己改写**：五种牛排是**同一个物品**（`orefood:ore_food`），只有组件 /
NBT 不同，而三个版本的原版配料**都只按物品类型匹配**：

- 1.20.1 `Ingredient#test` → `itemstack.is(item)`，连 NBT 都不看；
- 1.21.1 `Ingredient#test` → `itemstack.is(stack.getItem())`，同样不看组件；
- 26.1.2 `Ingredient` 内部是 `HolderSet<Item>`——一组物品**类型**，按构造就不可能区分。

所以配料必须被换成本模组的自定义配料（注册名 `orefood:ore_food`）：

| | 1.20.1 (Forge) | 1.21.1 / 26.1.2 (NeoForge) |
|---|---|---|
| 实现方式 | 继承 `AbstractIngredient` + `IIngredientSerializer` | 实现 `ICustomIngredient` |
| 注册 | `CraftingHelper.register(id, serializer)`，在 `FMLCommonSetupEvent` 里 | `DeferredRegister` → `NeoForgeRegistries.Keys.INGREDIENT_TYPES` |
| 展示用物品 | `getItems()` → `ItemStack[]` | 1.21.1 `getItems()` → `Stream<ItemStack>`；**26.1.2 `items()` → `Stream<Holder<Item>>`** |

#### 拦截点：在「反序列化之前」改写原始 JSON

关键设计是**不碰任何 codec**，而是在配方 JSON 刚读盘、还没解析时原地改写：

| 版本 | 拦截点 | Mixin | 注册表从哪来 |
|---|---|---|---|
| 1.20.1 | `RecipeManager#apply` HEAD | 1 个 | `AddReloadListenerEvent#getRegistryAccess()` 暂存 |
| 1.21.1 | `RecipeManager#apply` HEAD | 1 个 | `RecipeManager` 的 `registries` 字段（`@Shadow`） |
| 26.1.2 | `ModifyRecipeJsonsEvent` | **0 个** | 事件自带 `lookupOrThrow` |

1.20.1 的 `RecipeManager` 只持有 `ICondition.IContext`（**不是**注册表），所以注册表要在
`AddReloadListenerEvent` 里暂存。那个事件在 `ReloadableServerResources` **组装监听器
列表**时同步触发（该类第 91 行），而真正跑监听器是下一行（第 92 行）——所以暂存一定
早于配方解析。**不要**改用 `ServerLifecycleHooks.getCurrentServer()`：那个注册表访问
在解析时还没换上本次重载的 RELOADABLE 层，查不到数据包注册表。

**为什么不替换物品 codec**（这条值得记住，走过弯路）：

- `ItemStack.CODEC` / `ItemStackTemplate.CODEC` / `Ingredient.CODEC` 全是
  `static final`，而 `RecordCodecBuilder.mapCodec` 是**立即求值**的——下游序列化器
  在自己的 `<clinit>` 里就把这些 codec **按值捕获**了。运行期改字段**没有任何效果**，
  且受类加载顺序支配，极脆弱；
- 一个 JSON 层面的改写点覆盖**全部配方类型**（有形状/无形状/熔炉/锻造/切石），
  而 codec 层面光 1.21.1 的配方包里就散落着 5 个不同的 codec 字段。

#### 配料与产物的 JSON 格式：三个版本不一样

| 版本 | 原版配料 | 定义 id（配料） | 产物 |
|---|---|---|---|
| 1.20.1 | `{"item": "minecraft:iron_ingot"}` | `{"item": "orefood:copper_beef"}` | `{"item": "<定义 id>"}` |
| 1.21.1 | 同上 | 同上 | `{"id": "<定义 id>"}` |
| 26.1.2 | `"minecraft:iron_ingot"` | `"orefood:copper_beef"` | `{"id": "<定义 id>"}` |

**26.1.2 的配料是裸字符串**：那边 `Ingredient` 换成了 `HolderSet<Item>`，由
`HolderSetCodec` 解析，只认「`#标签` 字符串」或「元素 id 数组」。对象形式
`{"item": ...}` 会**解析失败——连原版物品也一样失败**（原版 `shield.json` 里写的是
`"W": "#minecraft:wooden_tool_materials"`）。

自定义配料的判别字段也不同，写错会**静默**落到原版分支然后解析失败：

| 版本 | 判别字段 | 依据 |
|---|---|---|
| 1.20.1 / 1.21.1 | `"type"` | `NeoForgeExtraCodecs.dispatchMapOrElse` 判 `input.get("type")` |
| 26.1.2 | `"neoforge:ingredient_type"` | `IngredientCodecs.codec` 的 `dispatch(...)` 字段名 |

配方目录同样是单复数差异：1.20.1 是 `data/<ns>/recipes/`，1.21+ 是 `data/<ns>/recipe/`。

### 耐久附魔的物品标签

原版「耐久 / 经验修补」的 `supported_items` 是 `#minecraft:enchantable/durability`，
而本模组的牛排**不在**那个标签里，所以默认附不上——需要在
`data/minecraft/tags/item/enchantable/durability.json` 里补上 `orefood:ore_food`。

1.20.1 没有这套标签，它用 `EnchantmentCategory.BREAKABLE`（判定 `Item#canBeDepleted`），
带 `durability()` 注册的物品自动满足，**不需要**数据包。

#### 附魔的获取途径（1.21+ 全靠标签，漏了就是「哪都拿不到」）

原版的 `VanillaEnchantmentTagsProvider` 是这么组织的：

```java
this.tag(DOUBLE_TRADE_PRICE).addTag(TREASURE);
this.tag(IN_ENCHANTING_TABLE).addTag(NON_TREASURE);
this.tag(ON_MOB_SPAWN_EQUIPMENT).addTag(NON_TREASURE);
this.tag(ON_TRADED_EQUIPMENT).addTag(NON_TREASURE);
this.tag(ON_RANDOM_LOOT).addTag(NON_TREASURE).add(...几个宝藏附魔);
this.tag(TRADEABLE).addTag(NON_TREASURE).add(...几个宝藏附魔);
```

也就是说 **`in_enchanting_table` / `tradeable` / `on_random_loot` / `on_mob_spawn_equipment` /
`on_traded_equipment` 全部引用 `#minecraft:non_treasure`**。所以：

- **非宝藏附魔** → 只需写进 `data/minecraft/tags/enchantment/non_treasure.json`，其余途径自动全有；
- **宝藏附魔** → 写进 `treasure.json`（自动获得 `double_trade_price`，且不出现在附魔台），
  想让它是战利品/交易能拿到的（像原版 `mending`），再显式写进 `on_random_loot.json` 与 `tradeable.json`。

消费点：`EnchantmentMenu`（附魔台）读 `IN_ENCHANTING_TABLE`，
`VillagerTrades` 读 `TRADEABLE`，`EnchantRandomlyFunction` / `EnchantWithLevelsFunction` 读 `ON_RANDOM_LOOT`。

**1.20.1 没有这套标签**（`EnchantmentTags` 类在该版本不存在），
改用覆写 `isDiscoverable()`（附魔台）、`isTradeable()`（`VillagerTrades` 用
`Enchantment::isTradeable` 过滤）、`isTreasureOnly()`。

启动时会打印每个附魔实际挂到的标签，写错了看一眼日志就知道（见「踩坑清单」）。

> **1.21.1 / 26.1.2 没有任何一个附魔效果组件以「物品被吃下」为触发点**
> （30 / 31 个组件全查过，唯一沾边的 `minecraft:tick` 只扫装备槽位）。
> 所以「吃下时生效」三个版本都只能写代码，不存在纯数据驱动的做法。

### 食用流程（决定 Mixin 目标）

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 入口 | `Player#eat` → `LivingEntity#eat(Level, ItemStack)` | `Player#eat` → `LivingEntity#eat(Level, ItemStack, FoodProperties)` | `Consumable#onConsume(Level, LivingEntity, ItemStack)` |
| 消耗点 | `ItemStack#shrink(1)` | `ItemStack#consume(1, this)` | 同 1.21.1，但在 `Consumable#onConsume` 里 |
| 加营养 | `FoodData#eat(Item, ItemStack, LivingEntity)`，第二参是**饱和度系数** | `FoodData#eat(FoodProperties)` | `FoodProperties#onConsume` → `FoodData#eat(this)` |
| `LivingEntity#eat` | 存在 | 存在（二参重载是 `final`，只能注入三参） | **已被整个删除** |

**营养是在原版加完之后由我们补的**：物品注册时的食物属性是刻意的零值
（`nutrition(0).saturationModifier(0)`），真实数值在吃下的那一刻按定义加。
这样 `/give` 出来的裸物品栈也能正确工作。

1.20.1 的饱和度换算：`FoodData#eat(int, float)` 第二参是**系数**，
最终饱和度 = 饱食度 × 系数 × 2，所以代码里做了反推。另外两个版本没有这个问题。

### 耐久消耗：一律交给原版 `ItemStack#hurtAndBreak`

「吃一口扣一点耐久」**不要自己写**（`damage + 1`，到顶 `shrink`）。原版耐久子系统
就在 `hurtAndBreak` 内部，自己实现会丢掉：

- **耐久附魔**——唯一的判定点是 `EnchantmentHelper.processDurabilityChange`
  （1.21.1 在 `ItemStack.java:469`，26.1.2 在 `ItemStack#processDurabilityChange`）。
  绕开它 = 耐久 I/II/III 完全失效，附魔台上花掉的等级买到的只是个装饰；
- 创造模式的「不消耗」守卫；耐久变化进度触发器；耐久耗尽时的 `shrink` 与破坏回调。

签名差异：1.20.1 是 `hurtAndBreak(int, LivingEntity, Consumer<Item>)`；
1.21.1 / 26.1.2 是 `hurtAndBreak(int, ServerLevel, LivingEntity, Consumer<Item>)`。

**1.20.1 从一开始就是对的**（用的就是 `hurtAndBreak`）；1.21.1 / 26.1.2 曾经是手写版，
已修正。破坏回调传空实现即可——原版拿它通知装备槽，食物没有装备槽。

### Mixin 配置

- **Forge 1.20.1 的 `mods.toml` 不支持 `[[mixins]]`**——写了会被**静默忽略**。
  必须在 JAR 清单里写 `MixinConfigs`（见 `forge-1.20.1/build.gradle` 的 `jar` 段）。
- **NeoForge 两个版本相反**：必须在 `neoforge.mods.toml` 里写 `[[mixins]] config="..."`。
- **⚠️ 只把清单设在 `jar` 任务上，dev 运行下 mixin 会「静默失效」**：`runServer` /
  `runClient` 加载的是 `build/resources/main` **目录**而不是 jar，而 Mixin 只从 mod
  文件根部的 `META-INF/MANIFEST.MF` 读 `MixinConfigs`。症状是编译通过、启动不报任何错、
  功能就是不起作用——`LivingEntityEatMixin` 曾因此长期只在打进 jar 后才生效。
  修法见 `forge-1.20.1/build.gradle` 的 `processResources` 段：往输出目录补一份最小
  清单给 dev 用，并在 `jar` 任务里 `exclude 'META-INF/MANIFEST.MF'` 避免重复条目。

### 物品外观

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 机制 | NBT `CustomModelData` + `models/item/ore_food.json` 的 `overrides` | 数据组件 `CUSTOM_MODEL_DATA` + 同样的 overrides | 数据组件 `ITEM_MODEL` 直接指向 `assets/orefood/items/<id>.json` |
| 数据包字段 | `model_data` | `model_data` | 忽略 `model_data`，按定义 id 找模型 |

`CustomModelData` 的类型在各版本不同：1.21.1 是 `record CustomModelData(int)`，
26.1.2 是 `record CustomModelData(List<Float>, List<Boolean>, List<String>, List<Integer>)`。

### 其它破坏性差异（26.1.2）

- `ResourceLocation` → `Identifier`（包路径不变），`ResourceKey#location()` → `identifier()`，
  `level.isClientSide` 变成方法 `isClientSide()`。
- 四个药水效果常量改名：`MOVEMENT_SPEED`→`SPEED`、`DAMAGE_BOOST`→`STRENGTH`、
  `HEAL`→`INSTANT_HEALTH`、`DAMAGE_RESISTANCE`→`RESISTANCE`。
- 附魔能力从 `Item#getEnchantmentValue` 改成 `minecraft:enchantable` 数据组件。
- `Item#appendHoverText` 已废弃且签名大改（`List` → `Consumer`）。
- `RegistryAccess`：`registryOrThrow`→`lookupOrThrow`、`get`→`getValue`、`holders`→`listElements`。
- `pack.mcmeta` 用 `min_format`/`max_format` 而不是 `pack_format`。

---

## 五、需求与实现的对应关系

用户原始需求里的几条固定规则，落在这些地方（改之前先确认别破坏它们）：

- **附魔 I 级 = amplifier 0** → `FoodEnchantmentRules#potionAmplifier`
- **抗性提升固定 II 级（40%）** → `FoodEnchantmentRules#RESISTANCE_AMPLIFIER`
- **持续时间 = 附魔等级 × 系数** → `FoodEnchantmentRules` 里那几个 duration 方法
- **饱腹额外 等级×2 点饱食度** → `satietyBonus` / `satietyBonusSaturation`
- **耐久 0 = 无限** → `OreFoodDefinition#infiniteDurability` + `OreFoodConsume#consume`
- **吃完扣耐久而不是消失** → 三个平台的 `OreFoodConsume#consume`
- **提示框显示药水效果** → `client/OreFoodTooltip`

---

## 六、踩坑清单

这些坑都真踩过，别再踩回去：

1. **Mixin 的 `@Inject` 处理器必须带 `CallbackInfoReturnable`**（目标方法有返回值时），
   哪怕完全不用它。少了这个参数**编译能过**，但运行期直接抛
   `InvalidInjectionException: Invalid descriptor`。
   NeoForge 的 `:test` 任务会真正加载 Mixin，所以能抓到这类问题——**别跳过 test**。

2. **消耗耐久的隔离靠「取不到定义就交还原版」**。Mixin 挂在原版吃入口上，
   那个入口对**所有**可食用物品都会经过。`OreFoodConsume#consume` 与 `#onConsume`
   都以 `OreFoodItem.definitionOf(stack) == null` 作为「不是本模组的食物」的判据，
   取不到就直接 return 或走原版分支，因此原版食物与其它模组的物品行为完全不变。
   （不需要再额外判 `instanceof OreFoodItem`——那个组件只有本模组的物品会带。）

3. **新附魔必须自己写进附魔标签，否则「哪都拿不到」**。1.21+ 的附魔台 / 交易 / 战利品
   完全由数据包标签决定，原版对这些标签的默认内容里**没有**你的附魔。
   漏写的后果是静默的：附魔能注册、能通过命令附上、效果也正常，但附魔台刷不出、
   村民不卖、战利品里也永远不掉。非宝藏附魔写进 `non_treasure` 就够了
   （其余标签都引用它）；宝藏附魔还要额外写进 `on_random_loot` / `tradeable`
   才能像原版 `mending` 那样拿到。1.20.1 没有这套标签，靠覆写方法。
   启动日志会把每个附魔实际挂到的标签打出来，照它核对。

4. **1.20.1 的 DFU 版本只接受 `DataResult.error(Supplier<String>)`**，
   写 `error("字面量")` 编译失败。

5. **动态注册表路径是 `data/<pack_ns>/orefood/ore_food/`**——命名空间出现两次。
   写错不会报错，只是静默空注册表，所以主类里加了
   `ServerStartedEvent` 诊断日志打印实际加载条数。

6. **`/reload` 不重读动态注册表**，改 JSON 必须重进世界。

7. **标签目录单复数**：1.20.1 是 `tags/items`，1.21+ 是 `tags/item`。写错静默失效。

8. **`forge:food` 在 1.20.1 上不是官方标签**（NeoForge 的约定检查把它列为「常用但不符约定」）。
   所以 1.20.1 的「只对食物生效」靠 `Item::isEdible`，标签只是给别的模组看的。

9. **26.1.2 的配料 JSON 是裸字符串**，`{"item": ...}` 会解析失败——**连原版物品也一样
   失败**（错误长这样：`No key neoforge:ingredient_type in MapLike[{"item":"minecraft:diamond"}]`）。
   改配方前先确认自己在哪个版本，见第四节那张表。

10. **只把 `MixinConfigs` 设在 `jar` 任务上，dev 运行下 mixin 会静默失效**。
    编译过、启动无报错、功能就是不起作用。详见第四节「Mixin 配置」。

11. **自写耐久消耗会废掉耐久附魔**。必须走原版 `hurtAndBreak`，详见第四节。

12. **PowerShell 改写文件要用 `UTF8Encoding($false)`**。`Set-Content -Encoding UTF8`
    在部分环境会写 BOM，Gradle 解析带 BOM 的 `settings.gradle` 会直接
    `Unexpected character: '' @ line 1, column 1`。

13. **PowerShell 哈希表的键大小写不敏感**。`@{'Template'=...; 'template'=...}` 会抛
    `Duplicate keys`——`tools/rename-mod.ps1` 因此改用了键值对数组。

14. **重命名后要清 `build/` 目录**。增量编译不会清理改名前的 `.class`，
    会以 `NoClassDefFoundError: ... (wrong name: ...)` 的形式在测试阶段炸出来。

15. **`pack.mcmeta` 的 format 声明不对会让数据包不加载**。1.21.1 资源 34 / 数据 48，
    所以用 `supported_formats: [0, 2147483647]` 放宽。

---

## 七、贴图

`assets/orefood/textures/item/*_beef.png` 是美术资源（16×16）。
仓库里另有一个**代码生成**的版本，用来在没美术资源时出图：

```bash
java -Djava.awt.headless=true tools/GenerateOreFoodTexture.java <输出.png> [iron|gold|diamond|netherite]
```

---

## 八、提交前自检

```bash
./gradlew buildAll          # 三个平台都要 BUILD SUCCESSFUL
```

**光编译过不算数**——本项目的失败模式几乎都是静默的（mixin 没加载、标签没写、数据包
路径写错，全都编译通过、启动不报错）。跑一次开发服务器，核对这几行：

- `Loaded N ore food definition(s): [...]` —— 动态注册表有没有真的加载；
- `Loaded N recipes` 且**没有** `Parsing error loading recipe` / `Couldn't parse`
  —— 配方语法糖有没有生效。注意 1.20.1 那行打的是配方**类型**数不是条数，
  所以只能靠「有没有报错」判断；
- 附魔标签自检 —— 每个附魔实际挂到了哪些标签；
- 没有 `InvalidInjectionException`。

```bash
# 1.21.1
cd neoforge-1.21.1 && ./gradlew runServer
# 1.20.1（首次运行前要在 run/ 里放 eula.txt）
cd forge-1.20.1 && ./gradlew runServer
# 26.1.2（需要 Java 25）
cd neoforge-26.1.2 && ./gradlew runServer
```
