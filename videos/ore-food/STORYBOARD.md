---
format: 1920x1080
duration: 105s
message: "矿石牛排：一个物品编号五种矿物形态，吃完不掉物品只掉耐久，一块能啃十六到一百二十八口，还带五个食物附魔"
arc: 钩子 → 五种牛排 → 耐久机制 → 五个食物附魔 → 强身健体重点 → 逐级合成 → 锻造台 → 数据包扩展 → 版本收尾
audience: 玩 Minecraft 的玩家与整合包作者
mode: autonomous
music: 轻快像素游戏感 chiptune，有推进力，不抢人声
---

## Video direction

**调色系统（来自 `frame.md`，不得发明第二色）**：`paper #F0EBDE` 是地；`ink #1F2BE0` 是唯一的墨——标题、正文、细线、网格、故障条、QR 补丁全是他。`ink-soft #5560E5` 只给编辑性副标题。`grid rgba(31,43,224,0.10)` 是永久方格纸与图表「灭」格。强调从来不靠颜色，靠**尺寸**、靠 Newsreader 与 Hanken 的切换、靠 mono 箭头、靠透明度。禁止投影、渐变、圆角、第二个色相。

**像素素材处理**：原版与模组贴图一律经 `scripts/pixelate.mjs` 压成钴蓝墨面具（nearest-neighbour，`--grid` 到目标格数），**绝不双线性缩放**。面具贴在纸上，读作「钴蓝像素画」，与方格纸同源，这是本片把 Minecraft 素材纳入两色体系的办法。

**运动语法 + 揭示模型**：长尾缓动（`power3` 为默认，快速到达用 `expo.out`）——平滑优先，不要回弹（`back` / `bounce` / `elastic` 一律不用，「盛宴弹出」是唯一的俏皮例外，且仍走阻尼弹簧）。**每一次揭示都跟着旁白走**：t=0 只出现旁白此刻在说的东西，其余元素在时间线上等自己的那一句。画面**绝不前载**——这是本片最重要的一条。

**节奏与静止分配**：帧 1（钩子）与帧 8（锻造台）是**顶点帧**，节奏最紧；帧 3、帧 6 是**静止读帧**——它们没有旁白，承接上一句尾音，功能是把机制读清楚，所以内容揭示完成后**停住**，只允许极低幅度的 `sine-wave-loop` 抖动，不允许呼吸、不允许后半段推镜头。帧 4 是**呼吸帧**（附魔总览，密度最低），给帧 5 的连发留出落差。帧 7 是**密集帧**（四级合成），用重复的节拍器节奏承担密度，不靠加装饰。

**时间轴（九帧，合计 105 秒）**：0–6.6 钩子 · 6.6–16 五种牛排 · 16–26 耐久机制（静） · 26–34.4 附魔总览（呼吸） · 34.4–47.1 逐个触发 · 47.1–55.1 抗性提升重点（静） · 55.1–71.8 逐级合成 · 71.8–80.3 锻造台（顶点） · 80.3–105 数据包与收尾。

**字幕带**：画面底部 180px（900–1080）属于字幕，每帧内容一律规划在顶部 900px 以内。即便本片字幕最终被跳过，这条也照守——底边一致性是设计的一部分。

**负面清单（永不出现）**：第二色相、投影、渐变、圆角、真实浏览器/系统 chrome、通用装饰图形冒充真实素材、悬浮光斑与紫蓝「AI 渐变」。两种运动失败模式同样禁止：**幻灯片式**（前 25% 倒完然后冻住）与**屏保式**（每个元素各自漂浮）。外加：`repeat` / `yoyo` 无限动画、`Math.random` / `Date.now`、CSS `transition` / `@keyframes` 动画。

## Frame 1 — 咬下去

