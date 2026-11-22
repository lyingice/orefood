# AGENT.md — 开发与维护备忘

给接手这个仓库的 AI / 开发者看的。玩家向介绍在 [README.md](README.md)，
**给别人加新口味**的教程在 [docs/kubejs-guide.md](docs/kubejs-guide.md)。

`docs/research/` 下有五份**逐条带 `文件:行号` 证据**的 MC 源码对照报告
（附魔 / 食物与耐久 / 动态注册表 / 提示框 / 标签与资源）。前三者的结论仍然有效，
但**动态注册表那一份描述的机制已在 1.2.0 被删除**——读的时候留意。

> **1.2.0 是一次架构替换**：从「一个物品 + NBT/组件 + 数据包动态注册表」改成
> 「每种牛排一个真实物品，各自自带定义」。随之删掉的东西：动态注册表、定义 JSON、
> 自定义配料（`OreFoodIngredient`）、配方语法糖（`OreFoodRecipeSugar` + `RecipeManagerMixin`）、
> JEI 变体插件、数据组件（`OreFoodComponents`）、锻造产物 mixin。
> 本文里凡提到这些名字的地方，都是在说「已经不在了，别再找」。

---

## 一、项目结构

```
common/                       共享层：纯 JDK，由三个平台各自编译进 jar
  src/main/java/com/lyingice/orefood/
    OreFoodConstants.java       常量
    OreFoodTable.java           ★ 矿物食品的数值表（矿物/食物清单 + 计算规则，纯 JDK）
    FoodEnchantment.java        五个附魔的 id 与最大等级
    FoodEnchantmentRules.java   所有数值公式（含 duration / amplifier 换算）
  src/main/resources/assets/orefood/
    lang/ models/item/ textures/item/    三个平台共用的资源
forge-1.20.1/                 Forge 47.x  + MC 1.20.1 + Java 17 + Gradle 8.8
neoforge-1.21.1/              NeoForge 21.1.x + MC 1.21.1 + Java 21 + Gradle 8.14.2
neoforge-26.1.2/              NeoForge 26.1.2.x + MC 26.1.2 + Java 25 + Gradle 9.1.0
docs/research/                源码对照报告
docs/kubejs-guide.md          用 KubeJS 加新口味的教程
tools/                        源码提取 / 反编译 / 贴图生成脚本
  src/com/lyingice/orefood/tools/GenerateOreFoodAssets.java
                              资源生成器：模型 / 26.1.2 物品定义 / 三平台配方 / 标签 / 语言
```

各平台模块内的包结构一致：

```
<platform>/src/main/java/com/lyingice/orefood/
  <loader>/OreFood<Loader>.java   @Mod 主类，只做装配
  init/OreFoodItems.java          50 个矿物食品（由 OreFoodTable 双层循环生成）+ 创造栏
  init/OreFoodEnchantments.java   附魔：1.20.1 真注册，1.21+ 只声明 key
  item/OreFoodItem.java           物品类：自带定义，全部行为的取值入口
  data/OreFoodDefinition.java     数值定义 record（+ CODEC，供 KubeJS 解析用）
  data/PotionEffectSpec.java      单条食用效果 + Codec
  logic/OreFoodConsume.java       食用结算（三件事）+ 消耗逻辑
  logic/FoodEnchantmentEffects.java  附魔 → 效果列表（提示框与结算共用）
  logic/FoodEnchantmentLevels.java   读物品栈上的五个附魔等级
  mixin/*.java                    食用注入
                                  （1.20.1 / 1.21.1 是 LivingEntityEatMixin；
                                   26.1.2 是 ConsumableMixin，没有配方相关 mixin）
  client/OreFoodTooltip.java      提示框
  kubejs/OreFoodKubeJSPlugin.java KubeJS 插件：把「矿物牛排物品」注册成一个物品类型
  kubejs/OreFoodItemBuilder.java  KubeJS 的物品构建器（脚本写 .definition({...})）
```

**改数值只改 `OreFoodTable`**（common 里，纯 JDK，三平台共用同一份；数值规则与
「现有五个牛排不许变」由 `OreFoodTableTest` 锁住）。**加矿物 / 加食物也是改它**，
然后重跑资源生成器（见第二节）。
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

