# 04 · 三版本「物品提示框（tooltip）事件」与「状态效果文本格式化」调研报告

> 调研对象：`.mc-src/1.20.1/`、`.mc-src/1.21.1/`、`.mc-src/26.1.2/` 三份反编译源码。
> 所有结论均给出 `文件路径:行号` 证据；逐字贴出的代码来自源码树本身，未改写。
> 版本标识经 `.mc-src/26.1.2/version.json`、`.mc-src/26.1.2/net/neoforged/neoforge/common/version.properties`、
> `.mc-src/1.20.1/META-INF/MANIFEST.MF`（`Git-Branch: 1.20.1`）与各模块 `gradle.properties` 核实：
> **1.20.1 = Forge 47.4.5 / 1.21.1 = NeoForge 21.1.250 / 26.1.2 = NeoForge 26.1.2.109（MC 26.1.2，`Identifier` 取代 `ResourceLocation`）**。

---

## 0. 先看这里：六个会让人写错代码的结论

| # | 结论 | 证据 |
|---|---|---|
| 1 | **`ChatFormatting` 三个版本的包名完全一样，都是 `net.minecraft.ChatFormatting`**。它**不是** 26.1.2 的新变化，也从来不在 `net.minecraft.network.chat` 里。 | `.mc-src/1.20.1/net/minecraft/ChatFormatting.java:1`、`.mc-src/1.21.1/net/minecraft/ChatFormatting.java:1`、`.mc-src/26.1.2/net/minecraft/ChatFormatting.java:1` 三处均为 `package net.minecraft;`；`.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:23` = `import net.minecraft.ChatFormatting;` |
| 2 | `ItemTooltipEvent` **三版本都没改名、没改包**（只有构造器多了一个 `TooltipContext`），且**都在游戏总线（game bus）上**，不在 mod 总线上。 | `net.minecraftforge.event.entity.player.ItemTooltipEvent` / `net.neoforged.neoforge.event.entity.player.ItemTooltipEvent` |
| 3 | **1.20.1 的 `MobEffectInstance.getEffect()` 返回 `MobEffect`**（裸对象）；1.21.1 / 26.1.2 返回 **`Holder<MobEffect>`**，必须 `.value()`。 | `1.20.1/.../MobEffectInstance.java:160` vs `1.21.1/...:199`、`26.1.2/...:192` |
| 4 | **"力量"在 1.20.1 与 1.21.1 叫 `DAMAGE_BOOST`，只有 26.1.2 才叫 `STRENGTH`**；"抗性提升"1.20.1/1.21.1 叫 `DAMAGE_RESISTANCE`，26.1.2 叫 `RESISTANCE`；"瞬间治疗"1.20.1/1.21.1 是 `HEAL`，26.1.2 是 `INSTANT_HEALTH`；"速度"同理 `MOVEMENT_SPEED`→`SPEED`。 | 见 §6 常量表 |
| 5 | **26.1.2 的 `Item#appendHoverText` 已 `@Deprecated` 且签名大改**：`(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`——`List<Component>` 变成了 `Consumer<Component>`，还多了 `TooltipDisplay`。 | `.mc-src/26.1.2/net/minecraft/world/item/Item.java:341-343` |
| 6 | **`MobEffectUtil.formatDuration` 在 1.21.1 就已经是 3 参（带 `ticksPerSecond`）**，不是 26.1.2 才变的；1.20.1 是 2 参。 | `1.20.1/.../MobEffectUtil.java:15` vs `1.21.1/...:16`、`26.1.2/...:16` |

**另外两个环境性提醒（会影响检索本身）**

- `.mc-src/` 在 `.gitignore` 里，所以**在仓库根目录用 ripgrep 全局搜会静默跳过整个 `.mc-src/`**——必须显式把 `path` 指到 `.mc-src/<版本>` 或其子目录。
- 原版语言文件 `assets/minecraft/lang/en_us.json` **只有 26.1.2 的源码树里有**；1.20.1 树只有 `assets/forge/lang/en_us.json`，1.21.1 树只有 `assets/{c,neoforge}/lang/en_us.json`。所以"II""No Effects"这些**字面文本只能在 26.1.2 引用**（其余版本只能给出 key 名）。这同时说明：**这些 key 是原版提供的，三个版本通用，我们自己不需要写 lang 条目。**

---

## 1. `ItemTooltipEvent` 三版本对照

### 1.1 Forge 1.20.1

**全类名**：`net.minecraftforge.event.entity.player.ItemTooltipEvent`
**源文件**：`.mc-src/1.20.1/net/minecraftforge/event/entity/player/ItemTooltipEvent.java`

```java
// .mc-src/1.20.1/net/minecraftforge/event/entity/player/ItemTooltipEvent.java:11-41（全文）
public class ItemTooltipEvent extends PlayerEvent {
    private final TooltipFlag flags;
    @NotNull
    private final ItemStack itemStack;
    private final List<Component> toolTip;

    public ItemTooltipEvent(@NotNull ItemStack itemStack, @Nullable Player player, List<Component> list, TooltipFlag flags) {
        super(player);
        this.itemStack = itemStack;
        this.toolTip = list;
        this.flags = flags;
    }

    public TooltipFlag getFlags() { return this.flags; }

    @NotNull
    public ItemStack getItemStack() { return this.itemStack; }

    public List<Component> getToolTip() { return this.toolTip; }

    @Nullable
    @Override
    public Player getEntity() { return super.getEntity(); }
}
```

| 方法 | 签名 | 行号 |
|---|---|---|
| 构造器 | `ItemTooltipEvent(@NotNull ItemStack itemStack, @Nullable Player player, List<Component> list, TooltipFlag flags)` | `:17` |
| `getToolTip()` | `public List<Component> getToolTip()` | `:33` |
| `getItemStack()` | `@NotNull public ItemStack getItemStack()` | `:29` |
| `getEntity()` | `@Nullable public Player getEntity()`（`PlayerEvent` 重写） | `:39` |
| `getFlags()` | `public TooltipFlag getFlags()` | `:24` |

**没有** `getContext()`。
**事件总线：游戏总线（`MinecraftForge.EVENT_BUS`）**，不是 mod 总线：

```java
// .mc-src/1.20.1/net/minecraftforge/event/ForgeEventFactory.java:272-276
public static ItemTooltipEvent onItemTooltip(ItemStack itemStack, @Nullable Player entityPlayer, List<Component> list, TooltipFlag flags) {
    ItemTooltipEvent event = new ItemTooltipEvent(itemStack, entityPlayer, list, flags);
    MinecraftForge.EVENT_BUS.post(event);
    return event;
}
```

触发点：`ItemStack.getTooltipLines` 的**最后一行**——`ForgeEventFactory.onItemTooltip(this, p_41652_, list, p_41653_);`（`.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:791`）。
`getTooltipLines` 签名：`public List<Component> getTooltipLines(@javax.annotation.Nullable Player p_41652_, TooltipFlag p_41653_)`（`:616`）。

**注册方式（游戏总线监听）**：

```java
// 1.20.1：拿到 forge 总线后注册
MinecraftForge.EVENT_BUS.addListener(MyTooltips::onItemTooltip);
// 或用注解：@Mod.EventBusSubscriber(modid = ..., bus = Mod.EventBusSubscriber.Bus.FORGE)
//          + @SubscribeEvent public static void onItemTooltip(ItemTooltipEvent e)
```

> 注意：1.20.1 的 `EventBusSubscriber.java` / `DistExecutor.java` 定义本身**不在**该源码树里（`net/minecraftforge/fml/` 下只有 `core/` 与 `event/{config,lifecycle}/`），所以这两个类的签名无法逐字引用；但**用法**在树内有大量实例，见 §7。

### 1.2 NeoForge 1.21.1

**全类名**：`net.neoforged.neoforge.event.entity.player.ItemTooltipEvent`
**源文件**：`.mc-src/1.21.1/net/neoforged/neoforge/event/entity/player/ItemTooltipEvent.java`

```java
// .mc-src/1.21.1/net/neoforged/neoforge/event/entity/player/ItemTooltipEvent.java:17-70（节选，构造器与全部 getter）
public class ItemTooltipEvent extends PlayerEvent {
    private final TooltipFlag flags;
    private final ItemStack itemStack;
    private final List<Component> toolTip;
    private final TooltipContext context;

    /**
     * This event is fired in {@link ItemStack#getTooltipLines(TooltipContext, Player, TooltipFlag)}, which in turn is called from its respective GUIContainer.
     * Tooltips are also gathered with a null player during startup by {@link Minecraft#createSearchTrees()}.
     */
    public ItemTooltipEvent(ItemStack itemStack, @Nullable Player player, List<Component> list, TooltipFlag flags, TooltipContext context) {
        super(player);
        this.itemStack = itemStack;
        this.toolTip = list;
        this.flags = flags;
        this.context = context;
    }

    public TooltipFlag getFlags() { return flags; }               // :38
    public ItemStack getItemStack() { return itemStack; }         // :45
    public List<Component> getToolTip() { return toolTip; }       // :52

    @Override
    @Nullable
    public Player getEntity() { return super.getEntity(); }       // :61

    /** The {@link TooltipContext tooltip context}. */
    public TooltipContext getContext() { return context; }        // :68
}
```

**与 Forge 1.20.1 的差异**：多出第 5 个参数 `Item.TooltipContext context` 与 `getContext()`（`:27`、`:68`），构造器少一个、`getEntity()` 注解从 `org.jetbrains.annotations.Nullable` 换成 `org.jspecify.annotations.Nullable`（两版本各随其加载器生态）。**类名、包名（相对加载器改名）、总线归属（游戏总线）都不变。**

**总线**：`NeoForge.EVENT_BUS`（这就是 NeoForge 里 Forge 的"游戏总线"）：

```java
// .mc-src/1.21.1/net/neoforged/neoforge/event/EventHooks.java:440-444
public static ItemTooltipEvent onItemTooltip(ItemStack itemStack, @Nullable Player entityPlayer, List<Component> list, TooltipFlag flags, Item.TooltipContext context) {
    ItemTooltipEvent event = new ItemTooltipEvent(itemStack, entityPlayer, list, flags, context);
    NeoForge.EVENT_BUS.post(event);
    return event;
}
```

触发点：`.mc-src/1.21.1/net/minecraft/world/item/ItemStack.java:840`（`getTooltipLines` 末尾；该方法签名在 `:777`：`public List<Component> getTooltipLines(Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag)`）。

### 1.3 NeoForge 26.1.2

**结论：类名与包名和 1.21.1 完全一致，没有改名、没有搬到 `GatherTooltip*` 之类的新名下。**
**全类名**：`net.neoforged.neoforge.event.entity.player.ItemTooltipEvent`
**源文件**：`.mc-src/26.1.2/net/neoforged/neoforge/event/entity/player/ItemTooltipEvent.java`（全文 70 行）