- scene: 一块下界合金牛排顶在画面正中，一口咬下，六枚药水效果图标同时炸开成环
- src: compositions/frames/01-hook.html
- duration: 6.6s
- poster: 5.8s
- transition_in: cut
- type: hook
- status: animated
- voiceover: "把矿石做成牛排？吃一次扣一点耐久，一块能啃一百二十八口。"
- asset_candidates: netherite_beef=assets/orefood/netherite_beef.png, netherite_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_ingot.png, resistance=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\resistance.png, absorption=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\absorption.png, regeneration=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\regeneration.png, strength=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\strength.png, speed=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\speed.png, saturation=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\saturation.png, effect_bg=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\effect_background.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png
- sfx: impact-bass-1, sparkle, ping

全片的钩子。开场空场里只有一块下界合金牛排，右上角压着一行「矿石」的冷硬小字；咬下去的那一拍，六枚药水效果图标从中心炸开成一圈。
笑点靠同框完成：左边一个下界合金锭（矿石），右边就是它做出来的牛排，两者共用一个画幅。反差成立之后，「一百二十八口」这个数字立刻接上——钩子不解释，只负责让人产生「这什么玩意儿」的疑问。

## Frame 2 — 一个物品编号，五种矿物牛排

- scene: 五个物品格一字排开，铜到钻石逐个落位，末尾挂出一行「一个物品编号」
- src: compositions/frames/02-five-forms.html
- duration: 9.4s
- poster: 8.6s
- transition_in: whoosh
- type: product_intro
- status: animated
- voiceover: "你见过用铜、铁、金、钻石做的牛排吗？整个模组只有一个物品编号，却长出了五种矿物牛排。"
- asset_candidates: copper_beef=assets/orefood/copper_beef.png, iron_beef=assets/orefood/iron_beef.png, golden_beef=assets/orefood/golden_beef.png, diamond_beef=assets/orefood/diamond_beef.png, netherite_beef=assets/orefood/netherite_beef.png, cooked_beef=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\cooked_beef.png, copper_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\copper_ingot.png, iron_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\iron_ingot.png, gold_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\gold_ingot.png, diamond=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\diamond.png, netherite_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_ingot.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png
- sfx: pop, click, ping

先把「一个物品编号、五种形态」说成画面上的事实：五个格子的边框完全一样，里面的贴图完全不一样。原版熟牛排先立在最左边当参照物，右边五格逐个落位。
旁白念到「只有一个物品编号」时，五格之间的连线聚成一条，指向行尾那一句编号——把抽象话收成一个可见的结论。收尾半秒停在这行字上，五张贴图仍在。

## Frame 3 — 耐久不是装备耐久

- scene: 一块铁牛排被反复咬，右侧的像素耐久条逐格掉，物品始终没消失；随后推到一百二十八格
- src: compositions/frames/03-durability.html
- duration: 10s
- poster: 9.2s
- transition_in: cut
- type: product_intro
- status: animated
- voiceover: —
- asset_candidates: iron_beef=assets/orefood/iron_beef.png, netherite_beef=assets/orefood/netherite_beef.png, diamond_sword=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\diamond_sword.png, netherite_chestplate=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_chestplate.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png, hotbar=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\hotbar.png
- sfx: click-soft, pop, chime, glitch-1

这一帧没有旁白，承接上一条线的尾音。任务是把机制演清楚：先放一把钻石剑和一件胸甲当对照——那是「用坏就没了」的装备耐久，划掉；再放一块铁牛排，十六格耐久条，每咬一口掉一格，掉到零之前物品一直在。
然后镜头推到下界合金牛排的一百二十八格。这是全片最大的数字，该用 vbig-numeral 撑满。最后落「不可堆叠，一格一块」——一格只放一块的背包画面，把代价说清楚。

## Frame 4 — 五个只能附在食物上的附魔

