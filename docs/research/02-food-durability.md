# 02 · 食物组件、食用流程、耐久组件 与 Mixin 注入点（三版本调研）

> 目标需求：
> - **(A)** 让某种食物**食用时不消耗物品本身，而是消耗耐久**（耐久上限 0 = 无限耐久，永不消耗）。
> - **(B)** 给食物**追加额外饱食度**（在原版基础上加算）。
>
> 调研范围：`.mc-src/1.20.1`（MC 1.20.1 + Forge 47.4.5）、`.mc-src/1.21.1`（MC 1.21.1 + NeoForge 21.1.250）、
> `.mc-src/26.1.2`（MC 26.1.2 + NeoForge 26.1.2.109，`ResourceLocation` → `Identifier`）。
>
> **证据约定**：所有行号均以本仓库 `.mc-src/<版本>/...` 下的反编译源码为准，写作 `相对路径:行号`。
> 平台侧（Gradle / 加载器元数据）证据来自本机 Gradle 缓存中的真实 jar/pom 二进制核验与官方文档，
> 凡未找到的结论一律显式标注「未找到」。

---

## 0. 结论速查

| 项目 | 1.20.1 / Forge 47.4.5 | 1.21.1 / NeoForge 21.1.250 | 26.1.2 / NeoForge 26.1.2.109 |
| --- | --- | --- | --- |
| `FoodProperties` 形态 | `class` + `Builder` | `record` + `Builder` | `record` + `Builder`，**implement `ConsumableListener`** |
| 食物字段 | nutrition, saturationModifier, isMeat, canAlwaysEat, fastFood, effects | nutrition, saturation, canAlwaysEat, eatSeconds, usingConvertsTo, effects | **nutrition, saturation, canAlwaysEat**（其余迁到 `Consumable` 组件） |
| 食物挂载点 | `Item.Properties.food(...)` 字段 | `DataComponents.FOOD` | `DataComponents.FOOD` |
| `LivingEntity.eat` | **存在**，`eat(Level, ItemStack)` | **存在**，`eat(Level, ItemStack)`(final) + `eat(Level, ItemStack, FoodProperties)` | **不存在**（grep 未找到） |
| `Player.eat` | 存在（覆写） | 存在（覆写三参版本） | **不存在** |
| 消耗物品的那一行 | `LivingEntity.eat` → `shrink(1)` | `LivingEntity.eat(…,FoodProperties)` → `consume(1, this)` | `Consumable.onConsume` → `consume(1, user)` |
| 饱食度加算 | `FoodData.eat(int,float)` 内联 | `FoodData.add(int,float)` | `FoodData.add(int,float)` |
| 耐久组件 | **无组件系统**，NBT `Damage` + `Item.maxDamage` | `DataComponents.DAMAGE` / `MAX_DAMAGE` | 同左（API 重命名） |
| `hurtAndBreak` | `hurtAndBreak(int, T extends LivingEntity, Consumer<T>)` | `hurtAndBreak(int, ServerLevel, LivingEntity, Consumer<Item>)` / `(int, LivingEntity, EquipmentSlot)` | 同 1.21.1 + `hurtWithoutBreaking(int, Player)` |
| Mixin 声明方式 | **JAR 清单 `MixinConfigs`**（`mods.toml` **不支持** `[[mixins]]`） | `neoforge.mods.toml` → `[[mixins]] config="…"` | 同左（额外支持 `behaviorVersion`） |
| 加载器自带 Mixin | **0.8.5**（`org.spongepowered:mixin`） | **0.15.2+mixin.0.8.7**（`net.fabricmc:sponge-mixin`） | **0.17.3+mixin.0.8.7** |

---

## 1. `FoodProperties` 在三版本的定义

### 1.1 MC 1.20.1 —— `class`

文件：`.mc-src/1.20.1/net/minecraft/world/food/FoodProperties.java`

```
10: public class FoodProperties {
11:     private final int nutrition;
12:     private final float saturationModifier;
13:     private final boolean isMeat;
14:     private final boolean canAlwaysEat;
15:     private final boolean fastFood;
16:     private final List<Pair<Supplier<MobEffectInstance>, Float>> effects;
```

- 构造：**私有构造 + 公开静态内部 `Builder`**（`FoodProperties.java:64-111`）。
  - `Builder.nutrition(int)` :72、`saturationMod(float)` :77、`meat()` :82、`alwaysEat()` :87、`fast()` :92、
    `effect(Supplier<MobEffectInstance>, float)` :97、`build()` :108。
- 读取器：`getNutrition()` :37、`getSaturationModifier()` :41、`isMeat()` :45、`canAlwaysEat()` :49、
  `isFastFood()` :53、`getEffects()` :57。
- 注意：**`saturationModifier` 是"修饰系数"，不是绝对饱和度**；绝对饱和在 `FoodData` 里按 `nutrition * modifier * 2` 算出
  （见 §3）。没有 `eatSeconds`——食用时长由 `fastFood` 二值决定（16 vs 32 tick，见 §2.1）。

**挂载方式**：食物通过 `Item.Properties.food(FoodProperties)` 存在 `Item` 字段上，`Item` 是单例。

- `Item.getFoodProperties()` → `.mc-src/1.20.1/net/minecraft/world/item/Item.java:368`
- `Item.isEdible()` → `Item.java:362`
- Forge 的 `ItemStack` 便捷入口：`IForgeItemStack.getFoodProperties(@Nullable LivingEntity)` → `.mc-src/1.20.1/net/minecraftforge/common/extensions/IForgeItemStack.java:229-230`
  （默认实现转发到 `IForgeItem.getFoodProperties(ItemStack, LivingEntity)` → `IForgeItem.java:303-304`，默认又忽略 stack 直接返回 `item.getFoodProperties()`）
- `ItemStack.isEdible()` → `.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:995`

### 1.2 MC 1.21.1 —— `record`

文件：`.mc-src/1.21.1/net/minecraft/world/food/FoodProperties.java`

```
17: public record FoodProperties(
18:     int nutrition, float saturation, boolean canAlwaysEat, float eatSeconds,
19:     Optional<ItemStack> usingConvertsTo, List<FoodProperties.PossibleEffect> effects
20: ) {
```

- **是 record**。字段：`nutrition:int`、`saturation:float`（这里是**绝对饱和度**，不再是系数）、
  `canAlwaysEat:boolean`、`eatSeconds:float`、`usingConvertsTo:Optional<ItemStack>`、`effects:List<PossibleEffect>`。
- `DIRECT_CODEC` :21-31（`Codec` 字段名：`nutrition` / `saturation` / `can_always_eat` / `eat_seconds` /
  `using_converts_to` / `effects`），`DIRECT_STREAM_CODEC` :32-46。
- `eatDurationTicks()` :48-50 → `(int)(eatSeconds * 20.0F)`。
- 内嵌 `record PossibleEffect(Supplier<MobEffectInstance> effectSupplier, float probability)` :119-142。
- 构造：**公开静态内部 `Builder`**（:68-117）：`nutrition(int)` :76、`saturationModifier(float)` :81、
  `alwaysEdible()` :86、`fast()`（= 0.8s）:91、`effect(...)` :98/:103、`usingConvertsTo(ItemLike)` :108、`build()` :113。
  - `build()` :114 会把系数换算成绝对值：`FoodConstants.saturationByModifier(nutrition, saturationModifier)`。
- NeoForge 覆写了 `equals`（:52-66，注释 `// Neo: Fix MC-272643`）。

**挂载方式**：`DataComponents.FOOD` 组件。

- `.mc-src/1.21.1/net/minecraft/core/component/DataComponents.java:117-119`
  ```java
  public static final DataComponentType<FoodProperties> FOOD = register(
      "food", p_331418_ -> p_331418_.persistent(FoodProperties.DIRECT_CODEC).networkSynchronized(FoodProperties.DIRECT_STREAM_CODEC)
  );
  ```
- 读取入口：`IItemExtension.getFoodProperties(ItemStack, @Nullable LivingEntity)` 默认实现
  → `.mc-src/1.21.1/net/neoforged/neoforge/common/extensions/IItemExtension.java:749-751` = `stack.get(DataComponents.FOOD)`
- `ItemStack` 侧：`IItemStackExtension.getFoodProperties(@Nullable LivingEntity)` → `.mc-src/1.21.1/net/neoforged/neoforge/common/extensions/IItemStackExtension.java:464-465`
- `Item.getUseDuration(ItemStack, LivingEntity)` → `.mc-src/1.21.1/net/minecraft/world/item/Item.java:321-324`
- `Item.getUseAnimation(ItemStack)` → `Item.java:318`（`stack.has(DataComponents.FOOD) ? UseAnim.EAT : UseAnim.NONE`）
- **`ItemStack.isEdible()` 在 1.21.1 已不存在**（grep 未找到），改为判 `has(DataComponents.FOOD)`。
- **重要：1.21.1 没有 `DataComponents.CONSUMABLE`，也没有 `USE_REMAINDER`**（对 `DataComponents.java` 全文件 grep 未命中）。
  `Consumable` 组件是 MC 1.21.2 才引入的；1.21.1 里"餐具归还"仍由 `FoodProperties.usingConvertsTo` 承担（见 §2.2）。

### 1.3 MC 26.1.2 —— `record` + `ConsumableListener`

文件：`.mc-src/26.1.2/net/minecraft/world/food/FoodProperties.java`

```
20: public record FoodProperties(int nutrition, float saturation, boolean canAlwaysEat) implements ConsumableListener {
```

- **是 record，且只有 3 个字段**：`nutrition:int`、`saturation:float`、`canAlwaysEat:boolean`。
- 食用时长 / 音效 / 消耗粒子 / 用后转换 **全部搬到了独立的 `Consumable` 组件**。
- `FoodProperties` 直接 **实现 `ConsumableListener`**，因此它自己就是"食用完成后被回调的组件"：
  ```
  39:     @Override
  40:     public void onConsume(Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
  ...
  43:         if (user instanceof Player player) {
  44:             player.getFoodData().eat(this);
  ```
- 构造：`Builder`（:51-75）：`nutrition(int)` :56、`saturationModifier(float)` :61、`alwaysEdible()` :66、`build()` :71。
- `ConsumableListener` 接口：`.mc-src/26.1.2/net/minecraft/world/item/component/ConsumableListener.java`
  ```
  7: public interface ConsumableListener {
  8:     void onConsume(final Level level, final LivingEntity user, final ItemStack stack, final Consumable consumable);
  9: }
  ```

`Consumable` 组件本体：`.mc-src/26.1.2/net/minecraft/world/item/component/Consumable.java`

```
32: public record Consumable(
33:     float consumeSeconds, ItemUseAnimation animation, Holder<SoundEvent> sound,
34:     boolean hasConsumeParticles, List<ConsumeEffect> onConsumeEffects
35: ) {
```
- `startConsuming(LivingEntity, ItemStack, InteractionHand) : InteractionResult` :62-75
- `onConsume(Level, LivingEntity, ItemStack) : ItemStack` :77-93 ← **(A) 的关键点，见 §2.3 / §6**
- `canConsume(LivingEntity, ItemStack)` :95-98
- `consumeTicks()` :100-102
- `builder()` :126；`OverrideConsumeSound` 接口 :174-176

`DataComponents` 常量：`.mc-src/26.1.2/net/minecraft/core/component/DataComponents.java`
```
167:     public static final DataComponentType<FoodProperties> FOOD = register("food", ...);
170:     public static final DataComponentType<Consumable> CONSUMABLE = register("consumable", ...);
173:     public static final DataComponentType<UseRemainder> USE_REMAINDER = register("use_remainder", ...);
176:     public static final DataComponentType<UseCooldown> USE_COOLDOWN = register("use_cooldown", ...);
```
- **`getFoodProperties(...)` 在 26.1.2 已完全移除**（全仓库 grep 仅命中 1.20.1 / 1.21.1），一律写作 `stack.get(DataComponents.FOOD)`。

---