它与我实测的 1.21.1 版本**逐字相同**，仅 import 从 `org.jetbrains.annotations.Nullable` 换成 `org.jspecify.annotations.Nullable`：

```java
// .mc-src/26.1.2/net/neoforged/neoforge/event/entity/player/ItemTooltipEvent.java:16-69
public class ItemTooltipEvent extends PlayerEvent {
    private final TooltipFlag flags;
    private final ItemStack itemStack;
    private final List<Component> toolTip;
    private final TooltipContext context;

    public ItemTooltipEvent(ItemStack itemStack, @Nullable Player player, List<Component> list, TooltipFlag flags, TooltipContext context) { ... } // :26
    public TooltipFlag getFlags() { return flags; }        // :37
    public ItemStack getItemStack() { return itemStack; }  // :44
    public List<Component> getToolTip() { return toolTip; }// :51
    @Override @Nullable public Player getEntity() { ... }  // :60
    public TooltipContext getContext() { return context; } // :67
}
```

**总线**：同样是 `NeoForge.EVENT_BUS`：

```java
// .mc-src/26.1.2/net/neoforged/neoforge/event/EventHooks.java:458-462
public static ItemTooltipEvent onItemTooltip(ItemStack itemStack, @Nullable Player entityPlayer, List<Component> list, TooltipFlag flags, Item.TooltipContext context) {
    ItemTooltipEvent event = new ItemTooltipEvent(itemStack, entityPlayer, list, flags, context);
    NeoForge.EVENT_BUS.post(event);
    return event;
}
```

触发点：`.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java:916`（`getTooltipLines` 内，`:907` 是方法头）。

**26.1.2 的新增相关事件（同一个包/邻近包）**

| 事件 | 全类名 | 说明 |
|---|---|---|
| `FluidTooltipEvent` | `net.neoforged.neoforge.event.entity.player.FluidTooltipEvent` | 26.1.2 **新增**，与 `ItemTooltipEvent` 完全同形（`(FluidStack, Player, List<Component>, TooltipFlag, Item.TooltipContext)`），由 `EventHooks.onFluidTooltip`（`EventHooks.java:464-468`）post。1.21.1 无此类。 |
| `RegisterTooltipAppendersEvent` | `net.neoforged.neoforge.event.RegisterTooltipAppendersEvent` | **mod 总线**事件（`implements IModBusEvent`，`:20`），用于往 `TooltipLocation` 插槽注册 `TooltipAppender`。见 §3.3。 |
| `RenderTooltipEvent.GatherComponents` | `net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents` | 纯客户端渲染期事件，官方 javadoc 明确写着"要改内容请用 `ItemTooltipEvent`"（`RenderTooltipEvent.java:73`）。 |

**我搜过但确认不存在的关键字**（26.1.2，`path=.mc-src/26.1.2/net/neoforged/`）：`GatherTooltip`、`TooltipEvent`（只有 `ItemTooltipEvent`/`FluidTooltipEvent`/`RenderTooltipEvent`/`GatherSkippedAttributeTooltipsEvent`/`GatherEffectScreenTooltipsEvent`/`AddAttributeTooltipsEvent` 这几种命中的别名，**没有**名为 `GatherTooltipEvent` 的类）。

### 1.4 `TooltipFlag` 与三版本含义

`TooltipFlag` 是 `net.minecraft.world.item.TooltipFlag`（三版本同包同名），是 **interface + 嵌套 record `Default`**：

1.20.1（`.mc-src/1.20.1/net/minecraft/world/item/TooltipFlag.java`, 全文 26 行）：

```java
public interface TooltipFlag {
    TooltipFlag.Default NORMAL = new TooltipFlag.Default(false, false);
    TooltipFlag.Default ADVANCED = new TooltipFlag.Default(true, false);

    boolean isAdvanced();
    boolean isCreative();

    public record Default(boolean advanced, boolean creative) implements TooltipFlag {
        @Override public boolean isAdvanced() { return this.advanced; }
        @Override public boolean isCreative() { return this.creative; }
        public TooltipFlag.Default asCreative() { return new TooltipFlag.Default(this.advanced, true); }
    }
}
```

1.21.1 / 26.1.2（`.mc-src/1.21.1/.../TooltipFlag.java:1-26`、`.mc-src/26.1.2/.../TooltipFlag.java:1-26`，两者逐字相同）：

```java
public interface TooltipFlag extends net.neoforged.neoforge.common.extensions.TooltipFlagExtension {
    ... // 其余与 1.20.1 完全一致；Default 从 static class 改写为 record
}
```

**含义**

- `isAdvanced()`：是否显示"高级信息"，由 **F3+H** 切换（`1.20.1/.../ItemTooltipEvent.java` 没有注释，但 NeoForge 版本在 `:36` 写明 "toggled by F3+H"；游戏内可用 `TooltipFlag.ADVANCED`）。
- `isCreative()`：是否在创造模式界面收集（创造模式搜索树收集 tooltip 时用 `AS_CREATIVE`，`asCreative()` 就是给它用的）。
- `TooltipFlag.NORMAL` = `Default(false, false)`，`TooltipFlag.ADVANCED` = `Default(true, false)`。
- **NeoForge 额外能力**（1.21.1 与 26.1.2 都有，1.20.1 没有）：`TooltipFlag` 继承 `net.neoforged.neoforge.common.extensions.TooltipFlagExtension`，提供 `hasShiftDown()` / `hasControlDown()` / `hasAltDown()` / `shouldDisplayAllInformation()`（`.mc-src/26.1.2/net/neoforged/neoforge/common/extensions/TooltipFlagExtension.java:11,16,21,28`，默认全 `false`）。真实值由纯客户端类 `net.neoforged.neoforge.client.ClientTooltipFlag` 提供：

```java
// .mc-src/26.1.2/net/neoforged/neoforge/client/ClientTooltipFlag.java:17-49
public record ClientTooltipFlag(boolean advanced, boolean creative, boolean shiftDown, boolean controlDown, boolean altDown) implements TooltipFlag {
    @Override public boolean isAdvanced() { return this.advanced; }
    @Override public boolean isCreative() { return this.creative; }
    @Override public boolean hasControlDown() { return controlDown; }
    @Override public boolean hasShiftDown() { return shiftDown; }
    @Override public boolean hasAltDown() { return altDown; }

    public static TooltipFlag of(TooltipFlag other) {
        Minecraft mc = Minecraft.getInstance();
        return new ClientTooltipFlag(other.isAdvanced(), other.isCreative(), mc.hasShiftDown(), mc.hasControlDown(), mc.hasAltDown());
    }
}
```

→ 想写"按住 Shift 才显示附魔效果"这类 tooltip，NeoForge 两个版本直接 `flags.hasShiftDown()` 即可，**不要**自己去 `Minecraft.getInstance()`（那会把客户端类拖进公共代码）。1.20.1 Forge **没有**这个扩展（`.mc-src/1.20.1` 全树无 `ClientTooltipFlag.java`），只能自己判断。

---

## 2. `appendHoverText` 与 `TooltipContext` / `TooltipDisplay` 签名三版本对照

### 2.1 1.20.1 Forge

```java
// .mc-src/1.20.1/net/minecraft/world/item/Item.java:284
public void appendHoverText(ItemStack p_41421_, @Nullable Level p_41422_, List<Component> p_41423_, TooltipFlag p_41424_) {
}
```

- 第 2 参是 **`@Nullable Level`**（不是 `TooltipContext`）。
- 第 4 参是 `TooltipFlag`。
- **1.20.1 没有 `Item.TooltipContext`**（全树 `TooltipContext` 零命中）。玩家/世界信息靠 `Level` 参数与 `ItemTooltipEvent.getEntity()` 拿。

### 2.2 1.21.1 NeoForge

```java
// .mc-src/1.21.1/net/minecraft/world/item/Item.java:332
public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
}
```

- `TooltipFlag` **仍在第 4 位**；`Level` 位置换成了 **`Item.TooltipContext`**（注意：`Item.TooltipContext` 是 **MC 1.21 就引入的原版 API**，不是 26.1.2 新增的，也不是 NeoForge 扩展）。
- 1.21.1 的 `Item.TooltipContext`（`.mc-src/1.21.1/net/minecraft/world/item/Item.java:522-601`）：

```java
public interface TooltipContext {
    Item.TooltipContext EMPTY = new Item.TooltipContext() { ... };   // :523

    @Nullable HolderLookup.Provider registries();                    // :543
    float tickRate();                                                // :545
    @Nullable MapItemSavedData mapData(MapId mapId);                 // :548

    /** Neo: Returns the level if it's available. */
    @Nullable default Level level() { return null; }                 // :554

    static Item.TooltipContext of(@Nullable final Level level) { ... }              // :558
    static Item.TooltipContext of(final HolderLookup.Provider registries) { ... }   // :582
}
```

### 2.3 26.1.2 NeoForge —— 这里变了两次

**（a）`appendHoverText` 签名大改，并且被标记为废弃**

```java
// .mc-src/26.1.2/net/minecraft/world/item/Item.java:341-343
@Deprecated
public void appendHoverText(ItemStack itemStack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
}
```

变化点：
1. 多了 `TooltipDisplay display`（第 3 位）；
2. `List<Component>` → **`Consumer<Component> builder`**（第 4 位）——**不再直接操作列表**，必须 `builder.accept(line)`；
3. 第 5 位是 `TooltipFlag`；
4. **整体 `@Deprecated`**：官方推荐改用组件化的 `TooltipProvider#addToTooltip` 或 NeoForge 的 `TooltipAppender`（见 §3.3）。

`TooltipDisplay` 本体（`.mc-src/26.1.2/net/minecraft/world/item/component/TooltipDisplay.java:14-51`）：

```java
public record TooltipDisplay(boolean hideTooltip, SequencedSet<DataComponentType<?>> hiddenComponents) {
    public static final TooltipDisplay DEFAULT = new TooltipDisplay(false, ReferenceSortedSets.emptySet());  // :32

    public TooltipDisplay withHidden(DataComponentType<?> component, boolean hidden) { ... }                 // :34
    public boolean shows(DataComponentType<?> component) { ... }                                             // :49
}
```

**（b）`Item.TooltipContext` 扩展了**

```java
// .mc-src/26.1.2/net/minecraft/world/item/Item.java:725-768（节选）
public interface TooltipContext {
    Item.TooltipContext EMPTY = new Item.TooltipContext() { ... };   // :726

    HolderLookup.@Nullable Provider registries();   // :748
    float tickRate();                               // :750（EMPTY 返回 20.0F，:733）
    @Nullable MapItemSavedData mapData(MapId id);   // :752
    boolean isPeaceful();                           // :754（新增）

    @Nullable default Level level() { return null; }    // :760（Neo）
    @Nullable default Player player() { return null; }  // :766（Neo 新增）
}
```