### 资源生成（改矿物/食物清单后必跑）

50 个物品的模型、26.1.2 的物品定义、三平台各自的配方、四类标签、两个语言文件，
都由 `tools/src/com/lyingice/orefood/tools/GenerateOreFoodAssets.java` 产出：

```bash
javac -encoding UTF-8 -d "$env:TEMP/orefood-tools" `
    common/src/main/java/com/lyingice/orefood/OreFoodTable.java `
    tools/src/com/lyingice/orefood/tools/GenerateOreFoodAssets.java
java -cp "$env:TEMP/orefood-tools" com.lyingice.orefood.tools.GenerateOreFoodAssets .
```

- **清单只有一份**：生成器直接读 `OreFoodTable`，只在缺中英文名 / 基底物品时报错；
- 默认**不覆盖**已存在的模型与配方（保护 1.x 手写的那 5 份的格式与 diff），`--force` 才覆盖；
- 标签与语言文件**总是重写**（「列全 50 项」正是目的）；语言是合并，既有键与顺序保留；
- 生成器**不进构建流程**，产物直接提交仓库（避免 Gradle 依赖 javac 之外的东西）。

### KubeJS 依赖（compileOnly）

三个模块都只在**编译期**依赖 KubeJS（运行时没装也完全正常）：

- 1.20.1：`kubejs-forge` 需要 `fg.deobf`（SRG 环境），**还要单独补 `rhino-forge`**——
  KubeJS 的 `RegistryInfo` 实现了 rhino 的 `TypeWrapperFactory`，少了它 javac 直接报
  「无法访问 TypeWrapperFactory」；
- 两个 NeoForge 平台：`kubejs-neoforge` 直接 `compileOnly` 即可（不需要 deobf）；
- 三个模块都要加 Latvian / Architectury / Shedaniel / MavenCentral 仓库，
  因为 KubeJS 的传递依赖散在这些地方；
- 版本号在 `gradle.properties` 的 `kubejs_version` 写死 build 号——KubeJS 迭代很快。

**想在 dev 里真的跑一遍 KubeJS 脚本**（本项目实测过一次，见下）：
把构建文件里那行注释掉的 `localRuntime "dev.latvian.mods:kubejs-neoforge:<版本>"`
（1.20.1 是 `localRuntime fg.deobf("dev.latvian.mods:kubejs-forge:<版本>")`）打开，
脚本放到 `<module>/run/kubejs/startup_scripts/`，然后 `runServer`。
注意 KubeJS 的运行期依赖里有 `com.github.rtyley:animated-gif-lib-for-java`，
它在 **JitPack**——所以仓库列表里必须有 JitPack，否则 `:runServer` 会在解析阶段失败。

实测结论（1.21.1，1.2.0）：脚本 `event.create('orefood:test_redstone_beef', 'orefood:ore_food_item')
.definition({ nutrition: 7, saturation: 11.0, durability: 9, enchantment_value: 3 })`
注册出来的物品确实是 `OreFoodItem` 实例，并且带着脚本里写的定义。

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

### 数值规则（2.0）

50 个物品 = 5 矿物 × 10 食物，全部由 common 的 `OreFoodTable` 推导：

| 矿物 | 营养 / 饱和度倍率 | 耐久（能吃几次） | 附魔能力 | 负面效果时长倍率 |
|---|---|---|---|---|
| copper | ×0.75 / ×0.75 | 8 | 1 | ×0.5 |
| iron | ×1.0 / ×1.0 | 16 | 1 | ×1.0 |
| golden | ×1.0 / ×1.25 | 32 | 5 | ×1.5 |
| diamond | ×1.25 / ×1.5625 | 64 | 10 | ×2.0 |
| netherite | ×1.5 / ×1.875 | 128 | 15 | ×2.5 |

- 营养 = `max(1, round(基底营养 × 倍率))`；饱和度 = `round(基底饱和度 × 倍率)` 量化到 1 位小数；
- 食物基底的营养/饱和度取自原版 `Foods` 的**熟**版本（牛排→`cooked_beef`、鳕鱼→`cooked_cod`……；
  面包、河豚、腐肉用原版那三个）；