## 2. 物品"食用完成"的完整调用链（三版本）

### 2.1 MC 1.20.1

| 步骤 | 类#方法 | 签名 | 位置 |
| --- | --- | --- | --- |
| 1 | `ItemStack#use` | `InteractionResultHolder<ItemStack> use(Level, Player, InteractionHand)` | `.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:283-285` |
| 2 | `Item#use` | `InteractionResultHolder<ItemStack> use(Level, Player, InteractionHand)` | `.mc-src/1.20.1/net/minecraft/world/item/Item.java:147-159` |
| 3 | 判可食 + `startUsingItem` | `itemstack.isEdible()` :149；`canEat(...)` :150；`startUsingItem` :151 | 同上 |
| 4 | 食用时长 | `Item#getUseDuration(ItemStack)` :273-277（`isFastFood() ? 16 : 32`） | `Item.java:273` |
| 5 | `LivingEntity#updateUsingItem` | `protected void updateUsingItem(ItemStack)` | `.mc-src/1.20.1/net/minecraft/world/entity/LivingEntity.java:2926` 起 |
| 6 | **触发完成（仅服务端）** | `if (--this.useItemRemaining <= 0 && !this.level().isClientSide && !p_147201_.useOnRelease()) this.completeUsingItem();` | `LivingEntity.java:2943-2945` |
| 7 | `LivingEntity#completeUsingItem` | `protected void completeUsingItem()` | `LivingEntity.java:3056-3074`（`ForgeEventFactory.onItemUseFinish(...)` :3064） |
| 8 | `ItemStack#finishUsingItem` | `ItemStack finishUsingItem(Level, LivingEntity)` | `ItemStack.java:287-289` |
| 9 | `Item#finishUsingItem` | `ItemStack finishUsingItem(ItemStack, Level, LivingEntity)` | `Item.java:161-163` → `this.isEdible() ? p_41411_.eat(p_41410_, p_41409_) : p_41409_` |
| 10 | `Player#eat`（覆写） | `ItemStack eat(Level, ItemStack)` | `.mc-src/1.20.1/net/minecraft/world/entity/player/Player.java:2156-2167` |
| 10a | **加饱食度** | `this.getFoodData().eat(p_36186_.getItem(), p_36186_, this);` | `Player.java:2157` |
| 10b | 统计/音效/进度 | `awardStat` :2158、`PLAYER_BURP` :2159-2161、`CONSUME_ITEM` :2162-2164 | `Player.java:2158-2164` |
| 10c | 转发父类 | `return super.eat(p_36185_, p_36186_);` | `Player.java:2166` |
| 11 | `LivingEntity#eat` | `ItemStack eat(Level, ItemStack)` | `.mc-src/1.20.1/net/minecraft/world/entity/LivingEntity.java:3329-3350` |
| 12 | 进食音效 | `playSound(... getEatingSound(p_21068_) ...)` | `LivingEntity.java:3331-3340` |
| 13 | 效果 | `this.addEatEffect(p_21068_, p_21067_, this);` | `LivingEntity.java:3341`（实现 :3352-3361） |
| 14 | ▶ **消耗物品** ◀ | `if (!(this instanceof Player) || !((Player)this).getAbilities().instabuild) { p_21068_.shrink(1); }` | **`LivingEntity.java:3342-3344`** |
| 15 | `gameEvent(GameEvent.EAT)` | | `LivingEntity.java:3346` |
| 16 | `FoodData#eat` | `void eat(Item, ItemStack, @Nullable LivingEntity)` | `.mc-src/1.20.1/net/minecraft/world/food/FoodData.java:33-38` → 转 `eat(int,float)` :23-26 |

**1.20.1 的消耗点：`LivingEntity.java:3343` 的 `p_21068_.shrink(1)`，且第 3342 行已经内置创造模式（`instabuild`）判断。**
注意 `Player.eat`（第 2157 行）**先加饱食度**，再 `super.eat`（第 2166 行）**后消耗物品**——顺序对 (A)(B) 的注入点选择很关键。

### 2.2 MC 1.21.1

| 步骤 | 类#方法 | 签名 | 位置 |
| --- | --- | --- | --- |
| 1 | `ItemStack#use` | `InteractionResultHolder<ItemStack> use(Level, Player, InteractionHand)` | `.mc-src/1.21.1/net/minecraft/world/item/ItemStack.java:399` |
| 2 | `Item#use` | `InteractionResultHolder<ItemStack> use(Level, Player, InteractionHand)` | `.mc-src/1.21.1/net/minecraft/world/item/Item.java:165-178` |
| 3 | 取食物 + 判可食 | `FoodProperties foodproperties = itemstack.getFoodProperties(player);` :167；`player.canEat(...)` :169 | `Item.java:167-174` |
| 4 | 食用时长 | `Item#getUseDuration(ItemStack, LivingEntity)` :321-324 → `foodproperties.eatDurationTicks()` | `Item.java:321` |
| 5 | `LivingEntity#updateUsingItem` | `protected void updateUsingItem(ItemStack)` | `.mc-src/1.21.1/net/minecraft/world/entity/LivingEntity.java:3151-3163` |
| 6 | **触发完成（仅服务端）** | `if (--this.useItemRemaining <= 0 && !this.level().isClientSide && !usingItem.useOnRelease()) this.completeUsingItem();` | `LivingEntity.java:3160-3162` |
| 7 | `LivingEntity#completeUsingItem` | `protected void completeUsingItem()` | `LivingEntity.java:3271-3290`（`EventHooks.onItemUseFinish(...)` :3280） |
| 8 | `ItemStack#finishUsingItem` | `ItemStack finishUsingItem(Level, LivingEntity)` | `ItemStack.java:406-408` |
| 9 | `Item#finishUsingItem` | `ItemStack finishUsingItem(ItemStack, Level, LivingEntity)` | `.mc-src/1.21.1/net/minecraft/world/item/Item.java:183-186` → `livingEntity.eat(level, stack, foodproperties)` |
| 10 | `LivingEntity#eat`（二参，`final`） | `public final ItemStack eat(Level, ItemStack)` | `LivingEntity.java:3546-3549` |
| 11 | `LivingEntity#eat`（三参） | `public ItemStack eat(Level, ItemStack, FoodProperties)` | **`LivingEntity.java:3551-3566`** |
| 12 | 进食音效 | `level.playSound(... this.getEatingSound(food) ...)` | `LivingEntity.java:3552-3561` |
| 13 | 效果 | `this.addEatEffect(foodProperties);` | `LivingEntity.java:3562`（实现 :3568-3576） |
| 14 | ▶ **消耗物品** ◀ | `food.consume(1, this);` | **`LivingEntity.java:3563`** |
| 15 | `gameEvent(GameEvent.EAT)` | | `LivingEntity.java:3564` |

**1.21.1 的消耗点：`LivingEntity.java:3563` 的 `food.consume(1, this)`。**
`consume` 本身已内含创造模式判断：
```
.mc-src/1.21.1/net/minecraft/world/item/ItemStack.java:1089-1093
public void consume(int amount, @Nullable LivingEntity entity) {
    if (entity == null || !entity.hasInfiniteMaterials()) {
        this.shrink(amount);
    }
}
```

`Player#eat(Level, ItemStack, FoodProperties)` → `.mc-src/1.21.1/net/minecraft/world/entity/player/Player.java:2181-2210`：
```
2182:         this.getFoodData().eat(foodProperties);          // ← 加饱食度
2183:         this.awardStat(Stats.ITEM_USED.get(food.getItem()));
2184-2186:   PLAYER_BURP
2187-2189:   CriteriaTriggers.CONSUME_ITEM
2191:         ItemStack itemstack = super.eat(level, food, foodProperties);   // ← 消耗物品 + 效果
2192-2204:   usingConvertsTo（餐具归还；`!this.hasInfiniteMaterials()` 时）
```
> 注意：1.21.1 的 **`LivingEntity.eat(Level, ItemStack)` 是 `final`**，不能被 Mixin 覆写；要注入必须用
> `@Inject` / `@Redirect` 或瞄准三参重载。

### 2.3 MC 26.1.2（架构已重构）

**26.1.2 已经没有 `LivingEntity.eat` / `Player.eat`**（对 `.mc-src/26.1.2/net/minecraft/world/entity/` 递归 grep
`public ItemStack eat` 未命中）。食用完成被拆成 `Consumable` 组件的 `onConsume`：

| 步骤 | 类#方法 | 签名 | 位置 |
| --- | --- | --- | --- |
| 1 | `ItemStack#use` | `InteractionResult use(Level, Player, InteractionHand)`（返回 `InteractionResult`，不再是 `InteractionResultHolder`） | `.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java:396-407` |
| 2 | `Item#use` | `InteractionResult use(Level, Player, InteractionHand)` | `.mc-src/26.1.2/net/minecraft/world/item/Item.java:207-230` |
| 3 | 取 `Consumable` 并开始 | `Consumable consumable = stack.get(DataComponents.CONSUMABLE);` :209 → `consumable.startConsuming(player, stack, hand)` :211 | `Item.java:209-211` |
| 4 | 立即食用分支 | `Consumable.startConsuming` :62-75：`consumeTicks() > 0 ? startUsingItem : onConsume(...)` | `.mc-src/26.1.2/net/minecraft/world/item/component/Consumable.java:62-75` |
| 5 | `LivingEntity#updateUsingItem` | `protected void updateUsingItem(ItemStack)` | `.mc-src/26.1.2/net/minecraft/world/entity/LivingEntity.java:3503-3511` |
| 6 | **触发完成（仅服务端）** | `if (--this.useItemRemaining <= 0 && !this.level().isClientSide() && !useItem.useOnRelease()) this.completeUsingItem();` | `LivingEntity.java:3508-3510` |
| 7 | `LivingEntity#completeUsingItem` | `protected void completeUsingItem()` | `LivingEntity.java:3602-3619`（`EventHooks.onItemUseFinish(...)` :3610） |
| 8 | `ItemStack#finishUsingItem` | `ItemStack finishUsingItem(Level, LivingEntity)` | `ItemStack.java:409-413`（**之后**还会跑 `applyAfterUseComponentSideEffects` :412 / :415-429，处理 `USE_REMAINDER`、`USE_COOLDOWN`） |
| 9 | `Item#finishUsingItem` | `ItemStack finishUsingItem(ItemStack, Level, LivingEntity)` | `.mc-src/26.1.2/net/minecraft/world/item/Item.java:232-235` → `consumable.onConsume(level, entity, itemStack)` |
| 10 | `Consumable#onConsume` | `ItemStack onConsume(Level, LivingEntity, ItemStack)` | **`Consumable.java:77-93`** |
| 10a | 粒子/音效 | `this.emitParticlesAndSounds(random, user, stack, 16);` | `Consumable.java:79`（实现 :104-117） |
| 10b | 统计/进度 | `serverPlayer.awardStat(...)` :81、`CriteriaTriggers.CONSUME_ITEM.trigger(...)` :82 | `Consumable.java:80-83` |
| 10c | ▶ **回调所有 `ConsumableListener` 组件** ◀ | `stack.getAllOfType(ConsumableListener.class).forEach(component -> component.onConsume(level, user, stack, this));` | **`Consumable.java:85`** ← `FoodProperties`（实现该接口）在此处加饱食度 |
| 10c' | 加饱食度（在 `FoodProperties` 内） | `player.getFoodData().eat(this);` | `.mc-src/26.1.2/net/minecraft/world/food/FoodProperties.java:44` |
| 10d | `ConsumeEffect` 列表（仅服务端） | `if (!level.isClientSide()) { this.onConsumeEffects.forEach(action -> action.apply(level, stack, user)); }` | `Consumable.java:86-88` |
| 10e | `gameEvent(EAT/DRINK)` | :90 | `Consumable.java:90` |
| 10f | ▶ **消耗物品** ◀ | `stack.consume(1, user);` | **`Consumable.java:91`** |
| 11 | `FoodData#eat` | `void eat(FoodProperties)` | `.mc-src/26.1.2/net/minecraft/world/food/FoodData.java:28-30` |

