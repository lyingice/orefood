package com.lyingice.orefood.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.ExtraCodecs;

/**
 * 「矿物牛排」的数值定义（Forge 1.20.1）。
 *
 * <p><b>1.2.0 起这份定义由物品自己携带</b>：五种牛排各自是一个真实物品
 * （{@code orefood:copper_beef} … {@code orefood:netherite_beef}），构造
 * {@code OreFoodItem} 时把定义常量交给它。随之删掉的东西：
 * <ul>
 *   <li>{@code id} —— 身份就是物品 id；</li>
 *   <li>{@code model_data} —— 每物品有自己的模型文件，不再需要 custom_model_data 分发；</li>
 *   <li>动态注册表（{@code REGISTRY_KEY}）与物品栈 NBT 读写。</li>
 * </ul>
 *
 * <p>{@link #CODEC} 保留下来只为一件事：让 <b>KubeJS</b> 插件把脚本里的
 * {@code .definition({...})} 解析成这个 record。字段名与 1.1.x 的数据包 JSON 兼容：
 * <pre>{@code
 * { "nutrition": 8, "saturation": 12.8, "durability": 16,
 *   "enchantment_value": 1, "effects": [ ... ] }
 * }</pre>
 *
 * @param nutrition        饱食度
 * @param saturation       饱和度
 * @param durability       最大耐久；<b>0 表示无限耐久</b>（吃了不掉耐久）
 * @param enchantmentValue 附魔能力
 * @param effects          食用后附带的状态效果，可为空
 * @param repairItemId     铁砧里修复用的材料 id（原版物品）；空串表示不可修复
 * @param fireResistant    是否抗火（下界合金为 true：掉进岩浆/火里不会被烧毁）
 */
public record OreFoodDefinition(
        int nutrition,
        float saturation,
        int durability,
        int enchantmentValue,
        List<PotionEffectSpec> effects,
        String repairItemId,
        boolean fireResistant) {

    public static final Codec<OreFoodDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("nutrition", 0).forGetter(OreFoodDefinition::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.0F).forGetter(OreFoodDefinition::saturation),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("durability", 0).forGetter(OreFoodDefinition::durability),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("enchantment_value", 0)
                    .forGetter(OreFoodDefinition::enchantmentValue),
            PotionEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(OreFoodDefinition::effects),
            Codec.STRING.optionalFieldOf("repair_item", "").forGetter(OreFoodDefinition::repairItemId),
            Codec.BOOL.optionalFieldOf("fire_resistant", false).forGetter(OreFoodDefinition::fireResistant)
    ).apply(instance, OreFoodDefinition::new));

    /**
     * @return 该定义是否「没有耐久设定」（{@code durability} 为 0）。
     *         这种牛排既不消耗耐久，也<b>不消耗本体</b>——用不掉，参考马铠。
     */
    public boolean infiniteDurability() {
        return this.durability <= 0;
    }
}