- **耐久与附魔能力只由矿物决定**，与食物无关；
- 负面效果**只来自基底自带负面的食物**（河豚：中毒 II/饥饿 III/反胃 I；腐肉：饥饿 I 80%）：
  时长随矿物等级放大（×0.5 → ×2.5）；**等级只有下界合金会动**，且只动「本来就是 I 级」的那些
  （腐肉饥饿 I→II、河豚反胃 I→II）——已经是 II / III 级的不叠加。
  想改成「下界合金全部 +1」，删掉 `debuffs()` 里的 `amplifier == 0` 判断即可。
- **创造栏顺序**：`creativeTabPlan()` 给出「每种原版食物 + 紧跟的五个矿物版本」，
  平台侧按它插入（见下）。

`OreFoodTableTest`（common 的测试，三个构建各跑一遍）锁住最容易被改坏的两件事：
**现有五个牛排的数值 bit-for-bit 不变**、**负面效果不升级**。

### 创造栏顺序（2.0）

50 个物品不是简单追加到「食物与饮品」末尾，而是**每种原版食物后面紧跟它的五个矿物版本**：
熟牛排 → 铜/铁/金/钻/下界合金牛排 → 面包 → 铜/……/下界合金面包 → ……

顺序由 common 的 `OreFoodTable#creativeTabPlan()` 给出（纯数据；单元测试锁住：10 组 × 5 个、
锚点就是合成基底、合起来正好 50 个且不重复），平台侧只负责「插」：

| | 1.20.1 | 1.21.1 / 26.1.2 |
|---|---|---|
| 插入 API | **没有 `insertAfter`**，改用 `event.getEntries()`（`MutableHashedLinkedMap`）的 `putAfter` | `event.insertAfter(锚点, 待插, visibility)` |
| 兜底 | 锚点不在表里时退化成 `accept`（用 `entries.contains` 判断） | 先查 `getParentEntries()` / `getSearchEntries()`，锚点不在才退化成 `accept`——**绝不能插完再 accept 兜底**（见踩坑 16） |

**两个易错点**：① 每次要以**上一个插入的物品**为锚点，否则连续 `putAfter` / `insertAfter`
同一个目标会让顺序反过来；② 锚点要用「值等价」的 `ItemStack`（`new ItemStack(Items.COOKED_BEEF)`），
原版加进去的就是它自己。

### 物品与定义

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 定义放哪 | `OreFoodItem` 的字段 | 同左 | 同左 |
| 名字 | 原版描述 id `item.orefood.<路径>`（不覆写 `getName`） | 同左 | 同左 |
| 耐久上限 | 覆写 `getMaxDamage(ItemStack)` 读定义 | 同左 | 同左 |
| 附魔能力 | 覆写 `getEnchantmentValue(ItemStack)` | 同左 | **`minecraft:enchantable` 组件**，注册时写（构造器要求 value > 0） |
| 模型 | `assets/orefood/models/item/<路径>.json` | 同左 | `assets/orefood/items/<路径>.json`，注册时用 `ITEM_MODEL` 组件指向 |
| 可损坏 | `Properties.durability(1)` | 同左 | 同左（让 `MAX_DAMAGE`/`DAMAGE` 组件存在） |
| 食物属性 | Builder：`.nutrition(N).saturationMod(S/(2N))`（**系数**，公开 API 只有这一条路） | record 直构：`new FoodProperties(N, S, true, 1.6F, Optional.empty(), List.of())`（第二参是**绝对饱和度**） | record 直构：`new FoodProperties(N, S, true)`（绝对饱和度，只有 3 个字段） |

**这些差异的根因**：1.20.1 没有数据组件（1.20.5 才引入），26.1.2 把附魔能力与
物品模型都搬进了组件；`FoodProperties` 在 1.21 从 class+Builder（系数语义）变成
record（绝对饱和度），26.1.2 又精简成 3 字段（效果/食用时长搬去了 `Consumable`）。
**Builder 的 `saturationModifier` 始终是系数**（`build()` 时乘 `N×2` 换算），别与 record 直构混用。

