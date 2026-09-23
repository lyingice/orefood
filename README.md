# MultiLoader Mod OreFood

一个可直接运行的多加载器 Minecraft 模组模板：**一份共享逻辑 + 三个独立平台构建**。

解压后无需任何改动即可编译、启动与调试，范例内容是一个注册了示例物品的最小模组。

| 模块 | 加载器 | MC 版本 | Java | Gradle | 构建插件 |
|---|---|---|---|---|---|
| `forge-1.20.1`      | Forge 47.x        | 1.20.1 | **17** | `8.8`   | ForgeGradle `[6.0,6.2)` |
| `neoforge-1.21.1`   | NeoForge 21.1.x   | 1.21.1 | **21** | `8.14.2` | ModDevGradle `2.0.147` |
| `neoforge-26.1.2`   | NeoForge 26.1.2.x | 26.1.2 | **25** | `9.1.0`  | ModDevGradle `2.0.147` |

> MC 26.1 起 Mojang 改用年份版本号，NeoForge 随之变成四段式
> （`26.1.2.109` = MC 26.1.2 + 第 109 次发布），且**要求 Java 25 + Gradle 9.1.0 以上**。

---

## 为什么是三个独立构建

根目录**刻意不使用 composite build（`includeBuild`）**。三个模块对 Gradle 的要求互不兼容：

- ForgeGradle 6 → Gradle 8.8
- ModDevGradle（1.21.1） → Gradle 8.14.2
- ModDevGradle（26.1.2） → Gradle 9.1.0

一旦纳入同一个 Gradle 会话，它们就被迫共用同一个 Gradle 版本，必然有一方跑不起来。
所以根构建只提供 `Exec` 便捷任务，转调各模块**自己的 wrapper**，各用各的版本。

```
./gradlew buildAll        # 依次构建三个平台
./gradlew buildForge      # 只构建 Forge 1.20.1
./gradlew buildNeoForge   # 只构建 NeoForge 1.21.1
```

> 根构建本身不产出任何 artifact，它只是个调度器。
> 日常开发请直接进到对应模块目录里跑 `gradlew build` / `gradlew runClient`。

---

## 目录结构

```
├── common/                        ← 共享层：纯 JDK 代码，无构建文件
│   └── src/main/java/com/lyingice/orefood/      常量与纯逻辑
│   └── src/main/resources/assets/orefood/       语言文件、模型、贴图
│   └── src/test/java/...                         共享层单元测试
├── forge-1.20.1/                  ← 独立 Gradle 构建（含自己的 wrapper）
│   ├── settings.gradle  build.gradle  gradle.properties  gradlew*
│   └── src/main/java/com/lyingice/orefood/
│       ├── forge/OreFoodForge.java              模组入口
│       └── init/OreFoodItems.java               物品注册
│   └── src/main/resources/META-INF/mods.toml     元数据
├── neoforge-1.21.1/               ← 同上结构
│   └── src/main/templates/META-INF/neoforge.mods.toml
└── neoforge-26.1.2/               ← 同上结构
```

**共享层怎么进 jar**：每个模块用
`sourceSets.main.java.srcDir '../common/src/main/java'`
把 `common` 直接当源码目录编译进自己的 jar —— 不打包 jarJar / Shadow。
对纯逻辑层来说这是最省事、也最不容易出错的做法：任何一边编不过都会立刻暴露共享层的问题。

> `common/` 没有自己的 `build.gradle`，它靠三份构建分别编译，天然获得三重校验。
> 因此 `common/` 里**只能放纯 JDK 代码**，一旦 import 游戏类，三个平台里只要有一个缺符号就全崩。

---

## 快速开始

**1. 环境**：只需要一个能跑 Gradle 的 JDK（建议 21）。缺失的 Java 17 / 21 / 25 工具链
由 `foojay-resolver-convention` 插件自动下载，无需手工安装。

**2. 打开**：用 IntelliJ IDEA 分别 **Link Gradle Project** 打开三个平台模块目录
（不要只打开根目录，否则 IDEA 不会把三个独立构建都载入）。

**3. 构建**：

```bash
cd forge-1.20.1      && ./gradlew build          # 产物 build/libs/orefood-forge-1.20.1-1.0.0.jar
cd neoforge-1.21.1   && ./gradlew build
cd neoforge-26.1.2   && ./gradlew build          # 首次会下载 Java 25
```

**4. 调试运行**：

```bash
./gradlew runClient     # 启动带模组的客户端
./gradlew runServer     # 启动服务端
./gradlew test          # 跑共享层单元测试
```

---

## 改成你自己的模组：改名清单

模板里所有标识都是 `orefood`，改名时以下位置**必须同步**，漏一处就会在运行期静默失败：