工厂方法：`of(@Nullable Level)`（`:770`）、**`of(@Nullable Level, @Nullable Player)`（`:774`，新增）**、`of(HolderLookup.Provider)`（`:810`）。
→ **在 26.1.2 里，tooltip 上下文自己就带 `player()`**，很多场景不必再从事件取 `getEntity()`。

### 2.4 三版本签名一览

| | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge |
|---|---|---|---|
| `appendHoverText` | `(ItemStack, @Nullable Level, List<Component>, TooltipFlag)` `Item.java:284` | `(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)` `Item.java:332` | `@Deprecated (ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` `Item.java:342` |
| 中间层可写性 | `List<Component>` 可 `add`/`addAll` | 同左 | **只能 `consumer.accept(...)`** |
| `Item.TooltipContext` | **不存在** | 接口，`of(Level)` / `of(HolderLookup.Provider)` | 接口，另有 `of(Level,Player)`、`isPeaceful()`、`player()` |
| `TooltipDisplay` | 不存在 | 不存在 | 组件 `DataComponents.TOOLTIP_DISPLAY`，record |
| `TooltipFlag` | interface + `Default` record，无扩展方法 | 同左 + `TooltipFlagExtension`（shift/ctrl/alt） | 同 1.21.1（逐字相同） |

---

## 3. 如何按位置插入 tooltip 行

### 3.1 三版本 `getTooltipLines` 的实际追加顺序（决定"插哪儿"）

**1.20.1**（`.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:616-793`）

1. `:623` 悬停名（`list.add(mutablecomponent)`）
2. `:624-629` 地图编号（仅未开高级信息且无自定义名）
3. `:633` **`this.getItem().appendHoverText(this, p_41652_ == null ? null : p_41652_.level(), list, p_41653_);`** ← 物品自己的行
4. `:638` 盔甲纹饰 `ArmorTrim.appendUpgradeHoverText`
5. `:642` **附魔名 `appendEnchantmentNames(list, this.getEnchantmentTags())`**
6. `:645` 起 染色 / Lore / CanDestroy / CanPlaceOn
7. `:~690-743` 属性修饰符（`attribute.modifier.*`）
8. `:748` 不可破坏 / `:776-785` 高级信息（耐久、物品 id、NBT 计数）
9. `:791` **`ForgeEventFactory.onItemTooltip(...)`** ← 事件在这里，**列表已完整**

**1.21.1**（`.mc-src/1.21.1/net/minecraft/world/item/ItemStack.java:777-841`）

1. `:787` 悬停名
2. `:797` **`Item#appendHoverText`**
3. `:800-805` 组件 tooltip：`JUKEBOX_PLAYABLE`、`TRIM`、`STORED_ENCHANTMENTS`、**`ENCHANTMENTS`（`:803`）**、`DYED_COLOR`、`LORE`
4. `:807` 属性
5. `:809` 不可破坏 … `:824-834` 高级信息
6. `:840` **`EventHooks.onItemTooltip(...)`**

**26.1.2**（`.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java:907-929`）

```java
// .mc-src/26.1.2/net/minecraft/world/item/ItemStack.java:907-929
public List<Component> getTooltipLines(Item.TooltipContext context, @Nullable Player player, TooltipFlag tooltipFlag) {
    TooltipDisplay display = this.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
    if (!tooltipFlag.isCreative() && display.hideTooltip()) {
        boolean shouldPrintOpWarning = this.getItem().shouldPrintOpWarning(this, player);
        return shouldPrintOpWarning ? OP_NBT_WARNING : List.of();
    } else {
        List<Component> lines = Lists.newArrayList();
        lines.add(this.getStyledHoverName());
        this.addDetailsToTooltip(context, display, player, tooltipFlag, lines::add);
        net.neoforged.neoforge.event.EventHooks.onItemTooltip(this, player, lines, tooltipFlag, context);
        return lines;
    }
}

public void addDetailsToTooltip(
    Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, TooltipFlag tooltipFlag, Consumer<Component> builder
) {
    net.neoforged.neoforge.common.tooltip.ItemTooltipHandler.addDetailsToTooltipHead(this, context, display, player, tooltipFlag, builder);
    this.getItem().appendHoverText(this, context, display, builder, tooltipFlag);
    net.neoforged.neoforge.common.tooltip.ItemTooltipHandler.addDetailsToTooltipMiddle(this, context, display, player, tooltipFlag, builder);
    this.addDetailsToTooltipTail(context, display, player, tooltipFlag, builder);
    net.neoforged.neoforge.common.tooltip.ItemTooltipHandler.addDetailsToTooltipTail(this, context, display, player, tooltipFlag, builder);
}
```

顺序：**悬停名 → `Item#appendHoverText` → （组件 tooltip，含 `ENCHANTMENTS`）→ …尾部**。组件 tooltip 的权威顺序在 `.mc-src/26.1.2/net/neoforged/neoforge/common/tooltip/VanillaDataComponentTooltips.java:29-89`（`POTION_CONTENTS` 在 `:43`、`ENCHANTMENTS` 在 `:47`）。

### 3.2 结论：三版本里"原版物品行之后、附魔之前"该怎么插

**关键事实**：`ItemTooltipEvent` 在三版本**都是最后才触发**，此时 `getToolTip()` 返回的列表里已经有附魔行了。所以**用事件做"附魔之前"的插入，必须靠索引，不能靠"追加"**。

推荐三档方案，从最稳到最灵活：

**方案 A（推荐，三版本通吃）：在自己的 `Item` 子类里重写 `appendHoverText`。**
它天然落在"悬停名之后、附魔之前"——这正是原版药水 `PotionItem.appendHoverText` 的位置（见 §5）。1.20.1/1.21.1 拿到的就是那个 `List<Component>`，`add`/`addAll` 随便用；26.1.2 拿到的 `Consumer<Component>` 语义位置完全一样（`ItemStack.addDetailsToTooltip` 里就夹在 head/middle 之间）。**唯一代价**：26.1.2 这个方法带 `@Deprecated`。若不想吃 `@Deprecated`，等价替代是 26.1.2 专有的方案 C。

**方案 B：在 `ItemTooltipEvent` 里按索引插入。**
因为**悬停名永远在 `index 0`**（1.20.1 `ItemStack.java:623`、1.21.1 `:787`、26.1.2 `:914` 都是一开始就 add），所以：

```java
List<Component> lines = event.getToolTip();
// 紧贴物品名下方插入 → 一定在附魔之前，视觉上与原版药水的效果段一致
lines.addAll(1, myEffectLines);
```

**不要**尝试去"找附魔行的下标"——附魔行由 `Enchantment#getFullname(level)` 生成，形如 `Component.translatable(descId)`，加不加 `enchantment.level.N` 取决于等级（见 §4.3），没有稳定的可识别特征。想插到"物品自定义文本之后、附魔之前"的精确边界，只靠事件不可靠；用方案 A/C。

三版本的列表都是**可变的 `ArrayList`**：1.20.1 `Lists.newArrayList()`（`ItemStack.java:617`）、1.21.1 `Lists.newArrayList()`（`:781`）、26.1.2 `Lists.newArrayList()`（`:913`）。所以 `add`/`addAll`/`set`/`remove` 全部可用。

**方案 C（仅 26.1.2）：`TooltipLocation.POST_CUSTOM`。**
NeoForge 26.1.2 提供带插槽的 `TooltipAppender` 体系，其 `POST_CUSTOM` 的定义就是"`Item#appendHoverText` 与数据组件 tooltip 之间"——**正是"原版之后、附魔之前"**：

```java
// .mc-src/26.1.2/net/neoforged/neoforge/common/tooltip/TooltipLocation.java:16-31
public enum TooltipLocation {
    /** Append tooltip lines before {@link Item#appendHoverText(...)} */
    HEAD,
    /**
     * Append tooltip lines between {@link Item#appendHoverText(...)}
     * and {@linkplain DataComponentType data component} tooltips
     */
    POST_CUSTOM,
    /**
     * Append tooltip lines between {@linkplain DataComponentType data component} tooltips and additional item info
     * (item ID, data component count, disabled status, OP warning)
     */
    PRE_ITEM_INFO,
    /** Append tooltip lines after additional item info (item ID, data component count, disabled status, OP warning) */
    TAIL
}
```

三条插槽的装配顺序（`.mc-src/26.1.2/net/neoforged/neoforge/common/tooltip/ItemTooltipHandler.java:90-96`）：

```java
HEAD_APPENDERS.addAll(appenders.getOrDefault(TooltipLocation.HEAD, List.of()));

MIDDLE_APPENDERS.addAll(appenders.getOrDefault(TooltipLocation.POST_CUSTOM, List.of()));
addDataComponentAppenders(componentAppenders, componentGraph);
MIDDLE_APPENDERS.addAll(appenders.getOrDefault(TooltipLocation.PRE_ITEM_INFO, List.of()));

TAIL_APPENDERS.addAll(appenders.getOrDefault(TooltipLocation.TAIL, List.of()));
```

注册 API（`.mc-src/26.1.2/net/neoforged/neoforge/event/RegisterTooltipAppendersEvent.java:45`）：

```java
public void registerAppender(TooltipLocation location, TooltipAppender appender) {
    this.appenders.computeIfAbsent(location, _ -> new ArrayList<>()).add(appender);
}
```

`TooltipAppender` 接口（`.mc-src/26.1.2/net/neoforged/neoforge/common/tooltip/TooltipAppender.java:19-20`）：

```java
public interface TooltipAppender {
    void append(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, @Nullable Player player, TooltipFlag tooltipFlag, Consumer<Component> builder);
```

**注意**：`RegisterTooltipAppendersEvent` 是 **mod 总线**事件（`IModBusEvent`），在 `ItemTooltipHandler.init()`（`ItemTooltipHandler.java:83`）里 `ModLoader.postEvent(...)` 一次性触发，且**注册内容是全局的**（不是"仅客户端"的开关）。对本需求（"只给我们的食物加效果行"）它比方案 A/B 重，仅在不想用 `@Deprecated` 的 `appendHoverText` 时才值得。

---

## 4. 原版药水效果文本是怎么拼的

### 4.1 `MobEffectUtil.formatDuration` —— 三版本签名与实现

**1.20.1（2 参）**

```java
// .mc-src/1.20.1/net/minecraft/world/effect/MobEffectUtil.java:15-22
public static Component formatDuration(MobEffectInstance p_268116_, float p_268280_) {
    if (p_268116_.isInfiniteDuration()) {
        return Component.translatable("effect.duration.infinite");
    } else {
        int $$2 = Mth.floor(p_268116_.getDuration() * p_268280_);
        return Component.literal(StringUtil.formatTickDuration($$2));
    }
}
```

底层 `StringUtil.formatTickDuration`（`.mc-src/1.20.1/net/minecraft/util/StringUtil.java:14`）：

```java
public static String formatTickDuration(int p_14405_) {
    int $$1 = p_14405_ / 20;
    int $$2 = $$1 / 60;
    $$1 %= 60;
    int $$3 = $$2 / 60;
    $$2 %= 60;
    return $$3 > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", $$3, $$2, $$1) : String.format(Locale.ROOT, "%02d:%02d", $$2, $$1);
}
```

