# 三版本「数据包驱动的动态注册表（datapack registry / dynamic registry）」调研报告

> 调研目标：为 OreFood 模组实现「矿物食品定义通过数据包声明的动态注册表」。
> 覆盖版本：Minecraft 1.20.1 + Forge 47.4.5 / Minecraft 1.21.1 + NeoForge 21.1.250 / Minecraft 26.1.2 + NeoForge 26.1.2.109
>
> 源码根：`.mc-src/1.20.1/`、`.mc-src/1.21.1/`、`.mc-src/26.1.2/`
> 所有结论均附 `文件:行号` 证据；未找到的地方明确标注「未找到」并列出检索关键字。
> 本报告不含臆测内容。

---

## 0. 结论速览（TL;DR）

| 维度 | 1.20.1 / Forge | 1.21.1 / NeoForge | 26.1.2 / NeoForge |
|---|---|---|---|
| 注册事件类 | `net.minecraftforge.registries.DataPackRegistryEvent.NewRegistry` | `net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry` | **同名同包，未改名**：`net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry` |
| 重载数量 | 2 个 | 3 个 | 3 个（签名与 1.21.1 完全一致） |
| `dataPackRegistry` 参数 | `(key, codec)` / `(key, codec, networkCodec)` | 同左 + `(key, codec, networkCodec, Consumer<RegistryBuilder<T>>)` | 同 1.21.1 |
| 注册表 key | `ResourceKey.createRegistryKey(ResourceLocation)` | 同左 | `ResourceKey.createRegistryKey(Identifier)` |
| 数据包目录 | `data/<pack_ns>/<ns>/<path>/*.json`（`minecraft` 命名空间省略 `<ns>/`） | 同左 | **同左，规则未变** |
| network codec | 可选（`@Nullable Codec<T>`） | 可选 | 可选 |
| network codec 类型 | `Codec<T>`（不是 `StreamCodec`） | 同左 | 同左 |
| 读取注册表 | `registryAccess().registryOrThrow(key)` | 同左 | **`registryAccess().lookupOrThrow(key)`**（`registryOrThrow` 已改名） |
| 按 id 取值 | `registry.getOptional(ResourceLocation)` | `registry.get(ResourceLocation)` | **`registry.getValue(Identifier)`** |
| 遍历 | `registry.holders()` / `listElements()` | 同左 | **只有 `listElements()`**（`holders()` 已删） |
| 数据包重载监听 | `AddReloadListenerEvent`（game bus） | `AddReloadListenerEvent`（game bus） | **`AddServerReloadListenersEvent`**（`AddReloadListenerEvent` 已删） |
| reload listener 接口 | `PreparableReloadListener.reload` **6 参数** | 同 1.20.1（6 参数） | **4 参数**（`SharedState, Executor, PreparationBarrier, Executor`） |
| 客户端 reload 事件 | `RegisterClientReloadListenersEvent`（mod bus） | 同名 | **`AddClientReloadListenersEvent`**（mod bus） |
| `/reload` 是否重读动态注册表 | **否** | **否** | **否** |
| 动态注册表同步表 | `RegistrySynchronization` 里的 `Map`（6 条） | `RegistryDataLoader.SYNCHRONIZED_REGISTRIES` `List`（11 条） | `RegistryDataLoader.SYNCHRONIZED_REGISTRIES` `List`（28 条） |

> ⚠️ **最重要的反直觉结论**：任务描述中猜测「26.1.2 里 `DataPackRegistryEvent` 很可能改了名」——**这个猜测是错的**。26.1.2 中 `DataPackRegistryEvent.NewRegistry` 的类名、包名、三个 `dataPackRegistry` 重载签名与 1.21.1 **逐字相同**。26.1.2 真正变化的是它内部构造 `RegistryData` 的第三个参数（`false` → `RegistryValidator.none()`），以及 `RegistryAccess` / `Registry` 的读取方法改名。

---

## 1. 注册自定义动态注册表的 API

### 1.1 Forge 1.20.1

**确切类名**：`net.minecraftforge.registries.DataPackRegistryEvent`（外层），嵌套静态类 `net.minecraftforge.registries.DataPackRegistryEvent.NewRegistry`。

`.mc-src/1.20.1/net/minecraftforge/registries/DataPackRegistryEvent.java:14`

```java
public class DataPackRegistryEvent extends Event implements IModBusEvent {
    record DataPackRegistryData<T>(RegistryDataLoader.RegistryData<T> loaderData, @Nullable Codec<T> networkCodec) {
    }

    public static final class NewRegistry extends DataPackRegistryEvent {
```

**确切方法签名**（只有 2 个重载，`.mc-src/1.20.1/net/minecraftforge/registries/DataPackRegistryEvent.java:21` 与 `:25`）：

```java
// :21
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec)

// :25
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec, @Nullable Codec<T> networkCodec)
```

- 实现体：`:26-27` — `this.registryDataList.add(new DataPackRegistryData<>(new RegistryDataLoader.RegistryData<>(registryKey, codec), networkCodec));`
- 因为是 `IModBusEvent`（`:14`），必须注册在 **mod 事件总线**上。
- 内部收集结果由 `NewRegistry.process()`（`:30-34`）写入 `DataPackRegistriesHooks.addRegistryCodec(...)`。

**事件触发点**（`.mc-src/1.20.1/net/minecraftforge/registries/RegistryManager.java:134-142`）：

```java
public static void postNewRegistryEvent() {
    NewRegistryEvent event = new NewRegistryEvent();
    DataPackRegistryEvent.NewRegistry dataPackEvent = new DataPackRegistryEvent.NewRegistry();   // :136
    vanillaRegistryKeys = Set.copyOf(BuiltInRegistries.REGISTRY.keySet());
    ModLoader.get().postEventWrapContainerInModOrder(event);
    ModLoader.get().postEventWrapContainerInModOrder(dataPackEvent);                            // :139
    event.fill();
    dataPackEvent.process();                                                                    // :141
}
```

触发时机由 `ForgeStatesProvider` 在 `GATHER` 阶段调用（`.mc-src/1.20.1/net/minecraftforge/common/ForgeStatesProvider.java:16`）：

```java
"CREATE_REGISTRIES", "CONSTRUCT", ModLoadingPhase.GATHER, ml -> RegistryManager.postNewRegistryEvent()
```

> 即：**在 `GATHER` 阶段（早于 `FMLCommonSetupEvent`）**，所以监听器不能用 `FMLCommonSetupEvent` 注册。

### 1.2 NeoForge 1.21.1

**确切类名**：`net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry`。

`.mc-src/1.21.1/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:23`（外层）与 `:36`（`NewRegistry`）：

```java
public abstract class DataPackRegistryEvent extends Event implements IModBusEvent {   // :23
    @ApiStatus.Internal
    public DataPackRegistryEvent() {}

    public static final class NewRegistry extends DataPackRegistryEvent {              // :36
```

**确切方法签名**（3 个重载，`.mc-src/1.21.1/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:53`、`:74`、`:97`）：

```java
// :53  —— 不同步（无客户端同步）
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec)

// :74  —— 可指定 network codec
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec, @Nullable Codec<T> networkCodec)

// :97  —— 额外用 Consumer<RegistryBuilder<T>> 配置注册表本身
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec,
                                 @Nullable Codec<T> networkCodec, Consumer<RegistryBuilder<T>> consumer)
```

- `:74` 实现体：`this.registryDataList.add(new DataPackRegistryData<>(new RegistryDataLoader.RegistryData<>(registryKey, codec, false), networkCodec));`
- `:97` 实现体：`... new RegistryDataLoader.RegistryData<>(registryKey, codec, false, consumer) ...`
- 注意第三个参数是字面量 `false`，对应 `RegistryData.requiredNonEmpty`（1.21.1 语义）。

**事件触发点**（`.mc-src/1.21.1/net/neoforged/neoforge/registries/RegistryManager.java:78-97`）：

```java
public static void postNewRegistryEvent() {
    NewRegistryEvent event = new NewRegistryEvent();
    DataPackRegistryEvent.NewRegistry dataPackEvent = new DataPackRegistryEvent.NewRegistry();  // :80
    vanillaRegistryKeys = Set.copyOf(BuiltInRegistries.REGISTRY.keySet());
    ModLoader.postEventWrapContainerInModOrder(event);
    ModLoader.postEventWrapContainerInModOrder(dataPackEvent);                                  // :84
    event.fill();
    dataPackEvent.process();                                                                    // :87
    ModLoader.postEvent(new ModifyRegistriesEvent());                                           // :89
    ...
}
```

由 `.mc-src/1.21.1/net/neoforged/neoforge/internal/CommonModLoader.java:52` 调用。

### 1.3 NeoForge 26.1.2

**确切类名**：`net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry` —— **与 1.21.1 完全相同，未改名**。

`.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:23`（外层）与 `:36`（`NewRegistry`）。

**确切方法签名**（3 个重载，`.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:53`、`:74`、`:97`）——**与 1.21.1 逐字相同**：

```java
// :53
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec)
// :74
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec, @Nullable Codec<T> networkCodec)
// :97
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec,
                                 @Nullable Codec<T> networkCodec, Consumer<RegistryBuilder<T>> consumer)
```

**唯一内部差异**（`.mc-src/26.1.2/.../DataPackRegistryEvent.java:75` 与 `:98`）：

```java
// 26.1.2 :75   —— 第三参数由 boolean 改为 RegistryValidator
new RegistryDataLoader.RegistryData<>(registryKey, codec, RegistryValidator.none())

// 26.1.2 :98
new RegistryDataLoader.RegistryData<>(registryKey, codec, RegistryValidator.none(), consumer)

// 对比 1.21.1 :75 / :98 —— 第三参数是 boolean
new RegistryDataLoader.RegistryData<>(registryKey, codec, false)
new RegistryDataLoader.RegistryData<>(registryKey, codec, false, consumer)
```

**事件触发点**：`.mc-src/26.1.2/net/neoforged/neoforge/registries/RegistryManager.java:78-97`，与 1.21.1 结构一致（`:80` 构造事件、`:84` post、`:87` process）；由 `.mc-src/26.1.2/net/neoforged/neoforge/internal/CommonModLoader.java:52` 调用。

### 1.4 `RegistryDataLoader.RegistryData` 记录签名对比（三版本差异的根源）

| 版本 | `RegistryData<T>` 记录组件 | 证据 |
|---|---|---|
| 1.20.1 | `(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec)` | `.mc-src/1.20.1/net/minecraft/resources/RegistryDataLoader.java:186` |
| 1.21.1 | `(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, boolean requiredNonEmpty, Consumer<RegistryBuilder<T>> registryBuilderConsumer)` | `.mc-src/1.21.1/net/minecraft/resources/RegistryDataLoader.java:312` |
| 26.1.2 | `(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, RegistryValidator<T> validator, Consumer<RegistryBuilder<T>> registryBuilderConsumer)` | `.mc-src/26.1.2/net/minecraft/resources/RegistryDataLoader.java:323` |

26.1.2 的便捷构造器（`.mc-src/26.1.2/.../RegistryDataLoader.java:324` 与 `:329`）：

```java
public RegistryData(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, RegistryValidator<T> validator)  // :324
// :329
this(key, elementCodec, RegistryValidator.none());
```

`RegistryValidator` 是 26.1.2 新增的 `@FunctionalInterface`（`.mc-src/26.1.2/net/minecraft/resources/RegistryValidator.java:7-24`）：

```java
@FunctionalInterface
public interface RegistryValidator<T> {
    RegistryValidator<?> NONE = (var0, var1) -> {};                       // :8
    RegistryValidator<?> NON_EMPTY = (registry, loadingErrors) -> {       // :9
        if (registry.size() == 0) {
            loadingErrors.put(registry.key(), new IllegalStateException("Registry must be non-empty: " + registry.key().identifier()));
        }
    };
    static <T> RegistryValidator<T> none() { return (RegistryValidator<T>)NONE; }      // :15
    static <T> RegistryValidator<T> nonEmpty() { return (RegistryValidator<T>)NON_EMPTY; }  // :19
    void validate(Registry<T> registry, Map<ResourceKey<?>, Exception> loadingErrors);  // :23
}
```

> ⚠️ **重要限制**：26.1.2 的 `dataPackRegistry(...)` **硬编码传入 `RegistryValidator.none()`**（`:75`、`:98`），事件 API **没有**暴露 validator 参数。因此**无法**通过事件把自定义注册表声明为「必须非空」。原版注册表是在 `RegistryDataLoader.WORLDGEN_REGISTRIES` 里直接写 `RegistryValidator.nonEmpty()` 的（`.mc-src/26.1.2/.../RegistryDataLoader.java:102-114`），mod 走事件时没有这条路径。

### 1.5 检索过程说明（关键字与结果）

在 `.mc-src/26.1.2/net/neoforged/` 下检索了以下关键字，用于确认「是否改名」：

| 关键字 | 结果 |
|---|---|
| `dataPackRegistry` | 命中 `.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:53,54,74,95,97`；`.mc-src/26.1.2/net/neoforged/neoforge/common/NeoForgeMod.java:550,551` |
| `DataPackRegistryEvent` | 命中 `DataPackRegistryEvent.java`（自身）、`DataPackRegistriesHooks.java:43`、`NeoForgeMod.java:148,549`、`RegistryManager.java:80,87` |
| `NewRegistry` | 命中 `DataPackRegistryEvent.java:36`、`RegistryManager.java:79,80`（`NewRegistryEvent` 是另一个类） |
| `RegistryDataLoader` | 命中 `DataPackRegistryEvent.java:13,75,98,108`、`DataPackRegistriesHooks.java:15,25,26,32,44,48,56,60,75,76`、`RegistryManager.java`、`WorldLoader.java:42,49,50` 等 |
| `RegistryValidator` | **26.1.2 独有**：`RegistryValidator.java`（新文件）、`RegistryDataLoader.java:102-161,323,324,329`、`DataPackRegistryEvent.java:14,75,98`、`DataPackRegistriesHooks.java:16,48` |
| `ResourceManagerRegistryLoadTask` | **26.1.2 独有**（1.20.1/1.21.1 无）：`RegistryDataLoader.java:173`、`ResourceManagerRegistryLoadTask.java` |

**结论**：26.1.2 中 `DataPackRegistryEvent` **未改名、未换包**；改的是 `RegistryData` 的校验参数类型与内部加载任务类。

---

### 1.6 三版本 `RegistryData` 列表结构对照

| 项 | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| 世界生成注册表条数 | 19（`RegistryDataLoader.java:56-74`） | 25（`:74-98`） | 45（`:84-128`） |
| 同步表条数 | 6（`RegistrySynchronization.java:28-33`） | 11（`:104-114`） | 28（`:134-161`） |
| 同步表位置 / 容器类型 | `RegistrySynchronization.java:26-34`，`Map` | `RegistryDataLoader.java:103-115`，`List` | `RegistryDataLoader.java:133-162`，`List` |
| 空注册表校验 | 无 | `boolean requiredNonEmpty` | `RegistryValidator<T> validator` |
| 拆出独立 `NETWORK_CODEC` 的注册表 | Biome、DimensionType | 仅 Biome | Biome、DimensionType、Timeline、全部 `*_VARIANT` |
| 装载实现 | 同步，`JsonParser` + `FileToIdConverter.json`（`:157-180`） | 同步 `load(...)`（`:117-128`） | 全异步 `CompletableFuture` + `RegistryLoadTask` / `ResourceManagerRegistryLoadTask`（`:164-249`） |

> ⚠️ **`RegistryValidator.nonEmpty()` / `requiredNonEmpty = true` 只在「直接写进 `WORLDGEN_REGISTRIES` 列表」时可用**（原版自己这么干，如 `.mc-src/26.1.2/.../RegistryDataLoader.java:102` 的 `WOLF_VARIANT`）。
> **mod 通过 `DataPackRegistryEvent` 注册时拿不到这个能力**——事件在 `.mc-src/1.21.1/.../DataPackRegistryEvent.java:75,98` 硬编码 `false`，在 `.mc-src/26.1.2/.../DataPackRegistryEvent.java:75,98` 硬编码 `RegistryValidator.none()`。详见 §7.2。

---

## 2. 如何声明 `RegistryKey<Registry<T>>`

三版本都用 `ResourceKey.createRegistryKey(...)`，只是参数类型不同。

| 版本 | 确切签名 | 证据 |
|---|---|---|
| 1.20.1 | `public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation p_135789_)` | `.mc-src/1.20.1/net/minecraft/resources/ResourceKey.java:23` |
| 1.21.1 | `public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation location)` | `.mc-src/1.21.1/net/minecraft/resources/ResourceKey.java:51` |
| 26.1.2 | `public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier identifier)` | `.mc-src/26.1.2/net/minecraft/resources/ResourceKey.java:29` |

### 2.1 `ResourceLocation` → `Identifier` 改名后的确切类型名（26.1.2）

`.mc-src/26.1.2/net/minecraft/resources/ResourceKey.java`：

```java
private final Identifier identifier;                                        // :15
public static <T> ResourceKey<Registry<T>> createRegistryKey(Identifier identifier)  // :29
public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registryName, Identifier location)  // :25
public Identifier identifier() { return this.identifier; }                  // :55
public Identifier registry() { return ...; }                                // :59
```

- 26.1.2 中 `ResourceLocation` 已被 `Identifier` 完全取代（`net.minecraft.resources.Identifier`）。
- `ResourceKey.location()` → **`ResourceKey.identifier()`**（`:55`）。
- `ResourceKey.registry()` 返回类型由 `ResourceLocation` 变为 `Identifier`（`:59`）。

**`Identifier` 的工厂方法**（三版本命名空间工厂对照）：

