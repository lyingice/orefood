package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.FoodEnchantment;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 五种食物附魔的注册表键（1.21.1 / 26.1.2）。
 *
 * <p>1.21 起附魔是<b>数据包驱动</b>的动态注册表：这里只声明「有这么几个键」，
 * 真正的定义在 {@code data/orefood/enchantment/<path>.json}。因此本类不做注册，
 * 只是给代码侧提供类型安全的引用。
 *
 * <p>对照 1.20.1：那边附魔是静态注册的 {@code abstract class}，
 * 见 {@code forge-1.20.1/.../init/OreFoodEnchantments.java}。
 */
public final class OreFoodEnchantments {

    /** 强效恢复：瞬间治疗 + 生命恢复。 */
    public static final ResourceKey<Enchantment> POWERFUL_RECOVERY = key(FoodEnchantment.POWERFUL_RECOVERY);

    /** 肾上腺素：力量 + 速度。 */
    public static final ResourceKey<Enchantment> ADRENALINE = key(FoodEnchantment.ADRENALINE);

    /** 强身健体：生命吸收 + 抗性提升。 */
    public static final ResourceKey<Enchantment> FITNESS = key(FoodEnchantment.FITNESS);

    /** 饱腹：额外饱食度。 */
    public static final ResourceKey<Enchantment> SATIETY = key(FoodEnchantment.SATIETY);

    /** 盛宴：饱和效果（宝藏附魔）。 */
    public static final ResourceKey<Enchantment> FEAST = key(FoodEnchantment.FEAST);

    private static ResourceKey<Enchantment> key(FoodEnchantment enchantment) {
        return ResourceKey.create(
                Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath(OreFoodConstants.MOD_ID, enchantment.path()));
    }

    private OreFoodEnchantments() {
        throw new AssertionError("No instances.");
    }
}