**1.21.1（3 参，已带 tickrate）**

```java
// .mc-src/1.21.1/net/minecraft/world/effect/MobEffectUtil.java:16-23
public static Component formatDuration(MobEffectInstance effect, float durationFactor, float ticksPerSecond) {
    if (effect.isInfiniteDuration()) {
        return Component.translatable("effect.duration.infinite");
    } else {
        int i = Mth.floor((float)effect.getDuration() * durationFactor);
        return Component.literal(StringUtil.formatTickDuration(i, ticksPerSecond));
    }
}
```

**26.1.2（3 参，与 1.21.1 同形）**

```java
// .mc-src/26.1.2/net/minecraft/world/effect/MobEffectUtil.java:16-23
public static Component formatDuration(MobEffectInstance instance, float scale, float tickrate) {
    if (instance.isInfiniteDuration()) {
        return Component.translatable("effect.duration.infinite");
    } else {
        int duration = Mth.floor(instance.getDuration() * scale);
        return Component.literal(StringUtil.formatTickDuration(duration, tickrate));
    }
}
```

**辅助方法位置**

| 方法 | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| `MobEffectInstance#isInfiniteDuration()` | `:148`（`return !...&& this.duration == -1`） | `:187` | `:174` |
| `MobEffectInstance#isAmbient()` | `:172` | `:211` | `:204` |
| `MobEffectInstance#isVisible()` | `:176` | `:215` | `:208` |
| `MobEffectInstance#showIcon()` | `:180` | `:219` | `:212` |
| `MobEffectInstance#endsWithin(int)` | `:152` | `:191` | `:178` |
| `MobEffectInstance#getDuration()` | `:164` | `:203` | `:196` |
| `StringUtil#formatTickDuration` | `StringUtil.java:14` | 同名（带 tickrate 重载） | 同名 |
| `INFINITE_DURATION = -1` 常量 | `MobEffectInstance.java:27` | `:30` | `:27` |

（以上行号均已逐条 grep 复核，非转述。）

> **`formatDuration` 三版本都只有 1 个重载，没有 `formatDuration(MobEffectInstance, float, float)` 之外的变体。**
> 搜过的关键字：`formatDuration`（各自树内命中 = 1 处定义 + 若干调用点）。
> `formatDuration` **不调用** `isAmbient()`（`isAmbient` 只被 update/compareTo/NBT 用）。
> 注意：`MobEffectUtil` 里**没有** `getMobEffectDescriptionId` 这种辅助方法（三版本均 0 命中）。

### 4.2 效果名 + 等级（"力量 II"）到底由谁生成

**`MobEffect.getDisplayName()` 只给效果名，不带等级。** 三个版本都是同一个实现：

```java
// 1.20.1: .mc-src/1.20.1/net/minecraft/world/effect/MobEffect.java:134-136
// 1.21.1: .mc-src/1.21.1/net/minecraft/world/effect/MobEffect.java:119-121
// 26.1.2: .mc-src/26.1.2/net/minecraft/world/effect/MobEffect.java:133-135
public Component getDisplayName() {
    return Component.translatable(this.getDescriptionId());
}
```

`getDescriptionId()` → `getOrCreateDescriptionId()`（1.20.1 `MobEffect.java:130`/`:122`；1.21.1 `:115`/`:109-113`；26.1.2 `:129`/`:121-127`）：

```java
// .mc-src/26.1.2/net/minecraft/world/effect/MobEffect.java:121-131
protected String getOrCreateDescriptionId() {
    if (this.descriptionId == null) {
        this.descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this));
    }
    return this.descriptionId;
}

public String getDescriptionId() { return this.getOrCreateDescriptionId(); }
```

→ key 形如 `effect.minecraft.strength`。

**"II" 有两条完全不同的来源，别混用：**

| 场景 | 生成代码 | 语言 key | 位置 |
|---|---|---|---|
| **物品 tooltip（药水/药箭/滞留药水/迷之炖菜）** | `Component.translatable("potion.withAmplifier", line, Component.translatable("potion.potency." + amplifier))` | `potion.potency.N`（**N = amplifier**，`0` 是空串，`1` = "II"） | 1.20.1 `PotionUtils.java:177-179`；1.21.1 `PotionContents.java:153-157`；26.1.2 `PotionContents.java:233` |
| **物品栏/背包右侧状态效果面板（HUD）** | `name.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (amplifier + 1)))` | `enchantment.level.N`（**N = amplifier + 1**，`2` = "II"） | 1.20.1 `EffectRenderingInventoryScreen.java:133-140`（`enchantment.level` 在 `:136`）；1.21.1 `:146-153`（`:149`）；26.1.2 `EffectsInInventory.java:134-141`（`:137`） |

**26.1.2 生成"HUD 版"等级后缀的逐字代码**：

```java
// .mc-src/26.1.2/net/minecraft/client/gui/screens/inventory/EffectsInInventory.java:134-141
private Component getEffectName(MobEffectInstance effect) {
    MutableComponent name = effect.getEffect().value().getDisplayName().copy();
    if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
        name.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
    }
    return name;
}
```

**罗马数字本身没有生成代码**——`enchantment.level.*` / `potion.potency.*` 都是纯语言文件文本。26.1.2 树里能逐字引用到（`.mc-src/26.1.2/assets/minecraft/lang/en_us.json`）：

```json
"potion.potency.0": "",         // :6248
"potion.potency.1": "II",       // :6249
"potion.potency.2": "III",      // :6250
"potion.potency.3": "IV",       // :6251
"potion.potency.4": "V",        // :6252
"potion.potency.5": "VI",       // :6253
"potion.withAmplifier": "%s %s",// :6255
"potion.withDuration": "%s (%s)",// :6256
"effect.duration.infinite": "∞",// :3461
"effect.none": "No Effects",    // :3502
"enchantment.level.1": "I",     // :3503
"enchantment.level.2": "II",    // :3504
... "enchantment.level.10": "X" // :3512
```

→ **本项目只需写 zh_cn / en_us 的效果名（`effect.minecraft.*` 原版已有），等级与时长格式全部复用原版 key，无需自定义。**

### 4.3 `MobEffectInstance` 三版本构造器签名

**1.20.1**（`.mc-src/1.20.1/net/minecraft/world/effect/MobEffectInstance.java`，普通 class，`:25`）

```java
public MobEffectInstance(MobEffect p_19513_)                                                                     // :39
public MobEffectInstance(MobEffect p_19515_, int p_19516_)                                                       // :43
public MobEffectInstance(MobEffect p_19518_, int p_19519_, int p_19520_)                                         // :47
public MobEffectInstance(MobEffect p_19522_, int p_19523_, int p_19524_, boolean p_19525_, boolean p_19526_)     // :51
public MobEffectInstance(MobEffect p_19528_, int p_19529_, int p_19530_, boolean p_19531_, boolean p_19532_, boolean p_19533_)  // :55
public MobEffectInstance(MobEffect p_216887_, int p_216888_, int p_216889_, boolean p_216890_, boolean p_216891_,
                         boolean p_216892_, @Nullable MobEffectInstance p_216893_, Optional<MobEffectInstance.FactorData> p_216894_)  // :59
public MobEffectInstance(MobEffectInstance p_19543_)                                                             // :79
```

**1.21.1**（`:28` 普通 class）

```java
public MobEffectInstance(Holder<MobEffect> effect)                                                              // :56
public MobEffectInstance(Holder<MobEffect> effect, int duration)                                                // :60
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier)                                 // :64
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) // :68
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) // :72
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible,
                         boolean showIcon, @Nullable MobEffectInstance hiddenEffect)                            // :76
public MobEffectInstance(MobEffectInstance other)                                                               // :89
```

**26.1.2**（`:25` 普通 class）

```java
public MobEffectInstance(Holder<MobEffect> effect)                                                              // :49
public MobEffectInstance(Holder<MobEffect> effect, int duration)                                                // :53
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier)                                 // :57
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) // :61
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible, boolean showIcon) // :65
public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible,
                         boolean showIcon, @Nullable MobEffectInstance hiddenEffect)                            // :69
public MobEffectInstance(MobEffectInstance copy)                                                                // :81
```

**默认值语义（三版本一致，取自委托链）**：`duration = 0`、`amplifier = 0`、`ambient = false`、`visible = true`、`showIcon = visible`（4/5 参版把 `visible` 同时给 `visible` 与 `showIcon`）、`hiddenEffect = null`。
26.1.2 / 1.21.1 里 `amplifier` 会被 `Mth.clamp(amplifier, 0, 255)` 夹紧。
**`duration` 单位是 tick，`-1`（`INFINITE_DURATION`）表示无限。**

`MobEffectInstance` 的 getter（三版本 `showIcon` 都叫 `showIcon()`，不是 `isShowIcon()`）：

| getter | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| `getEffect()` | `:160` → **`MobEffect`** | `:199` → `Holder<MobEffect>` | `:192` → `Holder<MobEffect>` |
| `getAmplifier()` | `:168` | `:207` | `:200` |
| `getDuration()` | `:164` | `:203` | `:196` |
| `isAmbient()` | `:172` | `:211` | `:204` |
| `isVisible()` | `:176` | `:215` | `:208` |
| `showIcon()` | `:180` | `:219` | `:212` |
| `getDescriptionId()` | `:221` | `:266` | `:278` |
| 是否 record | 否（`class`，`:25`） | 否（`class`，`:28`；内嵌 `record Details` `:418`） | 否（`class`，`:25`；内嵌 `record Details` `:406`） |

> `MobEffectInstance` **没有** `getDisplayName()`（三版本均如此）。要显示名请走 `instance.getEffect().value().getDisplayName()`（1.21.1+）或 `instance.getEffect().getDisplayName()`（1.20.1）。

---

## 5. 原版物品 tooltip 里"状态效果"段落在哪里生成 + 能不能复用

### 5.1 生成位置三版本

| 版本 | 类 | 方法 | 行号 |
|---|---|---|---|
| 1.20.1 | `net.minecraft.world.item.alchemy.PotionUtils` | `addPotionTooltip(ItemStack, List<Component>, float)` / `addPotionTooltip(List<MobEffectInstance>, List<Component>, float)` | `:154` / `:158` |
| 1.21.1 | `net.minecraft.world.item.alchemy.PotionContents`（record） | 实例 `addPotionTooltip(Consumer<Component>, float, float)` / 静态 `addPotionTooltip(Iterable<MobEffectInstance>, Consumer<Component>, float, float)` | `:140` / `:144` |
| 26.1.2 | `net.minecraft.world.item.alchemy.PotionContents` | **只有静态** `addPotionTooltip(Iterable<MobEffectInstance>, Consumer<Component>, float, float)`；另有新公开的 `getPotionDescription(Holder<MobEffect>, int)` | `:167` / `:231` |