| 版本 | 工厂 | 证据 |
|---|---|---|
| 1.20.1 | `ResourceLocation.fromNamespaceAndPath(String, String)` | `.mc-src/1.20.1/net/minecraft/resources/ResourceLocation.java:255` |
| 1.20.1 | `new ResourceLocation(String)`（构造器，Forge 自己也在用） | `.mc-src/1.20.1/net/minecraft/resources/ResourceLocation.java:47` |
| 1.20.1 | `ResourceLocation.withDefaultNamespace(String)` | `.mc-src/1.20.1/net/minecraft/resources/ResourceLocation.java:263` |
| 1.21.1 | `ResourceLocation.fromNamespaceAndPath(String, String)` | `.mc-src/1.21.1/net/neoforged/neoforge/registries/NeoForgeRegistries.java:65` |
| 1.21.1 | `ResourceLocation.withDefaultNamespace(String)` | `.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:248` |
| 26.1.2 | `Identifier.fromNamespaceAndPath(String, String)` | `.mc-src/26.1.2/net/neoforged/neoforge/registries/NeoForgeRegistries.java:65` |
| 26.1.2 | `Identifier.withDefaultNamespace(String)` | `.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:322` |

### 2.2 原版 / 加载器自己的声明范例

**原版 `Registries` 的私有辅助方法**（26.1.2，`.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:321-323`）：

```java
private static <T> ResourceKey<Registry<T>> createRegistryKey(String name) {
    return ResourceKey.createRegistryKey(Identifier.withDefaultNamespace(name));
}
```

**NeoForge 自己的动态注册表 key**（26.1.2，`.mc-src/26.1.2/net/neoforged/neoforge/registries/NeoForgeRegistries.java:61-66`）：

```java
// NeoForge Dynamic
public static final ResourceKey<Registry<BiomeModifier>> BIOME_MODIFIERS = key("biome_modifier");        // :61
public static final ResourceKey<Registry<StructureModifier>> STRUCTURE_MODIFIERS = key("structure_modifier");  // :62

private static <T> ResourceKey<Registry<T>> key(String name) {                                          // :64
    return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoForgeMod.MOD_ID, name));    // :65
}
```

**Forge 1.20.1 的对应写法**（`.mc-src/1.20.1/net/minecraftforge/registries/ForgeRegistries.java:171-176`）：

```java
public static final ResourceKey<Registry<BiomeModifier>> BIOME_MODIFIERS = key("forge:biome_modifier");        // :171
public static final ResourceKey<Registry<StructureModifier>> STRUCTURE_MODIFIERS = key("forge:structure_modifier");  // :172

private static <T> ResourceKey<Registry<T>> key(String name) {                                                 // :174
    return ResourceKey.createRegistryKey(new ResourceLocation(name));                                          // :175
}
```

> 注意 Forge 1.20.1 把命名空间硬编码进字符串（`"forge:biome_modifier"`），而 NeoForge 用 `fromNamespaceAndPath`。**OreFood 应统一用 `Identifier.fromNamespaceAndPath("orefood", "ore_food")`（26.1.2）/ `ResourceLocation.fromNamespaceAndPath("orefood", "ore_food")`（1.20.1、1.21.1）**。

---

## 3. 数据包目录路径约定

### 3.1 路径是怎么拼出来的（完整调用链）

**1.20.1**（`.mc-src/1.20.1/net/minecraft/resources/RegistryDataLoader.java`）：

```java
private static String registryDirPath(ResourceLocation p_252033_) {     // :145
    return ForgeHooks.prefixNamespace(p_252033_);                       // :146
}

static <E> void loadRegistryContents(..., ResourceKey<? extends Registry<E>> p_255792_, ...) {  // :149
    String s = registryDirPath(p_255792_.location());                   // :157
    FileToIdConverter filetoidconverter = FileToIdConverter.json(s);    // :158
    ...
    for (Entry<ResourceLocation, Resource> entry : filetoidconverter.listMatchingResources(p_256349_).entrySet()) {  // :161
        ResourceLocation resourcelocation = entry.getKey();             // :162
        ResourceKey<E> resourcekey = ResourceKey.create(p_255792_, filetoidconverter.fileToId(resourcelocation));  // :163
```

`ForgeHooks.prefixNamespace`（`.mc-src/1.20.1/net/minecraftforge/common/ForgeHooks.java:1296-1298`）：

```java
public static String prefixNamespace(ResourceLocation registryKey) {
    return registryKey.getNamespace().equals("minecraft") ? registryKey.getPath() : registryKey.getNamespace() + "/" + registryKey.getPath();
}
```

**1.21.1**（`.mc-src/1.21.1/net/minecraft/resources/RegistryDataLoader.java:227-233`）：

```java
String s = Registries.elementsDirPath(registry.key());                          // :227
FileToIdConverter filetoidconverter = FileToIdConverter.json(s);                // :228
...
for (Entry<ResourceLocation, Resource> entry : filetoidconverter.listMatchingResources(resourceManager).entrySet()) {  // :231
    ResourceLocation resourcelocation = entry.getKey();                         // :232
    ResourceKey<E> resourcekey = ResourceKey.create(registry.key(), filetoidconverter.fileToId(resourcelocation));  // :233
```

`Registries.elementsDirPath`（`.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:251-253`）：

```java
public static String elementsDirPath(ResourceKey<? extends Registry<?>> registryKey) {
    return net.neoforged.neoforge.common.CommonHooks.prefixNamespace(registryKey.location());   // :252
}
```

**26.1.2**（`.mc-src/26.1.2/net/minecraft/resources/ResourceManagerRegistryLoadTask.java:39-47`）：

```java
FileToIdConverter lister = FileToIdConverter.registry(this.registryKey());                       // :39
return CompletableFuture.<Map<Identifier, Resource>>supplyAsync(() -> lister.listMatchingResources(this.resourceManager), executor)  // :40
    .thenCompose(registryResources -> {
        ...
        ResourceKey<T> elementKey = ResourceKey.create(this.registryKey(), lister.fileToId(resourceId));  // :47
```

`.mc-src/26.1.2/net/minecraft/resources/FileToIdConverter.java:15-17`：

```java
public static FileToIdConverter registry(ResourceKey<? extends Registry<?>> registry) {
    return json(Registries.elementsDirPath(registry));      // :16
}
```

`.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:325-331`：

```java
private static String registryDirPath(ResourceKey<? extends Registry<?>> registryKey) {          // :325
    return net.neoforged.neoforge.common.CommonHooks.prefixNamespace(registryKey.identifier());  // :326
}

public static String elementsDirPath(ResourceKey<? extends Registry<?>> registryKey) {           // :329
    return registryDirPath(registryKey);                                                         // :330
}
```

**三版本的 `prefixNamespace` 定义**（逻辑完全一致）：

| 版本 | 位置 | 签名 |
|---|---|---|
| 1.20.1 | `.mc-src/1.20.1/net/minecraftforge/common/ForgeHooks.java:1296` | `public static String prefixNamespace(ResourceLocation registryKey)` |
| 1.21.1 | `.mc-src/1.21.1/net/neoforged/neoforge/common/CommonHooks.java:1340` | `public static String prefixNamespace(ResourceLocation registryKey)` |
| 26.1.2 | `.mc-src/26.1.2/net/neoforged/neoforge/common/CommonHooks.java:1410` | `public static String prefixNamespace(Identifier registryKey)` |

三处实现体都是：

```java
return registryKey.getNamespace().equals("minecraft") ? registryKey.getPath() : registryKey.getNamespace() + "/" + registryKey.getPath();
```

`FileToIdConverter` 把 `prefix` 与 `.json` 组合成资源路径（26.1.2 `.mc-src/26.1.2/net/minecraft/resources/FileToIdConverter.java:10-34`）：

```java
public record FileToIdConverter(String prefix, String extension) {          // :10
    public static FileToIdConverter json(String prefix) {                   // :11
        return new FileToIdConverter(prefix, ".json");                      // :12
    }
    public Identifier idToFile(Identifier id) {                             // :19
        return id.withPath(this.prefix + "/" + id.getPath() + this.extension);   // :20
    }
    public Identifier fileToId(Identifier file) {                           // :23
        String path = file.getPath();
        return file.withPath(path.substring(this.prefix.length() + 1, path.length() - this.extension.length()));  // :25
    }
    public Map<Identifier, Resource> listMatchingResources(ResourceManager manager) {   // :32
        return manager.listResources(this.prefix, this::extensionMatches);             // :33
    }
}
```

### 3.2 最终结论：目录规则

设注册表 key 为 `<rns>:<rpath>`（如 `orefood:ore_food`），条目 id 为 `<id>`：

```
prefix  = (rns == "minecraft") ? rpath : rns + "/" + rpath
文件路径 = data/<数据包自身命名空间>/<prefix>/<id>.json
```

**对 `orefood:ore_food`**（`rns = "orefood"`，非 minecraft）→ `prefix = "orefood/ore_food"`：

```
data/<pack_ns>/orefood/ore_food/iron_steak.json
data/<pack_ns>/orefood/ore_food/gold_steak.json
data/<pack_ns>/orefood/ore_food/diamond_steak.json
data/<pack_ns>/orefood/ore_food/netherite_steak.json
```

> 注意路径里出现**两次命名空间**：`data/` 后第一个是**数据包自己的命名空间**（可以不是 `orefood`，例如别的整合包在 `data/mypack/orefood/ore_food/...` 里追加定义），第二个 `orefood/` 来自**注册表 key 的命名空间**。

### 3.3 真实文件证据

**证据 1：minecraft 命名空间的注册表不带前缀**

`.mc-src/1.21.1/data/neoforge/damage_type/poison.json`（真实存在）：

```json
{
    "exhaustion": 0.0,
    "message_id": "magic",
    "scaling": "when_caused_by_living_non_player"
}
```

注册表 key 是 `minecraft:damage_type`（`.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:131`：`DAMAGE_TYPE = createRegistryKey("damage_type")`），数据包命名空间是 `neoforge`。路径为 `data/neoforge/damage_type/poison.json` —— **没有 `minecraft/` 这一层**，与 `prefixNamespace` 的 `minecraft` 分支一致。

**证据 2：26.1.2 的 `data/minecraft/` 目录布局（平铺，无统一 `data/<ns>/<registry>/` 重构）**

```
.mc-src/26.1.2/data/minecraft/
  advancement/  banner_pattern/  cat_sound_variant/  cat_variant/  chat_type/
  chicken_sound_variant/  chicken_variant/  cow_sound_variant/  cow_variant/
  damage_type/  datapacks/  dialog/  dimension_type/  enchantment/
  enchantment_provider/  frog_variant/  instrument/  jukebox_song/
  loot_table/  painting_variant/  pig_sound_variant/  pig_variant/
  recipe/  structure/  tags/  test_environment/  test_instance/  timeline/
  trade_set/  trial_spawner/  trim_material/  trim_pattern/  villager_trade/
  wolf_sound_variant/  wolf_variant/  worldgen/  world_clock/
  zombie_nautilus_variant/
```

（由 `Get-ChildItem .mc-src/26.1.2/data/minecraft` 枚举得到。）

可以看到 `damage_type`、`enchantment`、`jukebox_song`、`wolf_variant`、`painting_variant`、`worldgen/`（含 `biome`、`configured_feature` 等子目录）都是**平铺在 `data/minecraft/` 下**，与 1.20.1 / 1.21.1 的规则相同。

### 3.4 26.1.2 路径规则有没有变？

**没有变。** 证据链：

1. 26.1.2 仍走 `Registries.elementsDirPath(...)` → `CommonHooks.prefixNamespace(...)`（`.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:325-331`），实现体与 1.21.1 相同。
2. `FileToIdConverter.registry(...)` 只是 `json(elementsDirPath(registry))` 的语法糖（`.mc-src/26.1.2/net/minecraft/resources/FileToIdConverter.java:15-17`），没有引入新的目录层级。
3. 26.1.2 实际数据包目录仍是平铺结构（见 3.3 证据 2）。
4. 26.1.2 的 `DataPackRegistryEvent` javadoc 仍写着 `data/<datapack_namespace>/modid/registryname/`（`.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:31,47,61,82`）——与 1.21.1 完全相同的措辞。

**唯一新增的是** 26.1.2 里多了一个 `componentsDirPath`（`.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:337-339`），用于数据组件而非动态注册表：

```java
public static String componentsDirPath(ResourceKey<? extends Registry<?>> registryKey) {   // :337
    return "components/" + registryDirPath(registryKey);                                   // :338
}
```

> **未找到**：任何证据表明 26.1.2 要求「统一放在 `data/<ns>/<registry>/` 下」。检索关键字：`elementsDirPath`、`registryDirPath`、`prefixNamespace`、`FileToIdConverter`、`componentsDirPath`。

---

## 4. codec 与 network codec

### 4.1 是否必须提供 network codec？

**不是必须的。** 三版本的第三个参数都是 `@Nullable Codec<T> networkCodec`：

- 1.20.1：`.mc-src/1.20.1/net/minecraftforge/registries/DataPackRegistryEvent.java:25`
- 1.21.1：`.mc-src/1.21.1/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:74`
- 26.1.2：`.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:74`

且都存在「不传 networkCodec」的 2 参重载，内部直接传 `null`：

```java
// 1.20.1 :21-23 / 1.21.1 :53-55 / 26.1.2 :53-55  —— 三版本完全相同
public <T> void dataPackRegistry(ResourceKey<Registry<T>> registryKey, Codec<T> codec) {
    this.dataPackRegistry(registryKey, codec, null);
}
```

### 4.2 提供 / 不提供的后果（确切行为）

行为在 `DataPackRegistriesHooks.addRegistryCodec` 里分支（三版本逻辑一致）：

**1.20.1**（`.mc-src/1.20.1/net/minecraftforge/registries/DataPackRegistriesHooks.java:43-50`）：

```java
static <T> void addRegistryCodec(DataPackRegistryEvent.DataPackRegistryData<T> data) {
    RegistryDataLoader.RegistryData<T> loaderData = data.loaderData();
    DATA_PACK_REGISTRIES.add(loaderData);                                        // :45  总是加入加载列表
    if (data.networkCodec() != null) {                                           // :46
        SYNCED_CUSTOM_REGISTRIES.add(loaderData.key());                          // :47
        NETWORKABLE_REGISTRIES.put(loaderData.key(),
            new RegistrySynchronization.NetworkedRegistryData<>(loaderData.key(), data.networkCodec()));  // :48
    }
}
```

**1.21.1**（`.mc-src/1.21.1/net/neoforged/neoforge/registries/DataPackRegistriesHooks.java:42-49`）：

```java
static <T> void addRegistryCodec(DataPackRegistryEvent.DataPackRegistryData<T> data) {
    RegistryDataLoader.RegistryData<T> loaderData = data.loaderData();
    DATA_PACK_REGISTRIES.add(loaderData);                                        // :44
    if (data.networkCodec() != null) {                                           // :45
        SYNCED_CUSTOM_REGISTRIES.add(loaderData.key());                          // :46
        NETWORKABLE_REGISTRIES.add(new RegistryDataLoader.RegistryData<T>(loaderData.key(), data.networkCodec(), false));  // :47
    }
}
```

**26.1.2**（`.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistriesHooks.java:43-50`）：

```java
static <T> void addRegistryCodec(DataPackRegistryEvent.DataPackRegistryData<T> data) {
    RegistryDataLoader.RegistryData<T> loaderData = data.loaderData();
    DATA_PACK_REGISTRIES.add(loaderData);                                        // :45
    if (data.networkCodec() != null) {                                           // :46
        SYNCED_CUSTOM_REGISTRIES.add(loaderData.key());                          // :47
        NETWORKABLE_REGISTRIES.add(new RegistryDataLoader.RegistryData<T>(loaderData.key(), data.networkCodec(), RegistryValidator.none()));  // :48
    }
}
```

**官方 javadoc 对后果的明确说明**（`.mc-src/26.1.2/net/neoforged/neoforge/registries/DataPackRegistryEvent.java:65-71`，1.21.1 `:65-71` 同）：

```
@param networkCodec the codec to be used for syncing loaded data to clients.
                   If {@code networkCodec} is null, data will not be synced, and clients are not required to have this
                   datapack registry to join a server.
                   <p>
                   If {@code networkCodec} is not null, clients must have this datapack registry/mod
                   when joining a server that has this datapack registry/mod.
                   The data will be synced using the network codec and accessible via {@link ClientPacketListener#registryAccess()}.
```

以及 `getSyncedCustomRegistries()` 的 javadoc（`.mc-src/26.1.2/.../DataPackRegistriesHooks.java:64-68`）：

```
{@return An unmodifiable view of the set of synced non-vanilla datapack registry IDs}
Clients must have each of a server's synced datapack registries to be able to connect to that server;
vanilla clients therefore cannot connect if this list is non-empty on the server.
```

**归纳**：

| networkCodec | 服务端加载 JSON | 同步到客户端 | 原版客户端能否进服 | 客户端可读 |
|---|---|---|---|---|
| `null`（不提供） | ✅ 会加载 | ❌ 不同步 | ✅ 能（只要服务端没有别的 synced 自定义注册表） | ❌ 客户端读不到（客户端侧注册表为空/不存在） |
| 非 null | ✅ 会加载 | ✅ 用该 codec 序列化为 NBT 同步 | ❌ 不能（客户端必须装同一个 mod） | ✅ 可通过 `ClientPacketListener.registryAccess()` 读 |

### 4.3 network codec 的确切类型：是 `Codec<T>`，**不是** `StreamCodec` / `PacketCodec`

三版本参数类型都是 `com.mojang.serialization.Codec<T>`：

- 1.20.1 `DataPackRegistryEvent.java:3` `import com.mojang.serialization.Codec;` + `:25` 参数 `Codec<T> networkCodec`
- 1.21.1 `DataPackRegistryEvent.java:8` + `:74`
- 26.1.2 `DataPackRegistryEvent.java:8` + `:74`

