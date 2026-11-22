package com.lyingice.orefood.logic;

import com.lyingice.orefood.OreFoodEffectSpec;
import com.lyingice.orefood.data.OreFoodEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 「吃这个食物会施加哪些状态效果」——<b>完全由数据包驱动</b>（NeoForge 1.21.1）。
 *
 * <p>数据来源是物品上每个附魔携带的 {@code orefood:food_effects} 组件
 * （见 {@link OreFoodEffects}）：哪个附魔在几级时给什么效果、持续多久、等级多少，
 * 全部写在附魔 JSON 里，Java 这边不再有任何写死的附魔名单。
 *
 * <p>这个类被两处共用，从而保证<b>提示框显示的</b>与实际吃下去生效的完全一致：
 * <ul>
 *   <li>{@code OreFoodConsume}：真正把效果加到生物身上（并按 probability 掷骰）；</li>
 *   <li>{@code OreFoodTooltip}：把同样的效果渲染成原版药水那样的提示行（不掷骰）。</li>
 * </ul>
 */
public final class FoodEnchantmentEffects {

    /**
     * 一条已解析好的效果：效果实例 + 触发概率。
     *
     * @param instance    要施加的效果实例
     * @param probability 触发概率，0.0 ~ 1.0
     */
    public record Resolved(MobEffectInstance instance, float probability) {

        /**
         * @param random 取随机数的来源
         * @return 本次是否应当触发
         */
        public boolean roll(RandomSource random) {
            return this.probability >= 1.0F || random.nextFloat() < this.probability;
        }
    }

    /**
     * 按物品上附魔携带的数据包定义，算出应施加的全部效果。
     *
     * @param stack 被食用或正在查看的物品栈
     * @return 效果列表；没有附魔、没有配置、或数据包里的效果 id 无效时跳过对应条目
     */
    public static List<Resolved> effectsFor(ItemStack stack) {
        ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments.isEmpty()) {
            return List.of();
        }
        List<Resolved> effects = new ArrayList<>(8);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            for (OreFoodEffectSpec spec : OreFoodEffects.specsOf(holder.value())) {
                MobEffectInstance instance = toInstance(spec, level);
                if (instance != null) {
                    effects.add(new Resolved(instance, spec.probability()));
                }
            }
        }
        return List.copyOf(effects);
    }

    /**
     * @param spec  数据包里的单条定义
     * @param level 该附魔在物品上的等级
     * @return 实例化的效果；效果 id 解析不出来时返回 null（跳过该条，不让整份食物失效）
     */
    private static MobEffectInstance toInstance(OreFoodEffectSpec spec, int level) {
        ResourceLocation id = ResourceLocation.tryParse(spec.effectId());
        // MobEffectInstance 的构造器要 Holder（不是裸对象），用 getHolder 拿引用。
        Holder.Reference<MobEffect> effect = id == null
                ? null
                : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
        if (effect == null) {
            return null;
        }
        return new MobEffectInstance(effect, spec.durationFor(level), spec.amplifierFor(level));
    }

    private FoodEnchantmentEffects() {
        throw new AssertionError("No instances.");
    }
}
