# 矿物牛排 · Ore Food

把矿物做成能吃的牛排：**一个物品**，数值全部由数据包定义；**五个食物附魔**，
吃下去按附魔等级给你药水效果；吃完**不掉物品、只掉耐久**。

一份共享逻辑 + 三个平台独立构建：

| 模块 | 加载器 | MC 版本 | Java | 附魔怎么注册 |
|---|---|---|---|---|
| `forge-1.20.1` | Forge 47.x | 1.20.1 | 17 | **代码**静态注册 |
| `neoforge-1.21.1` | NeoForge 21.1.x | 1.21.1 | 21 | **数据包** JSON |
| `neoforge-26.1.2` | NeoForge 26.1.2.x | 26.1.2 | 25 | **数据包** JSON |

---

## 一、特性

### 1. 食物附魔

五个附魔都**只对食物生效**，吃下带附魔的食物时在服务端结算。
数值公式集中在共享层 `common/.../FoodEnchantmentRules.java`，三个版本共用同一份：

| 附魔 | id | 最大等级 | 效果 | 时长 |
|---|---|---|---|---|
| 强效恢复 | `orefood:powerful_recovery` | 4 | 瞬间治疗 + 生命恢复 | 生命恢复 `等级 × 100` tick |
| 肾上腺素 | `orefood:adrenaline` | 3 | 力量 + 速度 | 力量 `等级 × 100`、速度 `等级 × 200` tick |
| 强身健体 | `orefood:fitness` | 5 | 生命吸收 + 抗性提升 | 两者均 `等级 × 400` tick |
| 饱腹 | `orefood:satiety` | 3 | 额外饱食度 | 永久（吃下即加） |
| 盛宴 | `orefood:feast` | 3（**宝藏**） | 饱和效果 | `等级 × 1 分钟` |

几条容易搞错、已按需求固定下来的规则：

- **附魔 I 级对应药水 amplifier 0**（游戏内显示「I 级」）。换算见
  `FoodEnchantmentRules#potionAmplifier`——`amplifier = 等级 - 1`。
- **抗性提升固定 amplifier 1**，即游戏内「抗性提升 II」＝ 40% 减伤，
  **不随附魔等级变化**；同一附魔的生命吸收才跟随等级。
- **饱腹**追加 `等级 × 2` 点饱食度，饱和度按原版换算同步补上
  （原版规则：饱和度 = 饱食度 × 系数 × 2）。
- **盛宴是宝藏附魔**：1.21.1 / 26.1.2 通过 `#minecraft:treasure` 标签声明；
  1.20.1 通过覆写 `isTreasureOnly()` 声明。

### 2. 唯一的物品 + 数据包驱动的牛排

模组本体**只注册一个物品** `orefood:ore_food`。铁/金/钻石/下界合金牛排
都不是独立物品，而是「同一个物品 + 不同的定义」：

| 牛排 | 定义 id | 耐久 | 附魔能力 | 饱食度 | 饱和度 |
|---|---|---|---|---|---|
| 铁牛排 | `orefood:iron_steak` | 16 | 1 | 8 | 12.8 |
| 金牛排 | `orefood:gold_steak` | 32 | 5 | 8 | 16.0 |
| 钻石牛排 | `orefood:diamond_steak` | 64 | 10 | 10 | 20.0 |
| 下界合金牛排 | `orefood:netherite_steak` | 128 | 15 | 12 | 24.0 |

「哪种牛排」由物品栈自己携带：

- 1.21.1 / 26.1.2：数据组件 `orefood:definition`（存完整定义）；
- 1.20.1：NBT 复合标签 `orefood`（同样是完整定义，用同一个 Codec + `NbtOps` 读写）。

> 因为定义是**随物品栈走**的，所以改数据包不会追溯已经发出去的物品——
> 这是原版物品组件的正常语义。

### 3. 吃的是耐久，不是物品

Mixin 把「食用完成时消耗 1 个物品」改成「扣 1 点耐久」：

- 耐久耗尽 → 这一份吃掉就没了；
- 定义里 `durability: 0` → **无限耐久**，吃多少次都不掉；
- 创造模式仍然不消耗（原版守卫随重定向失效，代码里手工补回）。

物品因此强制不可堆叠（`stacksTo(1)`）——带耐久的物品堆叠会让整堆共享一份耐久，语义混乱。

### 4. 提示框