**1.20.1 的容器类型**（`.mc-src/1.20.1/net/minecraft/core/RegistrySynchronization.java:86`）：

```java
public record NetworkedRegistryData<E>(ResourceKey<? extends Registry<E>> key, Codec<E> networkCodec) {
```

**同步的实际实现走 NBT**（26.1.2，`.mc-src/26.1.2/net/minecraft/core/RegistrySynchronization.java:27-67`）：

```java
public static void packRegistries(
    DynamicOps<Tag> ops,                                                       // :28  —— Tag = NBT
    RegistryAccess registries,
    Set<KnownPack> clientKnownPacks,
    BiConsumer<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> output
) {
    RegistryDataLoader.SYNCHRONIZED_REGISTRIES
        .forEach(registryEntry -> packRegistry(ops, (RegistryDataLoader.RegistryData<?>)registryEntry, registries, clientKnownPacks, output));  // :33-34
}

private static <T> void packRegistry(DynamicOps<Tag> ops, RegistryDataLoader.RegistryData<T> registryData, ...) {   // :37
    registries.lookup(registryData.key())                                      // :44
        .ifPresent(registry -> {
            List<RegistrySynchronization.PackedRegistryEntry> packedElements = new ArrayList<>(registry.size());
            registry.listElements()                                            // :48
                .forEach(element -> {
                    ...
                    Tag encodedElement = registryData.elementCodec()           // :59
                        .encodeStart(ops, element.value())                     // :60
                        .getOrThrow(s -> new IllegalArgumentException("Failed to serialize " + element.key() + ": " + s));
                    contents = Optional.of(encodedElement);
                    ...
                    packedElements.add(new RegistrySynchronization.PackedRegistryEntry(element.key().identifier(), contents));  // :65
                });
            output.accept(registry.key(), packedElements);
        });
}
```

> **结论**：network codec 是一个普通的 JSON/NBT `Codec<T>`，被 `encodeStart(NbtOps.INSTANCE, value)` 编码成 NBT 后通过 `ClientboundRegistryDataPacket` 发送。**不要**传 `StreamCodec` / `PacketCodec`。
>
> 原版元素类上另有的 `DIRECT_STREAM_CODEC`（例如 `JukeboxSong.DIRECT_STREAM_CODEC`，`.mc-src/26.1.2/net/minecraft/world/item/JukeboxSong.java:29`）**不是**给动态注册表同步用的，而是给物品组件 / `Holder` 的二进制序列化用的（配合 `ByteBufCodecs.holder(Registries.JUKEBOX_SONG, DIRECT_STREAM_CODEC)`，`:41-42`）。两者用途不同，不要混淆。

### 4.4 codec 的写法（元素 codec）

三版本都用 `RecordCodecBuilder` 生成 `Codec<T>`（不是 `MapCodec`）。范例 `DamageType`：

**1.20.1**（`.mc-src/1.20.1/net/minecraft/world/damagesource/DamageType.java:6-13`）：

```java
public record DamageType(String msgId, DamageScaling scaling, float exhaustion, DamageEffects effects, DeathMessageType deathMessageType) {
    public static final Codec<DamageType> CODEC = RecordCodecBuilder.create(       // :7
        instance -> instance.group(
            Codec.STRING.fieldOf("message_id").forGetter(DamageType::msgId),       // :9
            DamageScaling.CODEC.fieldOf("scaling").forGetter(DamageType::scaling), // :10
            Codec.FLOAT.fieldOf("exhaustion").forGetter(DamageType::exhaustion),   // :11
            DamageEffects.CODEC.optionalFieldOf("effects", DamageEffects.HURT).forGetter(DamageType::effects),  // :12
            DeathMessageType.CODEC.optionalFieldOf("death_message_type", DeathMessageType.DEFAULT).forGetter(DamageType::deathMessageType)  // :13
        ).apply(instance, DamageType::new));
```

**26.1.2**（`.mc-src/26.1.2/net/minecraft/world/damagesource/DamageType.java:12-24`）——注意字段名由 `CODEC` 改为 `DIRECT_CODEC`，并新增了供 `Holder` 用的 `CODEC` 与 `STREAM_CODEC`：

```java
public record DamageType(String msgId, DamageScaling scaling, float exhaustion, DamageEffects effects, DeathMessageType deathMessageType) {
    public static final Codec<DamageType> DIRECT_CODEC = RecordCodecBuilder.create(  // :13
        instance -> instance.group(
            Codec.STRING.fieldOf("message_id").forGetter(DamageType::msgId),         // :15
            DamageScaling.CODEC.fieldOf("scaling").forGetter(DamageType::scaling),   // :16
            Codec.FLOAT.fieldOf("exhaustion").forGetter(DamageType::exhaustion),     // :17
            DamageEffects.CODEC.optionalFieldOf("effects", DamageEffects.HURT).forGetter(DamageType::effects),  // :18
            DeathMessageType.CODEC.optionalFieldOf("death_message_type", DeathMessageType.DEFAULT).forGetter(DamageType::deathMessageType)  // :19
        ).apply(instance, DamageType::new));

    public static final Codec<Holder<DamageType>> CODEC = RegistryFixedCodec.create(Registries.DAMAGE_TYPE);            // :23
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<DamageType>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.DAMAGE_TYPE);  // :24
}
```

**命名约定**：注册进 `RegistryDataLoader` 时用 `DIRECT_CODEC`（元素自身的 codec，字段直接平铺在 JSON 根对象）；引用该注册表条目时用 `CODEC`（`RegistryFixedCodec`，值为 id 字符串）。

### 4.5 一个真实可同步注册表的 `NETWORK_CODEC` 范例（26.1.2）

`Timeline`（`.mc-src/26.1.2/net/minecraft/world/timeline/Timeline.java`）：

```java
public static final Codec<Holder<Timeline>> CODEC = RegistryFixedCodec.create(Registries.TIMELINE);   // :33
public static final Codec<Timeline> DIRECT_CODEC = RecordCodecBuilder.<Timeline>create(...)           // :37
public static final Codec<Timeline> NETWORK_CODEC = DIRECT_CODEC.xmap(Timeline::filterSyncableTracks, Timeline::filterSyncableTracks);  // :49
public static void validateRegistry(Registry<Timeline> timelines, Map<ResourceKey<?>, Exception> loadingErrors) {   // :72
```

它被同时注册进两个列表（`.mc-src/26.1.2/net/minecraft/resources/RegistryDataLoader.java`）：

```java
new RegistryDataLoader.RegistryData<>(Registries.TIMELINE, Timeline.DIRECT_CODEC, Timeline::validateRegistry),   // :126  WORLDGEN_REGISTRIES
new RegistryDataLoader.RegistryData<>(Registries.TIMELINE, Timeline.NETWORK_CODEC),                              // :161  SYNCHRONIZED_REGISTRIES
```

> 这说明：**加载用 codec 与同步用 codec 可以不同**。`NETWORK_CODEC` 通常是 `DIRECT_CODEC` 的 `xmap`/`comapFlatMap` 变体，用来裁剪掉不需要同步的字段。若你的字段全部都要同步，直接复用同一个 `Codec<T>` 即可。

---

## 5. 运行期如何读取注册表内容

### 5.1 `RegistryAccess` 上的注册表获取方法（**26.1.2 有重大改名**）

**1.20.1**（`.mc-src/1.20.1/net/minecraft/core/RegistryAccess.java`）：

```java
<E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> p_123085_);          // :19
default <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> p_256275_) {  // :22
    return this.registry(p_256275_).map(Registry::asLookup);                                        // :23
}
default <E> Registry<E> registryOrThrow(ResourceKey<? extends Registry<? extends E>> p_175516_) {   // :26
    return this.registry(p_175516_).orElseThrow(() -> new IllegalStateException("Missing registry: " + p_175516_));  // :27
}
Stream<RegistryAccess.RegistryEntry<?>> registries();                                               // :30
```

**1.21.1**（`.mc-src/1.21.1/net/minecraft/core/RegistryAccess.java`）：

```java
<E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> registryKey);        // :26
default <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {  // :29
    return this.registry(registryKey).map(Registry::asLookup);                                      // :30
}
default <E> Registry<E> registryOrThrow(ResourceKey<? extends Registry<? extends E>> registryKey) { // :36
    return this.registry(registryKey).orElseThrow(() -> new IllegalStateException("Missing registry: " + registryKey));  // :37
}
Stream<RegistryAccess.RegistryEntry<?>> registries();                                               // :40
default Stream<ResourceKey<? extends Registry<?>>> listRegistries() {                               // :43
    return this.registries().map(RegistryAccess.RegistryEntry::key);                                // :44
}
```

**26.1.2**（`.mc-src/26.1.2/net/minecraft/core/RegistryAccess.java`）—— **`registry` → `lookup`**：

```java
<E> Optional<Registry<E>> lookup(final ResourceKey<? extends Registry<? extends E>> registryKey);    // :20   （原 registry）
default <E> Registry<E> lookupOrThrow(ResourceKey<? extends Registry<? extends E>> name) {          // :22   （原 registryOrThrow）
    return this.lookup(name).orElseThrow(() -> new IllegalStateException("Missing registry: " + name));  // :23
}
Stream<RegistryAccess.RegistryEntry<?>> registries();                                               // :26
default Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {                             // :29   （原 listRegistries）
    return this.registries().map(e -> e.key);                                                       // :30
}
```

> ⚠️ **26.1.2 里 `RegistryAccess.registry(...)` 与 `registryOrThrow(...)` 已经不存在**（被 `lookup` / `lookupOrThrow` 取代）。同时 26.1.2 **没有** `lookup(ResourceKey)` 返回 `HolderLookup.RegistryLookup` 的默认方法了——`lookup` 现在直接返回 `Registry<E>`（而 `Registry<T> extends HolderLookup.RegistryLookup<T>`，见 5.3，所以仍可当 lookup 用）。

### 5.2 从 server / level 拿到 `RegistryAccess`

| 来源 | 1.20.1 | 1.21.1 | 26.1.2 |
|---|---|---|---|
| `MinecraftServer.registryAccess()` | `.mc-src/1.20.1/net/minecraft/server/MinecraftServer.java:1797` → `RegistryAccess.Frozen` | 同（NeoForge 侧示例见 `.mc-src/1.21.1/net/neoforged/neoforge/server/ServerLifecycleHooks.java:159`） | `.mc-src/26.1.2/net/minecraft/server/MinecraftServer.java:2078` → `RegistryAccess.Frozen` |
| `LevelReader.registryAccess()` | `.mc-src/1.20.1/net/minecraft/world/level/LevelReader.java:219` → `RegistryAccess registryAccess();` | 同 | `.mc-src/26.1.2/net/minecraft/world/level/LevelReader.java:215` → `RegistryAccess registryAccess();` |
| `ServerLevel` 继承 `LevelReader` | `.mc-src/1.20.1/net/minecraft/server/level/ServerLevel.java`（如 `:1277` 使用 `this.registryAccess()`） | 同 | `.mc-src/26.1.2/net/minecraft/server/level/ServerLevel.java:245`（`super(levelData, dimension, server.registryAccess(), ...)`） |
| `StructureManager.registryAccess()` | `.mc-src/1.20.1/net/minecraft/world/level/StructureManager.java:168-169` | 同 | `.mc-src/26.1.2/net/minecraft/world/level/StructureManager.java:170-171` |
| `WorldGenRegion.registryAccess()` | `.mc-src/1.20.1/net/minecraft/server/level/WorldGenRegion.java:364-365` | 同 | `.mc-src/26.1.2/net/minecraft/server/level/WorldGenRegion.java:333-334` |
| `ClientPacketListener.registryAccess()`（客户端） | 由 javadoc 提及（`.mc-src/1.20.1/.../DataPackRegistryEvent.java` 无；1.21.1 javadoc `DataPackRegistryEvent.java:71`） | `.mc-src/1.21.1/.../DataPackRegistryEvent.java:71` | `.mc-src/26.1.2/.../DataPackRegistryEvent.java:71` |
| `CommandSourceStack.registryAccess()` | `.mc-src/1.20.1/net/minecraftforge/server/command/TPSCommand.java:50` 用法 | `.mc-src/1.21.1/net/neoforged/neoforge/server/command/DimensionsCommand.java:28` | `.mc-src/26.1.2/net/neoforged/neoforge/server/command/DimensionsCommand.java:28` |

### 5.3 `Registry<T>` 上的取值 / 遍历方法（**26.1.2 亦有改名**）

`Registry<T>` 在三个版本都继承 `HolderLookup.RegistryLookup<T>`，因此 `listElements()` 三版本都可用：

- 1.20.1：`public interface Registry<T> extends ... HolderLookup.RegistryLookup<T>`（`.mc-src/1.20.1/net/minecraft/core/Registry.java`，`holders()` 在 `:153`）
- 1.21.1：`holders()` 在 `.mc-src/1.21.1/net/minecraft/core/Registry.java:157`
- 26.1.2：`public interface Registry<T> extends IdMap<T>, Keyable, HolderLookup.RegistryLookup<T>, net.neoforged.neoforge.registries.IRegistryExtension<T>`（`.mc-src/26.1.2/net/minecraft/core/Registry.java:26`）

`HolderLookup.RegistryLookup.listElements()` 声明：

- 1.20.1：`.mc-src/1.20.1/net/minecraft/core/HolderLookup.java:15` — `Stream<Holder.Reference<T>> listElements();`
- 1.21.1：`.mc-src/1.21.1/net/minecraft/core/HolderLookup.java:17` — 同上
- 26.1.2：`.mc-src/26.1.2/net/minecraft/core/HolderLookup.java:18` — 同上

**按 id 查找 / 遍历对照表**：

| 操作 | 1.20.1（`Registry.java`） | 1.21.1（`Registry.java`） | 26.1.2（`Registry.java`） |
|---|---|---|---|
| 按 id 取（可空） | `@Nullable T get(@Nullable ResourceKey<T> key)` `:75`；`@Nullable T get(@Nullable ResourceLocation name)` `:78-79` | 同 `:76`、`:78-79` | **`@Nullable T getValue(@Nullable ResourceKey<T> key)` `:67`**；**`@Nullable T getValue(@Nullable Identifier key)` `:69`** |
| 按 id 取（Optional） | `Optional<T> getOptional(@Nullable ResourceLocation)` `:79`；`Optional<T> getOptional(@Nullable ResourceKey<T>)` `:83` | 同 `:85`、`:89` | `Optional<T> getOptional(@Nullable Identifier)` `:73`；`Optional<T> getOptional(@Nullable ResourceKey<T>)` `:77` |
| 按 id 取（抛异常） | 未找到名为 `getOrThrow` 的方法（检索关键字 `getOrThrow`、`orElseThrow`） | `default T getOrThrow(ResourceKey<T> key)` `:95` | **`default T getValueOrThrow(ResourceKey<T> key)` `:83`** |
| 取 Holder | `Optional<Holder.Reference<T>> getHolder(int)` `:143`；`getHolder(ResourceKey<T>)` `:145` | `getHolder(int)` `:145`；`getHolder(ResourceLocation)` `:147`；`getHolder(ResourceKey<T>)` `:149`；`getHolderOrThrow(ResourceKey<T>)` `:153` | `Optional<Holder.Reference<T>> get(int id)` `:133`；**`Optional<Holder.Reference<T>> get(Identifier id)` `:135`** |
| 遍历 id 集合 | `Set<ResourceLocation> keySet()` `:96` | `Set<ResourceLocation> keySet()` `:104` | **`Set<Identifier> keySet()` `:92`** |
| 遍历键值对 | `Set<Entry<ResourceKey<T>, T>> entrySet()` `:98` | 同 `:106` | 同 `:94` |
| 遍历 key | `Set<ResourceKey<T>> registryKeySet()` | `Set<ResourceKey<T>> registryKeySet()` `:108` | `Set<ResourceKey<T>> registryKeySet()` `:96` |
| 遍历 Holder | `Stream<Holder.Reference<T>> holders()` `:153` | `Stream<Holder.Reference<T>> holders()` `:157` | **未找到 `holders()`** — 检索关键字 `holders`、`listElements`，仅剩 `listElements()`（继承自 `HolderLookup.RegistryLookup`） |
| 遍历元素 | `default Stream<T> stream()`（继承 `Iterable`/`IdMap`） | `default Stream<T> stream()` `:112` | `default Stream<T> stream()` `:100` |
| 是否存在 | `containsKey(ResourceLocation)` `:108`；`containsKey(ResourceKey<T>)` `:110` | `containsKey(ResourceLocation)` `:116`；`containsKey(ResourceKey<T>)` `:118` | `containsKey(Identifier)` `:104`；`containsKey(ResourceKey<T>)` `:106` |
| 反向取 id | `@Nullable ResourceLocation getKey(T value)`（1.21.1 `:64-65`） | 同 | **`@Nullable Identifier getKey(T thing)` `:60`** |

> ⚠️ **26.1.2 的 `holders()` 已被删除**，只能用 `listElements()`（返回 `Stream<Holder.Reference<T>>`，与 `holders()` 等价）。
> 另外 26.1.2 的 `Registry` 也**没有** `registryLifecycle()`（1.21.1 `:83` 有；26.1.2 检索 `registryLifecycle` 无命中）。

### 5.4 三版本读取代码对照（可直接照抄）

**1.20.1 / 1.21.1**：

```java
// 拿注册表（不存在会抛 IllegalStateException）
Registry<OreFoodDefinition> reg = server.registryAccess().registryOrThrow(OreFoodRegistries.ORE_FOOD);

// 按 id 查（1.20.1）
Optional<OreFoodDefinition> def = reg.getOptional(ResourceLocation.fromNamespaceAndPath("orefood", "iron_steak"));
// 按 id 查（1.21.1）
OreFoodDefinition def2 = reg.get(ResourceLocation.fromNamespaceAndPath("orefood", "iron_steak"));   // 可空

// 遍历全部
reg.holders().forEach(h -> { ResourceKey<OreFoodDefinition> k = h.key(); OreFoodDefinition v = h.value(); });
// 或
reg.listElements().forEach(h -> ...);
```

