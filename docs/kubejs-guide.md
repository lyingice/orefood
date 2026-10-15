# 用 KubeJS 加一种矿物牛排

1.2.0 起，五种内置牛排各自是一个**真实物品**（`orefood:copper_beef` … `orefood:netherite_beef`）。
你可以在**不改模组**的前提下，用 KubeJS 注册自己的矿物牛排物品——它会直接继承全部行为：
按定义加营养、吃一口扣耐久、附魔效果与提示框、按定义给耐久上限与附魔能力。

> 前提：装了 KubeJS（1.20.1 用 KubeJS 6；1.21.1 / 26.1.2 用 KubeJS 7 / 8）。
> 没装也能正常玩，只是加不了新口味。

---

## 一、最小例子

`kubejs/startup_scripts/redstone_beef.js`：

```js
StartupEvents.registry('item', event => {
  event.create('mypack:redstone_beef', 'orefood:ore_food_item')
    .definition({
      nutrition: 6,           // 饱食度
      saturation: 9.6,        // 饱和度
      durability: 12,         // 能吃几次（耐久）
      enchantment_value: 2,   // 附魔能力
      effects: []             // 食用后附带的状态效果，可省略
    })
    .tag('c:foods')           // NeoForge 平台必写，见第四节
    .texture('mypack:item/redstone_beef')
})
```

物品注册是**启动期**的事，所以脚本要放在 `startup_scripts/`（不是 `server_scripts/`）。

---

## 二、`.definition({...})` 的字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `nutrition` | 整数 | 饱食度（吃一次回多少格） |
| `saturation` | 小数 | 饱和度 |
| `durability` | 整数 | **能吃几次**；`0` 表示没有耐久设定（不掉耐久、也不消耗本体） |
| `enchantment_value` | 整数 | 附魔能力（内置：铜/铁 1、金 5、钻石 10、下界合金 15） |
| `effects` | 数组 | 食用后附带的状态效果，可省略 |

`effects` 每一项：

```js
effects: [
  { effect: 'minecraft:regeneration', duration: 100, amplifier: 0, probability: 1.0 }
]
```

`duration` 单位是 tick（20 tick = 1 秒），`amplifier: 0` 对应游戏里显示的 I 级。
这些字段名与 1.1.x 数据包时代的定义 JSON **完全一致**，从旧数据包迁移过来可以照抄。

---

## 三、你会自动得到什么

- **吃**：按定义加营养与饱和度（物品自身的食物属性被模组强制为零值，不会翻倍）；
- **耐久**：每吃一口扣 1 点，扣完这一块才消失（`durability` 就是能吃几次）；
- **附魔**：本模组的五个食物附魔可以附在它上面，吃下即触发效果；
- **提示框**：拿着它悬停，会像原版药水那样列出当前附魔与定义里声明的效果；
- **数值**：耐久上限、附魔能力都按你的定义走，而不是物品默认值。

名字用原版的物品语言键：`item.<命名空间>.<路径>`，例如 `item.mypack.redstone_beef`。
模型与贴图按 KubeJS 的常规做法给（`.texture(...)` 等），与本模组无关。

---

## 四、平台差异（只有两条）

| | 1.20.1（Forge） | 1.21.1 / 26.1.2（NeoForge） |
|---|---|---|
| 物品类型名 | `orefood:ore_food_item` | 同左 |
| 附魔前提 | **自动满足**（本模组的附魔只要求「物品能吃」） | **必须**把物品加进 `#c:foods` 标签，即脚本里写 `.tag('c:foods')`；否则附魔台刷不出、也附不上 |

NeoForge 上忘了打这个标签**不会报错**，只是附魔静默不可用——这是最容易踩的一条。

---

## 五、三条约束（模组已经替你挡掉，但值得知道）

1. **不要给它写 `.food(...)`**：营养由 `definition` 负责；跟着脚本的食物属性走会**翻倍**，
   所以模组在构建器里强制覆盖成零值；
2. **不要试图让它可堆叠**：带耐久的物品堆叠会让整堆共享一份耐久，语义混乱；
3. **`durability: 0` 是特殊值**：表示「没有耐久设定」——吃多少次都不掉耐久，也不会消耗本体。

---

## 六、想把它做成模组内置的一部分？

那就不需要 KubeJS：在 `OreFoodItems` 里加一个定义常量与一次 `register(...)` 调用即可，
数值写法与 `.definition({...})` 完全对应（见 `OreFoodItems` 里的五个常量）。

---

## 七、排查

- **脚本没生效**：看 `logs/kubejs/startup.log`，KubeJS 的脚本报错很具体（连行号都给）；
- **`.definition(...)` 报解析失败**：异常信息里会带上你写的那段 JSON，按字段名核对；
- **物品能拿到但没名字/没贴图**：那是 KubeJS 的资源侧问题（语言文件 / `.texture`），与本模组无关。