### 附魔

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 形态 | `abstract class Enchantment` | `record Enchantment` | 同 1.21.1 |
| 注册 | `DeferredRegister` + `ForgeRegistries.ENCHANTMENTS` | 数据包 `data/orefood/enchantment/*.json` | 同 1.21.1 |
| 「只对食物生效」 | `EnchantmentCategory.create(..., Item::isEdible)` | `"supported_items": "#c:foods"` | 同 1.21.1 |
| 获取途径 | 覆写 `isTreasureOnly()` / `isDiscoverable()` / `isTradeable()` | 数据包附魔标签 | 同 1.21.1 |
| 等级读取 | `EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)` | `stack.getEnchantmentLevel(Holder<Enchantment>)` | 同 1.21.1 |

**NeoForge 平台的附魔前提是标签**：物品不在 `#c:foods` 里就附不上，而且**不报错**。
所以那两边的 `data/c/tags/item/foods.json` 必须列全五种牛排；
KubeJS 注册的新物品也要自己 `.tag('c:foods')`。

### 配方

真物品时代，配方就是普通配方：产物与材料直接写物品 id。

| | 1.20.1 | 1.21.1 / 26.1.2 |
|---|---|---|
| 目录 | `data/<ns>/recipes/` | `data/<ns>/recipe/` |
| 产物写法 | `{ "item": "orefood:diamond_beef" }` | `{ "id": "orefood:diamond_beef" }` |
| 材料写法 | `{ "item": "orefood:golden_beef" }` | 同左 |

26.1.2 的配料内部换成了 `HolderSet<Item>`，只认「`#标签` 字符串」或「元素 id 数组」，
对象形式 `{"item": ...}` 会**解析失败**——但我们的五个配方都是 1.21.1 格式的，
在 26.1.2 上照常工作（`{"item": ...}` 是被接受的简写）。改配方前先在本平台跑一次。

### Mixin 配置

- **Forge 1.20.1 的 `mods.toml` 不支持 `[[mixins]]`**——写了会被**静默忽略**。
  必须在 JAR 清单里写 `MixinConfigs`（见 `forge-1.20.1/build.gradle` 的 `jar` 段）。
- **NeoForge 两个版本相反**：必须在 `neoforge.mods.toml` 里写 `[[mixins]] config="..."`。
- **⚠️ 只把清单设在 `jar` 任务上，dev 运行下 mixin 会「静默失效」**：`runServer` /
  `runClient` 加载的是 `build/resources/main` **目录**而不是 jar，而 Mixin 只从 mod
  文件根部的 `META-INF/MANIFEST.MF` 读 `MixinConfigs`。
  修法见 `forge-1.20.1/build.gradle` 的 `processResources` 段：往输出目录补一份最小清单。
- **⚠️ 1.20.1 还需要 refmap**：注解里写的是 Mojang 名（`eat`），正式客户端里是 SRG 名
  （`m_5584_`）。三样缺一不可：build.gradle 的 `mixin { add ... }`（MixinGradle 插件）、
  `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`（**ForgeGradle 6 不会自动加**）、
  `orefood.mixins.json` 里的 `"refmap"` 字段。缺了它 jar 一进正式客户端就崩
  （`InvalidInjectionException: could not find any targets matching 'eat(...)'`），
  而 **dev 永远测不出来**。两个 NeoForge 平台不需要这套（1.20.2 起运行时就是 Mojang 名）。

### KubeJS 集成

让脚本能注册「继承矿物牛排行为的独立物品」。三平台差异只有注册入口：

| | 1.20.1 | 1.21.1 / 26.1.2 |
|---|---|---|
| 插件基类 | `KubeJSPlugin`（**class，extends**） | `plugin.KubeJSPlugin`（**interface**） |
| 注册类型 | `RegistryInfo.ITEM.addType(name, builderClass, factory)`（插件 `init()` 里） | `registerBuilderTypes(reg -> reg.of(Registries.ITEM, types -> types.add(name, class, factory)))` |
| 插件发现 | 资源根部 `kubejs.plugins.txt` 写类全名 | 同左 |
| 构建器基类 | `dev.latvian.mods.kubejs.item.ItemBuilder` | 同左 |

**两条硬约束写在构建器里**，脚本改不动：
1. **食物属性以定义为准**——构建器把定义的营养/饱和度写进 `FoodProperties`，并覆盖脚本的
   `.food(...)`；跟着脚本值走会翻倍。注意 `createObject()` 必须**先解析定义再建属性**；
