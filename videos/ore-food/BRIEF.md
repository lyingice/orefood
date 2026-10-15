---
workflow: product-launch-video
flow: automation
storyboard: no
message: "矿石牛排：一个物品五种矿物形态，吃完不掉物品只掉耐久，一块能啃 8~128 口，还带 5 个食物附魔"
destination: bilibili
aspect: 1920x1080
language: zh-CN
length: 105s
angle: feature-reveal
audience: 玩 Minecraft（Forge 1.20.1 / NeoForge 1.21.1 / 26.1.2）的玩家与整合包作者，知道原版食物与附魔机制，但没听说过 OreFood
---

## Intent

OreFood（矿物牛排）模组宣传片，B 站横屏，中文配音 + 同步字幕。用用户给定的六段脚本结构讲清楚一件事：这不是普通的食物模组——**唯一的物品 ID 派生五种矿物牛排，食物自带耐久，每吃一口扣 1 点，扣完才消失**（下界合金牛排 128 口），同时新增 5 个只能附在食物上的附魔，吃下瞬间触发药水效果。

用户明确选择「强冲突反差」钩子（版本 1）：开场就啃下界合金牛排 + buff 爆发，直接把「矿石做成牛排」的反差摔在观众脸上。语气直接、有节奏、偏游戏内图鉴而非广告腔。

## Assets

- `scripts/stage-mod-assets.mjs` — 从模组仓库与用户提供的原版贴图库抽取成片所需素材（本片素材全部为像素贴图，禁止平滑插值）
- 模组真实物品贴图（16×16）：copper_beef / iron_beef / golden_beef / diamond_beef / netherite_beef（`common/src/main/resources/assets/orefood/textures/item/`）——**必须原样出现，放大用 `image-rendering: pixelated`**
- 原版贴图库 `C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures`（用户授权任取）：矿物锭、钻石、下界合金锭、下界合金升级模板、熟牛排、附魔台、锻造台、经验瓶、药水效果图标、方块等
- 模组 lang 文件 `zh_cn.json` 提供牛排与附魔的官方中文名，片中一律用官方名

## Customizations

- 3 秒钩子用用户给定的版本 1 台词（强冲突反差）。
- 全程中文 TTS 解说（离线 Kokoro）+ 与语音同步的中文字幕。
- 六段结构照用户脚本：0-3s 钩子 / 3-20s 五牛排与耐久 / 20-50s 五个附魔（重点强调强身健体 40% 减伤）/ 50-80s 合成升级链 + 锻造台 / 80-105s 数据包自定义 + 版本协议收尾。
- 画面按 Minecraft 游戏内视角重建：物品栏、悬浮提示框（tooltip）、合成网格、锻造台界面，全部用真实贴图 + 像素风格渲染。

## Notes

- **数值来源**：模组 README 的数值表（铜 6/8、铁 8/16、金 8/32 饱和度16、钻石 10/64、下界合金 12/128）。片中呈现按该表原样。
- **易错点必须在片子里讲对**：附魔 I 级 = 药水 I 级（强效恢复 I 给的是生命恢复 I）；抗性提升固定 II 级（40% 减伤）不随等级变，跟着等级走的是生命吸收；盛宴是宝藏附魔，附魔台刷不出。
- **一个物品 ID + 5 种形态**：因为共用同一物品，合成时中间那一格必须是正确的那一级；JEI 下五种牛排分开显示、不合并。
- **像素纪律**：所有贴图放大一律 nearest-neighbor，否则像素感全丢。
- 作者 lyingice，MIT 协议，支持 Forge 1.20.1 / NeoForge 1.21.1 / NeoForge 26.1.2。
- 音画：无 HeyGen 凭证（未登录），走离线引擎——Kokoro 中文旁白 + 本地生成 BGM + 内置 SFX 库。
- 画幅 1920x1080：B 站横屏由 destination 推导。
