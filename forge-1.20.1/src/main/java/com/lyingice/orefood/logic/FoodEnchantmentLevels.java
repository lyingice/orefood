package com.lyingice.orefood.logic;

import com.lyingice.orefood.init.OreFoodEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.RegistryObject;

/**
 * 一份物品栈上五个食物附魔各自的等级（Forge 1.20.1）。
 *
 * <p>与 1.21.1 侧的同类文件相比，这里<b>不需要 {@code RegistryAccess}</b>：
 * 1.20.1 的附魔是静态注册表，直接持有 {@code RegistryObject} 即可取到实例。
 * 1.21.1 那边附魔是数据包驱动的动态注册表，必须层层传入 {@code HolderLookup.Provider}。
 *
 * @param recovery   强效恢复等级
 * @param adrenaline 肾上腺素等级
 * @param fitness    强身健体等级
 * @param satiety    饱腹等级
 * @param feast      盛宴等级
 */
public record FoodEnchantmentLevels(int recovery, int adrenaline, int fitness, int satiety, int feast) {

    /** 一个附魔都没有。 */
    public static final FoodEnchantmentLevels NONE = new FoodEnchantmentLevels(0, 0, 0, 0, 0);

    /** 读取物品栈上的五个附魔等级。 */
    public static FoodEnchantmentLevels of(ItemStack stack) {
        return new FoodEnchantmentLevels(
                levelOf(stack, OreFoodEnchantments.POWERFUL_RECOVERY),
                levelOf(stack, OreFoodEnchantments.ADRENALINE),
                levelOf(stack, OreFoodEnchantments.FITNESS),
                levelOf(stack, OreFoodEnchantments.SATIETY),
                levelOf(stack, OreFoodEnchantments.FEAST));
    }

    private static int levelOf(ItemStack stack, RegistryObject<Enchantment> enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(), stack);
    }

    /** @return 是否五个附魔全为 0 级。 */
    public boolean isEmpty() {
        return this.recovery == 0
                && this.adrenaline == 0
                && this.fitness == 0
                && this.satiety == 0
                && this.feast == 0;
    }
}
