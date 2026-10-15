# Frame packet: 03-durability

## Project inputs

- Project: D:\0CreaterMods\OreFood\videos\ore-food
- Design tokens: D:\0CreaterMods\OreFood\videos\ore-food\frame.md
- RULES_DIR: C:\Users\12501\.dsh\profiles\web\node_modules\dsh-hyperframes\skills\hyperframes-animation\rules

## Assigned storyboard block

## Frame 3 — 耐久不是装备耐久

- scene: 一块铁牛排被反复咬，右侧的像素耐久条逐格掉，物品始终没消失；随后推到一百二十八格
- src: compositions/frames/03-durability.html
- duration: 10s
- poster: 9.2s
- transition_in: cut
- type: product_intro
- status: outline
- voiceover: —
- asset_candidates: iron_beef=assets/orefood/iron_beef.png, netherite_beef=assets/orefood/netherite_beef.png, diamond_sword=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\diamond_sword.png, netherite_chestplate=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\item\netherite_chestplate.png, slot=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\container\slot.png, hotbar=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\hotbar.png
- sfx: click-soft, pop, chime, glitch-1

这一帧没有旁白，承接上一条线的尾音。任务是把机制演清楚：先放一把钻石剑和一件胸甲当对照——那是「用坏就没了」的装备耐久，划掉；再放一块铁牛排，十六格耐久条，每咬一口掉一格，掉到零之前物品一直在。
然后镜头推到下界合金牛排的一百二十八格。这是全片最大的数字，该用 vbig-numeral 撑满。最后落「不可堆叠，一格一块」——一格只放一块的背包画面，把代价说清楚。