- scene: 一本附魔书摊开，五枚附魔条目依次浮出，每枚下面挂一行效果
- src: compositions/frames/04-enchants.html
- duration: 8.4s
- poster: 7.6s
- transition_in: crossfade
- type: feature_showcase
- status: animated
- voiceover: "它们自带五个食物附魔，只能附在食物上，吃下去的瞬间触发药水效果。来一个一个看。"
- asset_candidates: enchanted_book=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\enchanted_book.png, book=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\book.png, steak=assets/orefood/netherite_beef.png, resistance=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\resistance.png, absorption=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\absorption.png, regeneration=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\regeneration.png, strength=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\strength.png, speed=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\speed.png, saturation=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\saturation.png, hunger=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\hunger.png, nether_star=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\nether_star.png, effect_bg=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\effect_background.png
- sfx: whoosh-short, pop, sparkle

总览帧：先让观众看到「五个」这个数量，再逐个打开。附魔书是原版玩家一眼认得的符号，用它当这五条的容器，五枚条目自上而下浮出。
盛宴那一条单独标记——旁边压一枚下界之星，表示它是宝藏附魔。末尾用「来一个一个看」把画面推向下一帧，所以最后一拍要留推力，不要收死。

## Frame 5 — 一口一个效果

- scene: 三次咬下，每次弹出一组药水效果图标，图标下方跟着等级与秒数
- src: compositions/frames/05-bite-triggers.html
- duration: 12.7s
- poster: 11.9s
- transition_in: cut
- type: feature_showcase
- status: animated
- voiceover: "强效恢复给你瞬间治疗和生命恢复；肾上腺素给你力量和速度；强身健体给你生命吸收，还有固定的抗性提升二级，稳稳减伤百分之四十。"
- asset_candidates: diamond_beef=assets/orefood/diamond_beef.png, instant_health=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\instant_health.png, regeneration=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\regeneration.png, strength=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\strength.png, speed=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\speed.png, absorption=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\absorption.png, resistance=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\resistance.png, effect_bg=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\effect_background.png
- sfx: ping, pop, sparkle, impact-bass-2

旁白三个短句，画面就三段：强效恢复（瞬间治疗 + 生命恢复，两枚图标）、肾上腺素（力量 + 速度，两枚图标）、强身健体（生命吸收 + 抗性提升，两枚图标）。每段都以一次「咬下去」触发。
每段之间是速度匹配的硬切，不是淡入淡出：咬一拍，图标弹出。第三段把抗性提升那枚图标的尺寸推到最大，直接交给下一帧。

## Frame 6 — 固定的抗性提升二级

- scene: 抗性提升图标顶出画面，旁边立着「固定 II」与「百分之四十减伤」；生命吸收那一枚的等级数字在旁边跳
- src: compositions/frames/06-resistance.html
- duration: 8s
- poster: 7.2s
- transition_in: cut
- type: feature_showcase
- status: animated
- voiceover: —
- asset_candidates: resistance=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\resistance.png, absorption=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\absorption.png, diamond_beef=assets/orefood/diamond_beef.png, enchanted_book=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\enchanted_book.png, absorb_heart=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\heart\absorbing_full.png, heart_full=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\heart\full.png
- sfx: riser, impact-bass-1, sparkle

全片对数值最较真的一帧，也是没有旁白的一帧——把上一条线最后半句「固定的抗性提升二级，稳稳减伤百分之四十」用画面讲完。
排版的分工是：抗性提升固定不动，旁边钉死「固定 II」；生命吸收的等级数字却在跳。两条并排，一眼看出「一个不跟等级、一个跟等级」。这是最容易讲错的地方，所以给它整整一帧。

## Frame 7 — 逐级升上去