`ItemStack.consume(int, @Nullable LivingEntity)` 仍在同一位置守卫创造模式：
```
.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java:1172-1176
public void consume(int amount, @Nullable LivingEntity owner) {
    if (owner == null || !owner.hasInfiniteMaterials()) {
        this.shrink(amount);
    }
}
```

**调用链一句话总结**

- **1.20.1**：`ItemStack.use` → `Item.use` →(右键持续 32/16t)→ `LivingEntity.updateUsingItem` →`(仅服务端)`→ `LivingEntity.completeUsingItem` → `ItemStack.finishUsingItem` → `Item.finishUsingItem` → `Player.eat`（**加饱食度** :2157）→ `LivingEntity.eat`（**shrink(1)** :3343 + 效果）→ `FoodData.eat`。
- **1.21.1**：`ItemStack.use` → `Item.use` →(持续 eatSeconds)→ `updateUsingItem` →`(仅服务端)`→ `completeUsingItem` → `ItemStack.finishUsingItem` → `Item.finishUsingItem` → `LivingEntity.eat(3参)`；其中 `Player.eat`（**加饱食度** :2182）先于 `super.eat`（**consume(1,this)** :3563 + 效果）。
- **26.1.2**：`ItemStack.use` → `Item.use` → `Consumable.startConsuming` →(持续 consumeSeconds；或 `consumeSeconds==0` 时**立即**在 `startConsuming` 内直接 `onConsume`)→ `updateUsingItem` →`(仅服务端)`→ `completeUsingItem` → `ItemStack.finishUsingItem` → `Item.finishUsingItem` → `Consumable.onConsume`（**回调 ConsumableListener → FoodProperties.onConsume 加饱食度** :85/:44）→ `stack.consume(1,user)` :91 → `ItemStack.applyAfterUseComponentSideEffects`（`USE_REMAINDER` / `USE_COOLDOWN`）。

---

## 3. 饱食度增加的具体代码位置 与 (B) 的最佳注入点

### 3.1 加算点

| 版本 | 最终加算方法 | 代码 | 位置 |
| --- | --- | --- | --- |
| 1.20.1 | `FoodData#eat(int, float)` | `this.foodLevel = Math.min(p_38708_ + this.foodLevel, 20);`<br>`this.saturationLevel = Math.min(this.saturationLevel + p_38708_ * p_38709_ * 2.0F, (float)this.foodLevel);` | `.mc-src/1.20.1/net/minecraft/world/food/FoodData.java:23-26`（第 **24** 行 = 饥饿值，第 **25** 行 = 饱和度） |
| 1.21.1 | `FoodData#add(int, float)`（**私有**） | `this.foodLevel = Mth.clamp(foodLevel + this.foodLevel, 0, 20);`<br>`this.saturationLevel = Mth.clamp(saturationLevel + this.saturationLevel, 0.0F, (float)this.foodLevel);` | `.mc-src/1.21.1/net/minecraft/world/food/FoodData.java:20-23`（第 **21** / **22** 行） |
| 26.1.2 | `FoodData#add(int, float)`（**私有**） | 同上 | `.mc-src/26.1.2/net/minecraft/world/food/FoodData.java:19-22`（第 **20** / **21** 行） |

上层入口：

- 1.20.1：`FoodData.eat(int,float)` :23；`FoodData.eat(Item, ItemStack, @Nullable LivingEntity)` :33-38
  （`eat(foodproperties.getNutrition(), foodproperties.getSaturationModifier())` :36）——由 `Player.eat` :2157 调用。
- 1.21.1：`FoodData.eat(int, float)` :28-30 → `add(foodLevelModifier, FoodConstants.saturationByModifier(...))`；
  `FoodData.eat(FoodProperties)` :32-34 → `add(fp.nutrition(), fp.saturation())`——由 `Player.eat` :2182 调用。
- 26.1.2：`FoodData.eat(int, float)` :24-26；`FoodData.eat(FoodProperties)` :28-30——由 `FoodProperties.onConsume` :44 调用。

**"原版加算"公式**（三版本一致，见各版本 `FoodConstants`）：
`FoodConstants.saturationByModifier(int foodLevel, float modifier) = foodLevel * modifier * 2.0F`
（1.21.1 `.mc-src/1.21.1/net/minecraft/world/food/FoodConstants.java:30-32`；26.1.2 同文件 :30；1.20.1 无此方法，
公式内联在 `FoodData.java:25`）。上限 `MAX_FOOD = 20`、`MAX_SATURATION = 20.0F`（`FoodConstants.java:4-5`，三版本相同），
且 1.21+ 用 `Mth.clamp(..., 0.0F, this.foodLevel)` 把饱和度钳到当前饥饿值。

### 3.2 (B) 最佳注入点：「额外 n*2 点饱食度」

**推荐：注入 `FoodData` 的**最终加算原语**，用 `@ModifyVariable(argsOnly = true)` 给参数加码。**

| 版本 | 目标方法（含描述符） | 注入方式 |
| --- | --- | --- |
| 1.20.1 | `net.minecraft.world.food.FoodData#eat(IF)V` | `@ModifyVariable(method = "eat(IF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0) int` → `+n`；`ordinal = 1`（float）→ `+2n` |
| 1.21.1 | `net.minecraft.world.food.FoodData#add(IF)V`（private） | 同上，两个 `@ModifyVariable`（ordinal 0 = int foodLevel，ordinal 1 = float saturation） |
| 26.1.2 | `net.minecraft.world.food.FoodData#add(IF)V`（private） | 同上 |

理由：

1. **`add` / `eat(int,float)` 是三版本里唯一的最终加算点**，无论调用方是 `Player.eat`（1.20.1/1.21.1）还是
   `FoodProperties.onConsume`（26.1.2）都必然经过它，一处注入即可覆盖玩家、狐狸、狼等所有食用路径。
2. 只改这一个方法，不会与其它模组在 `Player.eat` 上的注入打架。
3. **不想放大饱和度**时（即"只加饥饿值，不按 modifier 比例放大"），换成在**同一方法的 `TAIL`** 追加一次
   显式调用更可控：
   - 1.20.1 / 1.21.1 / 26.1.2 均有 `public void eat(int, float)` 公开入口，
     调用 `((FoodData)(Object)this).eat(n, 1.0F)` 会得到 `foodLevel += n`、`saturation += n * 1.0F * 2.0F = 2n`
     —— 正好是「额外 n*2 点饱食度」。
   - ⚠️ 若注入目标是 `add(IF)V` 本身，**不能在 `add` 内再调 `eat(...)`**（`eat` 会转调 `add` → 无限递归）。
     此时应改注入 `eat(FoodProperties)V` / `eat(IF)V` 的 `TAIL`，或直接在 `add` 的 `TAIL` 里操作
     `this.foodLevel` / `this.saturationLevel` 字段（需要 Mixin 访问私有字段，或使用 `@Shadow`）。

**另一种等价且更"语义化"的做法（26.1.2 专属，无需 Mixin）**：因为 `ConsumableListener` 是**公开可实现的接口**，
且 `Consumable.onConsume` 会在 `Consumable.java:85` 回调 `stack.getAllOfType(ConsumableListener.class)`，
可以注册一个**自定义数据组件**实现 `ConsumableListener`，在其中 `player.getFoodData().eat(n, 1.0F)`。
但注意：**它不能阻止第 91 行的 `stack.consume(1, user)`**——所以 (A) 仍然必须靠 Mixin 拦那一行。

**回到"额外 n 点饥饿值"还是"额外 2n 点饱和度"**：题目表述为「额外 n*2 点饱食度」。按 MC 内部命名，
`FoodData.foodLevel` 是饥饿值（0–20 半格），`saturationLevel` 是饱和度（0–20）。
`eat(n, 1.0F)` 的净效果就是 **`foodLevel += n`、`saturation += 2n`**，与"额外 n*2 点"完全吻合，因此推荐直接用
`eat(n, 1.0F)` 而不是拆开两个 `@ModifyVariable`。

---

## 4. 三版本 `DataComponents` / 耐久 / 食物 组件常量

### 4.1 MC 1.20.1 —— **没有组件系统**

对 `.mc-src/1.20.1/net/minecraft/core/component/` 做 glob：**目录不存在，未找到任何文件**。
耐久与堆叠信息存在 `ItemStack` 的字段 / NBT 上：

| 需求 | API | 位置 |
| --- | --- | --- |
| 最大堆叠 | `ItemStack.getMaxStackSize()` → `Item.getMaxStackSize(ItemStack)` | `.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:307-309`；`Item.getMaxStackSize()` :166 |
| 最大耐久 | `ItemStack.getMaxDamage()` → `Item.getMaxDamage(ItemStack)`（Forge 默认转发无参版） | `ItemStack.java:336-338`；`IForgeItem.getMaxDamage(ItemStack)` `.mc-src/1.20.1/net/minecraftforge/common/extensions/IForgeItem.java:176-178`；`Item.getMaxDamage()` :171 |
| 当前损伤 | `ItemStack.getDamageValue()` / `setDamageValue(int)` | `ItemStack.java:328-330` / :332-334（内部即 `Item.getDamage(ItemStack)` / `Item.setDamage(ItemStack,int)`） |
| 是否可损坏 | `ItemStack.isDamageableItem()` | `ItemStack.java:315-322`（`item.isDamageable(stack)` 且 NBT 无 `Unbreakable`） |
| 不可破坏标记 | NBT `Unbreakable`（boolean） | 同上 :317-318 |
| 是否已损坏 | `ItemStack.isDamaged()` | `ItemStack.java:324-326` |
| 食物 | `Item.getFoodProperties()` / `ItemStack.getFoodProperties(LivingEntity)` | `Item.java:368`；`IForgeItemStack.java:229` |
| 是否可食 | `ItemStack.isEdible()` / `Item.isEdible()` | `ItemStack.java:995-997`；`Item.java:362` |

要"给食物加耐久"，在 1.20.1 必须让该 `Item` 用 `Item.Properties.durability(int)` 注册（`maxDamage > 0`，
`Item.canBeDepleted()` → `Item.java:175-177`）。

### 4.2 MC 1.21.1 —— 组件系统

文件：`.mc-src/1.21.1/net/minecraft/core/component/DataComponents.java`

```
58:     public static final DataComponentType<Integer> MAX_STACK_SIZE = register("max_stack_size", ...);
61:     public static final DataComponentType<Integer> MAX_DAMAGE     = register("max_damage", ...);
64:     public static final DataComponentType<Integer> DAMAGE         = register("damage", ...);
67:     public static final DataComponentType<Unbreakable> UNBREAKABLE = register("unbreakable", ...);
117:    public static final DataComponentType<FoodProperties> FOOD     = register("food", ...);
```
- **`CONSUMABLE` / `USE_REMAINDER` / `USE_COOLDOWN` 在 1.21.1 未找到**（对同文件全文 grep 未命中）。

获取方式（`DataComponentHolder` 默认方法，`.mc-src/1.21.1/net/minecraft/core/component/DataComponentHolder.java:5-20`）：
```java
@Nullable default <T> T get(DataComponentType<? extends T> component);
             default <T> T getOrDefault(DataComponentType<? extends T> c, T defaultValue);
             default boolean has(DataComponentType<?> component);
```

`ItemStack` 侧：
- `getDamageValue()` :448、`setDamageValue(int)` :452、`getMaxDamage()` :456（→ `Item.getMaxDamage(ItemStack)`）
- `isDamageableItem()` :440-442 = `has(MAX_DAMAGE) && !has(UNBREAKABLE) && has(DAMAGE)`
- `getMaxStackSize()` :436 附近 / `isStackable()` :437
- 常用读取：`stack.get(DataComponents.FOOD)`（经 `IItemExtension.java:750`）、`stack.has(DataComponents.FOOD)`（`Item.java:318`）

### 4.3 MC 26.1.2 —— 组件系统（API 重命名）