**26.1.2**：

```java
// 拿注册表（不存在会抛 IllegalStateException）
Registry<OreFoodDefinition> reg = server.registryAccess().lookupOrThrow(OreFoodRegistries.ORE_FOOD);

// 按 id 查
OreFoodDefinition def = reg.getValue(Identifier.fromNamespaceAndPath("orefood", "iron_steak"));   // 可空
Optional<OreFoodDefinition> opt = reg.getOptional(Identifier.fromNamespaceAndPath("orefood", "iron_steak"));
OreFoodDefinition must = reg.getValueOrThrow(ResourceKey.create(OreFoodRegistries.ORE_FOOD,
        Identifier.fromNamespaceAndPath("orefood", "iron_steak")));

// 遍历全部（holders() 已删，用 listElements()）
reg.listElements().forEach(h -> { ResourceKey<OreFoodDefinition> k = h.key(); OreFoodDefinition v = h.value(); });
```

**`RegistryAccess` 级别遍历全部注册表**：

```java
// 1.20.1 / 1.21.1
server.registryAccess().registries().forEach(e -> { ResourceKey<? extends Registry<?>> k = e.key(); Registry<?> v = e.value(); });
// 1.21.1 另有
server.registryAccess().listRegistries().forEach(k -> ...);

// 26.1.2
server.registryAccess().registries().forEach(e -> { var k = e.key(); var v = e.value(); });
server.registryAccess().listRegistryKeys().forEach(k -> ...);
```

---

## 6. 数据加载完成的事件 / 回调

### 6.1 Forge 1.20.1

**`net.minecraftforge.event.AddReloadListenerEvent`**（`.mc-src/1.20.1/net/minecraftforge/event/AddReloadListenerEvent.java:17`）：

```java
public class AddReloadListenerEvent extends Event {                                          // :17
    private final ReloadableServerResources serverResources;                                 // :19
    private final RegistryAccess registryAccess;                                             // :20

    public AddReloadListenerEvent(ReloadableServerResources serverResources, RegistryAccess registryAccess) {  // :22
    ...
    public void addListener(PreparableReloadListener listener) {                             // :27
        this.listeners.add(new AddReloadListenerEvent.WrappedStateAwareListener(listener));  // :28
    }
    public List<PreparableReloadListener> getListeners() { ... }                             // :31
    public ReloadableServerResources getServerResources() { ... }                            // :35
    public ICondition.IContext getConditionContext() { ... }                                 // :39
    public RegistryAccess getRegistryAccess() { return this.registryAccess; }                 // :43-45
}
```

- **参数类型是 `PreparableReloadListener`**（`import net.minecraft.server.packs.resources.PreparableReloadListener;`，`:10`），不是 `ResourceManagerReloadListener`。
- 该事件**不实现 `IModBusEvent`**（只 `extends Event`，`:17`）→ 必须注册在 **`MinecraftForge.EVENT_BUS`**（Forge 总线）上。
- 在事件里能拿到 `RegistryAccess`（`:43`），且其 javadoc 在 NeoForge 版本里明确写着「All built-in and dynamic registries are loaded and frozen by this point」。

**生命周期事件**（`.mc-src/1.20.1/net/minecraftforge/event/server/`，实际存在这些文件）：

- `ServerAboutToStartEvent.java` — `.mc-src/1.20.1/net/minecraftforge/event/server/ServerAboutToStartEvent.java:5-8`
  ```java
  public class ServerAboutToStartEvent extends ServerLifecycleEvent {     // :5
      public ServerAboutToStartEvent(MinecraftServer server) { super(server); }   // :6-8
  }
  ```
  → 通过 `ServerLifecycleEvent.getServer()` 拿 `MinecraftServer`，再 `server.registryAccess()`。**注意此时动态注册表可能尚未加载完成**（见 6.4 的时序分析）。
- `ServerLifecycleEvent.java`、`ServerStartedEvent.java`、`ServerStartingEvent.java`、`ServerStoppedEvent.java`、`ServerStoppingEvent.java` 均存在（同目录）。
- **监听器注册方式**：Forge 自己的写法是 `MinecraftForge.EVENT_BUS.addListener(this::serverStopping);`（`.mc-src/1.20.1/net/minecraftforge/common/ForgeMod.java:427`）；mod 事件总线用 `context.getModEventBus().addListener(...)`（`:408`）。

> 参考：`net.minecraftforge.event.TagsUpdatedEvent`（`.mc-src/1.20.1/net/minecraftforge/event/TagsUpdatedEvent.java:7,11,17`）也能拿到 `RegistryAccess`：
> ```java
> private final RegistryAccess registryAccess;                                                    // :7
> public TagsUpdatedEvent(RegistryAccess registryAccess, boolean fromClientPacket, boolean isIntegratedServerConnection) {  // :11
> public RegistryAccess getRegistryAccess() { ... }                                               // :17
> ```

### 6.2 NeoForge 1.21.1

**`net.neoforged.neoforge.event.AddReloadListenerEvent`**（`.mc-src/1.21.1/net/neoforged/neoforge/event/AddReloadListenerEvent.java:31`）：

```java
public class AddReloadListenerEvent extends Event {                                    // :31
    private final List<PreparableReloadListener> listeners = new ArrayList<>();        // :32
    private final ReloadableServerResources serverResources;                           // :33
    private final RegistryAccess registryAccess;                                       // :34

    public AddReloadListenerEvent(ReloadableServerResources serverResources, RegistryAccess registryAccess) {  // :36
    public void addListener(PreparableReloadListener listener) {                       // :44
        listeners.add(new WrappedStateAwareListener(listener));                        // :45
    }
    public List<PreparableReloadListener> getListeners() { ... }                       // :48
    public ReloadableServerResources getServerResources() { ... }                      // :55
    public ICondition.IContext getConditionContext() { ... }                           // :64
    public RegistryAccess getRegistryAccess() { return registryAccess; }               // :74-76
}
```

- 同样 `extends Event`（`:31`），**在 `NeoForge.EVENT_BUS` 上触发**（javadoc `:29`：`The event is fired on the {@link NeoForge#EVENT_BUS}`）。
- `WrappedStateAwareListener extends ContextAwareReloadListener implements PreparableReloadListener`（`:78`），会把 `ICondition.IContext` 与 `HolderLookup.Provider` 注入（`:86-90`）——说明**推荐在监听器里通过 `ContextAwareReloadListener` 拿到带 tag 的 registry lookup**。

**`AddServerReloadListenersEvent` 在 1.21.1 中不存在** —— 检索 `.mc-src/1.21.1/net/neoforged/neoforge/event/`（含子目录）未找到该文件名；1.21.1 该目录顶层文件列表见下（无 `AddServerReloadListenersEvent.java`）：

```
AddPackFindersEvent.java  AddReloadListenerEvent.java  AnvilUpdateEvent.java
BlockEntityTypeAddBlocksEvent.java  BuildCreativeModeTabContentsEvent.java
CommandEvent.java  DifficultyChangeEvent.java  EventHooks.java  GameShuttingDownEvent.java
GrindstoneEvent.java  ItemAttributeModifierEvent.java  ItemStackedOnOtherEvent.java
LootTableLoadEvent.java  ModifyDefaultComponentsEvent.java  ModMismatchEvent.java
OnDatapackSyncEvent.java  package-info.java  PlayLevelSoundEvent.java
RegisterCommandsEvent.java  RegisterGameTestsEvent.java  RegisterStructureConversionsEvent.java
ServerChatEvent.java  StatAwardEvent.java  TagsUpdatedEvent.java  VanillaGameEvent.java
```

（检索关键字：`AddServerReloadListenersEvent`、`SortedReloadListenerEvent`，1.21.1 均**未找到**。）

**NeoForge 1.21.1 的生命周期事件**（`.mc-src/1.21.1/net/neoforged/neoforge/event/server/`）：`ServerAboutToStartEvent.java`、`ServerLifecycleEvent.java`、`ServerStartedEvent.java`、`ServerStartingEvent.java`、`ServerStoppedEvent.java`、`ServerStoppingEvent.java` 全部存在。

**NeoForge 1.21.1 的 `TagsUpdatedEvent`** 也提供 `getRegistryAccess()`（`.mc-src/1.21.1/net/neoforged/neoforge/event/TagsUpdatedEvent.java:15,19,28`）。

### 6.3 NeoForge 26.1.2（**有破坏性变化**）

**`AddReloadListenerEvent` 已被删除** —— 检索 `.mc-src/26.1.2/net/neoforged/neoforge/event/` 顶层与子目录，**未找到** `AddReloadListenerEvent.java`。替代品是：

**`net.neoforged.neoforge.event.AddServerReloadListenersEvent`**（`.mc-src/26.1.2/net/neoforged/neoforge/event/AddServerReloadListenersEvent.java:27`）：

```java
public class AddServerReloadListenersEvent extends SortedReloadListenerEvent {          // :27
    private final ReloadableServerResources serverResources;                            // :28
    private final RegistryAccess registryAccess;                                        // :29
    private final Map<ListenerKey<?>, PreparableReloadListener> retainedListeners;      // :30

    @ApiStatus.Internal
    public AddServerReloadListenersEvent(
            ReloadableServerResources serverResources,
            RegistryAccess registryAccess,
            Map<ListenerKey<?>, PreparableReloadListener> retainedListeners) {          // :33-36
        super(serverResources.listeners(), AddServerReloadListenersEvent::lookupName);   // :37
        ...
    }

    /// Adds a new [`reload listener`][PreparableReloadListener] to the resource manager and retains it ...
    /// @param key      The resource location that identifies the reload listener for dependency sorting and lookup.
    /// @param listener The listener to add.
    /// @throws IllegalArgumentException if another listener with that key was already registered.
    public <T extends PreparableReloadListener> void addRetainedListener(ListenerKey<T> key, T listener) {   // :52
        this.addListener(key.getListenerId(), listener);                                // :53
        this.retainedListeners.put(key, listener);                                      // :54
    }

    public ReloadableServerResources getServerResources() { ... }                       // :60
    public ICondition.IContext getConditionContext() { ... }                            // :69

    /// ... All built-in and dynamic registries are loaded and frozen by this point.
    /// @deprecated Use [ContextAwareReloadListener#getRegistryLookup()] instead
    @Deprecated(forRemoval = true, since = "26.1.2")
    public RegistryAccess getRegistryAccess() { return registryAccess; }                // :83-86
}
```

**父类 `SortedReloadListenerEvent`**（`.mc-src/26.1.2/net/neoforged/neoforge/event/SortedReloadListenerEvent.java:28`）提供排序式注册：

```java
public abstract class SortedReloadListenerEvent extends Event {                        // :28
    public void addListener(Identifier key, PreparableReloadListener listener) {        // :62
        if (this.registry.containsKey(key) || this.registry.containsValue(listener)) {
            throw new IllegalArgumentException("Attempted to register two reload listeners for the same key: " + key);   // :64
        }
        ...
    }
    public void addDependency(Identifier first, Identifier second) { ... }              // :85
    public Graph<PreparableReloadListener> getGraph() { ... }                           // :92
    public Map<Identifier, PreparableReloadListener> getRegistry() { ... }              // :101
    public NameLookup getNameLookup() { ... }                                           // :108
}
```

**关键签名差异（1.21.1 → 26.1.2）**：

| | 1.21.1 | 26.1.2 |
|---|---|---|
| 事件类 | `AddReloadListenerEvent` | `AddServerReloadListenersEvent extends SortedReloadListenerEvent` |
| 添加方法 | `addListener(PreparableReloadListener)` | `addListener(Identifier key, PreparableReloadListener)`（**多了 key 参数**） |
| 保留式添加 | 无 | `addRetainedListener(ListenerKey<T>, T)` |
| 依赖排序 | 无 | `addDependency(Identifier, Identifier)` |
| `getRegistryAccess()` | 正常可用 | **`@Deprecated(forRemoval = true, since = "26.1.2")`**，官方建议改用 `ContextAwareReloadListener#getRegistryLookup()` |

**26.1.2 的生命周期事件**（`.mc-src/26.1.2/net/neoforged/neoforge/event/server/`，实际存在）：

```
ServerAboutToStartEvent.java   ServerLifecycleEvent.java   ServerStartedEvent.java
ServerStartingEvent.java       ServerStoppedEvent.java     ServerStoppingEvent.java
```

`ServerAboutToStartEvent`（`.mc-src/26.1.2/net/neoforged/neoforge/event/server/ServerAboutToStartEvent.java:19-22`）：

```java
public class ServerAboutToStartEvent extends ServerLifecycleEvent {                    // :19
    public ServerAboutToStartEvent(MinecraftServer server) { super(server); }          // :20-22
}
```

`ServerStartedEvent`（`.mc-src/26.1.2/net/neoforged/neoforge/event/server/ServerStartedEvent.java:15-18`）—— javadoc `:11`：`Called after {@link ServerStartingEvent} when the server is available and ready to play.`

### 6.4 读取时机的权威答案：注册表到底什么时候被加载

**加载点唯一**：`WorldLoader.load(...)`（服务端启动 / 进入世界时调用一次）。

**26.1.2**（`.mc-src/26.1.2/net/minecraft/server/WorldLoader.java:42` 与 `:49`）：

```java
return RegistryDataLoader.load(resources, worldgenContextRegistries,
        net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistries(), backgroundExecutor)   // :42  ← 自定义注册表在这里加载
    .thenComposeAsync(loadedWorldgenRegistries -> {
        ...
        return RegistryDataLoader.load(resources, dimensionContextRegistries,
                RegistryDataLoader.DIMENSION_REGISTRIES, backgroundExecutor)                                       // :49
            .thenComposeAsync(initialWorldgenDimensions -> {
                ...
                return ReloadableServerResources.loadResources(resources, resourcesLoadContext, ...)            // :64
```

- 自定义动态注册表通过 `DataPackRegistriesHooks.getDataPackRegistries()` 在 **`:42`** 一次性加载。
- 之后才加载 `ReloadableServerResources`（`:64`），也就是 `AddServerReloadListenersEvent` 的触发点。

**1.20.1 / 1.21.1** 结构相同：`.mc-src/1.20.1/net/minecraft/server/WorldLoader.java:42` 与 `:80`；`.mc-src/1.21.1/net/minecraft/server/WorldLoader.java:39`、`:76`。

**结论（三版本一致）**：

| 时机 | 动态注册表是否可读 |
|---|---|
| `DataPackRegistryEvent.NewRegistry` 事件内 | ❌ 尚未加载（此时只是在声明注册表） |
| `AddReloadListenerEvent` / `AddServerReloadListenersEvent` 内 | ✅ 可读（javadoc 明确：「All built-in and dynamic registries are loaded and frozen by this point」，`.mc-src/1.21.1/.../AddReloadListenerEvent.java:70`、`.mc-src/26.1.2/.../AddServerReloadListenersEvent.java:74`） |
| `ServerAboutToStartEvent` 内 | ⚠️ 不保证 —— `WorldLoader.load` 在 `ServerAboutToStartEvent` **之后**才被调用（`ServerAboutToStartEvent` javadoc 说 "Called before the server begins loading anything"，`.mc-src/26.1.2/.../ServerAboutToStartEvent.java:12`）。**不要在这里读注册表内容。** |
| `ServerStartedEvent` 内 | ✅ 可读（此时世界已加载完成） |
| 运行时（`ServerLevel` 的 tick / 逻辑中） | ✅ 可读，用 `level.registryAccess()` |

> **推荐**：运行时逻辑一律走 `level.registryAccess().lookupOrThrow(KEY)`（26.1.2）/ `registryOrThrow(KEY)`（1.20.1、1.21.1）**惰性读取**，而不是在启动事件里缓存 —— 因为 `/reload` 不会重建注册表（见 7.3），缓存是安全的，但惰性读取更简单且天然规避时序问题。

---

### 6.5 26.1.2 的 reload listener 接口本身也变了（实现时必须注意）

**`PreparableReloadListener` 从 6 参数变成 4 参数**：

- 1.20.1（`.mc-src/1.20.1/net/minecraft/server/packs/resources/PreparableReloadListener.java:8-15`）—— 6 参数：
  ```java
  CompletableFuture<Void> reload(
      PreparableReloadListener.PreparationBarrier stage,
      ResourceManager resourceManager,
      ProfilerFiller preparationsProfiler,
      ProfilerFiller reloadProfiler,
      Executor backgroundExecutor,
      Executor gameExecutor);
  ```
- 26.1.2（`.mc-src/26.1.2/net/minecraft/server/packs/resources/PreparableReloadListener.java:9-18`）—— **4 参数，且是 `@FunctionalInterface`**：
  ```java
  @FunctionalInterface
  public interface PreparableReloadListener {
      CompletableFuture<Void> reload(
          PreparableReloadListener.SharedState currentReload,          // :12  新类型
          Executor taskExecutor,
          PreparableReloadListener.PreparationBarrier preparationBarrier,
          Executor reloadExecutor);                                     // :16
      default void prepareSharedState(PreparableReloadListener.SharedState currentReload) {}   // :18
  }
  ```
  新类型 `SharedState`（`:30-49`）与 `StateKey<T>`（`:51-52`）。

> ⚠️ **这是从 1.21.1 迁到 26.1.2 时最容易踩的坑**：老代码的 `reload(PreparationBarrier, ResourceManager, ProfilerFiller, ProfilerFiller, Executor, Executor)` 签名在 26.1.2 里**已不存在**。

