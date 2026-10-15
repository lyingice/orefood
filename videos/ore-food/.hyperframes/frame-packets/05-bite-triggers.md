# Frame packet: 05-bite-triggers

## Project inputs

- Project: D:\0CreaterMods\OreFood\videos\ore-food
- Design tokens: D:\0CreaterMods\OreFood\videos\ore-food\frame.md
- RULES_DIR: C:\Users\12501\.dsh\profiles\web\node_modules\dsh-hyperframes\skills\hyperframes-animation\rules

## Assigned storyboard block

## Frame 5 — 一口一个效果

- scene: 三次咬下，每次弹出一组药水效果图标，图标下方跟着等级与秒数
- src: compositions/frames/05-bite-triggers.html
- duration: 12.7s
- poster: 11.9s
- transition_in: cut
- type: feature_showcase
- status: outline
- voiceover: "强效恢复给你瞬间治疗和生命恢复；肾上腺素给你力量和速度；强身健体给你生命吸收，还有固定的抗性提升二级，稳稳减伤百分之四十。"
- asset_candidates: diamond_beef=assets/orefood/diamond_beef.png, instant_health=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\instant_health.png, regeneration=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\regeneration.png, strength=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\strength.png, speed=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\speed.png, absorption=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\absorption.png, resistance=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\mob_effect\resistance.png, effect_bg=C:\Users\12501\Desktop\旧版MUT贴图\我的世界原版资源包\textures\gui\sprites\hud\effect_background.png
- sfx: ping, pop, sparkle, impact-bass-2

旁白三个短句，画面就三段：强效恢复（瞬间治疗 + 生命恢复，两枚图标）、肾上腺素（力量 + 速度，两枚图标）、强身健体（生命吸收 + 抗性提升，两枚图标）。每段都以一次「咬下去」触发。
每段之间是速度匹配的硬切，不是淡入淡出：咬一拍，图标弹出。第三段把抗性提升那枚图标的尺寸推到最大，直接交给下一帧。