2. **不可堆叠 + 可损坏**（`stacksTo(1)` / `durability(1)`），真实上限由 `OreFoodItem` 给出。

脚本里 `.definition({...})` 的参数类型是 `JsonElement`（KubeJS 会把脚本对象转过来），
内部用 `OreFoodDefinition.CODEC` 解析。**定义里不再有 `id` 字段**——物品 id 就是身份。

### 附魔的食用效果：数据包驱动

「哪个附魔吃下去给什么效果」**不再写死在 Java 里**。数据形状是 `OreFoodEffectSpec`
（common，纯 JDK）：

```json
{
  "effect": "minecraft:regeneration",
  "duration":  { "base": 0, "per_level": 100 },
  "amplifier": { "base": 0, "per_level": 1 },
  "probability": 1.0
}
```

换算语义（两条**不对称**，写数据包时必须记住）：

- `duration`：`base + per_level × 等级`（I 级代入 1）
- `amplifier`：`base + per_level × (等级 - 1)`（附魔 I 级 = amplifier 0）

**只有 1.21.1 / 26.1.2 走数据包**；1.20.1 保持硬编码（与那个版本的附魔体系一致）：

| | 1.20.1 | 1.21.1 / 26.1.2 |
|---|---|---|
| 机制 | **硬编码**在 `FoodEnchantmentEffects#effectsFor(4 个 int)` 里 | 自定义**附魔效果组件** `orefood:food_effects` |
| 数据在哪 | common 的 `FoodEnchantmentRules`（三个平台共用同一套数值） | 附魔 JSON 的 `effects` 里 |
| 为什么这样 | 1.20.1 的附魔本身就是代码注册、没有 effects 字段，配套硬编码更一致 | `Enchantment#effects()` 是 `DataComponentMap`，组件注册在 `Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE` |

消费侧只有一处：`FoodEnchantmentEffects#effectsFor(stack[, registryAccess])`，
提示框与食用共用它（提示框不掷 `probability`，食用掷）。

### 联动模组（AppleSkin / Enchantment Descriptions）

**AppleSkin**：物品注册时按定义写入**真实食物属性**（营养/饱和度不再是零值），
AppleSkin 的基础数值天然正确；精妙背包自动喂食（要求 `nutrition >= 1`，见踩坑 23）这类
直接读 `FoodProperties` 的模组也因此正常识别。`FoodValuesEvent` 监听现在只负责一件事：
**预览「饱腹」附魔的额外加成**——附魔等级 > 0 时在基础值上叠加加成，使显示与实际食用一致；
等级为 0 时不干预事件。

- 每平台两个类：`OreFoodAppleSkinSetup`（只判断 `ModList.isLoaded("appleskin")`，**不引用 AppleSkin 类型**）
  与 `OreFoodAppleSkinIntegration`（引用事件，**只在上面判断为真时才会被加载**）；
- 入口用**显式** `modEventBus.addListener(OreFoodAppleSkinSetup::onClientSetup)`，不用注解——
  26.1.2 的 `@EventBusSubscriber` 在编译期拿不到（见踩坑 20）；
- 换算：加成营养 = `等级 × 2`，加成饱和度 = `等级 × 4`。1.20.1 事件要 `FoodValues`，
  等效系数 = `(基础饱和度 + 加成饱和度) / (2 × (基础营养 + 加成营养))`；
  3.x 事件要 `FoodProperties`，record 的绝对饱和度直接加加成，**保留 canAlwaysEat /
  eatSeconds / effects 等其余字段**（旧版重建时丢字段是个坑，已修）。

**Enchantment Descriptions**：不需要任何代码，只提供语言键
`enchantment.<ns>.<path>.desc`（五个附魔的中英描述已在 lang 里）。

**dev 里想真实测联动**，三层依赖要分清：

| 配置 | 作用 | 是否进 jar |
|---|---|---|
| `compileOnly` | 编译期能引用 AppleSkin 的 API | 否 |
| `runtimeOnly` | `runClient` / `runServer` 里真的加载它们 | 否 |
| 玩家的 jar | 什么都不带，靠玩家自己装 | — |