**`ResourceManagerReloadListener` 三版本都存在，但 26.1.2 签名也变了**（`.mc-src/26.1.2/net/minecraft/server/packs/resources/ResourceManagerReloadListener.java:9-26`）：

```java
public interface ResourceManagerReloadListener extends PreparableReloadListener {     // :9
    @Override
    default CompletableFuture<Void> reload(
        PreparableReloadListener.SharedState currentReload,
        Executor taskExecutor,
        PreparableReloadListener.PreparationBarrier preparationBarrier,
        Executor reloadExecutor) { ... }                                              // :11-16
    void onResourceManagerReload(ResourceManager resourceManager);                    // :26
}
```
（1.20.1 同项在 `.mc-src/1.20.1/net/minecraft/server/packs/resources/ResourceManagerReloadListener.java:9`；1.21.1 在 `.mc-src/1.21.1/net/minecraft/server/packs/resources/ResourceManagerReloadListener.java:9`。）

> 因此 1.20.1 / 1.21.1 的 `event.addListener(ResourceManagerReloadListener)` 能编译（它 `extends PreparableReloadListener`），26.1.2 也能编译，但**回调方法的参数列表不同**。

**客户端 reload 事件也改名了**：

| 版本 | 客户端事件类 | 总线 |
|---|---|---|
| 1.20.1 | `net.minecraftforge.client.event.RegisterClientReloadListenersEvent`（`.mc-src/1.20.1/net/minecraftforge/client/event/RegisterClientReloadListenersEvent.java:9`，`implements IModBusEvent`），方法 `registerReloadListener(PreparableReloadListener)`（`:17`） | **mod bus** |
| 1.21.1 | `net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent`（`.mc-src/1.21.1/.../RegisterClientReloadListenersEvent.java:28`，`implements IModBusEvent`），方法 `registerReloadListener(PreparableReloadListener)`（`:41`）；javadoc `:26` 明确「fired on the mod-specific event bus」 | **mod bus** |
| 26.1.2 | **改名为 `net.neoforged.neoforge.client.event.AddClientReloadListenersEvent`**（`.mc-src/26.1.2/.../AddClientReloadListenersEvent.java:26`，`extends SortedReloadListenerEvent implements IModBusEvent`），继承 `addListener(Identifier, PreparableReloadListener)` | **mod bus** |

> 注意服务端事件在 **game bus**（`NeoForge.EVENT_BUS`），客户端事件在 **mod bus**（`IModBusEvent`）——两者不对称。

**26.1.2 读注册表的推荐新姿势：`ContextAwareReloadListener`**

`.mc-src/26.1.2/net/neoforged/neoforge/resource/ContextAwareReloadListener.java`：

```java
public abstract class ContextAwareReloadListener implements PreparableReloadListener {   // :28
    protected final ICondition.IContext getContext() { ... }                              // :42
    protected final HolderLookup.Provider getRegistryLookup() { ... }                     // :49
    protected final ConditionalOps<JsonElement> makeConditionalOps() { ... }              // :56
}
```

- javadoc `:24`：context 保证在 `PreparableReloadListener#reload` 全程可用。
- 注入点：`.mc-src/26.1.2/net/minecraft/server/ReloadableServerResources.java:142-146`（在 `SimpleReloadInstance.create` 之前），清理在 `:157-165`。
- 1.21.1 同名类存在：`.mc-src/1.21.1/net/neoforged/neoforge/resource/ContextAwareReloadListener.java`，注入点 `.mc-src/1.21.1/net/minecraft/server/ReloadableServerResources.java:115-117`。

**26.1.2 的 `ListenerKey`（可后续取回 listener）**

- `net.neoforged.neoforge.resource.ListenerKey`，工厂 `public static <T extends PreparableReloadListener> ListenerKey<T> create(Identifier listenerId)`（`.mc-src/26.1.2/net/neoforged/neoforge/resource/ListenerKey.java:21`）
- 取回：`public <T extends PreparableReloadListener> T getListener(ListenerKey<T> key)`（`.mc-src/26.1.2/net/minecraft/server/ReloadableServerResources.java:87`）

**官方 26.1.2 reload 监听范例**（`.mc-src/26.1.2/net/neoforged/neoforge/common/NeoForgeEventHandler.java:152-159`）：

```java
@SubscribeEvent
public void onResourceReload(AddServerReloadListenersEvent event) {                                  // :153
    event.addListener(NeoForgeReloadListeners.RECIPE_PRIORITIES, new RecipePriorityManager(event.getServerResources().getRecipeManager()));  // :154
    event.addListener(NeoForgeReloadListeners.CREATIVE_TABS, CreativeModeTabRegistry.getReloadListener());   // :155
    event.addRetainedListener(NeoForgeReloadListeners.LOOT_MODIFIERS_KEY, new LootModifierManager());        // :157
    event.addRetainedListener(NeoForgeReloadListeners.DATA_MAPS_KEY, new DataMapLoader());                   // :158
}
```
（注册：`NeoForge.EVENT_BUS.register(new NeoForgeEventHandler());` — `.mc-src/26.1.2/net/neoforged/neoforge/common/NeoForgeMod.java:582`。）

**官方 1.21.1 reload 监听范例**（`.mc-src/1.21.1/net/neoforged/neoforge/common/NeoForgeEventHandler.java:155-160`）：

```java
@SubscribeEvent
public void onResourceReload(AddReloadListenerEvent event) {           // :156
    INSTANCE = new LootModifierManager();
    event.addListener(INSTANCE);                                        // :158
    event.addListener(DATA_MAPS = new DataMapLoader(event.getRegistryAccess()));   // :159  ← 直接吃 event.getRegistryAccess()
}
```

**事件 post 位置（三版本）**：

| 版本 | post 位置 |
|---|---|
| 1.20.1 | `ForgeEventFactory.onResourceReload(...)`（`.mc-src/1.20.1/net/minecraftforge/event/ForgeEventFactory.java:640-644`，`:642` 为 `MinecraftForge.EVENT_BUS.post(event)`），由 `.mc-src/1.20.1/net/minecraft/server/ReloadableServerResources.java:91` 调用 |
| 1.21.1 | `EventHooks.onResourceReload(...)`（`.mc-src/1.21.1/net/neoforged/neoforge/event/EventHooks.java:818-822`，`:820` 为 `NeoForge.EVENT_BUS.post(event)`），由 `.mc-src/1.21.1/net/minecraft/server/ReloadableServerResources.java:114` 调用 |
| 26.1.2 | `EventHooks.onResourceReload(...)`（`.mc-src/26.1.2/net/neoforged/neoforge/event/EventHooks.java:835-841`，`:840` 为 post，`:841` 做拓扑排序 `ReloadListenerSort.sort(event)`），由 `.mc-src/26.1.2/net/minecraft/server/ReloadableServerResources.java:139` 调用 |

**`@EventBusSubscriber` 用法差异**：

| 版本 | 注解全路径 | 可用属性 |
|---|---|---|
| 1.20.1 | `net.minecraftforge.fml.common.Mod.EventBusSubscriber`（`Mod` 的嵌套类型） | `value`(Dist)、`modid`(String)、**`bus`(Bus.MOD / Bus.FORGE)** — 用法见 `.mc-src/1.20.1/net/minecraftforge/common/ForgeSpawnEggItem.java:76,84`、`.mc-src/1.20.1/net/minecraftforge/client/model/data/ModelDataManager.java:16-23` |
| 1.21.1 | **独立类** `net.neoforged.fml.common.EventBusSubscriber`（**不存在** `Mod.EventBusSubscriber`） | `modid`(String)、`value`(Dist/Dist[]) —— **无 `bus` 参数**（`.mc-src/1.21.1/net/neoforged/neoforge/network/NetworkInitialization.java:40`、`.mc-src/1.21.1/net/neoforged/neoforge/client/BlockEntityRenderBoundsDebugRenderer.java:28`） |
| 26.1.2 | `net.neoforged.fml.common.EventBusSubscriber`（不变） | 同 1.21.1（`.mc-src/26.1.2/net/neoforged/neoforge/network/NetworkInitialization.java:37`、`.mc-src/26.1.2/net/neoforged/neoforge/client/NeoForgeRenderPipelines.java:19`） |

> NeoForge 的 `@EventBusSubscriber` **只用于 game bus**（无 `bus` 参数）。mod bus 事件必须显式 `modEventBus.addListener(...)`。
> ⚠️ **未找到**：`net.neoforged.fml` 包的源码不在这三棵反编译树里（`net/neoforged/fml` 目录不存在），因此上述注解的**声明本体与属性列表**只能从 import/用法反推。检索关键字：`EventBusSubscriber`、`@interface EventBusSubscriber`、glob `**/EventBusSubscriber.java`、`**/Mod.java`、`**/IEventBus.java`。

**`TagsUpdatedEvent` 在 26.1.2 变成 sealed 且有子类**：

- 1.20.1：`net.minecraftforge.event.TagsUpdatedEvent`，`getRegistryAccess()`（`.mc-src/1.20.1/net/minecraftforge/event/TagsUpdatedEvent.java:17`），post 于 `.mc-src/1.20.1/net/minecraft/server/ReloadableServerResources.java:103`
- 1.21.1：`net.neoforged.neoforge.event.TagsUpdatedEvent`，`getRegistryAccess()`（`.mc-src/1.21.1/.../TagsUpdatedEvent.java:28`），post 于 `.mc-src/1.21.1/net/minecraft/server/ReloadableServerResources.java:143`
- 26.1.2：`net.neoforged.neoforge.event.TagsUpdatedEvent`，**`sealed`**，子类 `ServerDataLoad`（`.mc-src/26.1.2/.../TagsUpdatedEvent.java:54`，带 `getServerResources()` `:64`）与 `ClientPacketReceived`（`:70`）；**`getRegistries()` `:27` 取代 `getLookupProvider()`（后者 `:34-37` 已 deprecated）**；post 于 `.mc-src/26.1.2/net/minecraft/server/ReloadableServerResources.java:174`

---

## 7. 自定义注册表条目的其他注意事项

### 7.1 是否需要在 `RegistryDataLoader` 里手工注册 `RegistryData`？

**不需要。** 事件已经处理了：

- 1.20.1：`DataPackRegistryEvent.NewRegistry.process()` → `DataPackRegistriesHooks.addRegistryCodec(data)` → `DATA_PACK_REGISTRIES.add(loaderData)`（`.mc-src/1.20.1/net/minecraftforge/registries/DataPackRegistryEvent.java:30-34` → `.mc-src/1.20.1/net/minecraftforge/registries/DataPackRegistriesHooks.java:43-50`）
- 1.21.1 / 26.1.2：同上（`.mc-src/1.21.1/.../DataPackRegistriesHooks.java:42-49`、`.mc-src/26.1.2/.../DataPackRegistriesHooks.java:43-50`）

`DATA_PACK_REGISTRIES` 初始化为 `new ArrayList<>(RegistryDataLoader.WORLDGEN_REGISTRIES)`（1.20.1 `:22`、1.21.1 `:25`、26.1.2 `:26`），即原版列表 + 你的自定义注册表，最后整体交给 `RegistryDataLoader.load`。

**唯一必须做的**：在 mod 事件总线上监听 `DataPackRegistryEvent.NewRegistry` 并调用 `event.dataPackRegistry(...)`。

### 7.2 注册表为空（数据包没提供 JSON）怎么处理？

**不会抛异常，注册表会存在但是空的。**

- 自定义注册表默认**允许为空**：
  - 1.20.1：`RegistryData` 只有 2 个组件，无 `requiredNonEmpty`（`.mc-src/1.20.1/.../RegistryDataLoader.java:186`）→ 无校验
  - 1.21.1：事件硬编码 `requiredNonEmpty = false`（`.mc-src/1.21.1/.../DataPackRegistryEvent.java:75,98`）
  - 26.1.2：事件硬编码 `RegistryValidator.none()`（`.mc-src/26.1.2/.../DataPackRegistryEvent.java:75,98`）
- 因此 `registryOrThrow(KEY)` / `lookupOrThrow(KEY)` **不会抛异常**（注册表本身已注册，只是 `size() == 0`）。
- 需要自己判空：`registry.size() == 0` 或 `registry.listElements().findAny().isEmpty()`。
- 原版对**必须非空**的注册表用 `RegistryValidator.nonEmpty()`，例如 26.1.2 的 `wolf_variant`（`.mc-src/26.1.2/.../RegistryDataLoader.java:102`）：
  ```java
  new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, RegistryValidator.nonEmpty()),
  ```
  `nonEmpty()` 的失败行为（`.mc-src/26.1.2/net/minecraft/resources/RegistryValidator.java:9-13`）：
  ```java
  RegistryValidator<?> NON_EMPTY = (registry, loadingErrors) -> {
      if (registry.size() == 0) {
          loadingErrors.put(registry.key(), new IllegalStateException("Registry must be non-empty: " + registry.key().identifier()));
      }
  };
  ```
  校验结果最终会让 `RegistryDataLoader.load` 抛异常（`.mc-src/26.1.2/.../RegistryDataLoader.java:234-237`）。
- 1.21.1 的等价机制是 `requiredNonEmpty = true`，例如 `.mc-src/1.21.1/.../RegistryDataLoader.java:91-92`（`WOLF_VARIANT`、`PAINTING_VARIANT`）。

> ⚠️ **26.1.2 的 mod 无法让自定义注册表「必须非空」**（事件 API 硬编码 `RegistryValidator.none()`，见 1.4）。若 OreFood 需要「数据包必须提供至少一个定义」，只能在代码里手动检查并记日志 / 抛异常。

### 7.3 是否需要处理 datapack 重载（`/reload`）？

**需要知道这个事实：`/reload` 不会重读动态注册表。**

`MinecraftServer.reloadResources(Collection<String>)` 的实现只重建 `ReloadableServerResources`，**没有调用 `RegistryDataLoader.load`**：

- **1.20.1**（`.mc-src/1.20.1/net/minecraft/server/MinecraftServer.java:1369-1418`）：
  ```java
  public CompletableFuture<Void> reloadResources(Collection<String> p_129862_) {              // :1369
      RegistryAccess.Frozen registryaccess$frozen = this.registries.getAccessForLoading(RegistryLayer.RELOADABLE);  // :1370
      ...
      return ReloadableServerResources.loadResources(closeableresourcemanager, registryaccess$frozen, ...)          // :1378
      ...
      this.resources.managers.updateRegistryTags(this.registryAccess());                       // :1404
  ```
- **26.1.2**（`.mc-src/26.1.2/net/minecraft/server/MinecraftServer.java:1550-1601`）：
  ```java
  public CompletableFuture<Void> reloadResources(Collection<String> packsToEnable) {           // :1550
      ...
      List<Registry.PendingTags<?>> postponedTags = TagLoader.loadTagsForExistingRegistries(resources, this.registries.compositeAccess());  // :1562
      return ReloadableServerResources.loadResources(resources, this.registries, postponedTags, ...)   // :1563
      ...
      this.resources.managers.updateComponentsAndStaticRegistryTags();                          // :1587
  ```
  —— 只有 **tags** 和 **components** 被更新，注册表内容没被重新加载。

**实践含义**：

1. 动态注册表的 JSON **只在服务器启动 / 进入世界时读取**。修改数据包后必须**重启服务器或重新进入世界**（对单人游戏是退出重进世界）。
2. 因此，**在注册表加载完成后把内容缓存到静态字段是安全的**（不会因 `/reload` 而失效）。
3. 但如果你**确实**想在 `/reload` 时刷新缓存，可以注册一个 reload listener（1.20.1/1.21.1 用 `AddReloadListenerEvent`，26.1.2 用 `AddServerReloadListenersEvent`）在回调里重新读 `getRegistryAccess()` —— 此时读到的是**同一份**（未变的）注册表内容，所以只能刷新你自己的派生缓存，不能刷新注册表本身。
4. 客户端侧：若提供了 network codec，注册表内容在**连接时**由服务端同步；`/reload` 后服务端会重新 `packRegistries` 吗？—— `RegistrySynchronization.packRegistries` 由连接握手流程调用（`.mc-src/26.1.2/net/minecraft/core/RegistrySynchronization.java:27`），不在 `reloadResources` 路径上。**未找到** `/reload` 后重新同步动态注册表的代码路径（检索关键字：`packRegistries`、`reloadResources`、`RegistrySynchronization`）。

**`/reload` 到底重载了什么（精确证据）**：

`ReloadableServerResources.loadResources(...)` 内部**只**重载 `RegistryLayer.RELOADABLE` 层，而该层只包含**掉落物相关**的 3 个注册表：

- 1.21.1：`.mc-src/1.21.1/net/minecraft/server/ReloadableServerResources.java:107` → `ReloadableServerRegistries.reload(...)`，而 `.mc-src/1.21.1/net/minecraft/server/ReloadableServerRegistries.java:51-53` 只遍历 `LootDataType.values()`，即 **`minecraft:predicate` / `minecraft:item_modifier` / `minecraft:loot_table`**（`.mc-src/1.21.1/net/minecraft/world/level/storage/loot/LootDataType.java:21-27`），写入 `RegistryLayer.RELOADABLE`（`:100`）。
- 26.1.2：`.mc-src/26.1.2/net/minecraft/server/ReloadableServerResources.java:121` → `ReloadableServerRegistries.reload(...)`；`.mc-src/26.1.2/net/minecraft/server/ReloadableServerRegistries.java:48-50` 同样只走 `LootDataType.values()` = `PREDICATE` / `MODIFIER` / `TABLE`（`.mc-src/26.1.2/net/minecraft/world/level/storage/loot/LootDataType.java:47-49`）。
- 1.20.1：更彻底——`reloadResources` 里**连 `ReloadableServerRegistries` 都没有**，`/reload` 只重建 `TagManager` / `LootDataManager` / `RecipeManager` / `FunctionLibrary` / `ServerAdvancementManager`（`.mc-src/1.20.1/net/minecraft/server/ReloadableServerResources.java:76-78` 的 `listeners()`）。