文件：`.mc-src/26.1.2/net/minecraft/core/component/DataComponents.java`

```
109:    public static final DataComponentType<Integer> MAX_STACK_SIZE = register("max_stack_size", ...);
112:    public static final DataComponentType<Integer> MAX_DAMAGE     = register("max_damage", ...);
115:    public static final DataComponentType<Integer> DAMAGE         = register("damage", ...);
118:    public static final DataComponentType<Unit> UNBREAKABLE       = register("unbreakable", ...);
119:    public static final DataComponentType<UseEffects> USE_EFFECTS = register("use_effects", ...);
167:    public static final DataComponentType<FoodProperties> FOOD    = register("food", ...);
170:    public static final DataComponentType<Consumable> CONSUMABLE  = register("consumable", ...);
173:    public static final DataComponentType<UseRemainder> USE_REMAINDER = register("use_remainder", ...);
176:    public static final DataComponentType<UseCooldown> USE_COOLDOWN   = register("use_cooldown", ...);
```
- `UNBREAKABLE` 的类型从 1.21.1 的 `Unbreakable` 变成了 **`Unit`**（:118）。
- `ITEM_MODEL` 的类型是 `Identifier`（:134）——对应题目提到的「26.1.2 `ResourceLocation` 改名 `Identifier`」。

`ItemStack` 侧（`.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java`）：
```
103: public final class ItemStack implements DataComponentHolder, ItemInstance, ... {
433:     public int getMaxStackSize()
437:     public boolean isStackable()
449:     public int getDamageValue()
453:     public void setDamageValue(int value)
457:     public int getMaxDamage()          → this.getItem().getMaxDamage(this)
462:     public boolean isBroken()
465:     public boolean nextDamageWillBreak()
```
读取：`stack.get(DataComponents.CONSUMABLE)`（`Item.java:209`）、`stack.get(DataComponents.FOOD)`（`Consumable.java:96`）、
`stack.get(DataComponents.USE_REMAINDER)`（`ItemStack.java:416`）。

---

## 5. `ItemStack.hurtAndBreak(...)` 三版本签名对比

### 5.1 MC 1.20.1 —— **泛型 + Consumer<T>，无 ServerLevel 参数**

```
.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java
370:    public <T extends LivingEntity> void hurtAndBreak(int p_41623_, T p_41624_, Consumer<T> p_41625_) {
371:        if (!p_41624_.level().isClientSide && (!(p_41624_ instanceof Player) || !((Player)p_41624_).getAbilities().instabuild) && this.isDamageableItem()) {
372:            p_41623_ = this.getItem().damageItem(this, p_41623_, p_41624_, p_41625_);
373:            if (this.hurt(p_41623_, p_41624_.getRandom(), p_41624_ instanceof ServerPlayer ? (ServerPlayer)p_41624_ : null)) {
374:                p_41625_.accept(p_41624_);
375:                Item item = this.getItem();
376:                this.shrink(1);
377:                if (p_41624_ instanceof Player) { ((Player)p_41624_).awardStat(Stats.ITEM_BROKEN.get(item)); }
381:                this.setDamageValue(0);
```
- 仅有 **一个** `hurtAndBreak` 重载：`hurtAndBreak(int, T, Consumer<T>)`，`T extends LivingEntity`。
- 参考：`hurt(int, RandomSource, @Nullable ServerPlayer)` :340-368（内含 `Enchantments.UNBREAKING` 判定 :345）。
- 校验顺序：`isClientSide` / `instabuild` / `isDamageableItem()` **都在 `hurtAndBreak` 内部**（:371），
  **创造模式天然不掉耐久**。
- `setDamageValue(int)` :332、`getDamageValue()` :328、`getMaxDamage()` :336、`shrink(int)` :982。

**1.20.1 的 `da` 用法示例**：`stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND))`。

### 5.2 MC 1.21.1 —— 引入 `ServerLevel`，Consumer 参数类型从 `T` 变 `Item`

```
.mc-src/1.21.1/net/minecraft/world/item/ItemStack.java
460:    public void hurtAndBreak(int damage, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak)
464:    public void hurtAndBreak(int p_220158_, ServerLevel p_346256_, @Nullable LivingEntity p_220160_, Consumer<Item> p_348596_) {
465:        if (this.isDamageableItem()) {
466:            p_220158_ = getItem().damageItem(this, p_220158_, p_220160_, p_348596_);
467:            if (p_220160_ == null || !p_220160_.hasInfiniteMaterials()) {
469:                    p_220158_ = EnchantmentHelper.processDurabilityChange(p_346256_, this, p_220158_);
479:                int i = this.getDamageValue() + p_220158_;
480:                this.setDamageValue(i);
481:                if (i >= this.getMaxDamage()) { this.shrink(1); p_348596_.accept(item); }
490:    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot) {   // 最常用
501:    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike item, LivingEntity entity, EquipmentSlot slot)
```
- 新增 `hurtAndBreak(int, LivingEntity, EquipmentSlot)`（:490）作为日常入口，内部要求 `entity.level() instanceof ServerLevel`（:491）——
  **客户端调用它是空操作**。
- 创造模式判断改用 `hasInfiniteMaterials()`（:467）。
- `EnchantmentHelper.processDurabilityChange(ServerLevel, ItemStack, int)` → `.mc-src/1.21.1/.../EnchantmentHelper.java:104`。

### 5.3 MC 26.1.2 —— 同 1.21.1，另有"不会破坏"的变体

```
.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java
469:    public void hurtAndBreak(int amount, ServerLevel level, @Nullable ServerPlayer player, Consumer<Item> onBreak)
473:    public void hurtAndBreak(int amount, ServerLevel level, @Nullable LivingEntity player, Consumer<Item> onBreak) {
474:        amount = getItem().damageItem(this, amount, player, onBreak);
475:        int newAmount = this.processDurabilityChange(amount, level, player);
476:        if (newAmount != 0) { this.applyDamage(this.getDamageValue() + newAmount, player, onBreak); }
485:    private int processDurabilityChange(int amount, ServerLevel level, @Nullable LivingEntity player) {
486:        if (!this.isDamageableItem()) return 0;
488:        else if (player != null && player.hasInfiniteMaterials()) return 0;
491:        else return amount > 0 ? EnchantmentHelper.processDurabilityChange(level, this, amount) : amount;
495:    private void applyDamage(int newDamage, @Nullable ServerPlayer player, Consumer<Item> onBreak)
499:    private void applyDamage(int newDamage, @Nullable LivingEntity player, Consumer<Item> onBreak) {
504:        this.setDamageValue(newDamage);
505:        if (this.isBroken()) { Item item = this.getItem(); this.shrink(1); onBreak.accept(item); }
512:    public void hurtWithoutBreaking(int amount, Player player) {      // ★ 新增：钳在 maxDamage-1，永不破碎
513:        if (player instanceof ServerPlayer serverPlayer) {
519:            int newDamage = Math.min(this.getDamageValue() + newAmount, this.getMaxDamage() - 1);
520:            this.applyDamage(newDamage, serverPlayer, i -> {});
524:    public void hurtAndBreak(int amount, LivingEntity owner, InteractionHand hand)
528:    public void hurtAndBreak(int amount, LivingEntity owner, EquipmentSlot slot)
536:    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike newItem, LivingEntity owner, EquipmentSlot slot)
```
- **`hurtAndBreak(int, LivingEntity, InteractionHand)`（:524）是新增的便捷入口**（1.21.1 只有 `EquipmentSlot` 版本）。
- **`hurtWithoutBreaking(int, Player)`（:512）非常适合需求 (A)**：它把伤害钳在 `getMaxDamage() - 1`，
  物品永远不会被销毁；且只在 `ServerPlayer` 上生效（:513）。

### 5.4 差异速查

| | 1.20.1 | 1.21.1 | 26.1.2 |
| --- | --- | --- | --- |
| 签名 | `<T extends LivingEntity> hurtAndBreak(int, T, Consumer<T>)` | `hurtAndBreak(int, ServerLevel, LivingEntity, Consumer<Item>)` + `(int, LivingEntity, EquipmentSlot)` | 同 1.21.1 + `(int, LivingEntity, InteractionHand)` + `hurtWithoutBreaking(int, Player)` |
| `Consumer` 泛型 | `Consumer<T>`（实体） | `Consumer<Item>`（被毁物品） | `Consumer<Item>` |
| 创造模式判断 | `Player.getAbilities().instabuild`（在方法内） | `LivingEntity.hasInfiniteMaterials()` | 同 1.21.1 |
| 附魔（Unbreaking） | `hurt()` 内 `EnchantmentHelper.getItemEnchantmentLevel(Enchantments.UNBREAKING, stack)`（`ItemStack.java:345`） | `EnchantmentHelper.processDurabilityChange(ServerLevel, ItemStack, int)`（`EnchantmentHelper.java:104`） | 同（`EnchantmentHelper.java:102`） |
| 客户端调用 | 方法内即 `isClientSide` 判断 | `(int, LivingEntity, EquipmentSlot)` 入口要求 `ServerLevel` | 同 1.21.1 |

`setDamageValue` / `set(DataComponents.DAMAGE, ...)` 用法：

- 1.20.1：`stack.setDamageValue(int)`（`ItemStack.java:332`）；无组件 API，写 NBT 用 `stack.getOrCreateTag()`（`ItemStack.java:521`）。
- 1.21.1：`stack.setDamageValue(int)`（:452）；
  或 `stack.set(DataComponents.DAMAGE, value)`（`DataComponentHolder` 体系，`.mc-src/1.21.1/net/minecraft/core/component/DataComponentHolder.java`；
  也可用 `MutableDataComponentHolder`）。**直接用 `setDamageValue` 更安全**，它会走 `Item.setDamage(ItemStack,int)`。
- 26.1.2：`stack.setDamageValue(int)`（:453）；`applyComponents(DataComponentPatch)`（:854）、
  `applyComponentsAndValidate(...)`（:844）、`remove(DataComponentType)`（:840）等同样可用。

> ⚠️ 手动 `setDamageValue` **不会**触发 `Unbreaking` 附魔减免，也**不会**在到顶时自动销毁物品。
> 若希望"耐久耗尽 = 消耗掉这个食物"，用 `hurtAndBreak(...)`；若希望"耐久耗尽 = 停在最后一点、永不破坏"，
> 用 26.1.2 的 `hurtWithoutBreaking(...)`，或自己 `setDamageValue(min(cur+1, max))`。

---

## 6. Mixin 注入点设计（需求 A / B）

### 6.1 前置：需求语义约定

- **「耐久 0 = 无限耐久」的实现口径**：当物品**没有耐久条**时视为"无限"。
  - 1.20.1 判据：`!stack.isDamageableItem()` 或 `stack.getMaxDamage() <= 0`（`ItemStack.java:315` / :336）
  - 1.21.1 / 26.1.2 判据：`!stack.isDamageableItem()`（`has(MAX_DAMAGE) && !has(UNBREAKABLE) && has(DAMAGE)`），
    或 `stack.getOrDefault(DataComponents.MAX_DAMAGE, 0) <= 0`
  - 也可额外支持"耐久已满时视为无限"的口径；两种口径互斥，需在实现时二选一并写进注释。
- **消耗优先级**：`可耐久 && 未视为无限` → 扣耐久、**不** `shrink`；否则 → 走原版 `shrink`/`consume`。

### 6.2 需求 (A)：食用改为消耗耐久

#### 1.20.1

**首选注入点**

- 目标类：`net.minecraft.world.entity.LivingEntity`
- 目标方法：`eat(Level, ItemStack)`
- 描述符：`(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;`
- 方法位置：`.mc-src/1.20.1/net/minecraft/world/entity/LivingEntity.java:3329`
- 注入时机：**`@Redirect`** 打在消耗那一次调用上
  - `@At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V")`
  - 对应源码 `LivingEntity.java:3343`（该行 `shrink` 在 `eat` 内**只出现一次**，`@Redirect` 唯一匹配）
  - 备选：`@Inject(method = "eat(...)", at = @At("HEAD"), cancellable = true)` 整体重写
    —— 但那样必须自己补 `playSound`（:3331-3340）、`addEatEffect`（:3341）、`gameEvent`（:3346），**不推荐**。