- scene: 三乘三合成网格，八块矿物围住中间的上一级牛排，一级一节往上走；错误的那一级被红叉否掉
- src: compositions/frames/07-crafting-chain.html
- duration: 16.7s
- poster: 15.9s
- transition_in: whoosh
- type: feature_showcase
- status: animated
- voiceover: "合成是逐级升上去的：八块矿物围一圈，中间放上一级牛排，从熟牛排一路升到钻石牛排。最后一步换成锻造台，成型模板加上下界合金锭，配一块钻石牛排，才是下界合金牛排。"
- asset_candidates: crafting_gui=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\container\crafting_table.png, copper_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\copper_ingot.png, iron_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\iron_ingot.png, gold_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\gold_ingot.png, diamond=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\diamond.png, cooked_beef=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\cooked_beef.png, copper_beef=assets/orefood/copper_beef.png, iron_beef=assets/orefood/iron_beef.png, golden_beef=assets/orefood/golden_beef.png, diamond_beef=assets/orefood/diamond_beef.png, netherite_beef=assets/orefood/netherite_beef.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png, error=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\smithing\error.png
- sfx: click, pop, whoosh-short, chime, error

这一帧是全片信息量最大的，靠节奏吃下来：铜、铁、金、钻石四级，每级都是同样的图形语言——八块矿物围一圈，中间换一级牛排。重复本身就是记忆点。
旁白说到「一路升到钻石牛排」时，五级链条已经在画面左侧排成一列，右侧的新配方逐个落位。收尾留一拍给易错点：故意把错误的级别放进中间格，结果是空的，只弹出一个红叉。

## Frame 8 — 最后一步：锻造台

- scene: 锻造台三格界面，成型模板加下界合金锭加钻石牛排，产物格弹出下界合金牛排
- src: compositions/frames/08-smithing.html
- duration: 8.5s
- poster: 7.7s
- transition_in: cut
- type: feature_showcase
- status: animated
- voiceover: —
- asset_candidates: smithing_gui=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\container\smithing.png, smithing_template=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_upgrade_smithing_template.png, netherite_ingot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_ingot.png, netherite_scrap=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_scrap.png, diamond_beef=assets/orefood/diamond_beef.png, netherite_beef=assets/orefood/netherite_beef.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png, smithing_table_top=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\block\smithing_table_top.png
- sfx: impact-bass-2, sparkle, chime

没有旁白，但这是全片的顶点：一百二十八口的那个东西是在这里做出来的。所以节奏要慢下来，三格一个一个落位——模板、合金锭、钻石牛排——然后产物格亮起。
下界合金牛排弹出的那一拍配一次低频冲击，之后画面停住，让观众看一眼它。

## Frame 9 — 数据包、兼容与收尾

- scene: 数据包的一行被改写、重进世界即生效；随后是分开显示的物品栏与悬浮提示框；最后收在三平台、开源协议与互动引导
- src: compositions/frames/09-datapack-outro.html
- duration: 24.7s
- poster: 23.9s
- transition_in: crossfade
- type: branding
- status: animated
- voiceover: "所有数值都写在数据包里，改一行，重进世界就生效，你甚至可以自己加一块红石牛排；装了界面模组，五种牛排会分开显示，悬浮提示框按原版药水格式列出效果和时长。支持三个平台，开源协议，自由修改分发。喜欢的话，点个赞收个藏，评论区聊聊你想加的矿物。"
- asset_candidates: redstone=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\redstone.png, redstone_block=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\block\redstone_block.png, lapis_lazuli=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\lapis_lazuli.png, lapis_block=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\block\lapis_block.png, potion=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\potion.png, enchanted_book=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\enchanted_book.png, copper_beef=assets/orefood/copper_beef.png, iron_beef=assets/orefood/iron_beef.png, golden_beef=assets/orefood/golden_beef.png, diamond_beef=assets/orefood/diamond_beef.png, netherite_beef=assets/orefood/netherite_beef.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png, slot_hl=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot_highlight_back.png
- sfx: typing, key-press, ping, chime, sparkle

最后一帧承担三件事，按旁白的三拍分开：一、数据包改一行就生效，顺带摆出「你也可以加一块红石牛排」；二、五种牛排分开显示、提示框按原版药水格式列出效果和时长；三、三平台、开源协议、互动引导。
收尾要把画面收干净：五张贴图归位，标题字落下，没有多余装饰。最后半秒留白。