**前置链条**（装不全就是启动直接崩，日志会写清楚缺谁）：

| 平台 | Enchantment Descriptions 需要 |
|---|---|
| 1.20.1（ED 17.1.21） | Balm + **Bookshelf** |
| 1.21.1（ED 21.1.11） | Balm + **Bookshelf [21.1, 21.2)** → 而 Bookshelf 21.x 又要 **Prickle** |
| 26.1.2（ED 26.1.2.6） | 同上（Balm + Bookshelf + Prickle 的 26.1.2 版） |

四个坑：

① **Bookshelf 不是只有 1.20.1 才需要**，21.1.11 的 mods.toml 明确要 `bookshelf [21.1, 21.2)`，
   而 Bookshelf 21.x 自己又依赖 `prickle`——只加 Balm 会依次报 `'bookshelf'`、`'prickle'` 缺失。
   1.20.1 没有 Prickle（无 Forge 版），所以那一边反而只要 Balm + Bookshelf。
② **1.20.1 的 `runtimeOnly` 必须 `fg.deobf(...)`**：模组生产 jar 的 mixin 用 SRG 名
   （如 `@Shadow field f_20889_`），而 dev 环境是 Mojang 名，直接加载会 `InvalidMixinException`。
   NeoForge 两个平台不需要（1.20.2 起运行时就是 Mojang 名）。
③ **Modrinth 的版本号不唯一**：`21.1.11` 同时存在 Fabric 与 NeoForge 两条记录，
   `maven.modrinth:<slug>:<version_number>` 只会取其中一条的文件——1.21.1 的 ED 就这样
   拉成了 Fabric 版（NeoForge 直接不认）。**一律用 version id**（每个加载器唯一），
   `gradle.properties` 里注释对应版本号即可。检查办法：翻 jar 里是 `fabric.mod.json`
   还是 `META-INF/neoforge.mods.toml`。
④ **启动日志里搜 `Found valid mod file <名字>`** 能确认加载的到底是哪条版本；
   联动成功的标志是 `[orefood] 检测到 AppleSkin：...` 那一行。

### 物品外观

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 机制 | `models/item/<路径>.json` 自动发现 | 同左 | `items/<路径>.json` + `ITEM_MODEL` 组件 |
| 贴图 | `textures/item/<路径>.png` | 同左 | 同左 |

**每物品一套模型与贴图**，不再有 `custom_model_data` 分发。

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
- **吃一次扣 1 点耐久、扣完才消失** → `OreFoodConsume#consume` + `OreFoodItem#getMaxDamage`
- **五级升级链** → `data/*/recipe(s)/*_beef.json`（含锻造台那一步）

### 食用结算：三件事

1. **补「饱腹」附魔加成**：基础营养/饱和度由注册时写入的**真实 `FoodProperties`** 经原版结算
   （1.20.1 / 1.21.1 的 `Player#eat` 在进 `LivingEntity#eat` 之前就加了；26.1.2 在
   `Consumable#onConsume` 的 listener 阶段加），mixin 只调 `FoodData#eat(bonus, 1.0F)` 补加成——
   加成饱和度恰为 `bonus × 2`，系数 1.0 三平台都精确。**别把基础值再加一遍，否则翻倍**（踩坑 24）；
2. **触发附魔效果**：读栈上五个附魔等级 → `FoodEnchantmentEffects`；
   定义自带的负面效果（河豚/腐肉）也在代码里结算（`applyDefinitionEffects`），不进 `FoodProperties`；
3. **扣耐久**：一律交给原版 `ItemStack#hurtAndBreak`，别自己写——
   耐久附魔、创造模式守卫、进度触发器都在它内部。

---

## 六、踩坑清单

按「症状 → 原因」排列。**1.2.0 删掉的那些坑不再列出**（语法糖、自定义配料、
JEI 变体、锻造产物 NBT 覆盖、1.21.1 的 Ingredient 缓存——它们随架构一起消失）。

1. **附魔 I 级显示成 II 级** → amplifier 要减 1，见 `FoodEnchantmentRules`。
2. **`OreFoodItem#definitionOf` 返回 null 时不能抛异常**：原版食物与别的模组物品都会走到
   这条路径，取不到定义就交还原版行为。