**1.20.1 的 `PotionUtils`（`PotionUtils` 在 1.21 已整个删除，1.21.1/26.1.2 树中 0 命中）**：

```java
// .mc-src/1.20.1/net/minecraft/world/item/alchemy/PotionUtils.java:154-225（完整方法体）
public static void addPotionTooltip(ItemStack p_43556_, List<Component> p_43557_, float p_43558_) {
    addPotionTooltip(getMobEffects(p_43556_), p_43557_, p_43558_);
}

public static void addPotionTooltip(List<MobEffectInstance> p_259687_, List<Component> p_259660_, float p_259949_) {
    List<Pair<Attribute, AttributeModifier>> $$3 = Lists.newArrayList();
    if (p_259687_.isEmpty()) {
        p_259660_.add(NO_EFFECT);
    } else {
        for (MobEffectInstance $$4 : p_259687_) {
            MutableComponent $$5 = Component.translatable($$4.getDescriptionId());
            MobEffect $$6 = $$4.getEffect();
            Map<Attribute, AttributeModifier> $$7 = $$6.getAttributeModifiers();
            if (!$$7.isEmpty()) {
                for (Entry<Attribute, AttributeModifier> $$8 : $$7.entrySet()) {
                    AttributeModifier $$9 = $$8.getValue();
                    AttributeModifier $$10 = new AttributeModifier(
                        $$9.getName(), $$6.getAttributeModifierValue($$4.getAmplifier(), $$9), $$9.getOperation()
                    );
                    $$3.add(new Pair($$8.getKey(), $$10));
                }
            }

            if ($$4.getAmplifier() > 0) {
                $$5 = Component.translatable("potion.withAmplifier", $$5, Component.translatable("potion.potency." + $$4.getAmplifier()));
            }

            if (!$$4.endsWithin(20)) {
                $$5 = Component.translatable("potion.withDuration", $$5, MobEffectUtil.formatDuration($$4, p_259949_));
            }

            p_259660_.add($$5.withStyle($$6.getCategory().getTooltipFormatting()));
        }
    }

    if (!$$3.isEmpty()) {
        p_259660_.add(CommonComponents.EMPTY);
        p_259660_.add(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));
        // ... 属性行（attribute.modifier.plus/take.*）
    }
}
```

**1.21.1 的 `PotionContents`**：

```java
// .mc-src/1.21.1/net/minecraft/world/item/alchemy/PotionContents.java:33-35
public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor, List<MobEffectInstance> customEffects) {
    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of());
    private static final Component NO_EFFECT = Component.translatable("effect.none").withStyle(ChatFormatting.GRAY);
```

```java
// .mc-src/1.21.1/net/minecraft/world/item/alchemy/PotionContents.java:140-190（节选：效果段 + whenDrank 段）
public void addPotionTooltip(Consumer<Component> tooltipAdder, float durationFactor, float ticksPerSecond) {
    addPotionTooltip(this.getAllEffects(), tooltipAdder, durationFactor, ticksPerSecond);
}

public static void addPotionTooltip(Iterable<MobEffectInstance> effects, Consumer<Component> tooltipAdder, float durationFactor, float ticksPerSecond) {
    List<Pair<Holder<Attribute>, AttributeModifier>> list = Lists.newArrayList();
    boolean flag = true;

    for (MobEffectInstance mobeffectinstance : effects) {
        flag = false;
        MutableComponent mutablecomponent = Component.translatable(mobeffectinstance.getDescriptionId());
        Holder<MobEffect> holder = mobeffectinstance.getEffect();
        holder.value().createModifiers(mobeffectinstance.getAmplifier(), (p_331556_, p_330860_) -> list.add(new Pair<>(p_331556_, p_330860_)));
        if (mobeffectinstance.getAmplifier() > 0) {
            mutablecomponent = Component.translatable(
                "potion.withAmplifier", mutablecomponent, Component.translatable("potion.potency." + mobeffectinstance.getAmplifier())
            );
        }

        if (!mobeffectinstance.endsWithin(20)) {
            mutablecomponent = Component.translatable(
                "potion.withDuration", mutablecomponent, MobEffectUtil.formatDuration(mobeffectinstance, durationFactor, ticksPerSecond)
            );
        }

        tooltipAdder.accept(mutablecomponent.withStyle(holder.value().getCategory().getTooltipFormatting()));
    }

    if (flag) {
        tooltipAdder.accept(NO_EFFECT);
    }

    if (!list.isEmpty()) {
        tooltipAdder.accept(CommonComponents.EMPTY);
        tooltipAdder.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));

        // Neo: Override handling of potion attribute tooltips to support IAttributeExtension
        net.neoforged.neoforge.common.util.AttributeUtil.addPotionTooltip(list, tooltipAdder);
        if (true) return;   // ← 原版属性循环已成为死代码
        ...
    }
}
```

**26.1.2 的 `PotionContents`**：

```java
// .mc-src/26.1.2/net/minecraft/world/item/alchemy/PotionContents.java:167-182
public static void addPotionTooltip(Iterable<MobEffectInstance> effects, Consumer<Component> lines, float durationScale, float tickrate) {
    List<Pair<Holder<Attribute>, AttributeModifier>> modifiers = Lists.newArrayList();
    boolean noEffects = true;

    for (MobEffectInstance effect : effects) {
        noEffects = false;
        Holder<MobEffect> mobEffect = effect.getEffect();
        int amplifier = effect.getAmplifier();
        mobEffect.value().createModifiers(amplifier, (attribute, modifierx) -> modifiers.add(new Pair<>(attribute, modifierx)));
        MutableComponent line = getPotionDescription(mobEffect, amplifier);
        if (!effect.endsWithin(20)) {
            line = Component.translatable("potion.withDuration", line, MobEffectUtil.formatDuration(effect, durationScale, tickrate));
        }

        lines.accept(line.withStyle(mobEffect.value().getCategory().getTooltipFormatting()));
    }
    // ... noEffects → NO_EFFECT；modifiers → potion.whenDrank 段
}
```

**26.1.2 新增的公开辅助方法（**这是最省事的一行**）**：

```java
// .mc-src/26.1.2/net/minecraft/world/item/alchemy/PotionContents.java:231-234
public static MutableComponent getPotionDescription(Holder<MobEffect> mobEffect, int amplifier) {
    MutableComponent line = Component.translatable(mobEffect.value().getDescriptionId());
    return amplifier > 0 ? Component.translatable("potion.withAmplifier", line, Component.translatable("potion.potency." + amplifier)) : line;
}
```

（**1.21.1 没有这个方法**——该逻辑内联在 `addPotionTooltip` 里；1.20.1 也没有，逻辑内联在 `PotionUtils.addPotionTooltip` 里。）

**调用点（即"原版哪些物品用了它"）**

- 1.20.1：`PotionItem.appendHoverText` 倍率 `1.0F`（`PotionItem.java:131-133`）、`TippedArrowItem.appendHoverText` 倍率 `0.125F`（`TippedArrowItem.java:20-23`）。
- 1.21.1：`PotionItem.java:160`（`1.0F`）、`TippedArrowItem.java:26`（`0.125F`）、`LingeringPotionItem.java:22`（`0.25F`）、`SuspiciousStewItem.java:31`（`1.0F`）、`OminousBottleItem.java:66`（`1.0F`）。
- 26.1.2：`TooltipProvider#addToTooltip` 覆写（`PotionContents.java:241-244`）→ 组件化路径 `DataComponents.POTION_CONTENTS`；另有 `SuspiciousStewEffects.java:54`、`OminousBottleAmplifier.java:38`。

```java
// 1.20.1 原版药水的完整 tooltip 实现（就是"效果段"的入口）
// .mc-src/1.20.1/net/minecraft/world/item/PotionItem.java:130-133
@Override
public void appendHoverText(ItemStack p_42988_, @Nullable Level p_42989_, List<Component> p_42990_, TooltipFlag p_42991_) {
    PotionUtils.addPotionTooltip(p_42988_, p_42990_, 1.0F);
}
```

```java
// 1.21.1
// .mc-src/1.21.1/net/minecraft/world/item/PotionItem.java:156-162
@Override
public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
    PotionContents potioncontents = stack.get(DataComponents.POTION_CONTENTS);
    if (potioncontents != null) {
        potioncontents.addPotionTooltip(tooltipComponents::add, 1.0F, context.tickRate());
    }
}
```

```java
// 26.1.2（组件路径，没有 appendHoverText）
// .mc-src/26.1.2/net/minecraft/world/item/alchemy/PotionContents.java:241-244
@Override
public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
    addPotionTooltip(this.getAllEffects(), consumer, components.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0F), context.tickRate());
}
```

### 5.2 能不能直接复用来渲染"附魔会带来的效果"？——能，但有一个副作用要处理

**能做**：三个版本的 `addPotionTooltip` 都接受**任意 `MobEffectInstance` 集合**，不要求来自药水物品。我们只要为每个附魔准备一个 `MobEffectInstance`（效果 + 时长 + 等级），把列表喂进去就能得到与原版药水**逐字一致**的文本（名称、"II"、"(03:00)"、按 `MobEffectCategory` 着色的行）。

**副作用**：`addPotionTooltip` 除了效果行，**还会追加 `potion.whenDrank` 段落**（"When Applied:" + 属性修饰符行），只要效果带属性修饰符就会触发——力量(ATTACK_DAMAGE)、速度(MOVEMENT_SPEED)、吸收(MAX_ABSORPTION) 都带修饰符（见 §6 常量源码），所以我们的 tooltip 会多出 3 行"应用时"属性说明。

**三种处理方式：**

1. **接受它**（推荐给"最省事"）：这段文本本身信息量正确（"应用时：+3 攻击力"），且 100% 原版风格。
2. **只要效果行**：把 `addPotionTooltip` 输出到一个临时 `List` 再按需截取——脆弱（依赖 `CommonComponents.EMPTY` 的位置），**不推荐**。
3. **自己拼效果行**（3 行代码，最可控，三版本通吃）：

```java
// 1.21.1 / 26.1.2 版本（Holder 体系）
for (MobEffectInstance inst : effects) {
    MutableComponent line = Component.translatable(inst.getDescriptionId());          // = effect.minecraft.<id>
    if (inst.getAmplifier() > 0) {
        line = Component.translatable("potion.withAmplifier", line,
                Component.translatable("potion.potency." + inst.getAmplifier()));
    }
    if (!inst.endsWithin(20)) {
        line = Component.translatable("potion.withDuration", line,
                MobEffectUtil.formatDuration(inst, 1.0F, tickRate));                 // 1.21.1+ 三参
    }
    lines.add(line.withStyle(inst.getEffect().value().getCategory().getTooltipFormatting()));
}
```