**Redirect 处理器里要写什么**

```java
@Redirect(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
private void orefood$consumeDurability(ItemStack stack, int amount, Level level, ItemStack food) {
    // this 是 LivingEntity（Redirect 可访问 encloser 的 this）
    if (!LevelDurabilityFood.shouldUseDurability(stack)) {   // 见下方判据
        stack.shrink(amount);                                 // 回退原版
        return;
    }
    if (this instanceof Player p && p.getAbilities().instabuild) return;  // 创造模式：原版也不消耗
    // 扣 1 点耐久：走 hurtAndBreak 会带上 Unbreaking 附魔减免，并在到顶时破碎
    stack.hurtAndBreak(1, this, e -> {});                     // 1.20.1 签名（ItemStack.java:370）
}
```

- **创造模式**：1.20.1 的 `LivingEntity.eat` 第 3342 行原本就跳过 `shrink`，Redirect 一旦接管该调用，
  这个守卫就**被绕过**了 → 处理器内须自行判断 `instabuild`。
  另外 `ItemStack.hurtAndBreak`（:371）内部也判 `instabuild`，所以走 `hurtAndBreak` 时**天然不吃耐久**，
  显式 `return` 只是提前优化。
- **数量 > 1**：`shrink(1)` 语义是"从这一堆里拿走 1 个"，而"扣 1 点耐久"只作用于**这一个堆叠的 damage 值**。
  带耐久的物品原版最大堆叠是 1（`DataComponents.MAX_DAMAGE` + `MAX_STACK_SIZE` 互斥，见
  `.mc-src/26.1.2/.../ItemStack.java:276` 的校验），所以正常不会出现"耐久食物堆叠 > 1"。
  若确实允许堆叠 > 1，必须决定：整堆共享一个 damage 值（MC 的组件模型就是这样，同一槽位的多个同物品堆叠是同一份组件），
  因此"吃一次扣 1 点耐久"实际是**整堆一起掉耐久**——需要在实现里明确并在 tooltip 提示。

#### 1.21.1

**首选注入点**

- 目标类：`net.minecraft.world.entity.LivingEntity`
- 目标方法：`eat(Level, ItemStack, FoodProperties)`（**三参版本**；二参版本是 `final`，见 `LivingEntity.java:3546`）
- 描述符：`(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;`
- 方法位置：`.mc-src/1.21.1/net/minecraft/world/entity/LivingEntity.java:3551`
- 注入时机：**`@Redirect`**，`@At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V")`
  - 对应源码 `LivingEntity.java:3563`（方法内唯一）
  - 备选：`@Inject(at = @At("HEAD"), cancellable = true)` —— 同上，会丢掉音效/效果，不推荐。

```java
@Redirect(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
private void orefood$consumeDurability(ItemStack stack, int amount, LivingEntity owner) {
    if (!LevelDurabilityFood.shouldUseDurability(stack)) { stack.consume(amount, owner); return; }
    if (owner == null || owner.hasInfiniteMaterials()) return;   // 与 ItemStack.consume 行为对齐（ItemStack.java:1089-1092）
    if (owner.level() instanceof ServerLevel sl) {               // 1.21.1 的 hurtAndBreak 需要 ServerLevel
        stack.hurtAndBreak(1, sl, owner, item -> {});            // ItemStack.java:464
    }
}
```

- **必须自己补创造模式判断**：`consume`（`ItemStack.java:1089-1093`）原本内建 `hasInfiniteMaterials()` 守卫，
  一旦 Redirect 接管就没了。走 `hurtAndBreak` 时它内部同样判 `hasInfiniteMaterials()`（:467），
  所以显式判断只是对齐语义。
- 客户端安全：`hurtAndBreak(int, ServerLevel, ...)` 只在服务端路径被调用时有效——而 `completeUsingItem` 本来
  就是**仅服务端**（`LivingEntity.java:3160`），因此没问题。

#### 26.1.2

**首选注入点**

- 目标类：`net.minecraft.world.item.component.Consumable`
- 目标方法：`onConsume(Level, LivingEntity, ItemStack)`
- 描述符：`(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;`
- 方法位置：`.mc-src/26.1.2/net/minecraft/world/item/component/Consumable.java:77`
- 注入时机：**`@Redirect`**，`@At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V")`
  - 对应源码 `Consumable.java:91`（方法内唯一）
  - 备选：`@Inject(at = @At("HEAD"), cancellable = true)` 会丢掉粒子/音效（:79）、统计（:80-83）、
    `ConsumableListener` 回调（:85，**加饱食度就在这里**）、`ConsumeEffect`（:87）、`gameEvent`（:90）—— 强烈不推荐。

```java
@Redirect(method = "onConsume(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
private void orefood$consumeDurability(ItemStack stack, int amount, LivingEntity owner) {
    if (!LevelDurabilityFood.shouldUseDurability(stack)) { stack.consume(amount, owner); return; }
    if (owner == null || owner.hasInfiniteMaterials()) return;
    if (owner instanceof Player p) {
        stack.hurtWithoutBreaking(1, p);        // ★ ItemStack.java:512，钳在 maxDamage-1，永不破坏
        // 或：stack.hurtAndBreak(1, owner, InteractionHand.MAIN_HAND);   // ItemStack.java:524
    }
}
```

- 26.1.2 因为新增 `hurtWithoutBreaking(int, Player)`（`ItemStack.java:512`），
  实现「耐久掉到 0 就停在 0、永不消失」（即"0 耐久食物仍可继续吃"）**不需要自己算 clamp**。

#### 三版本方法签名差异（尤其"`LivingEntity.eat` 是否存在于 1.20.1"）

**`LivingEntity.eat` 在 1.20.1 存在**：

- 1.20.1：`public ItemStack eat(Level, ItemStack)`（`LivingEntity.java:3329`），**唯一重载**，非 final，
  `Player` 覆写之（`Player.java:2156`）。
- 1.21.1：`public final ItemStack eat(Level, ItemStack)`（:3546，**final**）+ `public ItemStack eat(Level, ItemStack, FoodProperties)`（:3551）；
  `Player` 覆写的是**三参版**（`Player.java:2181`）。
- 26.1.2：**不存在** `LivingEntity.eat`，也不存在 `Player.eat`；改为 `Consumable.onConsume` + `ConsumableListener`。

其他签名差异：

| 变更点 | 1.20.1 | 1.21.1 | 26.1.2 |
| --- | --- | --- | --- |
| `ItemStack.use` 返回 | `InteractionResultHolder<ItemStack>` | 同 | **`InteractionResult`** |
| `Item.use` 判可食 | `itemstack.isEdible()` | `stack.getFoodProperties(player) != null` | `stack.get(DataComponents.CONSUMABLE) != null` |
| `Item.finishUsingItem` 转发 | `eat(Level, ItemStack)` | `eat(Level, ItemStack, FoodProperties)` | `consumable.onConsume(Level, LivingEntity, ItemStack)` |
| 食用时长 | `getUseDuration(ItemStack)`（fastFood ? 16 : 32） | `getUseDuration(ItemStack, LivingEntity)`（`eatSeconds*20`） | `getUseDuration(ItemStack, LivingEntity)`（`Consumable.consumeSeconds*20`） |
| `ItemStack.getFoodProperties` | Forge 扩展方法 | NeoForge 扩展方法 | **已移除** |
| 维度判定 | `level.isClientSide`（字段） | 同 | **`level.isClientSide()`**（方法） |

### 6.3 三版本的共性风险清单

1. **创造模式（`instabuild` / `hasInfiniteMaterials`）**
   - 1.20.1：`LivingEntity.eat` 第 3342 行守卫 + `ItemStack.hurtAndBreak` 第 371 行守卫。Redirect 接管后
     第 3342 行守卫失效，需自补。
   - 1.21.1：守卫从 `LivingEntity.eat` **移到了 `ItemStack.consume`**（`ItemStack.java:1089-1092`）。
     `LivingEntity.eat(3参)` 第 3563 行是**无条件** `food.consume(1, this)`。Redirect 后守卫失效，需自补。
   - 26.1.2：同样在 `ItemStack.consume`（`ItemStack.java:1172-1176`），`Consumable.java:91` 无条件调用。
   - 走 `hurtAndBreak` / `hurtWithoutBreaking` 时它们内部也判 `instabuild` / `hasInfiniteMaterials`
     （1.20.1 `:371`；1.21.1 `:467`；26.1.2 `:488`、`:513`），所以**创造模式既不掉耐久也不消耗**，
     与"原版也不消耗"的直觉一致。

2. **堆叠数量 > 1**
   - 带 `MAX_DAMAGE` 的物品在组件模型下天然 `MAX_STACK_SIZE = 1`
     （校验见 `.mc-src/26.1.2/.../ItemStack.java:276`），所以"耐久食物"必须是不可堆叠物品。
   - 如果强行让"可耐久 + 可堆叠"共存，要意识到 damage 是**整个堆叠共享**的一份组件，
     "吃一次扣 1 耐久"会作用到整堆；此时"耐久 0 = 无限"的分支会造成整堆永不减少。
   - 1.20.1 无组件系统，`Item.Properties.durability(int)` 与 `stacksTo(int)` 可以同时设置（不推荐），
     行为同"整堆共享 damage"。

3. **客户端 / 服务端执行差异**
   - **定时食用（默认路径）只有服务端会完成**：三版本的 `updateUsingItem` 都写成
     `if (--this.useItemRemaining <= 0 && !level.isClientSide[...] && !useOnRelease()) completeUsingItem();`
     （1.20.1 `LivingEntity.java:2943`；1.21.1 `:3160`；26.1.2 `:3508`）。
     所以 `finishUsingItem` → 扣耐久 / 加饱食度的代码**天然是服务端独占**，客户端由物品栏同步纠正，
     **不会产生"客户端预测性消耗不同步"**。
   - **例外：26.1.2 的瞬时食用**。`Consumable.startConsuming`（`Consumable.java:62-75`）在
     `consumeTicks() <= 0` 时**立即**调用 `this.onConsume(user.level(), user, stack)`（:71），
     而 `Consumable.startConsuming` 是由 `Item.use`（`Item.java:211`）调用的——右键会在**客户端本地也执行一次**
     （`MultiPlayerGameMode` 的本地预测）。这条路径下：
     - `stack.consume(1, user)`（:91）会在客户端也跑；
     - 你的 Redirect 处理器如果扣耐久，客户端也会扣（然后被服务端同步覆盖）；
     - 客户端上 `user.level()` 不是 `ServerLevel`，若你走 `hurtAndBreak(int, ServerLevel, ...)` 会因拿不到
       `ServerLevel` 而**静默不生效**——这反而是安全的（客户端不扣，服务端扣，同步后一致）。
     - **若食物是瞬时食用的（`consumeSeconds = 0`），务必在处理器里加 `if (!level.isClientSide())` 或者在
       Redirect 内额外判断 `owner.level() instanceof ServerLevel`。**
   - `Consumable.onConsume` 内部本身也做了端区分：`ConsumeEffect` 只在 `!level.isClientSide()` 执行（:86）；
     `FoodProperties.onConsume` 的 `getFoodData().eat(this)`（:44）**没有**端判断——但 `FoodData` 客户端也有实例，
     属于原版设计（客户端也会更新本地饱食度用于 HUD 预测）。

4. **`@Redirect` 的作用域**：以上三处的 `shrink(1)` / `consume(1, this)` 在目标方法内均**只出现一次**，
   所以 `@Redirect` 能唯一匹配（可用 `require = 1` 强制校验，避免版本更新后静默失配）。
   若目标方法被 NeoForge 的补丁改变，建议用 `@Inject` + `@Local` / MixinExtras 的 `@WrapOperation` 提高健壮性
   （NeoForge 26.1.2 自带 `mixinextras-neoforge 0.5.4`，可直接用；Forge 1.20.1 **不自带** MixinExtras，
   需自行添加依赖或只用原生 Mixin 注解）。