3. **`hurtAndBreak` 需要物品先「可损坏」**：注册时必须写 `Properties.durability(1)`，
   否则 `isDamageableItem()` 为 false，耐久根本不掉。
4. **KubeJS 物品的食物属性必须以定义为准、覆盖脚本的 `.food(...)`**：否则营养 = 脚本写的值 + 定义值，翻倍。
   构建器里强制覆盖，别改成「尊重脚本」。
5. **NeoForge 平台漏 `#c:foods` 标签 = 附魔静默不可用**（不报错，只是附不上）。
   内置五种写在 `data/c/tags/item/foods.json`；KubeJS 新物品要 `.tag('c:foods')`。
6. **1.20.1 的 KubeJS 依赖少了 rhino 会编译不过**：`RegistryInfo` 继承 rhino 的
   `TypeWrapperFactory`，javac 需要那个类型在场（`rhino-forge`，见第二节）。
7. **dev 环境的 mixin 依赖 `MixinConfigs` 清单**：只设在 jar 任务上会导致 dev 下静默失效，
   见第四节「Mixin 配置」。
8. **1.20.1 打成 jar 后需要 refmap**，缺了正式客户端直接起不来，dev 测不出——见第四节。
9. **`pack.mcmeta` 的 format 声明不对会让数据包不加载**。1.21.1 资源 34 / 数据 48，
   所以用 `supported_formats: [0, 2147483647]` 放宽。
10. **多个附魔的 `max_level` 是手写的**：改 `FoodEnchantment` 时必须同步改三个平台的附魔 JSON。
11. **KubeJS 的 `JsonIO.of(...)` 在 7/8 已经没了**：构建器的 `.definition(...)` 参数用
    `JsonElement`（靠 KubeJS 自己的类型包装把脚本对象转过来），别去用 `JsonIO.of`。
12. **改了 `OreFoodTable` 却没重跑生成器**：模型/配方/标签/语言不会自动更新——
    跑一次第二节的生成器（它会列出写入了哪些文件，缺名字会直接报错）。
13. **不要手改生成器产出的那 250 个 JSON**：标签与语言下次生成会被整份重写。
    要改形状就改生成器里的模板方法（如 `recipe2612`），改完重跑加 `--force`。
14. **26.1.2 的配方材料必须写成纯字符串**（`"minecraft:copper_ingot"`），
    不能像 1.21.1 那样写 `{ "item": ... }`——那边的 Ingredient 只认标签串或元素 id 数组。
15. **26.1.2 的 `Registry#get(Identifier)` 返回 `Optional<Holder.Reference<T>>`**
    （1.20.1 / 1.21.1 是直接给值），按 id 找物品或状态效果时要再取一次 `value()`——
    见 `OreFoodItems#stackOf` 与 `PotionEffectSpec#of`。写惯了 1.21.1 的写法会编译不过。
16. **NeoForge 的 `BuildCreativeModeTabContentsEvent` 对「往本页加物品」有断言，会抛异常**：
    `accept` / `insertAfter` 都要求**该物品还没在本页里**（`assertNewEntryDoesNotAlreadyExists`），
    `insertAfter` 还要求锚点存在（`assertTargetExists`）。2.0 一开始写成「insertAfter 之后再 accept 兜底」，
    结果**一打开物品栏就崩**，而且只有两个 NeoForge 平台崩——1.20.1 的 Forge 没有这两个断言
    （`putAfter` 是覆盖语义）。正确做法：先用 `getParentEntries()` / `getSearchEntries()` 判断锚点在不在，
    只有不在时才 `accept`。
17. **创造栏/客户端界面这类「只能开客户端才能测」的东西，其实能在服务端复现**：
    在 `ServerStartedEvent` 里调一次
    `CreativeModeTabs.tryRebuildTabContents(params.enabledFeatures(), params.hasPermissions(), params.holders())`
    （`params` = `new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, registryAccess)`）
    就会走完整条创造栏构建路径并触发 `BuildCreativeModeTabContentsEvent`——崩溃与插入顺序都能直接看到。
    顺带：`CreativeModeTab#getDisplayItems()` 在 1.21.1 不可用，想看顺序就在事件里遍历
    `getParentEntries()`。