```java
// 1.20.1 版本
for (MobEffectInstance inst : effects) {
    MutableComponent line = Component.translatable(inst.getDescriptionId());
    if (inst.getAmplifier() > 0) {
        line = Component.translatable("potion.withAmplifier", line,
                Component.translatable("potion.potency." + inst.getAmplifier()));
    }
    if (!inst.endsWithin(20)) {
        line = Component.translatable("potion.withDuration", line,
                MobEffectUtil.formatDuration(inst, 1.0F));                            // 1.20.1 两参
    }
    lines.add(line.withStyle(inst.getEffect().getCategory().getTooltipFormatting()));
}
```

> `tickRate` 来源：1.21.1/26.1.2 的 `Item.TooltipContext#tickRate()`（`1.21.1/Item.java:545`、`26.1.2/Item.java:750`）；事件里则是 `event.getContext().tickRate()`。若在纯事件里且拿不到 context（1.20.1），用 `1.0F` 倍率固定时长即可（`formatDuration` 的 `scale` 就是倍率，药水用 `1.0F`）。
> 另外 26.1.2 可以直接用 `PotionContents.getPotionDescription(holder, amplifier)`（`:231`）替掉前 5 行。

---

## 6. 26.1.2 的类型变化与 7 种效果常量

### 6.1 `MobEffect` / `MobEffectInstance` / `MobEffects` 的类型

| 项 | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| `MobEffect` 本体 | 普通 class（`MobEffect.java:27`；`implements IForgeMobEffect`） | 普通 class（`MobEffect.java:38`；`implements FeatureElement, IMobEffectExtension`） | 普通 class（`MobEffect.java:39`；同 1.21.1）；**不是** `Holder` 子类 |
| 引用形式 | **裸 `MobEffect`**（`MobEffects` 常量、`getEffect()`、`hasEffect()` 全是裸类型） | **`Holder<MobEffect>`** | **`Holder<MobEffect>`** |
| 取实例 | 直接用常量 | `MobEffects.XXX.value()` | `MobEffects.XXX.value()` |
| 构造 `MobEffectInstance` | 第 1 参 `MobEffect` | 第 1 参 `Holder<MobEffect>` | 第 1 参 `Holder<MobEffect>` |
| `MobEffects#register` 返回 | `private static MobEffect register(int, String, MobEffect)`（`MobEffects.java:115`） | `private static Holder<MobEffect> register(String, MobEffect)`（`:131`） | `private static Holder<MobEffect> register(String, MobEffect)`（`:129`） |
| `Holder` 取值 API | 不适用 | `Holder.java:14-15`（`T value()`）、`:44`（`Holder.direct(T)`）、`:48`（`record Direct<T>`） | 同 1.21.1 |

**26.1.2 里 `Holder<MobEffect>` 的典型用法**（官方源码自带示例）：

```java
// .mc-src/26.1.2/net/minecraft/client/gui/screens/inventory/EffectsInInventory.java:135
MutableComponent name = effect.getEffect().value().getDisplayName().copy();
```

**结论**：26.1.2 与 1.21.1 在这一层**没有变化**；真正会踩坑的是 **1.20.1 → 1.21.1 的 `Holder` 化**。

### 6.2 本项目要用的 7 种效果：常量名 + 类型（逐字证据）

| 中文 | 1.20.1 字段名（类型 `MobEffect`） | 1.21.1 字段名（类型 `Holder<MobEffect>`） | 26.1.2 字段名（类型 `Holder<MobEffect>`） | 注册资源名 |
|---|---|---|---|---|
| 力量 | **`DAMAGE_BOOST`** `MobEffects.java:38` | **`DAMAGE_BOOST`** `:47` | **`STRENGTH`** `:43-47` | `"strength"` |
| 速度 | `MOVEMENT_SPEED` `:14` | `MOVEMENT_SPEED` `:16` | `SPEED` `:15-21` | `"speed"` |
| 生命恢复 | `REGENERATION` `:48` | `REGENERATION` `:64` | `REGENERATION` `:58` | `"regeneration"` |
| 瞬间治疗 | `HEAL` `:44` | `HEAL` `:54` | `INSTANT_HEALTH` `:48` | `"instant_health"` |
| 吸收 | `ABSORPTION` `:70` | `ABSORPTION` `:88-94` | `ABSORPTION` `:87-91` | `"absorption"` |
| 抗性提升 | `DAMAGE_RESISTANCE` `:49` | `DAMAGE_RESISTANCE` `:65` | `RESISTANCE` `:59` | `"resistance"` |
| 饱和 | `SATURATION` `:71` | `SATURATION` `:95` | `SATURATION` `:92` | `"saturation"` |

**26.1.2 逐字源码（`.mc-src/26.1.2/net/minecraft/world/effect/MobEffects.java`）**：

```java
// :15-21  速度
public static final Holder<MobEffect> SPEED = register(
    "speed",
    new MobEffect(MobEffectCategory.BENEFICIAL, 3402751)
        .addAttributeModifier(
            Attributes.MOVEMENT_SPEED, Identifier.withDefaultNamespace("effect.speed"), 0.2F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
);

// :43-47  力量
public static final Holder<MobEffect> STRENGTH = register(
    "strength",
    new MobEffect(MobEffectCategory.BENEFICIAL, 16762624)
        .addAttributeModifier(Attributes.ATTACK_DAMAGE, Identifier.withDefaultNamespace("effect.strength"), 3.0, AttributeModifier.Operation.ADD_VALUE)
);

// :48 瞬间治疗 / :58 生命恢复 / :59 抗性提升 / :87 吸收 / :92 饱和
public static final Holder<MobEffect> INSTANT_HEALTH = register("instant_health", new HealOrHarmMobEffect(MobEffectCategory.BENEFICIAL, 16262179, false));
public static final Holder<MobEffect> REGENERATION = register("regeneration", new RegenerationMobEffect(MobEffectCategory.BENEFICIAL, 13458603));
public static final Holder<MobEffect> RESISTANCE = register("resistance", new MobEffect(MobEffectCategory.BENEFICIAL, 9520880));
public static final Holder<MobEffect> ABSORPTION = register(
    "absorption",
    new AbsorptionMobEffect(MobEffectCategory.BENEFICIAL, 2445989)
        .addAttributeModifier(Attributes.MAX_ABSORPTION, Identifier.withDefaultNamespace("effect.absorption"), 4.0, AttributeModifier.Operation.ADD_VALUE)
);
public static final Holder<MobEffect> SATURATION = register("saturation", new SaturationMobEffect(MobEffectCategory.BENEFICIAL, 16262179));

// :129-131 注册工厂
private static Holder<MobEffect> register(String name, MobEffect mobEffect) {
    return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, Identifier.withDefaultNamespace(name), mobEffect);
}
```

**26.1.2 取值写法**：

```java
Holder<MobEffect> holder = MobEffects.STRENGTH;                 // 直接当 Holder 用（构造 MobEffectInstance 就传它）
MobEffect effect = MobEffects.STRENGTH.value();                 // 需要实例（如 getDescriptionId()/getCategory()）
new MobEffectInstance(MobEffects.STRENGTH, 600, 1);             // duration=600t(30s)，amplifier=1 → 显示 "力量 II (00:30)"
```

**1.21.1 逐字差别**（`.mc-src/1.21.1/net/minecraft/world/effect/MobEffects.java`）：

```java
public static final Holder<MobEffect> MOVEMENT_SPEED = register("speed", new MobEffect(MobEffectCategory.BENEFICIAL, 3402751)
    .addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.withDefaultNamespace("effect.speed"), 0.2F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));  // :16-22
public static final Holder<MobEffect> DAMAGE_BOOST = register("strength", new MobEffect(MobEffectCategory.BENEFICIAL, 16762624)
    .addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.withDefaultNamespace("effect.strength"), 3.0, AttributeModifier.Operation.ADD_VALUE));            // :47-53
public static final Holder<MobEffect> HEAL = register("instant_health", new HealOrHarmMobEffect(MobEffectCategory.BENEFICIAL, 16262179, false));                       // :54
public static final Holder<MobEffect> REGENERATION = register("regeneration", new RegenerationMobEffect(MobEffectCategory.BENEFICIAL, 13458603));                     // :64
public static final Holder<MobEffect> DAMAGE_RESISTANCE = register("resistance", new MobEffect(MobEffectCategory.BENEFICIAL, 9520880));                                // :65
public static final Holder<MobEffect> ABSORPTION = register("absorption", new AbsorptionMobEffect(MobEffectCategory.BENEFICIAL, 2445989)
    .addAttributeModifier(Attributes.MAX_ABSORPTION, ResourceLocation.withDefaultNamespace("effect.absorption"), 4.0, AttributeModifier.Operation.ADD_VALUE));         // :88-94
public static final Holder<MobEffect> SATURATION = register("saturation", new SaturationMobEffect(MobEffectCategory.BENEFICIAL, 16262179));                           // :95
```

**1.20.1 逐字差别**（`.mc-src/1.20.1/net/minecraft/world/effect/MobEffects.java`；注意类型是裸 `MobEffect`，且是 `register(int id, String name, MobEffect)`）：

```java
public static final MobEffect MOVEMENT_SPEED = register(1, "speed", new MobEffect(MobEffectCategory.BENEFICIAL, 3402751)
    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "91AEAA56-376B-4498-935B-2F7F68070635", 0.2F, AttributeModifier.Operation.MULTIPLY_TOTAL));  // :14-19
public static final MobEffect DAMAGE_BOOST = register(5, "strength", new AttackDamageMobEffect(MobEffectCategory.BENEFICIAL, 16762624, 3.0)
    .addAttributeModifier(Attributes.ATTACK_DAMAGE, "648D7064-6A60-4F59-8ABE-C2C23A6DD7A9", 0.0, AttributeModifier.Operation.ADDITION));          // :38-43
public static final MobEffect HEAL = register(6, "instant_health", new InstantenousMobEffect(MobEffectCategory.BENEFICIAL, 16262179));              // :44
public static final MobEffect REGENERATION = register(10, "regeneration", new MobEffect(MobEffectCategory.BENEFICIAL, 13458603));                   // :48
public static final MobEffect DAMAGE_RESISTANCE = register(11, "resistance", new MobEffect(MobEffectCategory.BENEFICIAL, 9520880));                 // :49
public static final MobEffect ABSORPTION = register(22, "absorption", new AbsoptionMobEffect(MobEffectCategory.BENEFICIAL, 2445989));               // :70（原版类名确实少一个 r）
public static final MobEffect SATURATION = register(23, "saturation", new InstantenousMobEffect(MobEffectCategory.BENEFICIAL, 16262179));           // :71
```

> **一句话记忆**：把 7 个效果写成"每版本一个映射"时，**只有 4 个名字在版本间不同**：力量、速度、瞬间治疗、抗性提升。其余 3 个（`REGENERATION`/`ABSORPTION`/`SATURATION`）三版本同名。

---

## 7. 客户端事件的正确注册姿势（三平台客户端隔离）

### 7.1 先明确一件事实：本需求其实**不需要**客户端专属类