### 6.4 需求 (B) 的注入点（汇总）

- 1.20.1：`net.minecraft.world.food.FoodData#eat(IF)V`（`FoodData.java:23`）→ `@ModifyVariable(argsOnly=true, ordinal=0)` 给饥饿值加 `n`；
  或 `@Inject(at = @At("TAIL"))` 调 `this.eat(n, 1.0F)`（**注意**：此处是 `eat` 自身，`TAIL` 内再调 `eat` 会无限递归，
  所以 `TAIL` 方案必须写在**上层** `eat(Item, ItemStack, LivingEntity)` 的 TAIL，或直接改字段）。
- 1.21.1：`FoodData#add(IF)V`（`FoodData.java:20`，private）→ 两个 `@ModifyVariable(argsOnly=true, ordinal=0/1)`。
  或 `FoodData#eat(Lnet/minecraft/world/food/FoodProperties;)V`（:32）`TAIL` → `this.eat(n, 1.0F)`（**安全**，因为
  `eat(FoodProperties)` 不会自递归）。
- 26.1.2：同 1.21.1，但目标 `FoodData#add(IF)V` 在 `.mc-src/26.1.2/.../FoodData.java:19`、
  `eat(FoodProperties)` 在 :28。
- **最简统一方案**：三版本都在 `FoodData.eat(FoodProperties)`（1.21.1 :32 / 26.1.2 :28）或
  1.20.1 的 `FoodData.eat(Item, ItemStack, LivingEntity)`（:33）的 `TAIL` 里追加 `this.eat(n, 1.0F)`。
  1.20.1 的 `eat(int,float)` 是 public（:23），1.21.1/26.1.2 的 `eat(int,float)` 也是 public
  （:28 / :24），都可安全调用。

---

## 7. 三平台的 Mixin 配置方式（**已查证，非猜测**）

### 7.1 Forge 1.20.1 —— **`mods.toml` 不支持 `[[mixins]]`，必须用 JAR 清单 `MixinConfigs`**

**查证过程与证据**

1. 本项目 `.mc-src/1.20.1` 全树 grep `mixin|Mixin`：
   - 仅命中 `.mc-src/1.20.1/net/minecraftforge/logging/CrashReportAnalyser.java:16-18,89-180`
     （它只是把 Mixin 的 `IMixinInfo` / `MixinMerged` 用于崩溃报告归类）。
   - `.mc-src/1.20.1/net/minecraftforge/fml/**` 内 grep `mixin|Mixin` → **未找到**。
   - 全树 grep `MixinConfigs|MixinConfig` → **未找到**。
   - → Mixin 的**加载代码不在反编译范围内**（位于 ModLauncher / `fmlloader` / Mixin 库自身）。
2. `.mc-src/1.20.1/META-INF/` 只有 `accesstransformer.cfg`、`coremods.json`、`MANIFEST.MF`、`mods.toml`，
   **没有 mixins.json**；`META-INF/mods.toml`（全 17 行，见 `-:1-17`）**没有 `[[mixins]]` 段**。
3. 对 Forge 官方 `1.20.x` 分支源码核验：
   - `fmlloader/.../moddiscovery/ModFileParser.java` 只读 `META-INF/mods.toml` 与 `META-INF/coremods.json`，
     **完全不解析 mixin**。
   - `fmlloader/.../moddiscovery/ModFileInfo.java` 读取的顶层字段是
     `modLoader` / `loaderVersion` / `license` / `showAsResourcePack` / `clientSideOnly` / `showAsDataPack` /
     `services` / `issueTrackerURL` / `properties` / `mods` —— **没有任何 mixin 字段**。
   - `forgespi` 的 `IModFileInfo` 接口方法为 `getMods` / `getFile` / `requiredLanguageLoaders` / `getFileProperties` /
     `showAsResourcePack` / `isClientSideOnly` / `showAsDataPack` / `getConfigElement` / `getConfigList` /
     `getLicense` / `getConfig` / `moduleName` / `versionString` / `usesServices` —— **没有 `getMixinConfigs`**。
   - 官方 1.20.1 文档 `https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/` 的 `mods.toml`
     属性表**未列出任何 mixin 属性**（只有非模组属性 / `[[mods]]` / `[[dependencies.<modid>]]`）。
4. **本地二进制核验（最硬证据）**：直接检查本项目环境里的真实 jar
   `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.minecraftforge\fmlloader\1.20.1-47.4.5\...\fmlloader-1.20.1-47.4.5.jar`：
   - `net/minecraftforge/fml/loading/moddiscovery/ModFileParser.class` → **无 mixin 引用**
   - `net/minecraftforge/fml/loading/moddiscovery/ModFileInfo.class` → **无 mixin 引用**
   - `net/minecraftforge/fml/loading/moddiscovery/AbstractModProvider$DefaultModFileInfo.class` → **无 mixin 引用**
   - `%...%\net.minecraftforge\forgespi\7.0.1\...\forgespi-7.0.1.jar` 的 `IModFileInfo.class` 方法列表 →
     **无 `getMixinConfigs`**。
   - `fmlloader-1.20.1-47.4.5.jar`、`fmlcore-1.20.1-47.4.5.jar`、`forgespi-7.0.1.jar` 全 jar 扫 class 常量池
     搜 `MixinConfigs|MixinConnector` → **零命中**。

**→ 结论：Forge 1.20.1 的 `mods.toml` 里写 `[[mixins]]` 无效（会被加载器静默忽略）。**

**实际机制**：Mixin 库自身的 ModLauncher 平台代理会读取**每个 mod jar 的 MANIFEST 属性** `MixinConfigs`
（以及 `MixinConnector`）。本地二进制核验：

- `org.spongepowered:mixin:0.8.5` 的 `mixin-0.8.5.jar` 中存在
  `org/spongepowered/asm/launch/MixinLaunchPlugin.class`（ModLauncher 的 `ILaunchPluginService` 实现）
  与 `MixinTransformationService.class`（`ITransformationService` 实现）。
- `org/spongepowered/asm/launch/platform/MixinPlatformAgentDefault.class` 的常量池中出现
  `mixinConfigs`、`MixinConfigs`、`MixinConnector`、`org/spongepowered/asm/util/Constants$ManifestAttributes`
  → 它确实从**清单属性**读配置名。
- `MixinPlatformAgentMinecraftForge.class` 的常量池中**没有**任何配置名单字符串
  （只有 `getMixinContainers` 等类型引用），即 Forge 专属代理不参与配置名解析。
- `CommandLineOptions.class` 出现 `--mixin.config`（开发环境的命令行注入通道）。

**因此 Forge 1.20.1 的正确写法**（本项目 `forge-1.20.1/build.gradle` 目前**没有**这段，需要新增）：

```groovy
// forge-1.20.1/build.gradle —— 追加到 tasks.named('jar', Jar).configure { manifest { attributes([...]) } } 里
jar {
    manifest {
        attributes([
            // 逗号分隔；每个名字必须能在 jar 根（classpath 资源根）找到
            'MixinConfigs': "${mod_id}.mixins.json"
        ])
    }
}
```

- **Mixin 配置文件放哪**：`forge-1.20.1/src/main/resources/${mod_id}.mixins.json`
  （即**资源根目录**，不是 `META-INF/`）。本模块通过
  `sourceSets.main.resources.srcDir '../common/src/main/resources'`（`forge-1.20.1/build.gradle:28`）合并共享资源，
  所以也可以放 `common/src/main/resources/`，但 `MixinConfigs` 里的字符串是**资源根相对路径**。
- **`mods.toml` 怎么写**：**什么都不用写**（不要加 `[[mixins]]`）。当前
  `forge-1.20.1/src/main/resources/META-INF/mods.toml` 是正确形态。
- **是否需要额外 Gradle 依赖/配置**：**不需要**。ForgeGradle 6（`net.minecraftforge.gradle` `[6.0,6.2)`，
  见 `forge-1.20.1/build.gradle:4`）的 userdev 会把 Mixin 接进 dev 运行环境；本机缓存中
  `org.spongepowered:mixin:0.8.5`（含 `mixin-0.8.5.jar` 与 `mixin-0.8.5-processor.jar`）已存在，
  `cpw.mods:modlauncher:10.0.9 / 10.0.10` 已存在。
  可选增强：`org.spongepowered:mixingradle:0.7.38`（本机缓存中也有，插件 id `org.spongepowered.mixin`）
  提供 refmap 生成与 IDE 支持，但**不是必需**——本项目当前未使用。
  > ⚠️ ForgeGradle 6 **没有** `[[mixins]]` 元数据支持，也没有 ModDevGradle 那种"自动把 mixin 配置写进元数据"
  > 的能力，所以清单方案是唯一途径。
- **Forge 自带的 Mixin 版本**：**0.8.5**（`org.spongepowered:mixin:0.8.5`，由 ModLauncher 10.x 传递依赖带入）。
  证据：本机 `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\org.spongepowered\mixin\0.8.5\` 下存在
  `mixin-0.8.5.jar` 与 `mixin-0.8.5-processor.jar`，且同缓存中 Forge 1.20.1 只用 ModLauncher 10.0.9 / 10.0.10。
  → 这个版本**不支持** `@WrapOperation`（MixinExtras），只能用原生 Mixin 注解。
- **MixinExtras**：Forge 1.20.1 **不自带**（本机缓存里 `io.github.llamalad7` 只有 `mixinextras-neoforge`，
  无 `mixinextras-forge`），若要用 `@WrapOperation` 需自行加依赖。

### 7.2 NeoForge 1.21.1 —— `[[mixins]] config="..."`，**无额外 Gradle 配置**

**证据（元数据字段名，直接来自真实 jar 二进制）**

1. NeoForge 21.1.250 自己的 `META-INF/neoforge.mods.toml`
   （`.mc-src/1.21.1/META-INF/neoforge.mods.toml:19-20`）：
   ```toml
   [[mixins]]
       config = "neoforge.mixins.json"
   ```
2. 同目录 `.mc-src/1.21.1/neoforge.mixins.json`（13 行）展示了配置文件的合法结构：
   ```json
   {
     "required": true,
     "package": "net.neoforged.neoforge.mixins",
     "mixins": ["BlockEntityTypeAccessor", "MappedRegistryAccessor"],
     "client": [],
     "injectors": { "defaultRequire": 1 },
     "minVersion": "0.8"
   }
   ```
3. **加载器实现核验**（本机 Gradle 缓存中的真实 jar）：
   `net.neoforged.fancymodloader:loader:4.0.44`（= NeoForge 21.1.250 的 `loader` 依赖，见
   `neoforge-21.1.250.pom`）包含：
   - `net/neoforged/fml/loading/moddiscovery/ModFileParser$MixinConfig.class`
     （常量池字段名：**`config`**、**`requiredMods`**）
   - `net/neoforged/fml/loading/moddiscovery/ModFileParser.class` 常量池字符串：
     `META-INF/neoforge.mods.toml`、**`mixins`**、`getConfigList`、**`config`**、`getConfigElement`、
     **`requiredMods`**、`getMixinConfigs`、`mixinsEntry`、`mixinsEntries`、`potentialMixins`、
     `Failed to load mixin configs from mod file`、**`Missing "config" in [[mixins]] entry`**
     → 即：从 `neoforge.mods.toml` 读 `[[mixins]]` 数组，每项必须有 `config`。
   - `net/neoforged/fml/loading/mixin/DeferredMixinConfigRegistration.class`
     （把解析到的配置延迟注册进 Mixin）。
4. 官方文档（`https://docs.neoforged.net/docs/gettingstarted/modfiles/`）：
   `[[mixins]]` 支持 `config`（**mandatory**）、`requiredMods`（array）、`behaviorVersion`（string）。