**`RegistryDataLoader.load` 的服务端调用者只有 `WorldLoader` 一个**（全树检索确认）：

| 版本 | 服务端调用点 |
|---|---|
| 1.20.1 | `.mc-src/1.20.1/net/minecraft/server/WorldLoader.java:42`、`:80` |
| 1.21.1 | `.mc-src/1.21.1/net/minecraft/server/WorldLoader.java:39`、`:76` |
| 26.1.2 | `.mc-src/26.1.2/net/minecraft/server/WorldLoader.java:42`、`:49`（客户端另有 `.mc-src/26.1.2/net/minecraft/client/multiplayer/RegistryDataCollector.java:97`） |

> **结论**：世界生成 / 动态注册表（biome、structure、enchantment、damage_type、wolf_variant、以及**你的 `orefood:ore_food`**）在服务器启动时加载并 freeze，`/reload` 期间它们的对象**不变**。所以「启动后拿到的 `RegistryAccess` 一直有效」成立；「`/reload` 会刷新它们」**不成立**——只有 loot 相关注册表会被重建。

### 7.4 其他注意事项清单

1. **`RegistryAccess` 是 frozen 的**：`MinecraftServer.registryAccess()` 返回 `RegistryAccess.Frozen`（1.20.1 `:1797`、26.1.2 `:2078`），不能在运行期往里注册条目。
2. **注册表 key 的命名空间决定目录**：key 用 `orefood:ore_food` 就固定了目录为 `data/<pack_ns>/orefood/ore_food/`。**不要**用 `minecraft:ore_food`（那样目录会变成 `data/<pack_ns>/ore_food/`，且污染 minecraft 命名空间）。
3. **条件加载**：1.20.1 在 `loadRegistryContents` 里检查 `ICondition.shouldRegisterEntry(jsonelement)`（`.mc-src/1.20.1/.../RegistryDataLoader.java:168`）；1.21.1/26.1.2 用 `ConditionalOps`（`.mc-src/1.21.1/.../RegistryDataLoader.java:229`、`.mc-src/26.1.2/net/minecraft/resources/ResourceManagerRegistryLoadTask.java:43`）：
   ```java
   RegistryOps<JsonElement> ops = new net.neoforged.neoforge.common.conditions.ConditionalOps<>(
       RegistryOps.create(JsonOps.INSTANCE, context),
       net.neoforged.neoforge.common.conditions.ICondition.IContext.TAGS_INVALID);
   ```
   → 条目 JSON 里可以写 `neoforge:conditions` / `forge:conditions` 做条件加载。
4. **错误处理**：解析失败的条目会被记录到 `loadingErrors` 并**整体抛异常**（26.1.2 `.mc-src/26.1.2/.../RegistryDataLoader.java:230-237`），即**一个 JSON 写错会导致加载失败**，不会静默跳过。
5. **`datapack` 的 `pack.mcmeta` pack_format** 必须匹配对应版本，否则数据包不会被加载。
6. **26.1.2 的 `RegistryValidator` 校验发生在 freeze 之后**（`.mc-src/26.1.2/.../RegistryDataLoader.java:229` freeze → `:234` validate），所以 validator 看到的是只读注册表。

---

## 8. 三版本「最小可运行」注册代码骨架

以下骨架假设：注册表 key = `orefood:ore_food`，元素类型 `OreFoodDefinition`。

### 8.1 公共部分：元素类型与 codec（三版本几乎相同）

```java
package com.example.orefood;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record OreFoodDefinition(
        int nutrition,
        float saturation,
        int durability,
        int enchantmentValue,
        // 药水组件：1.20.1 用 Potion / MobEffect 的 Holder 或 id，26.1.2 用 Holder<Potion>
        Optional<ResourceKey<Potion>> potion   // 类型名按版本调整
) {
    // 三版本都用 RecordCodecBuilder.create(...) —— 见 DamageType 范例
    public static final Codec<OreFoodDefinition> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("nutrition", 4).forGetter(OreFoodDefinition::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.3F).forGetter(OreFoodDefinition::saturation),
            Codec.INT.optionalFieldOf("durability", 0).forGetter(OreFoodDefinition::durability),
            Codec.INT.optionalFieldOf("enchantment_value", 0).forGetter(OreFoodDefinition::enchantmentValue),
            ResourceKey.codec(Registries.POTION).optionalFieldOf("potion").forGetter(OreFoodDefinition::potion)
    ).apply(instance, OreFoodDefinition::new));

    // 若要客户端同步，提供一个 network codec（普通 Codec，不是 StreamCodec）
    public static final Codec<OreFoodDefinition> NETWORK_CODEC = DIRECT_CODEC;  // 字段全同步时直接复用
}
```

> 注意：`id` **不需要**作为 JSON 字段 —— id 由文件名推导（`.mc-src/26.1.2/net/minecraft/resources/ResourceManagerRegistryLoadTask.java:47`：`ResourceKey.create(this.registryKey(), lister.fileToId(resourceId))`）。若确实想在 JSON 里也写 `id`，加一个 `Codec.STRING.optionalFieldOf("id")` 即可，但要自己保证与文件名一致。

### 8.2 1.20.1 / Forge

**（a）注册表 key 常量**

```java
package com.example.orefood;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class OreFoodRegistries {
    public static final ResourceKey<Registry<OreFoodDefinition>> ORE_FOOD =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("orefood", "ore_food"));
            // ResourceLocation.fromNamespaceAndPath 见 .mc-src/1.20.1/net/minecraft/resources/ResourceLocation.java:255
    private OreFoodRegistries() {}
}
```

**（b）事件监听（mod 事件总线）**

```java
package com.example.orefood;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;

@Mod(OreFood.MODID)
public class OreFood {
    public static final String MODID = "orefood";

    public OreFood(FMLJavaModLoadingContext context) {          // 1.20.1 也可用无参构造 + FMLJavaModLoadingContext.get()
        IEventBus modEventBus = context.getModEventBus();

        // 参照 ForgeMod.java:409-412 的官方写法
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC);
            // 若要客户端同步（客户端必须装本 mod）：
            // event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC, OreFoodDefinition.NETWORK_CODEC);
        });
    }
}
```

> 也可以不依赖构造器注入，改用 `FMLJavaModLoadingContext.get().getModEventBus()`。
> **不要**用 `@Mod.EventBusSubscriber` 标注这个监听器——`DataPackRegistryEvent` 是 `IModBusEvent`，需要 mod 总线；`@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)` 可以，但事件在 GATHER 阶段触发，静态注册要确保类被加载。

**（c）数据文件**

```
src/main/resources/data/orefood/orefood/ore_food/iron_steak.json
```

```json
{
  "nutrition": 8,
  "saturation": 0.8,
  "durability": 0,
  "enchantment_value": 0
}
```

**（d）读取**

```java
// 在 ServerStartedEvent 或运行时
Registry<OreFoodDefinition> reg = server.registryAccess().registryOrThrow(OreFoodRegistries.ORE_FOOD);

OreFoodDefinition iron = reg.getOptional(ResourceLocation.fromNamespaceAndPath("orefood", "iron_steak")).orElse(null);
if (iron == null) { /* 数据包未提供，走兜底 */ }

// 遍历全部
reg.holders().forEach(holder -> {
    ResourceKey<OreFoodDefinition> key = holder.key();
    OreFoodDefinition value = holder.value();
});
```

### 8.3 1.21.1 / NeoForge

**（a）注册表 key 常量**

```java
package com.example.orefood;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class OreFoodRegistries {
    public static final ResourceKey<Registry<OreFoodDefinition>> ORE_FOOD =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("orefood", "ore_food"));
            // 见 .mc-src/1.21.1/net/minecraft/resources/ResourceKey.java:51
    private OreFoodRegistries() {}
}
```

**（b）事件监听**

```java
package com.example.orefood;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@Mod(OreFood.MODID)
public class OreFood {
    public static final String MODID = "orefood";

    public OreFood(IEventBus modEventBus) {                       // NeoForge 支持 IEventBus 构造器注入
        // 参照 NeoForgeMod.java:549-552 的官方写法
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC);
            // 同步版：
            // event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC, OreFoodDefinition.NETWORK_CODEC);
            // 需要 RegistryBuilder 配置（例如默认值/同步策略）时用 4 参重载：
            // event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC,
            //         OreFoodDefinition.NETWORK_CODEC, builder -> { /* ... */ });
        });
    }
}
```

**（c）数据文件** —— 路径与 1.20.1 相同：

```
src/main/resources/data/orefood/orefood/ore_food/iron_steak.json
```

**（d）读取**

```java
Registry<OreFoodDefinition> reg = server.registryAccess().registryOrThrow(OreFoodRegistries.ORE_FOOD);

OreFoodDefinition iron = reg.get(ResourceLocation.fromNamespaceAndPath("orefood", "iron_steak"));   // 可空
Optional<Holder.Reference<OreFoodDefinition>> holder =
        reg.getHolder(ResourceLocation.fromNamespaceAndPath("orefood", "iron_steak"));

reg.holders().forEach(h -> { ... });          // 或 reg.listElements()
```

### 8.4 26.1.2 / NeoForge

**（a）注册表 key 常量（注意 `Identifier`）**

```java
package com.example.orefood;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

public final class OreFoodRegistries {
    public static final ResourceKey<Registry<OreFoodDefinition>> ORE_FOOD =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("orefood", "ore_food"));
            // 见 .mc-src/26.1.2/net/minecraft/resources/ResourceKey.java:29
            // Identifier.fromNamespaceAndPath 用法参照 NeoForgeRegistries.java:65
    private OreFoodRegistries() {}
}
```

**（b）事件监听（与 1.21.1 逐字相同）**

```java
package com.example.orefood;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

@Mod(OreFood.MODID)
public class OreFood {
    public static final String MODID = "orefood";

    public OreFood(IEventBus modEventBus) {
        // 参照 NeoForgeMod.java:549-552
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
            event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC);
            // 或带同步：
            // event.dataPackRegistry(OreFoodRegistries.ORE_FOOD, OreFoodDefinition.DIRECT_CODEC, OreFoodDefinition.NETWORK_CODEC);
        });
    }
}
```

**（c）数据文件** —— 路径规则未变：

```
src/main/resources/data/orefood/orefood/ore_food/iron_steak.json
```

**（d）读取（注意 `lookupOrThrow` / `getValue` / `listElements`）**

```java
Registry<OreFoodDefinition> reg = server.registryAccess().lookupOrThrow(OreFoodRegistries.ORE_FOOD);

OreFoodDefinition iron = reg.getValue(Identifier.fromNamespaceAndPath("orefood", "iron_steak"));    // 可空
Optional<OreFoodDefinition> opt = reg.getOptional(Identifier.fromNamespaceAndPath("orefood", "iron_steak"));
OreFoodDefinition must = reg.getValueOrThrow(
        ResourceKey.create(OreFoodRegistries.ORE_FOOD, Identifier.fromNamespaceAndPath("orefood", "iron_steak")));

// 遍历：holders() 已删，只能 listElements()
reg.listElements().forEach(h -> {
    ResourceKey<OreFoodDefinition> key = h.key();
    OreFoodDefinition value = h.value();
});

// 从 Level 读（运行时推荐）
Registry<OreFoodDefinition> fromLevel = level.registryAccess().lookupOrThrow(OreFoodRegistries.ORE_FOOD);
```

### 8.5 数据重载监听骨架（可选）

**1.20.1**：

```java
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.packs.resources.PreparableReloadListener;

// Forge 总线（不是 mod 总线！）
MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
    RegistryAccess access = event.getRegistryAccess();          // AddReloadListenerEvent.java:43
    event.addListener(myReloadListener);                        // :27  参数是 PreparableReloadListener
});
```

**1.21.1**：

```java
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {     // javadoc: AddReloadListenerEvent.java:29
    RegistryAccess access = event.getRegistryAccess();                 // :74
    event.addListener(myReloadListener);                               // :44
});
```

**26.1.2**（API 变了）：

```java
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

NeoForge.EVENT_BUS.addListener((AddServerReloadListenersEvent event) -> {
    RegistryAccess access = event.getRegistryAccess();   // :84 —— 已 @Deprecated(forRemoval = true, since = "26.1.2")
    // 推荐改用 ContextAwareReloadListener#getRegistryLookup()

    event.addListener(Identifier.fromNamespaceAndPath("orefood", "ore_food_cache"), myReloadListener);  // :62 需要 key
    // 或需要后续从 ReloadableServerResources 取回该 listener：
    // event.addRetainedListener(myListenerKey, myReloadListener);                                      // :52
    // 需要排序依赖：
    // event.addDependency(Identifier.fromNamespaceAndPath("orefood", "ore_food_cache"), someOtherKey);  // :85
});
```

> ⚠️ **26.1.2 的 listener 实现必须用新的 4 参数 `reload` 签名**（见 §6.5）：
> ```java
> // 26.1.2 —— 4 参数
> public CompletableFuture<Void> reload(PreparableReloadListener.SharedState currentReload,
>                                       Executor taskExecutor,
>                                       PreparableReloadListener.PreparationBarrier preparationBarrier,
>                                       Executor reloadExecutor)
> ```
> 最省事的做法是 **`extends ContextAwareReloadListener`**（或 `ResourceManagerReloadListener`），这样只需实现 `onResourceManagerReload(ResourceManager)`（`.mc-src/26.1.2/.../ResourceManagerReloadListener.java:26`），并在内部用 `getRegistryLookup()` 拿注册表。
>
> 客户端侧：26.1.2 的事件改名为 `AddClientReloadListenersEvent`（**mod bus**），不再叫 `RegisterClientReloadListenersEvent`（见 §6.5）。

---

## 9. 参考实现（原版 / 加载器自带的动态注册表范例）

### 9.1 加载器自带的 datapack registry 注册代码（最贴近我们需求的范例）

**Forge 1.20.1**（`.mc-src/1.20.1/net/minecraftforge/common/ForgeMod.java:408-412`）—— 在 mod 构造器里，用 **mod 事件总线** 的 lambda 监听：

```java
IEventBus modEventBus = context.getModEventBus();                                  // :408
modEventBus.addListener(event -> {                                                 // :409
    event.dataPackRegistry(ForgeRegistries.Keys.BIOME_MODIFIERS, BiomeModifier.DIRECT_CODEC);       // :410
    event.dataPackRegistry(ForgeRegistries.Keys.STRUCTURE_MODIFIERS, StructureModifier.DIRECT_CODEC);  // :411
});
```

> 注意 `:409` 用的是**隐式 lambda 参数类型**（`event` 被推断为 `DataPackRegistryEvent.NewRegistry`），因为 `modEventBus.addListener(Consumer<T>)` 需要泛型推断。
>
> ⚠️ **反编译产物瑕疵**：`ForgeMod.java` 里**根本没有 `DataPackRegistryEvent` 的 import**（对 `DataPackRegistryEvent` 检索 `.mc-src/1.20.1` 全树，命中文件仅 `RegistryManager.java`、`DataPackRegistryEvent.java`、`DataPackRegistriesHooks.java`，**`ForgeMod.java` 不在其中**）。原始 Forge 源码应是 `modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> { ... });`，**但这一点在本树内无法直接确证**——本报告只能确证「编译产物里 lambda 形参无显式类型，且该类型未被 import」。**在你的 mod 代码里请务必写显式类型**。
>
> 另外 `ForgeMod` 类本身**没有** `@EventBusSubscriber` 注解，也**没有** `@SubscribeEvent` 方法（检索 `EventBusSubscriber`、`@SubscribeEvent` 在 `ForgeMod.java` 中 0 命中）；注册完全靠构造器里的 `modEventBus.addListener(...)`（`:409`、`:413-419`）、`modEventBus.register(this)`（`:420`）与 `MinecraftForge.EVENT_BUS.addListener(...)`（`:427-428`）。类声明在 `.mc-src/1.20.1/net/minecraftforge/common/ForgeMod.java:132-133`（`@Mod("forge")` / `public class ForgeMod {`），构造器在 `:394`。

**NeoForge 1.21.1**（`.mc-src/1.21.1/net/neoforged/neoforge/common/NeoForgeMod.java:547-548`）：

```java
event.dataPackRegistry(NeoForgeRegistries.Keys.BIOME_MODIFIERS, BiomeModifier.DIRECT_CODEC);       // :547
event.dataPackRegistry(NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS, StructureModifier.DIRECT_CODEC);  // :548
```

**NeoForge 26.1.2**（`.mc-src/26.1.2/net/neoforged/neoforge/common/NeoForgeMod.java:548-552`）—— **推荐直接照抄这段**：

```java
// Forge-provided datapack registries
modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> {                              // :549
    event.dataPackRegistry(NeoForgeRegistries.Keys.BIOME_MODIFIERS, BiomeModifier.DIRECT_CODEC);      // :550
    event.dataPackRegistry(NeoForgeRegistries.Keys.STRUCTURE_MODIFIERS, StructureModifier.DIRECT_CODEC);  // :551
});
```

其中 `modEventBus` 来自构造器参数（`.mc-src/26.1.2/.../NeoForgeMod.java:528`）：

```java
public NeoForgeMod(IEventBus modEventBus, Dist dist, ModContainer container) {
```

**它注册在哪个事件里**：`DataPackRegistryEvent.NewRegistry`，通过 `modEventBus.addListener(...)`（mod 事件总线），在 `RegistryManager.postNewRegistryEvent()`（`.mc-src/26.1.2/.../RegistryManager.java:80-87`）里被 post，触发时机是 mod 加载的 GATHER 阶段（由 `.mc-src/26.1.2/net/neoforged/neoforge/internal/CommonModLoader.java:52` 驱动）。