`ItemTooltipEvent` 本身位于**公共包**（`...event.entity.player`），监听器中要用的类型（`ItemStack`、`Component`、`MobEffectInstance`、`MobEffects`、`MobEffectUtil`、`PotionContents`/`PotionUtils`、`Item.TooltipContext`）**全是双端类**。
它实际只在客户端触发：NeoForge 版本的 javadoc 明说 "Tooltips are also gathered with a null player during startup by `Minecraft#createSearchTrees()`"（`1.21.1/ItemTooltipEvent.java:25`、`26.1.2/ItemTooltipEvent.java:24`）——`Minecraft` 是纯客户端类。

**所以最省事、也最安全的做法是：把监听器写成"不引用任何客户端专属类"的普通类，直接注册到游戏总线。** 这样专用服务端上根本不存在加载客户端类的机会，也就没有 `Attempted to load class ... for invalid dist DEDICATED_SERVER` 的风险。

**唯一必须隔离的情形**：你想在 tooltip 里做 `Minecraft.getInstance()` / `Screen` 相关的事（例如用 `ClientTooltipFlag.of(...)` 读 Shift 键）。此时按下述三平台姿势处理。

### 7.2 Forge 1.20.1

树内可引用的**用法**证据（`DistExecutor.java` / `EventBusSubscriber.java` 定义文件不在树内）：

| 用法 | 证据 |
|---|---|
| `@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD, modid = "forge")` | `net/minecraftforge/common/ForgeSpawnEggItem.java:76`；`net/minecraftforge/client/ClientForgeMod.java:19`；`net/minecraftforge/client/ForgeHooksClient.java:1206`；`net/minecraftforge/client/model/data/ModelDataManager.java:23`（`@EventBusSubscriber(modid = "forge", bus = Bus.FORGE, value = Dist.CLIENT)`） |
| `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ...)` | `net/minecraftforge/fml/core/ModStateProvider.java:34` |
| `DistExecutor.runWhenOn(Dist.DEDICATED_SERVER, () -> () -> ...)` | `net/minecraftforge/server/ServerLifecycleHooks.java:92` |
| `DistExecutor.unsafeRunForDist(...)` | `net/minecraftforge/fml/core/ModStateProvider.java:46` |
| `FMLEnvironment.dist == Dist.CLIENT` | `net/minecraftforge/fluids/FluidType.java:336`；`net/minecraftforge/common/CreativeModeTabRegistry.java:218`；`net/minecraftforge/common/TierSortingRegistry.java:171`；`net/minecraftforge/common/MinecraftForge.java:31` |

项目里现有的 `mods.toml` 注释也提到了 `clientSideOnly=true`（`forge-1.20.1/src/main/resources/META-INF/mods.toml`，见 `forge-1.20.1/src/main/java/com/lyingice/orefood/forge/OreFoodForge.java:17-19` 的说明）。

**推荐写法（游戏总线的客户端监听）**：

```java
// Forge 1.20.1
@Mod.EventBusSubscriber(modid = OreFoodConstants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ForgeClientTooltips {
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {   // net.minecraftforge.event.entity.player.ItemTooltipEvent
        // event.getToolTip() / event.getItemStack() / event.getEntity() / event.getFlags()
    }
}
```

`@EventBusSubscriber(value = Dist.CLIENT)` 由 FML 在类扫描阶段按 dist 过滤，**这是 Forge 1.20.1 最干净的客户端隔离手段**；需要在装配代码里做条件分支时用 `DistExecutor.runWhenOn(Dist.CLIENT, () -> ClientOnly::init)`（lambda 内的类才会被延迟解析）。

### 7.3 NeoForge（1.21.1 与 26.1.2）

**NeoForge 没有 `DistExecutor`**：`path=.mc-src/26.1.2/net/neoforged` 全树 grep `DistExecutor` **0 命中**（同一次检索里 `OnlyIn` 有 11 命中，全部来自 `common/OnlyInWarningsHandler.java`——那是**警告**你用 `@OnlyIn` 的类：`OnlyInWarningsHandler.java:39` 明确写着 `the runtime member-stripping behaviour of this annotation is no longer present`）。
NeoForge 也没有 `clientSideOnly` 这种清单开关（项目 `OreFoodNeoForge.java:31-36` 的注释已经把这条总结过）。

**树内可引用的用法证据（26.1.2）**：

| 用法 | 证据 |
|---|---|
| `@EventBusSubscriber(value = Dist.CLIENT, modid = ...)` | `net/neoforged/neoforge/client/BlockEntityRenderBoundsDebugRenderer.java:35`；`client/NeoForgeRenderPipelines.java:19`；`client/network/ClientPayloadHandler.java:61`；`model/data/ModelDataManager.java:32` |
| **`@Mod(value = "neoforge", dist = Dist.CLIENT)`** | `net/neoforged/neoforge/client/ClientNeoForgeMod.java:89` |
| `FMLEnvironment.getDist() == Dist.CLIENT` / `.isClient()` / `.isDedicatedServer()` | `internal/CommonModLoader.java:61`、`:77`；`server/ServerLifecycleHooks.java:98`；`client/TagConventionLogWarningClient.java:51` |
| `FMLEnvironment.isProduction()` | 多处（例如 `common/util/SelfTest.java:35`） |

**两个必须注意的 26.1.2 差异**：
1. **`FMLEnvironment.dist` 字段 → `FMLEnvironment.getDist()` 方法**（`CommonModLoader.java:61` 用的是 `getDist()`；项目注释 `OreFoodNeoForge.java:23-24` 也已记录此改动）。
2. `@EventBusSubscriber` 在树内实例全部不带 `bus = ...` 参数（默认游戏总线），例如 `ClientPayloadHandler.java:61` 只有 `(modid = ..., value = Dist.CLIENT)`。

**推荐写法**：

```java
// NeoForge 1.21.1 / 26.1.2 —— 公共监听器（不需要客户端类，最省事）
public final class ModTooltips {                        // 放 common，双端安全
    public static void onItemTooltip(ItemTooltipEvent event) { ... }   // net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
}
// 在 @Mod 构造器里（拿到游戏总线）：
NeoForge.EVENT_BUS.addListener(ModTooltips::onItemTooltip);

// 或用注解（客户端隔离版）：
@EventBusSubscriber(modid = OreFoodConstants.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeClientTooltips {
    @EventBusSubscriber  // 默认就是游戏总线
    ...
}
```

> 若必须引用客户端类（例如 `ClientTooltipFlag.of(flag)`），把它封进一个单独的 `@EventBusSubscriber(value = Dist.CLIENT)` 类；`@Mod` 主类里如需分支，用 `FMLEnvironment.getDist()` 判断后**在另一个类的静态方法里**做客户端装配——服务端不进该分支，JVM 懒解析就不会加载那个类（这正是 `OreFoodNeoForge.java:31-36` 注释描述的策略）。

---

## 8. `Component` / `MutableComponent` / `ChatFormatting` 在 26.1.2 相对 1.21.1 的变化

### 8.1 `ChatFormatting` —— **没有任何变化**

| | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 包名 | `net.minecraft` | `net.minecraft` | `net.minecraft` |
| 类名 | `ChatFormatting` | `ChatFormatting` | `ChatFormatting` |
| 枚举常量 | 22 个（`BLACK`…`RESET`） | 同 | 同 |

证据：`.mc-src/1.20.1/net/minecraft/ChatFormatting.java:1`、`.mc-src/1.21.1/net/minecraft/ChatFormatting.java:1`、`.mc-src/26.1.2/net/minecraft/ChatFormatting.java:1` 全为 `package net.minecraft;`；`Import` 侧证据：`.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:23` = `import net.minecraft.ChatFormatting;`，`.mc-src/26.1.2/net/minecraft/world/item/alchemy/PotionContents.java:12` = `import net.minecraft.ChatFormatting;`。
`public static final char PREFIX_CODE = '\u00a7';` 三版本均在。26.1.2 新增 `CODEC`/`COLOR_CODEC` 字段。

> ⚠️ **注意**：如果子调研/记忆里出现"26.1.2 把 `ChatFormatting` 从 `net.minecraft.network.chat` 移到 `net.minecraft`"的说法，那是**错的**——我从没在 `net.minecraft.network.chat` 下找到过它，三个版本都在 `net.minecraft`。glob `**/ChatFormatting.java` 三版本各自只命中 1 个文件。

### 8.2 `Component`

- 仍是 `public interface Component extends Message, FormattedText`，包名不变（`net.minecraft.network.chat`）。
- `getString()` **仍存在**，但已成为 `default` 方法（委托 `FormattedText.super.getString()`）；26.1.2 另加了 `getString(int limit)`。
- `getContents()` 仍存在，返回 `net.minecraft.network.chat.ComponentContents`。
- **`Component.literal(String)` / `Component.translatable(String)` / `Component.translatable(String, Object...)` / `Component.empty()` 仍是 static 工厂，签名不变**（见下）。
- 26.1.2 新增：`tryCollapseToString()`、`toFlatList()`、`contains(Component)`、`translatableEscape`、`translatableWithFallback`、`object(...)`、`translationArg(Identifier)`；`nbt(...)`/`score(...)`/`selector(...)` 改为接受 `net.minecraft.util.CompilableString`。

### 8.3 `MutableComponent`

仍是 `public final class MutableComponent implements Component`，包名不变。`append(String)`、`append(Component)`、`withStyle(ChatFormatting)`、`withStyle(ChatFormatting...)`、`withStyle(Style)`、`withStyle(UnaryOperator<Style>)`、`copy()` 全部保留；26.1.2 新增 `withoutShadow()`。

**→ 结论：对我们的 tooltip 代码而言，`Component`/`MutableComponent`/`ChatFormatting` 三版本写法完全一致，唯一需要改的 26.1.2 迁移项是 `ResourceLocation` → `Identifier`（若用到）与 `FMLEnvironment.dist` → `getDist()`。**

---

## 9. 落地建议：三版本"直接抄原版"的最小实现

### 9.1 共享思路

1. 5 个附魔各配一个"效果 + 时长 + 等级"的静态数据（建议用**每版本一份的映射**，因为常量名不同）。
2. 为每个附魔构造 `MobEffectInstance`（三版本构造器差异见 §4.3）。
3. 在 tooltip 里把这批 `MobEffectInstance` 转成文本：**优先直接调原版 `addPotionTooltip`**（§5.2 方案 1/3）。

### 9.2 三版本代码骨架