**本项目现状**：`neoforge-1.21.1/src/main/templates/META-INF/neoforge.mods.toml` 已经有**注释掉的现成段落**：

```toml
# 声明 Mixin 配置（当前尚未使用，启用时取消注释，并补上
#   src/main/resources/${mod_id}.mixins.json 与对应的 mixin 类）。
#[[mixins]]
#config="${mod_id}.mixins.json"
```
→ 只需取消注释即可，写法完全正确。

- **配置文件放哪**：`neoforge-1.21.1/src/main/resources/${mod_id}.mixins.json`。
  ⚠️ 注意本模块的元数据是由 `generateModMetadata` 任务把 `src/main/templates` 展开到
  `build/generated/sources/modMetadata` 后作为资源目录（`neoforge-1.21.1/build.gradle:126-145`）——
  这个任务**只处理 `src/main/templates`**，而 `src/main/resources` 仍是普通资源目录，所以 mixins.json
  放 `src/main/resources/` 即可（不要放进 `templates/`，否则会被 `expand` 当成模板处理，
  `expand` 对 `$` 敏感，见 `forge-1.20.1/build.gradle:103` 的同类注释）。
- **是否需要 Gradle 侧额外配置**：**不需要**。ModDevGradle 2.0.147（`neoforge-1.21.1/build.gradle:5`）
  会自动处理 Mixin：dev 环境注入 Mixin AP / agent，打包时 `[[mixins]]` 会随 `neoforge.mods.toml` 被 NeoForge
  读走。本机缓存也确认了 `net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7` 与 `cpw.mods:modlauncher:11.0.5` 已就位。
- **NeoForge 自带的 Mixin 版本**：**`net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7`**
  （`neoforge-21.1.250.pom` 的 `sponge-mixin` 依赖版本；同 POM 中 `modlauncher` = `11.0.5`）。

### 7.3 NeoForge 26.1.2 —— 同 `[[mixins]]`，新增 `behaviorVersion`

**证据**

- `neoforge-26.1.2.109.pom`（本机缓存）→ `loader` = **11.0.15**、`sponge-mixin` = **0.17.3+mixin.0.8.7**、
  `modlauncher` = **11.0.5**、**`mixinextras-neoforge` = 0.5.4**。
- `net.neoforged.fancymodloader:loader:11.0.15` 中的
  `net/neoforged/fml/loading/moddiscovery/ModFileParser$MixinConfig.class` 常量池字段名为
  **`config` / `requiredMods` / `behaviorVersion`**（比 4.0.44 多一个 `behaviorVersion`）；
  `ModFileParser.class` 里同样出现 `mixins`、`getConfigList`、`Missing "config" in [[mixins]] entry`、
  `getMixinConfigs` 等字符串。
- `loader-11.0.15.jar` 另有 `net/neoforged/fml/loading/mixin/MixinFacade.class`、
  `FMLClassBytecodeProvider.class`、`FMLMixinService.class` 等（11.x 的新 Mixin 集成层）。
- 官方文档 `https://docs.neoforged.net/docs/gettingstarted/modfiles/`（Version: 26.1）的
  **Mixin Configuration Properties** 段落明确列出 `config` / `requiredMods` / `behaviorVersion`
  （`behaviorVersion` 需在「默认行为版本」与「运行时 fabric mixin 版本」之间）。
- ⚠️ 注意：`.mc-src/26.1.2/META-INF/neoforge.mods.toml` 是**Minecraft 自己的**
  （`modLoader="minecraft"`、`modId="minecraft"`），它**不含** `[[mixins]]`；NeoForge 自己的元数据不在这份反编译产物里。
  这**不**意味着 26.1.2 不支持 `[[mixins]]`——加载器实现（`loader-11.0.15.jar`）+ 官方文档已双重确认支持。

**本项目现状**：`neoforge-26.1.2/src/main/templates/META-INF/neoforge.mods.toml` 同样已有注释掉的
`#[[mixins]]` / `#config="${mod_id}.mixins.json"`，与 1.21.1 完全一致，取消注释即可。

- **配置文件放哪**：`neoforge-26.1.2/src/main/resources/${mod_id}.mixins.json`。
- **是否需要 Gradle 侧额外配置**：**不需要**（ModDevGradle 2.0.147，`neoforge-26.1.2/build.gradle:5`）。

### 7.4 三平台配置对照表

| | Forge 1.20.1 | NeoForge 1.21.1 | NeoForge 26.1.2 |
| --- | --- | --- | --- |
| 元数据文件 | `META-INF/mods.toml` | `META-INF/neoforge.mods.toml` | `META-INF/neoforge.mods.toml` |
| `[[mixins]]` 支持 | ❌ **不支持**（已二进制核验 47.4.5） | ✅ `[[mixins]] config="…"` | ✅ `[[mixins]] config="…"`（+ `requiredMods` / `behaviorVersion`） |
| 实际声明方式 | `jar { manifest { attributes 'MixinConfigs': 'x.mixins.json' } }` | `neoforge.mods.toml` 的 `[[mixins]]` | 同 1.21.1 |
| 配置文件路径 | `src/main/resources/x.mixins.json`（资源根） | `src/main/resources/x.mixins.json` | `src/main/resources/x.mixins.json` |
| Gradle 插件 | ForgeGradle 6 `[6.0,6.2)` | ModDevGradle 2.0.147 | ModDevGradle 2.0.147 |
| 额外 Gradle 配置 | 不需要（可选 MixinGradle 0.7.38） | 不需要 | 不需要 |
| 加载器自带 Mixin | `org.spongepowered:mixin:0.8.5` | `net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7` | `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` |
| ModLauncher | 10.0.9 / 10.0.10 | 11.0.5 | 11.0.5 |
| MixinExtras | ❌ 不自带 | ✅（NeoForge 提供） | ✅ `mixinextras-neoforge:0.5.4` |
| 加载器解析代码（本机核验） | `fmlloader-1.20.1-47.4.5.jar` → `ModFileParser`/`ModFileInfo` 无 mixin；`MixinPlatformAgentDefault`(mixin 0.8.5) 读清单 | `loader-4.0.44.jar` → `ModFileParser$MixinConfig{config, requiredMods}` | `loader-11.0.15.jar` → `ModFileParser$MixinConfig{config, requiredMods, behaviorVersion}` |

**「未找到」清单（如实记录）**

- `.mc-src/1.20.1`、`.mc-src/1.21.1`、`.mc-src/26.1.2` 内 **没有** Mixin 加载器实现代码
  （加载器代码在 `fmlloader` / `fancymodloader` / ModLauncher / Mixin 库里，不在反编译产物中）。
  因此 §7 的加载器结论来自**本机 Gradle 缓存的真实 jar/pom 二进制核验** + 官方文档，
  已在文中逐条标注证据来源。
- `.mc-src/26.1.2` 内 **没有** `net/minecraftforge/**` 包（该版本只有 `net/minecraft` 与 `net/neoforged`），
  因此题目要求的「在 `.mc-src/26.1.2/net/neoforged/` 下检索 mixin 相关加载代码」→ 找到的是
  **Mixin 的消费者**（`net/neoforged/neoforge/mixins/` 下的 accessor mixin、`PoiTypeExtender` 里的
  `MixinMerged` 反射），**不是加载器**。`.mc-src/1.21.1/net/neoforged/neoforge/mixins/` 里则有：
  - `MappedRegistryAccessor.java:12-15`（`@Mixin(MappedRegistry.class)` + `@Accessor`）
  - `BlockEntityTypeAccessor.java:11-15`（`@Mixin(BlockEntityType.class)` + `@Mutable` + `@Accessor`）
  这两个是 NeoForge 自己的 accessor mixin 范例，可作为写法参考。
- `.mc-src/1.20.1/net/minecraftforge/` 下 **没有** `@Mixin` 类（grep 仅命中 `CrashReportAnalyser`），
  即 Forge 47.4.5 自身不使用 Mixin。

---

## 8. 食用时如何拿到「谁在吃」与「食物是什么物品栈」

| 版本 | 钩子 | 「谁在吃」 | 「食物栈」 | 位置 |
| --- | --- | --- | --- | --- |
| 1.20.1 | `LivingEntity#eat(Level, ItemStack)` | **`this`**（实际类型 `Player` / `Mob`） | 参数 1 `ItemStack p_21068_` | `LivingEntity.java:3329` |
| 1.20.1（另一路径） | `Player#eat(Level, ItemStack)` / `FoodData#eat(Item, ItemStack, LivingEntity)` | `this` / 参数 3 `entity` | 参数 1 / 参数 2 `ItemStack` | `Player.java:2156` / `FoodData.java:33` |
| 1.21.1 | `LivingEntity#eat(Level, ItemStack, FoodProperties)` | **`this`** | 参数 1 `ItemStack food` | `LivingEntity.java:3551` |
| 1.21.1（另一路径） | `Player#eat(Level, ItemStack, FoodProperties)` | `this` | 参数 1 `ItemStack food` | `Player.java:2181` |
| 26.1.2 | `Consumable#onConsume(Level, LivingEntity, ItemStack)` | 参数 1 `LivingEntity user` | 参数 2 `ItemStack stack` | `Consumable.java:77` |
| 26.1.2（另一路径） | `FoodProperties#onConsume(Level, LivingEntity, ItemStack, Consumable)` | 参数 1 `user` | 参数 2 `stack` | `FoodProperties.java:40` |
| 26.1.2（组件回调） | `ConsumableListener#onConsume(Level, LivingEntity, ItemStack, Consumable)` | 参数 1 `user` | 参数 2 `stack` | `ConsumableListener.java:8` |

**查附魔等级（用于"吃一次扣多少耐久 / 加多少饱食度"）**

| 版本 | API | 位置 |
| --- | --- | --- |
| 1.20.1 | `EnchantmentHelper.getItemEnchantmentLevel(Enchantment, ItemStack)`（`Enchantment` 是对象，如 `Enchantments.UNBREAKING`） | `.mc-src/1.20.1/net/minecraft/world/item/enchantment/EnchantmentHelper.java:63` |
| 1.21.1 | `EnchantmentHelper.getItemEnchantmentLevel(Holder<Enchantment>, ItemStack)` | `.mc-src/1.21.1/net/minecraft/world/item/enchantment/EnchantmentHelper.java:56` |
| 1.21.1（NeoForge 便捷） | `ItemStack#getEnchantmentLevel(Holder<Enchantment>)` | `.mc-src/1.21.1/net/neoforged/neoforge/common/extensions/IItemStackExtension.java:162` |
| 26.1.2 | `ItemStack#getEnchantmentLevel(Holder<Enchantment>)`（NeoForge 推荐；`ItemStack implements ItemInstance`，见 `ItemStack.java:103`、`ItemInstance.java:7`） | `.mc-src/26.1.2/net/minecraft/world/item/EnchantmentHelper.java:51` 的 Javadoc 指明；`EnchantmentHelper.getItemEnchantmentLevel(Holder, ItemInstance)` 在 :54（已 `@Deprecated`） |
| 26.1.2（实体附魔） | `EnchantmentHelper.getEnchantmentLevel(Holder<Enchantment>, LivingEntity)` | `.mc-src/26.1.2/.../EnchantmentHelper.java:292` |

> 建议：**不要自己算 Unbreaking**。直接调用各版本的 `hurtAndBreak` / `hurtWithoutBreaking`，
> 它们已经内置了 `EnchantmentHelper.processDurabilityChange`（1.21.1 `:104` / 26.1.2 `:102`）
> 与 1.20.1 `hurt()` 里的 `UNBREAKING` 判定（`ItemStack.java:345-357`）。
> 若确实需要读附魔等级（例如"每级 Unbreaking 少扣一点"之类的自定义规则），按上表取用。

---

## 9. 实现骨架（伪代码，供落地参考）

### 9.1 共用判定