### 9.2 加载器自带 datapack registry 的 key 定义

```java
// NeoForge 26.1.2  .mc-src/26.1.2/net/neoforged/neoforge/registries/NeoForgeRegistries.java:61-66
public static final ResourceKey<Registry<BiomeModifier>> BIOME_MODIFIERS = key("biome_modifier");
public static final ResourceKey<Registry<StructureModifier>> STRUCTURE_MODIFIERS = key("structure_modifier");
private static <T> ResourceKey<Registry<T>> key(String name) {
    return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(NeoForgeMod.MOD_ID, name));
}

// Forge 1.20.1  .mc-src/1.20.1/net/minecraftforge/registries/ForgeRegistries.java:171-176
public static final ResourceKey<Registry<BiomeModifier>> BIOME_MODIFIERS = key("forge:biome_modifier");
public static final ResourceKey<Registry<StructureModifier>> STRUCTURE_MODIFIERS = key("forge:structure_modifier");
private static <T> ResourceKey<Registry<T>> key(String name) {
    return ResourceKey.createRegistryKey(new ResourceLocation(name));
}
```

**注意 Forge 用 `forge:biome_modifier`** → 目录 `data/<pack_ns>/forge/biome_modifier/`。NeoForge 用 `neoforge:biome_modifier` → `data/<pack_ns>/neoforge/biome_modifier/`。

### 9.3 原版 datapack registry 的完整清单（`WORLDGEN_REGISTRIES` / `SYNCHRONIZED_REGISTRIES`）

**1.20.1**（`.mc-src/1.20.1/net/minecraft/resources/RegistryDataLoader.java:55-77`）—— `WORLDGEN_REGISTRIES` **共 19 条**（`:56`-`:74`）：

```java
public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(     // :55
    new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),        // :56
    new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.DIRECT_CODEC),                          // :57
    new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.CODEC),                          // :58
    new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC),  // :59
    new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC),  // :60
    new RegistryDataLoader.RegistryData<>(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC),          // :61
    new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE, Structure.DIRECT_CODEC),                  // :62
    new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC),            // :63
    new RegistryDataLoader.RegistryData<>(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC), // :64
    new RegistryDataLoader.RegistryData<>(Registries.TEMPLATE_POOL, StructureOreFoodPool.DIRECT_CODEC),   // :65
    new RegistryDataLoader.RegistryData<>(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC), // :66
    new RegistryDataLoader.RegistryData<>(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC),     // :67
    new RegistryDataLoader.RegistryData<>(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC),      // :68
    new RegistryDataLoader.RegistryData<>(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC),              // :69
    new RegistryDataLoader.RegistryData<>(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC),  // :70
    new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),              // :71
    new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),            // :72
    new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.CODEC),                       // :73  ← 注意是 CODEC
    new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC)  // :74
);
public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(   // :76
    new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC)                 // :77
);
```

> ⚠️ **1.20.1 完全没有 `SYNCHRONIZED_REGISTRIES` 静态列表**（`RegistryDataLoader.java` 全文 195 行内检索 `SYNCHRONIZED_REGISTRIES` 无命中）。1.20.1 的同步表在**另一个文件**里，是一个 `Map` 而非 `List`（`.mc-src/1.20.1/net/minecraft/core/RegistrySynchronization.java:26-34`），共 **6 条**：
> ```java
> private static final Map<ResourceKey<? extends Registry<?>>, RegistrySynchronization.NetworkedRegistryData<?>> NETWORKABLE_REGISTRIES = Util.make(() -> {  // :26
>     Builder<...> builder = ImmutableMap.builder();
>     ... BIOME: Biome.NETWORK_CODEC            // :28
>     ... CHAT_TYPE: ChatType.CODEC             // :29
>     ... TRIM_PATTERN: TrimPattern.DIRECT_CODEC    // :30
>     ... TRIM_MATERIAL: TrimMaterial.DIRECT_CODEC  // :31
>     ... DIMENSION_TYPE: DimensionType.DIRECT_CODEC // :32
>     ... DAMAGE_TYPE: DamageType.CODEC         // :33
>     return DataPackRegistriesHooks.grabNetworkableRegistries(builder);   // :34  ← Forge 注入点
> });
> ```
> 即 1.20.1 的动态注册表同步是「`Map` + Forge hook」，而 1.21.1/26.1.2 是「`List<RegistryData>` + `grabNetworkableRegistries(List)`」。这是 1.20.1 → 1.21 的一个结构性重构。

**1.21.1**（`.mc-src/1.21.1/net/minecraft/resources/RegistryDataLoader.java:73-115`）—— `WORLDGEN_REGISTRIES` **25 条**（`:74`-`:98`），`DIMENSION_REGISTRIES` 1 条（`:101`），`SYNCHRONIZED_REGISTRIES` **11 条**（`:104`-`:114`，经 `DataPackRegistriesHooks.grabNetworkableRegistries(...)` 包装，`:103`）：

```java
public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(     // :73
    ... DimensionType, Biome, ChatType, ConfiguredWorldCarver, ConfiguredFeature, PlacedFeature,
        Structure, StructureSet, ProcessorList, OreFoodPool, NoiseSettings, Noise, DensityFunction,
        WorldPreset, FlatLevelGeneratorPreset, TrimPattern, TrimMaterial,
    new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),       // :91  ← requiredNonEmpty = true
    new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),  // :92
    new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),              // :93
    new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC),  // :94
    new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),        // :95
    new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),             // :96
    new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT_PROVIDER, EnchantmentProvider.DIRECT_CODEC),  // :97
    new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC)             // :98
);
public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(   // :100
    new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC)                 // :101
);
public static final List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES =
    net.neoforged.neoforge.registries.DataPackRegistriesHooks.grabNetworkableRegistries(List.of(  // :103  ← Neo 注入点
        new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.NETWORK_CODEC),              // :104  ← 用 NETWORK_CODEC
        new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),        // :105
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),  // :106
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),// :107
        new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),   // :108
        new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),  // :109
        new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),     // :110
        new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),           // :111
        new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),     // :112
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),          // :113
        new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC)          // :114
    ));
```

**26.1.2**（`.mc-src/26.1.2/net/minecraft/resources/RegistryDataLoader.java:83-162`）—— `WORLDGEN_REGISTRIES` **45 条**（`:84`-`:128`），`DIMENSION_REGISTRIES` 1 条（`:131`），`SYNCHRONIZED_REGISTRIES` **28 条**（`:134`-`:161`）：

```java
public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(     // :83
    ... (DimensionType, Biome, ChatType, ConfiguredWorldCarver, ConfiguredFeature, PlacedFeature,
         Structure, StructureSet, ProcessorList, OreFoodPool, NoiseSettings, Noise, DensityFunction,
         WorldPreset, FlatLevelGeneratorPreset, TrimPattern, TrimMaterial, TrialSpawnerConfig)
    new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, RegistryValidator.nonEmpty()),          // :102
    new RegistryDataLoader.RegistryData<>(Registries.WOLF_SOUND_VARIANT, WolfSoundVariant.DIRECT_CODEC, RegistryValidator.nonEmpty()),  // :103
    ... (PigVariant, PigSoundVariant, FrogVariant, CatVariant, CatSoundVariant, CowVariant,
         CowSoundVariant, ChickenVariant, ChickenSoundVariant, ZombieNautilusVariant, PaintingVariant 都是 nonEmpty())
    new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),                       // :115
    ...
    new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),                      // :118
    new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),                     // :120
    new RegistryDataLoader.RegistryData<>(Registries.INSTRUMENT, Instrument.DIRECT_CODEC),                        // :121
    ...
    new RegistryDataLoader.RegistryData<>(Registries.TIMELINE, Timeline.DIRECT_CODEC, Timeline::validateRegistry), // :126  ← 自定义 validator
    new RegistryDataLoader.RegistryData<>(Registries.VILLAGER_TRADE, VillagerTrade.CODEC),                        // :127
    new RegistryDataLoader.RegistryData<>(Registries.TRADE_SET, TradeSet.CODEC)                                   // :128
);
public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(   // :130
    new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC)                 // :131
);
public static final List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES =
    net.neoforged.neoforge.registries.DataPackRegistriesHooks.grabNetworkableRegistries(List.of(  // :133
        new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.NETWORK_CODEC),              // :134
        ...
        new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.NETWORK_CODEC, RegistryValidator.nonEmpty()),  // :138  ← 用 NETWORK_CODEC 且 nonEmpty
        ...
        new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.NETWORK_CODEC),   // :151  ← 加载用 DIRECT_CODEC，同步用 NETWORK_CODEC
        new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),          // :152
        ...
        new RegistryDataLoader.RegistryData<>(Registries.TIMELINE, Timeline.NETWORK_CODEC)               // :161
    ));  // :162  ← 注释：// Neo: Keep the list so custom registries can be added later
```

> 一个绝佳的「同一注册表加载/同步 codec 不同」范例：`DIMENSION_TYPE` 在 `WORLDGEN_REGISTRIES` 里用 `DimensionType.DIRECT_CODEC`（`:84`），在 `SYNCHRONIZED_REGISTRIES` 里用 `DimensionType.NETWORK_CODEC`（`:151`）。

### 9.4 单个原版 datapack registry 的完整定义链路范例

以 **`jukebox_song`** 为例（结构简单、字段类型多样，最适合当模板）：

**（1）注册表 key**（26.1.2 `.mc-src/26.1.2/net/minecraft/core/registries/Registries.java:278`）：

```java
public static final ResourceKey<Registry<JukeboxSong>> JUKEBOX_SONG = createRegistryKey("jukebox_song");
```

（1.21.1 同项在 `.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:160`；1.20.1 **未找到** `JUKEBOX_SONG`——该注册表是 1.21 才加入的，检索关键字 `JUKEBOX_SONG`、`jukebox_song` 在 `.mc-src/1.20.1/net/minecraft/core/registries/Registries.java` 无命中。）

**（2）元素 codec**（26.1.2 `.mc-src/26.1.2/net/minecraft/world/item/JukeboxSong.java:19-42`）：

```java
public record JukeboxSong(Holder<SoundEvent> soundEvent, Component description, float lengthInSeconds, int comparatorOutput) {  // :19
    public static final Codec<JukeboxSong> DIRECT_CODEC = RecordCodecBuilder.create(       // :20
        instance -> instance.group(
            SoundEvent.CODEC.fieldOf("sound_event").forGetter(JukeboxSong::soundEvent),                 // :22
            ComponentSerialization.CODEC.fieldOf("description").forGetter(JukeboxSong::description),    // :23
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("length_in_seconds").forGetter(JukeboxSong::lengthInSeconds),  // :24
            ExtraCodecs.intRange(0, 15).fieldOf("comparator_output").forGetter(JukeboxSong::comparatorOutput)  // :25
        ).apply(instance, JukeboxSong::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, JukeboxSong> DIRECT_STREAM_CODEC = StreamCodec.composite(  // :29
        SoundEvent.STREAM_CODEC, ..., ComponentSerialization.STREAM_CODEC, ..., ByteBufCodecs.FLOAT, ..., ByteBufCodecs.VAR_INT, ...);  // :30-36

    public static final Codec<Holder<JukeboxSong>> CODEC = RegistryFixedCodec.create(Registries.JUKEBOX_SONG);   // :40
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<JukeboxSong>> STREAM_CODEC = ByteBufCodecs.holder(  // :41
        Registries.JUKEBOX_SONG, DIRECT_STREAM_CODEC);                                                          // :42
}
```

**（3）是否注册进 `RegistryDataLoader`**：

```java
// 26.1.2
new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),      // :120  WORLDGEN_REGISTRIES
new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),      // :155  SYNCHRONIZED_REGISTRIES
// 1.21.1
new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),      // :98   WORLDGEN_REGISTRIES
new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC),      // :114  SYNCHRONIZED_REGISTRIES
```

> `JUKEBOX_SONG` **没有** 独立的 `NETWORK_CODEC`，两个列表都复用 `DIRECT_CODEC`。这与我们 OreFood 的情况一致（字段全同步）——**直接复用同一个 `Codec<T>` 是官方认可的做法**。

**（4）真实数据文件**：

```
.mc-src/26.1.2/data/minecraft/jukebox_song/      （目录存在，见 3.3 证据 2 的枚举）
```

### 9.5 真实数据包文件证据汇总

| 文件 | 注册表 | 说明 |
|---|---|---|
| `.mc-src/1.21.1/data/neoforge/damage_type/poison.json` | `minecraft:damage_type` | 加载器为原版注册表追加条目的真实例子；路径 `data/neoforge/damage_type/`，**无 `minecraft/` 层** |
| `.mc-src/26.1.2/data/minecraft/damage_type/`（目录） | `minecraft:damage_type` | 26.1.2 平铺布局 |
| `.mc-src/26.1.2/data/minecraft/enchantment/`（目录） | `minecraft:enchantment` | 26.1.2 平铺布局 |
| `.mc-src/26.1.2/data/minecraft/jukebox_song/`（目录） | `minecraft:jukebox_song` | 26.1.2 平铺布局 |
| `.mc-src/26.1.2/data/minecraft/wolf_variant/`、`painting_variant/`（目录） | `minecraft:wolf_variant` 等 | 26.1.2 平铺布局 |
| `.mc-src/26.1.2/data/minecraft/worldgen/`（目录，含 `biome` 等子目录） | `minecraft:worldgen/biome` 等 | 路径含 `/` 的注册表 key 会形成子目录 |
| `.mc-src/1.20.1/data/forge/loot_modifiers/global_loot_modifiers.json` | 非动态注册表（reload listener 数据） | 对照用 |
| `.mc-src/1.21.1/data/neoforge/data_maps/item/furnace_fuels.json` | 非动态注册表（DataMap） | 对照用，**不要混淆 DataMap 与 datapack registry** |

> **`data/<ns>/worldgen/biome/` 这一例子很有价值**：它证明注册表 key 的 path 里可以带 `/`（如 `minecraft:worldgen/biome`），此时 `prefixNamespace` 返回 `worldgen/biome`，最终目录就是 `data/<pack_ns>/worldgen/biome/`。所以 OreFood 若用 `orefood:ore_food`，目录就是 `data/<pack_ns>/orefood/ore_food/`；若用 `orefood:food/ore_food`，目录会是 `data/<pack_ns>/orefood/food/ore_food/`。

### 9.6 【最有价值的模板】加载器自己怎么写动态注册表元素的 codec 三件套

`BiomeModifier` / `StructureModifier` 是 Forge / NeoForge 官方给 mod 演示动态注册表的**标准形态**：一个 `DIRECT_CODEC` + 两个派生 codec。

**Forge 1.20.1**（`.mc-src/1.20.1/net/minecraftforge/common/world/BiomeModifier.java:13-18`）：

```java
public interface BiomeModifier {
    Codec<BiomeModifier> DIRECT_CODEC = ExtraCodecs.lazyInitializedCodec(() -> ForgeRegistries.BIOME_MODIFIER_SERIALIZERS.get().getCodec())
        .dispatch(BiomeModifier::codec, Function.identity());
    Codec<Holder<BiomeModifier>> REFERENCE_CODEC = RegistryFileCodec.create(ForgeRegistries.Keys.BIOME_MODIFIERS, DIRECT_CODEC);
    Codec<HolderSet<BiomeModifier>> LIST_CODEC = RegistryCodecs.homogeneousList(ForgeRegistries.Keys.BIOME_MODIFIERS, DIRECT_CODEC);
}
```

`StructureModifier` 同构（`.mc-src/1.20.1/net/minecraftforge/common/world/StructureModifier.java:13-18`）。

**NeoForge 1.21.1 / 26.1.2**（`.mc-src/1.21.1/net/neoforged/neoforge/common/world/BiomeModifier.java:40-53` 与 `.mc-src/26.1.2/net/neoforged/neoforge/common/world/BiomeModifier.java:40-53`，**两份文件逐字相同**）：

```java
public interface BiomeModifier {
    /** Codec for (de)serializing biome modifiers inline. Mods can use this for data generation. */
    Codec<BiomeModifier> DIRECT_CODEC = NeoForgeRegistries.BIOME_MODIFIER_SERIALIZERS.byNameCodec()
            .dispatch(BiomeModifier::codec, Function.identity());                                   // :41
    /** Codec for referring to biome modifiers by id in other datapack registry files. Can only be used with RegistryOps. */
    Codec<Holder<BiomeModifier>> REFERENCE_CODEC = RegistryFileCodec.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, DIRECT_CODEC);   // :48
    /** Codec for referring to biome modifiers by id, list of id, or tags. */
    Codec<HolderSet<BiomeModifier>> LIST_CODEC = RegistryCodecs.homogeneousList(NeoForgeRegistries.Keys.BIOME_MODIFIERS, DIRECT_CODEC);  // :54
}
```

官方 javadoc 还给出了 JSON 形状与「禁用」约定（`.mc-src/26.1.2/.../BiomeModifier.java:23-33`）：

```json
{
  "type": "yourmod:yourserializer", // Indicates a registered biome modifier serializer
  // Additional fields can be specified here according to the codec
}
```
> `Datapacks can also disable a biome modifier by overriding the json and using "type": "neoforge:none".`（`:33`）

**这套三件套的意义（对 OreFood 直接可借鉴）**：

| codec | 用途 | 何时需要 |
|---|---|---|
| `DIRECT_CODEC` | 元素**自身**在数据包 JSON 里的完整定义 | **必须有**（注册进 `dataPackRegistry` 的就是它） |
| `REFERENCE_CODEC` | 在**别的**数据包文件里按 id 引用该条目（`RegistryFileCodec` / `RegistryFixedCodec`） | 若别处要引用 `orefood:iron_steak` 则需要 |
| `LIST_CODEC` | 按 id 列表 / tag 引用（`RegistryCodecs.homogeneousList`） | 若需要 tag 支持则需要 |