```java
// ---- 1.20.1 Forge ----
// 效果数据（裸 MobEffect）
private static final List<MobEffectInstance> EFFECTS = List.of(
    new MobEffectInstance(MobEffects.DAMAGE_BOOST,   600, 1),   // 力量 II (00:30)
    new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0),   // 速度
    new MobEffectInstance(MobEffects.REGENERATION,   200, 0),   // 生命恢复
    new MobEffectInstance(MobEffects.HEAL,            1, 0),    // 瞬间治疗（endsWithin(20) → 不显示时长）
    new MobEffectInstance(MobEffects.ABSORPTION,     600, 0),
    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0),
    new MobEffectInstance(MobEffects.SATURATION,       1, 0)
);

// 方式 1：在物品里（位置：名字之后、附魔之前）
@Override
public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
    PotionUtils.addPotionTooltip(EFFECTS, lines, 1.0F);   // 直接复用原版
}

// 方式 2：在事件里（游戏总线），插在 index 1
@Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TooltipHandler {
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!isOurFood(event.getItemStack())) return;
        event.getToolTip().addAll(1, buildEffectLines(event.getFlags()));
    }
}
```

```java
// ---- 1.21.1 NeoForge ----
private static final List<MobEffectInstance> EFFECTS = List.of(
    new MobEffectInstance(MobEffects.DAMAGE_BOOST,      600, 1),  // 注意：不是 STRENGTH
    new MobEffectInstance(MobEffects.MOVEMENT_SPEED,    600, 0),
    new MobEffectInstance(MobEffects.REGENERATION,      200, 0),
    new MobEffectInstance(MobEffects.HEAL,                1, 0),
    new MobEffectInstance(MobEffects.ABSORPTION,        600, 0),
    new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0),
    new MobEffectInstance(MobEffects.SATURATION,          1, 0)
);

@Override
public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
    PotionContents.addPotionTooltip(EFFECTS, lines::add, 1.0F, context.tickRate());  // 三参 + tickRate
}

// 事件侧：NeoForge.EVENT_BUS.addListener(...) 或 @EventBusSubscriber(value = Dist.CLIENT)
// 插入位置：event.getToolTip().addAll(1, ...)
// 也能拿到 event.getContext().tickRate()
```

```java
// ---- 26.1.2 NeoForge ----
private static final List<MobEffectInstance> EFFECTS = List.of(
    new MobEffectInstance(MobEffects.STRENGTH,        600, 1),   // 改名了
    new MobEffectInstance(MobEffects.SPEED,           600, 0),   // 改名了
    new MobEffectInstance(MobEffects.REGENERATION,    200, 0),
    new MobEffectInstance(MobEffects.INSTANT_HEALTH,    1, 0),   // 改名了
    new MobEffectInstance(MobEffects.ABSORPTION,      600, 0),
    new MobEffectInstance(MobEffects.RESISTANCE,      600, 0),   // 改名了
    new MobEffectInstance(MobEffects.SATURATION,        1, 0)
);

// 方式 1（会吃 @Deprecated，但位置最正）：重写 appendHoverText
@Override
@Deprecated
public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                            Consumer<Component> builder, TooltipFlag flag) {
    List<Component> tmp = new ArrayList<>();
    PotionContents.addPotionTooltip(EFFECTS, tmp::add, 1.0F, context.tickRate());
    tmp.forEach(builder);   // 或 builder.accept(PotionContents.getPotionDescription(...))
}

// 方式 2（推荐）：事件 + index 1
NeoForge.EVENT_BUS.addListener((ItemTooltipEvent event) -> {
    if (!isOurFood(event.getItemStack())) return;
    var ctx = event.getContext();
    List<Component> tmp = new ArrayList<>();
    PotionContents.addPotionTooltip(EFFECTS, tmp::add, 1.0F, ctx.tickRate());
    event.getToolTip().addAll(1, tmp);
});

// 方式 3（26.1.2 独有、位置语义最精确）：TooltipLocation.POST_CUSTOM
// 在 mod 总线上监听 RegisterTooltipAppendersEvent：
//   event.registerAppender(TooltipLocation.POST_CUSTOM, (stack, context, display, player, flag, builder) -> { ... });
```

### 9.3 显示层注意点

- `MobEffectInstance#endsWithin(20)` 为真时**不显示时长**（`!effect.endsWithin(20)` 才拼 `potion.withDuration`）——瞬间治疗/饱和若设 `duration = 1`，会显示成"瞬间治疗"而无 "(00:00)"，这与原版一致。
- 时长文本的 `scale` 参数：药水是 `1.0F`，药箭 `0.125F`，滞留药水 `0.25F`。**给附魔用 `1.0F`**（即显示真实秒数）。
- 行颜色由 `MobEffectCategory#getTooltipFormatting()` 决定（全部 7 个效果都是 `BENEFICIAL`，会是蓝色系）。

---

## 10. 未找到 / 需注意的边界

| 想找的东西 | 结果 | 搜过的关键字 / 说明 |
|---|---|---|
| `net.minecraftforge.fml.DistExecutor` 源码定义 | **未找到**（树内只有它的**用法**） | `net/minecraftforge/fml/` 下只有 `core/`、`event/{config,lifecycle}/`；`DistExecutor` 用法见 `ModStateProvider.java:5,34,46`、`ServerLifecycleHooks.java:39,92` |
| `EventBusSubscriber` 注解定义 | **未找到**（同上，只有用法） | 用法见 `ForgeSpawnEggItem.java:76`、`ClientForgeMod.java:19`、`ForgeHooksClient.java:1206`、`ModelDataManager.java:23` |
| NeoForge 的 `DistExecutor` | **确认不存在** | `path=.mc-src/26.1.2/net/neoforged` grep `DistExecutor` 0 命中 |
| 26.1.2 里名为 `GatherTooltipEvent` / `TooltipEvent` 的新类 | **未找到**；只有 `ItemTooltipEvent`（同名同包）+ `FluidTooltipEvent` + `RenderTooltipEvent.GatherComponents` | grep `GatherTooltip`、`TooltipEvent`（`path=.mc-src/26.1.2/net/neoforged/`） |
| `MobEffectUtil.getMobEffectDescriptionId` | **三版本均未找到** | grep `getMobEffectDescriptionId` 0 命中 |
| `MobEffectUtil.formatDuration` 的其它重载 | **未找到**，三版本各只有 1 个 | grep `formatDuration` |
| `MobEffectInstance.getDisplayName()` | **未找到** | 应走 `getEffect().value().getDisplayName()` / `getEffect().getDisplayName()`；grep `getDisplayName`（`path=.../world/effect`） |
| `PotionUtils`（1.21.1 / 26.1.2） | **未找到**，已被 `PotionContents` 取代 | grep `PotionUtils`（1.21.1、26.1.2 树各 0 命中） |
| 1.21.1 的 `PotionContents.getPotionDescription(...)` | **未找到**（26.1.2 才有） | grep `getPotionDescription`（`path=.mc-src/1.21.1/.../alchemy/PotionContents.java`） |
| `MobEffects.STRENGTH`（1.20.1 / 1.21.1） | **未找到**，应为 `DAMAGE_BOOST` | grep `STRENGTH\b`（命中均为 `Potions.STRENGTH` / `Attributes.JUMP_STRENGTH` 等） |
| 1.20.1 / 1.21.1 树的原版 `assets/minecraft/lang/en_us.json` | **不存在**，故 "II" / "No Effects" 字面文本只能在 26.1.2 引用 | 1.20.1 只有 `assets/forge/lang/en_us.json`；1.21.1 只有 `assets/{c,neoforge}/lang/en_us.json` |
| 罗马数字生成代码（`toRoman` / `Roman`） | **未找到**，等级文本全部来自语言文件 | grep `(?i)roman`、`toRoman` |
| `ArrowItem` 的 `appendHoverText`（1.21.1） | **未找到**，tipped arrow 仍在独立的 `TippedArrowItem` 里 | `TippedArrowItem.java:26` 才有实现 |
| 1.20.1 `ClientTooltipFlag` | **不存在**（NeoForge 1.21.1+ 独有） | glob `**/ClientTooltipFlag.java` 仅命中 1.21.1 与 26.1.2 |

---

## 11. 三版本速查表

| 项目 | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge |
|---|---|---|---|
| tooltip 事件 | `net.minecraftforge.event.entity.player.ItemTooltipEvent` | `net.neoforged.neoforge.event.entity.player.ItemTooltipEvent` | **同 1.21.1（未改名）** |
| 事件总线 | `MinecraftForge.EVENT_BUS`（游戏总线） | `NeoForge.EVENT_BUS`（游戏总线） | `NeoForge.EVENT_BUS` |
| 事件 post 点 | `ForgeEventFactory.java:272` | `EventHooks.java:440` | `EventHooks.java:458` |
| 事件触发点 | `ItemStack.java:791` | `ItemStack.java:840` | `ItemStack.java:916` |
| 事件 ctx | 无 `getContext()` | `getContext()` → `Item.TooltipContext` | `getContext()` → `Item.TooltipContext`（多了 `player()`/`isPeaceful()`） |
| `appendHoverText` | `(ItemStack, @Nullable Level, List<Component>, TooltipFlag)` `Item.java:284` | `(ItemStack, Item.TooltipContext, List<Component>, TooltipFlag)` `Item.java:332` | `@Deprecated (ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` `Item.java:342` |
| 插入"附魔之前" | 重写 `appendHoverText` 或 `getToolTip().addAll(1, ...)` | 同左 | 同左，或 `TooltipLocation.POST_CUSTOM` |
| 时长格式化 | `MobEffectUtil.formatDuration(inst, scale)` | `MobEffectUtil.formatDuration(inst, scale, ticksPerSecond)` | 同 1.21.1（逐字相同） |
| 效果段入口 | `PotionUtils.addPotionTooltip(effects, lines, scale)` | `PotionContents.addPotionTooltip(effects, lines::add, scale, tickRate)` | 同左（另有 `PotionContents.getPotionDescription(holder, amp)`） |
| `getEffect()` | `MobEffect` | `Holder<MobEffect>`（`.value()`） | `Holder<MobEffect>`（`.value()`） |
| 力量常量 | `MobEffects.DAMAGE_BOOST` | `MobEffects.DAMAGE_BOOST` | `MobEffects.STRENGTH` |
| 速度常量 | `MobEffects.MOVEMENT_SPEED` | `MobEffects.MOVEMENT_SPEED` | `MobEffects.SPEED` |
| 瞬间治疗常量 | `MobEffects.HEAL` | `MobEffects.HEAL` | `MobEffects.INSTANT_HEALTH` |
| 抗性提升常量 | `MobEffects.DAMAGE_RESISTANCE` | `MobEffects.DAMAGE_RESISTANCE` | `MobEffects.RESISTANCE` |
| `ChatFormatting` 包 | `net.minecraft` | `net.minecraft` | `net.minecraft`（**无变化**） |
| 客户端隔离 | `@EventBusSubscriber(value = Dist.CLIENT)` / `DistExecutor` | `@EventBusSubscriber(value = Dist.CLIENT)` / `@Mod(dist = Dist.CLIENT)` / `FMLEnvironment.getDist()`（**无 `DistExecutor`**） | 同 1.21.1（且 `FMLEnvironment.dist` 字段 → `getDist()`） |
| 本需求是否真需要客户端类 | **不需要**（监听器只用双端类；除非要 `ClientTooltipFlag`/`Minecraft`） | 同左 | 同左 |
