# 用数据包给矿物牛排加新口味

本模组的牛排**不是硬编码的**：模组本体只注册了一个物品 `orefood:ore_food`，
「铜 / 铁 / 金 / 钻石 / 下界合金」这五种牛排都是**数据包**通过一张自定义注册表声明出来的。
所以你不用改代码、不用重新打包模组，写个数据包就能加出自己的矿物食物。

下面用一个完整的例子走一遍：**红石牛排**——比铁牛排顶饱，吃下去还会给你一小段急迫。

---

## 一、数据包放哪里

| 平台 | 位置 |
|---|---|
| 存档专用 | `<存档目录>/datapacks/<你的包名>/` |
| 全局 | `<游戏目录>/config/...` 或整合包自带的数据包目录 |

一个能用的数据包最小结构是这样（`pack.mcmeta` 是必需的）：

```
redstone_beef_pack/
├── pack.mcmeta
├── data/
│   └── mypack/                      ← 数据包自己的命名空间，随便起
│       └── orefood/
│           └── ore_food/
│               └── redstone_beef.json
└── assets/                          ← 只有要自定义贴图时才需要
    └── mypack/
```

> **注意路径里 `orefood` 出现了两次**：前面那个 `orefood` 是**注册表的命名空间**，
> 后面 `ore_food` 是注册表名。规则是「命名空间不是 `minecraft` 时，要在注册表名前再拼一次命名空间」。
> 写错这一层，本模组**不会报错**，只是默默什么也没加载——这是最容易卡住的地方。

`pack.mcmeta`（1.21.1 用的写法，`pack_format` 按你的游戏版本填）：

```json
{
  "pack": {
    "pack_format": 48,
    "description": "红石牛排"
  }
}
```

---

## 二、定义一块红石牛排

新建 `data/mypack/orefood/ore_food/redstone_beef.json`：

```json
{
  "id": "mypack:redstone_beef",
  "nutrition": 10,
  "saturation": 14.0,
  "durability": 48,
  "enchantment_value": 8,
  "model_data": 6,
  "effects": [
    {
      "effect": "minecraft:haste",
      "duration": 600,
      "amplifier": 1,
      "probability": 1.0
    }
  ]
}
```

进游戏、重进一次世界（动态注册表**不响应 `/reload`**），红石牛排就会出现在
创造模式「食物与饮品」页里，名字取语言键 `item.mypack.redstone_beef`。

### 每个字段是什么意思

| 字段 | 默认值 | 说明 |
|---|---|---|
| `id` | 无 | 这份定义自己的 id。**想让配方引用它就必须填**（见第五节） |
| `nutrition` | 0 | 饱食度（鸡腿图标数的一半，与原版一致） |
| `saturation` | 0 | 饱和度。**这里填最终值**，不是原版食谱里那个「饱和度系数」。原版换算：饱和度 = 饱食度 × 系数 × 2 |
| `durability` | 0 | 还能吃几次。**0 = 没有耐久设定**：既不消耗耐久，也不消耗本体——用不掉（参考马铠） |
| `enchantment_value` | 0 | 附魔能力。**0 表示附魔台附不了**（铁是 1、钻石是 10、下界合金是 15） |
| `model_data` | 0 | 外观选择，见下文第四节。0 = 用默认模型 |
| `effects` | 空 | 吃下后附带的状态效果，可以写多条 |

### `effects` 里每一项

| 字段 | 默认值 | 说明 |
|---|---|---|
| `effect` | 必填 | 状态效果 id，如 `minecraft:haste`、`minecraft:regeneration` |
| `duration` | 200 | 持续多少 **tick**（20 tick = 1 秒）。上面例子里 600 tick = 30 秒 |
| `amplifier` | 0 | 效果等级。**0 就是游戏内显示的 I 级**，1 是 II 级 |
| `probability` | 1.0 | 施加概率，0~1。写 0.5 就是一半机会 |

举个例子，想要「吃下去有 30% 概率给 10 秒抗火」：

```json
{
  "nutrition": 6,
  "effects": [
    { "effect": "minecraft:fire_resistance", "duration": 200, "probability": 0.3 }
  ]
}
```

`amplifier` 想要 II 级就写 1，III 级写 2，以此类推。

> 这里的效果**不吃附魔加成**，是写死的。想要「随附魔等级变化」的效果，
> 那是本模组那五个附魔的活儿，不是数据包能表达的。

---

## 三、给它一个名字

数据包（或资源包）里加语言文件 `assets/mypack/lang/zh_cn.json`：

```json
{
  "item.mypack.redstone_beef": "红石牛排"
}
```

键的规则是 `item.<数据包命名空间>.<定义文件名>`。
没有这个名字的话，物品会在提示框里显示成一长串原始 id。

---

## 四、给它一个外观（可选）

默认情况下所有没指定外观的牛排都用同一张图。想让它长得不一样，有两种做法，
**取决于你的游戏版本**。

### 通用做法：`model_data` + 覆盖（1.20.1 / 1.21.1）

1. 把贴图放进资源包：`assets/mypack/textures/item/redstone_beef.png`（16×16 PNG）；
2. 加一个模型 `assets/mypack/models/item/redstone_beef.json`：

   ```json
   {
     "parent": "minecraft:item/generated",
     "textures": { "layer0": "mypack:item/redstone_beef" }
   }
   ```

3. 在**本模组**的默认模型上追加一条覆盖规则——这一步需要覆盖
   `assets/orefood/models/item/ore_food.json`（资源包会覆盖模组自带的同名文件），
   在它的 `overrides` 数组里加一条：

   ```json
   { "predicate": { "custom_model_data": 6 }, "model": "mypack:item/redstone_beef" }
   ```

   这里的 `6` 就是上面定义里写的 `"model_data": 6`。两边对上即可。