```java
final class FoodDurability {
    /** 「耐久 0 = 无限耐久」：没有耐久上限即视为无限。 */
    static boolean infinite(ItemStack stack) {
        return !stack.isDamageableItem();          // 三版本通用入口
        // 1.20.1 备选： stack.getMaxDamage() <= 0
        // 1.21+  备选： stack.getOrDefault(DataComponents.MAX_DAMAGE, 0) <= 0
    }
}
```

### 9.2 每个平台需要的文件清单

| 文件 | 1.20.1 | 1.21.1 | 26.1.2 |
| --- | --- | --- | --- |
| mixin 配置 JSON | `forge-1.20.1/src/main/resources/<modid>.mixins.json` | `neoforge-1.21.1/src/main/resources/<modid>.mixins.json` | `neoforge-26.1.2/src/main/resources/<modid>.mixins.json` |
| 声明位置 | `build.gradle` → `jar { manifest { attributes 'MixinConfigs': '<modid>.mixins.json' } }` | `templates/META-INF/neoforge.mods.toml` 取消注释 `[[mixins]]` | 同 1.21.1 |
| (A) Mixin 目标 | `LivingEntity#eat(Level, ItemStack)` 的 `shrink(I)V` 调用 | `LivingEntity#eat(Level, ItemStack, FoodProperties)` 的 `consume(ILnet/minecraft/world/entity/LivingEntity;)V` 调用 | `Consumable#onConsume(Level, LivingEntity, ItemStack)` 的 `consume(ILnet/minecraft/world/entity/LivingEntity;)V` 调用 |
| (B) Mixin 目标 | `FoodData#eat(IF)V` | `FoodData#add(IF)V`（或 `eat(Lnet/minecraft/world/food/FoodProperties;)V` 的 TAIL） | 同 1.21.1 |

> 由于 `common/` 层是**直接参与三平台编译的源码目录**（`sourceSets.main.java.srcDir '../common/src/main/java'`），
> Mixin 类**不能**放在 `common/`（三版本签名不同，无法共用），必须各自放在
> `forge-1.20.1/src/main/java/…`、`neoforge-1.21.1/src/main/java/…`、`neoforge-26.1.2/src/main/java/…`，
> 只有平台无关的纯逻辑（如"额外饱食度"配置读取）才放进 `common/`。

---

## 10. 证据索引（快速跳转）

**1.20.1**

- `.mc-src/1.20.1/net/minecraft/world/food/FoodProperties.java:10,11-16,37,41,64-111`
- `.mc-src/1.20.1/net/minecraft/world/food/FoodData.java:23-26,33-38`
- `.mc-src/1.20.1/net/minecraft/world/food/FoodConstants.java:4-5`
- `.mc-src/1.20.1/net/minecraft/world/item/Item.java:147-159,161-163,166,171,175,273-277,362,368`
- `.mc-src/1.20.1/net/minecraft/world/item/ItemStack.java:283-289,307-309,315-322,324-338,340-368,370-384,521,982,995`
- `.mc-src/1.20.1/net/minecraft/world/entity/LivingEntity.java:2926,2943-2945,3056-3074,3329-3350,3352-3361`
- `.mc-src/1.20.1/net/minecraft/world/entity/player/Player.java:1812,1816-1818,2156-2167`
- `.mc-src/1.20.1/net/minecraft/world/item/enchantment/EnchantmentHelper.java:63`
- `.mc-src/1.20.1/net/minecraftforge/common/extensions/IForgeItem.java:176-178,303-304`
- `.mc-src/1.20.1/net/minecraftforge/common/extensions/IForgeItemStack.java:229-230`
- `.mc-src/1.20.1/net/minecraftforge/logging/CrashReportAnalyser.java:16-18,89-180`（唯一的 mixin 相关引用）

**1.21.1**

- `.mc-src/1.21.1/net/minecraft/world/food/FoodProperties.java:17-19,21-46,48-50,68-117,119-142`
- `.mc-src/1.21.1/net/minecraft/world/food/FoodData.java:20-23,28-30,32-34`
- `.mc-src/1.21.1/net/minecraft/world/food/FoodConstants.java:30-32`
- `.mc-src/1.21.1/net/minecraft/core/component/DataComponents.java:58,61,64,67,117-119`
- `.mc-src/1.21.1/net/minecraft/core/component/DataComponentHolder.java:5-20`
- `.mc-src/1.21.1/net/minecraft/world/item/Item.java:165-178,183-186,318,321-324`
- `.mc-src/1.21.1/net/minecraft/world/item/ItemStack.java:399,406-408,440-442,448-458,460-488,490-499,1085-1093,1089-1093`
- `.mc-src/1.21.1/net/minecraft/world/entity/LivingEntity.java:3151-3163,3271-3290,3546-3549,3551-3566,3568-3576`
- `.mc-src/1.21.1/net/minecraft/world/entity/player/Player.java:1805,1809,2181-2210`
- `.mc-src/1.21.1/net/minecraft/world/item/enchantment/EnchantmentHelper.java:56,104,288`
- `.mc-src/1.21.1/net/neoforged/neoforge/common/extensions/IItemExtension.java:749-751`
- `.mc-src/1.21.1/net/neoforged/neoforge/common/extensions/IItemStackExtension.java:162,464-465`
- `.mc-src/1.21.1/net/neoforged/neoforge/mixins/MappedRegistryAccessor.java:12-15`
- `.mc-src/1.21.1/net/neoforged/neoforge/mixins/BlockEntityTypeAccessor.java:11-15`
- `.mc-src/1.21.1/META-INF/neoforge.mods.toml:19-20`
- `.mc-src/1.21.1/neoforge.mixins.json:1-13`

**26.1.2**

- `.mc-src/26.1.2/net/minecraft/world/food/FoodProperties.java:20,21-37,39-49,51-75`
- `.mc-src/26.1.2/net/minecraft/world/food/FoodData.java:19-22,24-26,28-30`
- `.mc-src/26.1.2/net/minecraft/world/food/FoodConstants.java:30`
- `.mc-src/26.1.2/net/minecraft/world/item/component/Consumable.java:32-35,38-60,62-75,77-93,95-98,100-102,104-124,126-172,174-176`
- `.mc-src/26.1.2/net/minecraft/world/item/component/ConsumableListener.java:7-9`
- `.mc-src/26.1.2/net/minecraft/core/component/DataComponents.java:109,112,115,118,119,134,167,170,173,176`
- `.mc-src/26.1.2/net/minecraft/world/item/Item.java:207-230,232-235,328`
- `.mc-src/26.1.2/net/minecraft/world/item/ItemStack.java:103,276,396-407,409-429,433-439,449-510,512-522,524-534,840,844,854,1168-1182,1184-1189`
- `.mc-src/26.1.2/net/minecraft/world/item/ItemInstance.java:7`
- `.mc-src/26.1.2/net/minecraft/world/entity/LivingEntity.java:3503-3511,3602-3619`
- `.mc-src/26.1.2/net/minecraft/world/entity/player/Player.java:1351,1645,1649`
- `.mc-src/26.1.2/net/minecraft/world/item/enchantment/EnchantmentHelper.java:51-56,102,292`

**平台侧（本机 Gradle 缓存二进制核验，路径以 `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\` 为根）**

- `net.minecraftforge\fmlloader\1.20.1-47.4.5\…\fmlloader-1.20.1-47.4.5.jar`
  → `net/minecraftforge/fml/loading/moddiscovery/{ModFileParser,ModFileInfo,AbstractModProvider$DefaultModFileInfo}.class`：**无 mixin 引用**
- `net.minecraftforge\fmlcore\1.20.1-47.4.5\…`、`net.minecraftforge\forgespi\7.0.1\…\forgespi-7.0.1.jar`
  → 全 jar 常量池搜 `MixinConfigs|MixinConnector`：**零命中**；`IModFileInfo` 无 `getMixinConfigs`
- `org.spongepowered\mixin\0.8.5\…\mixin-0.8.5.jar`
  → `org/spongepowered/asm/launch/MixinLaunchPlugin.class`、`MixinTransformationService.class`；
  `…/platform/MixinPlatformAgentDefault.class` 常量池含 `MixinConfigs` / `MixinConnector` /
  `Constants$ManifestAttributes`；`…/platform/CommandLineOptions.class` 含 `--mixin.config`
  （另有 `mixin-0.8.5-processor.jar`）
- `cpw.mods\modlauncher\{10.0.9,10.0.10,11.0.5}\`（Forge 1.20.1 用 10.x；NeoForge 用 11.0.5）
- `net.neoforged\neoforge\21.1.250\…\neoforge-21.1.250.pom`
  → `loader=4.0.44`、`sponge-mixin=0.15.2+mixin.0.8.7`、`modlauncher=11.0.5`
- `net.neoforged\neoforge\26.1.2.109\…\neoforge-26.1.2.109.pom`
  → `loader=11.0.15`、`sponge-mixin=0.17.3+mixin.0.8.7`、`modlauncher=11.0.5`、`mixinextras-neoforge=0.5.4`
- `net.neoforged.fancymodloader\loader\4.0.44\…\loader-4.0.44.jar`
  → `net/neoforged/fml/loading/moddiscovery/ModFileParser$MixinConfig.class`（`config`,`requiredMods`）；
  `ModFileParser.class`（字符串 `mixins`,`getConfigList`,`Missing "config" in [[mixins]] entry`）；
  `net/neoforged/fml/loading/mixin/DeferredMixinConfigRegistration.class`
- `net.neoforged.fancymodloader\loader\11.0.15\…\loader-11.0.15.jar`
  → `ModFileParser$MixinConfig.class`（`config`,`requiredMods`,`behaviorVersion`）；
  `net/neoforged/fml/loading/mixin/{MixinFacade,FMLMixinService,FMLMixinClassProcessor,…}.class`

**官方文档（外部，已抓取）**

- Forge 1.20.1 `mods.toml` 属性表（无 mixin 属性）：
  <https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/>
- NeoForge 26.1 `neoforge.mods.toml`「Mixin Configuration Properties」（`config` / `requiredMods` / `behaviorVersion`）：
  <https://docs.neoforged.net/docs/gettingstarted/modfiles/>

---

## 11. 待办 / 风险提示

1. **`@Redirect` 的脆弱性**：三处目标调用在各自方法内都唯一，可用 `require = 1` 锁定；但 NeoForge 补丁若改动
   `LivingEntity.eat` / `Consumable.onConsume` 的字节码形态，Redirect 可能失配 → 优先考虑 MixinExtras 的
   `@WrapOperation`（NeoForge 平台自带；Forge 1.20.1 需自行加依赖）。
2. **1.20.1 的 `mods.toml` 不能写 `[[mixins]]`**：如果误写，Forge 会**静默忽略**（`ModFileInfo` 根本不读这个键），
   表现为"mixin 完全没生效且无任何报错"。落地时务必用清单方案，并在启动日志里确认
   `Mixin` 打印了配置名。
3. **1.20.1 需要确认 jar 任务的清单写入位置**：`forge-1.20.1/build.gradle:125-140` 已有 `jar { manifest { attributes([...]) } }`，
   把 `MixinConfigs` 加进那个 `attributes([...])` 即可（**不要**新开一个 `jar {}` 块覆盖它）。
   注意该文件同时有 `finalizedBy 'reobfJar'`（:139），清单属性会在重混淆后保留（清单不参与重混淆）。
4. **26.1.2 若使用"瞬时食用"（`Consumable.consumeSeconds = 0`）**，`onConsume` 会在客户端也执行一次，
   必须在 (A) 的 Redirect 处理器里加端判断（详见 §6.3 第 3 条）。
5. **`FoodProperties` 是 record（1.21.1 / 26.1.2）**：无法直接 `@ModifyVariable` 改它的字段；
   要改 nutrition/saturation 请注入 `FoodData`（见 §3.2 / §6.4）。
6. **本报告未验证的运行期行为**：Mixin 是否真正生效、创造模式行为、客户端预测表现——
   需要在三个模块各自 `runClient` 实测。建议加一个最小 mixin（例如只在
   `LivingEntity#eat` HEAD 打一条日志）先确认配置链路通了，再实现业务逻辑。