拿着带附魔的牛排时，物品提示框里会像原版药水那样逐条列出
「会得到什么效果、几级、多久」。渲染直接复用原版的
`PotionUtils#addPotionTooltip`（1.20.1）/ `PotionContents#addPotionTooltip`（1.21.1+），
所以效果名与罗马数字等级无需任何自定义语言键。

---

## 二、用数据包自定义

定义放在**动态注册表** `orefood:ore_food` 下，路径是
`data/<数据包命名空间>/orefood/ore_food/<名字>.json`（注意命名空间出现两次，
这是前向命名空间（`prefixNamespace`）规则决定的）。

```json
{
  "nutrition": 8,
  "saturation": 12.8,
  "durability": 16,
  "enchantment_value": 1,
  "effects": [
    { "effect": "minecraft:regeneration", "duration": 100, "amplifier": 0, "probability": 1.0 }
  ]
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `nutrition` | 否 | 饱食度，默认 0 |
| `saturation` | 否 | 饱和度（**最终值**，不是系数），默认 0 |
| `durability` | 否 | 最大耐久；**0 = 无限耐久**，默认 0 |
| `enchantment_value` | 否 | 附魔能力，默认 0 |
| `effects` | 否 | 食用后附带的状态效果，默认空 |

`effects` 里每一项：`effect`（效果 id）、`duration`（tick，默认 200）、
`amplifier`（默认 0）、`probability`（0~1，默认 1.0）。

新增一个条目后，它会**自动出现在创造模式「食物与饮品」页**里，
显示名取语言键 `item.<命名空间>.<名字>`。

### 标签

| 标签 | 1.20.1 | 1.21.1 / 26.1.2 |
|---|---|---|
| 通用食物标签 | `forge:food`（**本模组自建**） | `c:foods`（NeoForge 提供，本模组追加） |
| 标签目录 | `data/<ns>/tags/`**`items`** | `data/<ns>/tags/`**`item`** |

> ⚠️ 目录名 `items` / `item` 的单复数差异是**静默失败**：写错了标签根本不加载，
> 而且不会报错。这也是为什么三个平台的数据包**不能共用一份**。

1.20.1 上并没有官方定义的「所有食物」标签——`forge:food` 是社区约定，
NeoForge 自己的 `TagConventionLogWarning` 就把它列为「常用但不符约定」。
所以 1.20.1 侧「只对食物生效」实际是靠
`EnchantmentCategory.create(..., item -> item.isEdible())` 判定的，
比任何一个标签都更全；`forge:food` 标签只是为了让依赖它的其它模组能认出本模组的牛排。

---

## 三、构建

三个模块对 Gradle 的要求互不兼容（ForgeGradle 6 → 8.8，ModDevGradle 1.21.1 → 8.14.2，
26.1.2 → 9.1.0），因此根构建**刻意不使用 composite build**，只提供转调任务：

```bash
./gradlew buildAll          # 依次构建三个平台
./gradlew buildForge        # 只构建 Forge 1.20.1
./gradlew buildNeoForge     # 只构建 NeoForge 1.21.1
./gradlew buildNeoForge2612 # 只构建 NeoForge 26.1.2（首次会下载 Java 25）
```

日常开发请直接进到模块目录里跑：

```bash
cd forge-1.20.1    && ./gradlew build runClient
cd neoforge-1.21.1 && ./gradlew build runClient
cd neoforge-26.1.2 && ./gradlew build runClient
```

`common/` 没有自己的构建文件，它由三个平台各自
`sourceSets.main.java.srcDir '../common/src/main/java'` 直接编译进 jar。
因此**共享层只能放纯 JDK 代码**——一旦 import 游戏类，三个平台里只要有一个缺符号就全崩。
本模组放进共享层的是「有哪五个附魔、各自几级、数值怎么算」（`FoodEnchantment`、
`FoodEnchantmentRules`），其余全部留在各平台模块。

---

## 四、阅读源码：各版本到底差在哪

`tools/` 下有脚本可以把三个版本的 MC 源码取到本地检索对照
（产物写进 `.mc-src/`，已在 `.gitignore` 里）：

```bash
pwsh tools/extract-mc-src.ps1          # 1.21.1 / 26.1.2：直接从 Gradle 缓存解压
pwsh tools/decompile-forge-1.20.1.ps1  # 1.20.1：ForgeGradle 只缓存 class，需自行反编译
```

`docs/research/` 下有五份逐条带 `文件:行号` 证据的对照报告，
动手改代码前值得先读。下面是踩过的关键差异。

### 附魔

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 形态 | `abstract class Enchantment` | `record Enchantment` | 同 1.21.1 |
| 注册 | `DeferredRegister` + `ForgeRegistries.ENCHANTMENTS` | 数据包 `data/<ns>/enchantment/*.json` | 同 1.21.1 |
| 「只对食物生效」 | `EnchantmentCategory.create(..., Item::isEdible)` | `"supported_items": "#c:foods"` | 同 1.21.1 |
| 宝藏 | 覆写 `isTreasureOnly()` | `#minecraft:treasure` 标签 | 同 1.21.1 |
| 等级读取 | `EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)` | `stack.getEnchantmentLevel(Holder<Enchantment>)` | 同 1.21.1 |

> **1.21.1 / 26.1.2 没有任何一个附魔效果组件以「物品被吃下」为触发点**
> （30 / 31 个组件里，唯一沾边的 `minecraft:tick` 只扫装备槽位）。
> 所以「吃下时生效」这件事三个版本都只能写代码——本模组统一在 Mixin 的吃入口里结算。

### 食用流程

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 入口 | `Player#eat` → `LivingEntity#eat(Level, ItemStack)` | `Player#eat` → `LivingEntity#eat(Level, ItemStack, FoodProperties)` | `Consumable#onConsume(Level, LivingEntity, ItemStack)` |
| 消耗点 | `ItemStack#shrink(1)` | `ItemStack#consume(1, this)` | 同 1.21.1，但在 `Consumable#onConsume` 里 |
| 加营养 | `FoodData#eat(Item, ItemStack, LivingEntity)`，第二参是**饱和度系数** | `FoodData#eat(FoodProperties)` | `FoodProperties#onConsume` → `FoodData#eat(this)` |
| `LivingEntity#eat` | 存在 | 存在（二参重载是 `final`，注入三参的） | **已被整个删除** |

### Mixin 配置（这个最容易踩）

- **Forge 1.20.1 的 `mods.toml` 不支持 `[[mixins]]`**——写了会被**静默忽略**。
  必须在 JAR 清单里写 `MixinConfigs` 属性（见 `forge-1.20.1/build.gradle` 的 `jar` 段）。
- **NeoForge 两个版本相反**：必须在 `neoforge.mods.toml` 里写 `[[mixins]] config="..."`。

### 其它破坏性差异

- 26.1.2 把 `ResourceLocation` 改名为 `Identifier`（包路径不变），
  `ResourceKey#location()` 改名 `identifier()`，`level.isClientSide` 变成方法 `isClientSide()`。
- 26.1.2 的四个药水效果常量改名：`MOVEMENT_SPEED`→`SPEED`、`DAMAGE_BOOST`→`STRENGTH`、
  `HEAL`→`INSTANT_HEALTH`、`DAMAGE_RESISTANCE`→`RESISTANCE`。
- 26.1.2 的附魔能力从 `Item#getEnchantmentValue` 改成 `minecraft:enchantable` 数据组件。
- 26.1.2 的物品模型要额外的 `assets/<ns>/items/<id>.json` 指向真正的模型
  （1.21.4 起的新格式；`models/item/<id>.json` 仍然需要）。
- 三个版本的 `pack.mcmeta` 规则不同：1.20.1 资源/数据同为 15；1.21.1 资源 34、数据 48，
  因此用 `supported_formats: [0, 2147483647]` 放宽；26.1.2 用 `min_format` / `max_format`。

---

## 五、贴图

`common/src/main/resources/assets/orefood/textures/item/ore_food.png` 是
由 `tools/GenerateOreFoodTexture.java` **用代码画出来的**（16×16，可复现、可审阅），
仓库不携带任何二进制美术资源。想换风格改脚本里的配色与形状即可：

```bash
java -Djava.awt.headless=true tools/GenerateOreFoodTexture.java \
     common/src/main/resources/assets/orefood/textures/item/ore_food.png iron
```

可选色调：`iron` / `gold` / `diamond` / `netherite`。

> 目前四种牛排**共用**同一张贴图与模型（毕竟物品只有一个）。
> 想做成一牛一式，可在数据包条目里加模型字段，再用 `ITEM_MODEL`（26.1.2）
> 或 `CustomModelData`（1.20.1 / 1.21.1）区分。

---

## 六、许可

代码可自由使用。`mod_license` 当前为 All Rights Reserved，发布前请换成你自己的授权协议。