### NeoForge 26.1.2：`ITEM_MODEL` 组件

26.1.2（以及所有 1.21.4 之后的版本）有原生的物品模型组件，**本模组会按定义 id 自动找模型**，
所以只要放一个文件就行，连 `model_data` 都不用写：

`assets/mypack/items/redstone_beef.json`

```json
{
  "model": {
    "type": "minecraft:model",
    "model": "mypack:item/redstone_beef"
  }
}
```

外加上面那个 `models/item/redstone_beef.json` 和贴图。

> 简单说：**定义 id 是 `mypack:redstone_beef`**，
> 26.1.2 会自动去找 `assets/mypack/items/redstone_beef.json`；
> 而 1.20.1 / 1.21.1 没有这个组件，只能靠 `model_data` 的数字 + 覆盖列表。
> 两边都想要的话，两套资源都提供即可，数据包里 `model_data` 照填不影响 26.1.2。

---

## 五、让它能合成（可选）

> 这一节只在你想**用红石牛排当材料或产物**时才需要。

### 直接写定义 id

产物和配料两处都可以**直接写定义 id**，加载时会自动展开成原版写法。

`data/mypack/recipe/redstone_beef.json`（1.20.1 请把目录改成 `recipes/`）：

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "group": "ore_food",
  "key": {
    "#": { "item": "minecraft:redstone_block" },
    "X": { "item": "orefood:diamond_beef" }
  },
  "pattern": [ "###", "#X#", "###" ],
  "result": { "id": "mypack:redstone_beef" }
}
```

`"X": { "item": "orefood:diamond_beef" }` 和 `"result": { "id": "mypack:redstone_beef" }`
里写的都是**第一节那个 `id` 字段**，不是物品 id——`orefood:diamond_beef` 根本不在物品
注册表里。展开由本模组在配方**反序列化之前**完成。

**为什么需要这套**：五种牛排是同一个物品（`orefood:ore_food`），只有组件不同，而
**原版配料的匹配语义只比物品类型**——`orefood:ore_food` 会同时接受铜牛排、钻石牛排、
任意一种，配方会互相冲突。展开时配料会被换成按定义 id 匹配的自定义配料，产物会被换成
「真实物品 + 定义组件」。

**名字和外观不用你操心**：展开出的产物与创造栏里的物品是同一套组件（显示名、
外观、附魔能力都在），你只写定义 id 就够了。

### 配料与产物写法：三个版本不一样

原版配料的 JSON 格式在 26.1.2 变了，所以**同一个 `key` 不能三平台通用**：

| 版本 | 原版配料 | 定义 id |
|---|---|---|
| 1.20.1 / 1.21.1 | `{ "item": "minecraft:iron_ingot" }` | `{ "item": "orefood:diamond_beef" }` |
| 26.1.2 | `"minecraft:iron_ingot"` | `"orefood:diamond_beef"` |

26.1.2 的 `Ingredient` 内部换成了 `HolderSet<Item>`，只认「`#标签` 字符串」或「元素 id
数组」，对象形式 `{"item": ...}` 会**解析失败**——连原版物品也一样失败。

产物同理：

| 版本 | 产物 |
|---|---|
| 1.20.1 | `{ "item": "mypack:redstone_beef" }` |
| 1.21.1 / 26.1.2 | `{ "id": "mypack:redstone_beef" }` |

### 几个坑

- **`id` 字段必须填**，否则没法被配方引用；
- **配方目录单复数**：1.20.1 是 `recipes/`，1.21 起是 `recipe/`；
- **只有能查到定义的那个 id 会被展开**：`minecraft:iron_ingot` 这类真实物品 id 一律
  原样放过，所以本机制不可能影响别的模组的配方；
- **报错信息里看到的是展开后的内容**：如果定义 id 拼错了，原版会告诉你
  `Unknown item 'mypack:redstone_beef'`，而不是提示你「这不是物品」。

---

## 六、把它加进食物标签

本模组的五个附魔只能附在**能吃的物品**上，而判断依据是标签：

| 平台 | 标签 id | 标签文件路径 |
|---|---|---|
| 1.20.1 | `forge:food` | `data/forge/tags/`**`items`**`/food.json` |
| 1.21.1 / 26.1.2 | `c:foods` | `data/c/tags/`**`item`**`/foods.json` |

内容都是：

```json
{
  "values": [
    "orefood:ore_food"
  ]
}
```

> 本模组的矿物食品**已经**在这两个标签里了，所以你新加的牛排（也是 `orefood:ore_food`）
> 自动就能被附魔，**这一步通常不用你做**。只有当你在做整合包、
> 需要其它模组的食物也吃本模组的附魔时，才需要往这些标签里加东西。
>
> ⚠️ 注意目录名的单复数：1.20.1 是 `tags/`**`items`**，1.21 起是 `tags/`**`item`**。
> 写错了标签会**静默失效**，游戏不会报任何错。

---

## 七、几个一定会踩的坑

1. **改完要重进世界。** 动态注册表只在世界加载时读一次，`/reload` 不会重读。
2. **注册表变空不会报错。** 路径写错、JSON 语法错、字段类型不对，
   结果都是「什么都没加载」，而不是一个红色报错。用日志确认：
   服务器启动时会打印一行 `Loaded N ore food definition(s): [...]`，
   N 不对就是没读到。
3. **`durability` 是「吃几次」不是「用多久」。** 写 16 就是能吃 16 次。
4. **牛排不可堆叠。** 带耐久的物品堆叠起来语义混乱，所以本模组强制一格一块。
5. **`model_data` 只影响 1.20.1 / 1.21.1。** 26.1.2 走定义 id 找模型，忽略这个字段。
6. **数字别写成字符串。** `"durability": "16"` 会让整个条目解析失败。
