package com.lyingice.orefood.data;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.OreFoodEffectSpec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 自定义「附魔效果组件」：把「这个附魔吃下去给什么状态效果」交给数据包（NeoForge 26.1.2）。
 *
 * <p>与 1.21.1 侧同构：{@code Enchantment} 的 {@code effects} 是 {@code DataComponentMap}，
 * 组件类型注册在 {@link Registries#ENCHANTMENT_EFFECT_COMPONENT_TYPE}，
 * 所以附魔 JSON 可以直接写：
 * <pre>{@code
 * "effects": {
 *   "orefood:food_effects": [
 *     { "effect": "minecraft:instant_health",
 *       "duration":  { "base": 1, "per_level": 0 },
 *       "amplifier": { "base": 0, "per_level": 1 } }
 *   ]
 * }
 * }</pre>
 */
public final class OreFoodEffects {

    /** 附魔效果组件的注册表。 */
    public static final DeferredRegister<DataComponentType<?>> EFFECT_COMPONENTS =
            DeferredRegister.create(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, OreFoodConstants.MOD_ID);

    /** {@link OreFoodEffectSpec.Linear} 的序列化形状。 */
    public static final Codec<OreFoodEffectSpec.Linear> LINEAR_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("base", 0).forGetter(OreFoodEffectSpec.Linear::base),
                    Codec.INT.optionalFieldOf("per_level", 0)
                            .forGetter(OreFoodEffectSpec.Linear::perLevel)
            ).apply(instance, OreFoodEffectSpec.Linear::new));

    /** 单条效果的序列化形状。 */
    public static final Codec<OreFoodEffectSpec> SPEC_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING.fieldOf("effect").forGetter(OreFoodEffectSpec::effectId),
                    LINEAR_CODEC.fieldOf("duration").forGetter(OreFoodEffectSpec::duration),
                    LINEAR_CODEC.fieldOf("amplifier").forGetter(OreFoodEffectSpec::amplifier),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("probability", 1.0F)
                            .forGetter(OreFoodEffectSpec::probability)
            ).apply(instance, OreFoodEffectSpec::new));

    /** 效果列表的序列化形状。 */
    public static final Codec<List<OreFoodEffectSpec>> LIST_CODEC = SPEC_CODEC.listOf();

    /**
     * 附魔上的「食用效果」组件，注册名 {@code orefood:food_effects}。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<OreFoodEffectSpec>>> FOOD_EFFECTS =
            EFFECT_COMPONENTS.register("food_effects",
                    () -> DataComponentType.<List<OreFoodEffectSpec>>builder()
                            .persistent(LIST_CODEC)
                            .build());

    /**
     * @param enchantment 要查询的附魔
     * @return 该附魔上挂着的食用效果；没有就是空列表
     */
    public static List<OreFoodEffectSpec> specsOf(net.minecraft.world.item.enchantment.Enchantment enchantment) {
        List<OreFoodEffectSpec> specs = enchantment.effects().get(FOOD_EFFECTS.get());
        return specs == null ? List.of() : specs;
    }

    private OreFoodEffects() {
        throw new AssertionError("No instances.");
    }
}