---

18. **NeoForge 的 `Dist` 不在 `net.minecraftforge.api.distmarker` 里**，而是
    `net.neoforged.api.distmarker.Dist`——从 Forge 平台复制客户端代码过去必踩。
19. **`OreFoodEffectSpec` / `OreFoodTable` 在 common 的根包 `com.lyingice.orefood`**，
    不在 `.data` 子包；写 `import com.lyingice.orefood.data.OreFoodEffectSpec` 会「找不到符号」。
20. **26.1.2 编译期拿不到 `@EventBusSubscriber`**，所以三平台统一改用显式
    `modEventBus.addListener(类::方法)`，不依赖注解。
21. **（仅 1.21.1 / 26.1.2）附魔效果数据包的 `duration` 与 `amplifier` 换算不对称**：
    `duration` 用 `per_level × 等级`，`amplifier` 用 `per_level × (等级 - 1)`。
    写错的表现是「I 级附魔给药水 II 级」这种一眼看不出原因的问题。
22. **改附魔数值时两个平台都要动**：1.20.1 在 `FoodEnchantmentRules`（shared），
    1.21.1 / 26.1.2 在各自的附魔 JSON 里。两边数值必须一致——
    改完请对照一遍，`FoodEnchantmentRules` 只是 1.20.1 的来源。
23. **零值食物属性会被第三方模组静默过滤**：精妙背包自动喂食的判定是
    `stack.isEdible() && getFoodProperties(...).getNutrition() >= 1`
    （sophisticatedcore 的 `FeedingUpgradeWrapper.isEdible`，1.21+ 读 `DataComponents.FOOD` 同理），
    AppleSkin 把零值读成 0。注册真实数值才是正解；靠 `FoodValuesEvent` 补值只救得了 AppleSkin，
    救不了背包。
24. **注册真实数值后，食用 mixin 只能补附魔加成**：1.20.1 / 1.21.1 的 `Player#eat`
    在 `super.eat` 之前就加了基础营养，mixin 再把定义值加一遍就翻倍。参考模组 mut 正是踩了这个坑
    （还把绝对饱和度误当系数传给 `eat(int, float)`，又放大 `N×2` 倍）——别照抄它的 mixin。
25. **26.1.2 的加成注入点必须在 `Consumable#onConsume` 的 `stack.consume` 调用之前，不能用 HEAD**：
    基础营养在方法体内的 listener 阶段（`FoodProperties#onConsume`）才加，HEAD 太早——
    饱和度会 clamp 到 foodLevel，加成先加会让接近满饱食度时的结果与另两平台不一致；
    consume 调用前物品栈尚完整，附魔等级可读，这一口恰好损坏也不影响已结算的加成。

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

**光编译过不算数**——本项目的失败模式几乎都是静默的。跑一次开发服务器，核对这几行：

- 启动时五行 `orefood:<name>_beef -> OreFoodDefinition[...]` —— 五种牛排都注册上了、
  数值是想要的（数值现在在代码里，写错编译期就能发现，所以这行主要给「加新口味」用）；
- `[orefood] 食物属性自检：50/50 与定义一致` —— 注册后的 `FoodProperties` 与定义核对
  （1.20.1 容许系数往返的浮点误差，1.21+ 精确相等）；不一致会逐条 ERROR，
  专治「定义对了、注册错了」这种静默错误；
- `Loaded N recipes` 且**没有** `Parsing error loading recipe` / `Couldn't parse`；
- 附魔标签自检 —— 每个附魔实际挂到了哪些标签（NeoForge 平台尤其看 `non_treasure`）；
- 没有 `InvalidInjectionException`。

**装了 KubeJS 时**再补两条：

- 脚本注册的新物品能被 `getAllItemStacks` 之外的方式确认存在（日志 / `/give`）；
- 吃一口确认营养没有翻倍、耐久有掉。

```bash
# 1.21.1
cd neoforge-1.21.1 && ./gradlew runServer
# 1.20.1（首次运行前要在 run/ 里放 eula.txt）
# 26.1.2（需要 Java 25）
cd forge-1.20.1 && ./gradlew runServer
cd neoforge-26.1.2 && ./gradlew runServer
```