| # | 位置 | 内容 |
|---|---|---|
| 1 | 三个模块的 `gradle.properties` | `mod_id` / `mod_name` / `mod_group_id` / `mod_version` |
| 2 | 三个模块的 `settings.gradle` | `rootProject.name` |
| 3 | 三个模块的 `build.gradle` | `archivesName` 会跟随 `mod_id`，通常无需改 |
| 4 | `OreFoodConstants.java` | `MOD_ID` / `MOD_NAME` / `MOD_VERSION` / `PACKAGE_ROOT` |
| 5 | `OreFoodConstantsTest.java` | 里面对 `MOD_ID` 的断言 |
| 6 | 三个平台的 `@Mod(...)` 注解 | 已引用 `OreFoodConstants.MOD_ID`，通常无需改 |
| 7 | Java 包目录 | `com/lyingice/orefood` → 你的包路径 |
| 8 | 资源命名空间 | `assets/orefood/` → `assets/<你的 mod_id>/`（三个模块 + common） |
| 9 | 语言文件 | `item.orefood.example_item` 的键 |

> `mod_id` 有 **4 处**必须完全一致：`gradle.properties`、`OreFoodConstants.MOD_ID`、
> 元数据文件里的 `modId`（由 Gradle 展开自动同步）、`assets/<mod_id>/` 目录名。

仓库里附带 `tools/rename-mod.ps1` 可自动完成 1–9（**建议先提交到 git 再运行**）。

---

## 三平台差异速查

对照三份同类文件（`init/OreFoodItems.java`）最直观：

| | Forge 1.20.1 | NeoForge 1.21.1 / 26.1.2 |
|---|---|---|
| 建注册表 | `DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)` | `DeferredRegister.createItems(MOD_ID)` |
| 注册项类型 | `RegistryObject<Item>` | `DeferredItem<Item>` |
| 注册方法 | `register(name, Supplier)` | `registerItem(name, props -> Item)` |
| 插创造标签页 | `event.getEntries().putAfter(...)` | `event.insertAfter(...)` |
| 取事件总线 | `FMLJavaModLoadingContext.get().getModEventBus()` | 直接注入 `@Mod` 构造器 |
| 元数据位置 | `src/main/resources/META-INF/mods.toml` | `src/main/templates/META-INF/neoforge.mods.toml`（由 `generateModMetadata` 展开） |
| 依赖字段 | `mandatory=true` | `type="required"` |
| 客户端限定 | `clientSideOnly=true`（写在 mods.toml） | `displayTest="IGNORE_ALL_VERSION"` |
| `pack_format` | 15 | 34（1.21.1）/ 84（26.1.2） |
| 物品模型 | `models/item/<id>.json` | 1.21.1 同上；**26.1.2 需要 `items/<id>.json` 显式指向模型** |
| 数据生成 run | `runData` | `runData`；**26.1.2 改名为 `runClientData` / `runServerData`** |

---

## 踩坑记录

这些坑都踩过并已在模板中处理好，改代码时别踩回去：

1. **NeoForge 的 `@Mod` 类绝不能直接引用客户端专属类。**
   NeoForge 没有 Forge 的 `clientSideOnly` 开关，`@Mod` 类在专用服务端上照样被加载；
   一旦解析到建立在 `@OnlyIn(Dist.CLIENT)` 之上的类，就抛
   `Attempted to load class ... for invalid dist DEDICATED_SERVER`。
   客户端专属装配必须封进单独的类，且只在 `Dist.CLIENT` 分支里调用——服务端不进该分支，
   JVM 的懒解析就不会加载那个类。

2. **编译期常量会被 javac 内联。** 引用别处的 `public static final String` **不会**触发那个类的加载，
   所以"只引用一个常量"在服务端上安全，"调用一个方法"不安全。

3. **`minecraft_version_range` 不要写宽。** 写成 `[1.21.1, 1.22)` 会让 Modrinth 展开成
   "支持 1.21.1~1.21.8+"，但 1.21.2 起的 NeoForge 是另一条版本线、1.21.4 重写了物品模型系统，
   这个 jar 在那上面根本用不了。只声明确实验证过的版本。

4. **26.1.2 的 run type 不叫 `data`。** 原版把数据生成器拆成了 `clientData` / `serverData`，
   继续写 `data()` 只会在运行期报 `Trying to prepare unknown run: data`。

5. **Groovy 的 `expand` 对美元符号敏感。** `processResources` / `generateModMetadata` 用
   `SimpleTemplateEngine` 展开占位符，元数据文件里出现**未登记的** `${...}` 会直接
   `MissingPropertyException`。注释里写示例请用字面量。

6. **Forge 1.20.1 的 `@Mod` 构造器拿不到事件总线**，只能从
   `FMLJavaModLoadingContext.get().getModEventBus()` 取。

7. **`gradle-wrapper.jar` 是二进制文件**，`.gitignore` 里要用
   `!gradle/wrapper/gradle-wrapper.jar` 把它排除在 `build/` 之类的忽略规则之外。

---

## 许可

模板部分可自由使用。把 `mod_license`、`LICENSE` 换成你自己的授权协议。