若 OreFood 的定义**不需要多态**（没有 `"type"` 字段区分多种食品类型），`DIRECT_CODEC` 用普通 `RecordCodecBuilder.create(...)` 即可（见 §4.4 的 `DamageType` / `JukeboxSong` 范例），无需 `dispatch`。
若将来要支持「不同矿物类型走不同子 codec」，就照抄 `dispatch` 模式。

### 9.7 真实原版数据包 JSON 范例（可直接对照字段名）

以下全部来自 `.mc-src/26.1.2/data/minecraft/`（1.20.1 与 1.21.1 的反编译树里**没有**完整的原版动态注册表 JSON，见 §9.8）：

**`data/minecraft/damage_type/cactus.json`**（最小模板）：

```json
{
  "exhaustion": 0.1,
  "message_id": "cactus",
  "scaling": "when_caused_by_living_non_player"
}
```

对应 `.mc-src/26.1.2/net/minecraft/world/damagesource/DamageType.java:13-22` 的字段名 `message_id` / `scaling` / `exhaustion` / `effects` / `death_message_type`。

**`data/minecraft/wolf_variant/pale.json`**（嵌套对象 + 列表，对应 `WolfVariant.DIRECT_CODEC` 的 `assets`/`baby_assets`/`spawn_conditions`）：

```json
{
  "assets": {
    "angry": "minecraft:entity/wolf/wolf_angry",
    "tame": "minecraft:entity/wolf/wolf_tame",
    "wild": "minecraft:entity/wolf/wolf"
  },
  "baby_assets": {
    "angry": "minecraft:entity/wolf/wolf_angry_baby",
    "tame": "minecraft:entity/wolf/wolf_tame_baby",
    "wild": "minecraft:entity/wolf/wolf_baby"
  },
  "spawn_conditions": [
    { "priority": 0 }
  ]
}
```

**`data/minecraft/jukebox_song/cat.json`**：

```json
{
  "comparator_output": 2,
  "description": { "translate": "jukebox_song.minecraft.cat" },
  "length_in_seconds": 185.0,
  "sound_event": "minecraft:music_disc.cat"
}
```

**`data/neoforge/damage_type/poison.json`（1.21.1）** —— 加载器向原版注册表追加条目的真实例子：

```json
{
    "exhaustion": 0.0,
    "message_id": "magic",
    "scaling": "when_caused_by_living_non_player"
}
```

**动态注册表的 tag 路径**：`data/<pack_ns>/tags/<prefix>/<name>.json`，例如 `.mc-src/26.1.2/data/minecraft/tags/damage_type/bypasses_armor.json`。前缀由 `Registries.tagsDirPath` 计算（`.mc-src/1.21.1/net/minecraft/core/registries/Registries.java:255-257`、`.mc-src/26.1.2/.../Registries.java:333-335`）：

```java
public static String tagsDirPath(ResourceKey<? extends Registry<?>> registryKey) {
    return "tags/" + net.neoforged.neoforge.common.CommonHooks.prefixNamespace(registryKey.location());   // 26.1.2 用 registryKey.identifier()
}
```

### 9.8 反编译树 `data/` 目录的完整度差异（影响取证，务必注意）

| 树 | `data/` 顶层 | `data/minecraft/` 子目录 |
|---|---|---|
| `.mc-src/1.20.1/data/` | `forge`、`minecraft` | **只有** `loot_tables`、`recipes` |
| `.mc-src/1.21.1/data/` | `c`、`minecraft`、`neoforge` | **只有** `advancement`、`loot_table`、`recipe`、`tags` |
| `.mc-src/26.1.2/data/` | `minecraft`、`.mcassetsroot` | 38 个（见 §3.3 证据 2） |

> ⚠️ **1.20.1 与 1.21.1 的反编译树里没有完整的原版动态注册表 JSON**——它们只解出了 Forge/NeoForge 自己的资源和少量原版文件。**所有原版动态注册表的真实 JSON 例子只能在 26.1.2 里找到。** 这不代表 1.20.1/1.21.1 没有这些注册表（源码里都在），只是反编译产物的资源抽取不完整。

**26.1.2 独有：`data/minecraft/datapacks/` 内嵌数据包**（1.20.1/1.21.1 树内均未找到 `datapacks/` 目录）：

```
.mc-src/26.1.2/data/minecraft/datapacks/minecart_improvements/pack.mcmeta
.mc-src/26.1.2/data/minecraft/datapacks/redstone_experiments/pack.mcmeta
.mc-src/26.1.2/data/minecraft/datapacks/trade_rebalance/pack.mcmeta
.mc-src/26.1.2/data/minecraft/datapacks/trade_rebalance/data/minecraft/tags/enchantment/trades/desert_common.json
```

出现 `data/minecraft/datapacks/<pack>/data/minecraft/...` 的双层 `data` 嵌套——这是**内嵌数据包**（内置实验性数据包）的布局，与自定义动态注册表的目录规则无关，不要混淆。

**26.1.2 目录单数化改名**（写数据包时注意）：`loot_tables`→`loot_table`、`recipes`→`recipe`；tag 目录单数化 `tags/block`、`tags/item`、`tags/damage_type`（复数 `tags/blocks` 仅存在于 1.20.1 的 `data/forge/tags/blocks/`）。

---

## 10. 「未找到」与检索关键字清单（诚实记录）

| 待查项 | 结论 | 检索过的关键字 |
|---|---|---|
| 26.1.2 里 `DataPackRegistryEvent` 是否改名 | **未改名**（同名同包） | `dataPackRegistry`、`DataPackRegistryEvent`、`NewRegistry`、`RegistryDataLoader`、`RegistryValidator`、`ResourceManagerRegistryLoadTask` |
| 26.1.2 里 `AddReloadListenerEvent` | **已删除**，替代品 `AddServerReloadListenersEvent` | `AddReloadListenerEvent`、`AddServerReloadListenersEvent`、`SortedReloadListenerEvent`、`ReloadListener`、`PreparableReloadListener` |
| 1.21.1 里 `AddServerReloadListenersEvent` | **未找到**（1.21.1 该目录无此文件） | `AddServerReloadListenersEvent`、`SortedReloadListenerEvent` |
| 1.20.1 `Registry.getOrThrow` | **未找到** | `getOrThrow`、`orElseThrow`（在 `.mc-src/1.20.1/net/minecraft/core/Registry.java` 中） |
| 26.1.2 `Registry.holders()` | **未找到**（已删除） | `holders`、`listElements` |
| 26.1.2 `Registry.registryLifecycle()` | **未找到** | `registryLifecycle` |
| 1.20.1 `JUKEBOX_SONG` 注册表 | **未找到**（1.21+ 新增） | `JUKEBOX_SONG`、`jukebox_song` |
| `/reload` 后重新同步动态注册表到客户端的代码路径 | **未找到** | `packRegistries`、`reloadResources`、`RegistrySynchronization` |
| 26.1.2 要求「统一放 `data/<ns>/<registry>/`」的证据 | **未找到**（路径规则未变） | `elementsDirPath`、`registryDirPath`、`prefixNamespace`、`FileToIdConverter`、`componentsDirPath` |
| 通过 `DataPackRegistryEvent` 为自定义注册表指定非空校验（26.1.2） | **未找到**（事件硬编码 `RegistryValidator.none()`） | `RegistryValidator`、`dataPackRegistry`、`nonEmpty` |
| 1.20.1 的 `SYNCHRONIZED_REGISTRIES` | **未找到**（确认不存在；同步表在 `RegistrySynchronization.java:26-34`，是 `Map`） | `SYNCHRONIZED_REGISTRIES`（`1.20.1/.../RegistryDataLoader.java` 全文 195 行）、`NETWORKABLE_REGISTRIES` |
| 1.20.1 / 1.21.1 反编译树里的原版动态注册表 JSON（`data/minecraft/damage_type/`、`data/minecraft/worldgen/biome/`） | **未找到**（资源抽取不完整） | glob `**/damage_type/*.json`、目录列举 `data/minecraft/` |
| `neoforge:enchantment_effect` 之类的额外 datapack registry | **未找到**（确认不存在） | `enchantment_effect`、`ENCHANTMENT_EFFECT`（`.mc-src/26.1.2/net/neoforged` 下 0 命中；全树仅原版 `ENCHANTMENT_EFFECT_COMPONENT_TYPE`，且它是普通注册表） |
| `biome_modifier` / `structure_modifier` 的实际 JSON 数据文件 | **未找到** | glob `**/*{biome_modifier,structure_modifier}*` |
| `net.neoforged.fml` / `net.neoforged.bus` / `net.minecraftforge.fml.common` 包源码（含 `@Mod`、`@EventBusSubscriber`、`@SubscribeEvent`、`IEventBus` 的**声明本体**） | **未找到**（不在这三棵树里） | `EventBusSubscriber`、`@interface EventBusSubscriber`、glob `**/EventBusSubscriber.java`、`**/Mod.java`、`**/{SubscribeEvent,IEventBus,IModBusEvent}.java`、目录列举 `net/neoforged`、`net/minecraftforge/fml` |
| `net.minecraftforge.eventbus.api.IEventBus` / `net.neoforged.bus.api.IEventBus` 源码 | **未找到** | glob `**/eventbus/api/*.java`、`**/bus/api/*.java` |
| `ForgeMod.java:409` lambda 的显式参数类型 | **无法确证**（无该 import，属反编译瑕疵） | `DataPackRegistryEvent` 于 `.mc-src/1.20.1` 全树 |
| 26.1.2 的 `data/neoforge/` 目录 | **未找到** | 列出 `.mc-src/26.1.2/data` → 仅 `minecraft`、`.mcassetsroot` |
| 26.1.2 的 `ResourceLocation.java` | **未找到**（已改名 `Identifier`） | glob `**/ResourceLocation.java`（26.1.2 零命中）、glob `**/Identifier.java`（命中） |
| `ResourceManagerReloadListener` 被删除的迹象 | **三版本都存在**（未删除；仅 26.1.2 的 `reload` 签名由 6 参变 4 参） | `ResourceManagerReloadListener`（1.20.1 `:9`、1.21.1 `:9`、26.1.2 `:9`） |
| 26.1.2 的 `trade_set` / `villager_trade` / `trial_spawner` / `*_sound_variant` 目录内容 | **目录存在但 0 个 JSON 文件** | 逐目录 `Get-ChildItem -File` 计数 |

### 10.1 反编译树的取证局限（影响「未找到」的解读）

三棵树的包覆盖范围不同，会导致「未找到」有两种完全不同的含义：

| 树 | `net/` 下的包 |
|---|---|
| `.mc-src/1.20.1/` | `minecraft`、`minecraftforge`（`minecraftforge/fml` 下**只有 12 个文件**，`fml/common/` 不存在） |
| `.mc-src/1.21.1/` | `minecraft`、`neoforged/neoforge`（**无** `neoforged/fml`、**无** `neoforged/bus`） |
| `.mc-src/26.1.2/` | `minecraft`、`neoforged/neoforge`（同上） |

因此：

- **「事件类 / 注册表类 / 加载器类」的结论是可靠的**——这些都在 `net/minecraft` 与 `net/*/neoforge|minecraftforge` 里，可精确到行。
- **「注解声明本体 / 事件总线接口」的结论不可靠**——`@Mod`、`@EventBusSubscriber`、`@SubscribeEvent`、`IEventBus`、`IModBusEvent` 的源码不在这三棵树里，本报告中关于它们的描述都是从 **import 语句与调用点**反推的，已在相应位置标注。
- **「数据包 JSON 资源」的结论在 1.20.1/1.21.1 上不完整**——只有 26.1.2 树抽取了完整的原版数据资源。**不能**因为 1.20.1 树里没有 `data/minecraft/damage_type/` 就断言 1.20.1 没有该注册表（源码 `.mc-src/1.20.1/.../RegistryDataLoader.java:73` 明确注册了它）。

---

## 11. 对 OreFood 的落地建议（基于以上证据）

1. **注册表 key 用 `orefood:ore_food`**（不要用 `minecraft:`），三版本分别是：
   - 1.20.1 / 1.21.1：`ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("orefood", "ore_food"))`
   - 26.1.2：`ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("orefood", "ore_food"))`

2. **数据文件放 `data/orefood/orefood/ore_food/<name>.json`**（注意两次命名空间；第二个是注册表 key 的命名空间）。

3. **监听 `DataPackRegistryEvent.NewRegistry`**，用 `modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> ...)`；这段代码三版本的**业务写法完全相同**，只有 import 包名（`net.minecraftforge` vs `net.neoforged.neoforge`）不同 —— 非常适合放进 `common` 源集做少量条件编译，或用一个小型跨版本抽象层。

4. **network codec 决策**：
   - 若矿物食品定义只在**服务端**用于逻辑计算（饱食度、耐久、附魔能力、药水效果）→ **可以不提供**，客户端不需要装 mod 的注册表。
   - 若**客户端**需要读取定义（例如在 tooltip 里显示、或在客户端做预测）→ **必须提供 network codec**，且客户端必须装本 mod。此时把 `DIRECT_CODEC` 直接复用为 `NETWORK_CODEC` 即可（参照 `JUKEBOX_SONG` 的做法，9.4 节）。
   - 注意：物品的 `ItemStack` 组件本身不走这条同步路径（走 `DataComponent` 的 StreamCodec），所以**如果定义只影响物品的静态属性、且已烘焙进组件，就不需要动态注册表同步**。

5. **按版本使用正确的读取 API**（这是最容易踩的坑）：
   - 1.20.1 / 1.21.1：`registryAccess().registryOrThrow(KEY)` + `reg.get(ResourceLocation)` / `reg.getOptional(...)` + `reg.holders()`
   - 26.1.2：`registryAccess().lookupOrThrow(KEY)` + `reg.getValue(Identifier)` / `reg.getOptional(...)` + `reg.listElements()`（**`holders()` 不存在**）

6. **不要指望 `/reload` 生效**：改 JSON 后必须重启服务器 / 重进世界。若要在 `/reload` 时刷新派生缓存，按 8.5 节的骨架注册 reload listener（注意 26.1.2 的 `AddServerReloadListenersEvent` 需要给 listener 一个 `Identifier` key）。

7. **注册表可能为空**：数据包没提供任何 JSON 时注册表存在但 `size() == 0`。`registryOrThrow` / `lookupOrThrow` 不会抛异常，但 `get(...)` / `getValue(...)` 会返回 `null`。**必须在代码里处理空注册表**（兜底默认定义或跳过该功能）。

8. **一个 JSON 写错会导致整个注册表加载失败**（`.mc-src/26.1.2/.../RegistryDataLoader.java:230-237` 会把 `loadingErrors` 抛出），所以数据包 JSON 的字段名/类型必须与 codec 严格一致。

9. **元素 codec 建议按官方三件套写**（照抄 `BiomeModifier`，见 §9.6）：
   ```java
   Codec<OreFoodDefinition> DIRECT_CODEC = RecordCodecBuilder.create(...);                        // 必须有
   Codec<Holder<OreFoodDefinition>> REFERENCE_CODEC =
           RegistryFixedCodec.create(OreFoodRegistries.ORE_FOOD);                                  // 若别处要按 id 引用
   Codec<HolderSet<OreFoodDefinition>> LIST_CODEC =
           RegistryCodecs.homogeneousList(OreFoodRegistries.ORE_FOOD, DIRECT_CODEC);               // 若要 tag 支持
   ```
   若将来要支持「多种食品子类型」，改用 `byNameCodec().dispatch(OreFoodDefinition::codec, Function.identity())` 的多态模式（§9.6）。

10. **如果实现 reload listener，26.1.2 的 `reload` 是 4 参数**（§6.5）：
    - 1.20.1 / 1.21.1：6 参数（`PreparationBarrier, ResourceManager, ProfilerFiller, ProfilerFiller, Executor, Executor`）
    - 26.1.2：4 参数（`SharedState, Executor, PreparationBarrier, Executor`）
    - 最省事的做法是 `extends ResourceManagerReloadListener`（三版本都存在，只需实现 `onResourceManagerReload(ResourceManager)`），或 26.1.2 下 `extends ContextAwareReloadListener` 用 `getRegistryLookup()`。

11. **事件总线别搞错**：
    - `DataPackRegistryEvent.NewRegistry` → **mod bus**（`modEventBus.addListener(...)`），三版本都是。
    - `AddReloadListenerEvent` / `AddServerReloadListenersEvent` / `TagsUpdatedEvent` / `ServerStartedEvent` → **game bus**（1.20.1 用 `MinecraftForge.EVENT_BUS`；1.21.1/26.1.2 用 `NeoForge.EVENT_BUS`）。
    - 客户端 `RegisterClientReloadListenersEvent`（1.20.1/1.21.1）/ `AddClientReloadListenersEvent`（26.1.2）→ **mod bus**。
    - `@EventBusSubscriber`：1.20.1 是 `@Mod.EventBusSubscriber(modid=..., bus=Bus.MOD/FORGE)`；1.21.1/26.1.2 是独立的 `@EventBusSubscriber(modid=..., value=Dist...)`，**没有 `bus` 参数**（只服务 game bus）。

12. **1.20.1 的注册表 key 字符串写法注意**：Forge 自己在 1.20.1 用 `new ResourceLocation("forge:biome_modifier")`（把命名空间写进字符串，`ForgeRegistries.java:175`），而 NeoForge 用 `Identifier.fromNamespaceAndPath(MOD_ID, name)`（`NeoForgeRegistries.java:65`）。**OreFood 统一用 `fromNamespaceAndPath` 更清晰**（1.20.1 也有该方法，`ResourceLocation.java:255`）。

---

*报告结束。所有结论均基于 `.mc-src/` 中的反编译源码，附有 `文件:行号` 证据。*
