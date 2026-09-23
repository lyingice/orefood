package com.lyingice.orefood.logic;

import com.lyingice.orefood.init.OreFoodEnchantments;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 一份物品栈上五个食物附魔各自的等级（NeoForge 26.1.2）。
 *
 * <p>与 1.21.1 侧的差别只在导入：26.1.2 里 {@code ResourceLocation} 已改名 {@code Identifier}，
 * 而附魔等级的读取入口 {@code ItemStack#getEnchantmentLevel(Holder)} 保持不变
 * （底层实现从 {@code IItemStackExtension} 挪到了 {@code ItemInstanceExtension}，
 * 但对调用方透明）。
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

    /**
     * 读取物品栈上的五个附魔等级。
     *
     * @param stack      物品栈
     * @param registries 注册表访问入口；服务端用 {@code level.registryAccess()}，
     *                   客户端用 {@code Item.TooltipContext#registries()}
     */
    public static FoodEnchantmentLevels of(ItemStack stack, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        return new FoodEnchantmentLevels(
                levelOf(stack, lookup, OreFoodEnchantments.POWERFUL_RECOVERY),
                levelOf(stack, lookup, OreFoodEnchantments.ADRENALINE),
                levelOf(stack, lookup, OreFoodEnchantments.FITNESS),
                levelOf(stack, lookup, OreFoodEnchantments.SATIETY),
                levelOf(stack, lookup, OreFoodEnchantments.FEAST));
    }

    /** @return 某个附魔在该物品栈上的等级；附魔注册表缺失时返回 0。 */
    private static int levelOf(
            ItemStack stack, HolderLookup.RegistryLookup<Enchantment> lookup, ResourceKey<Enchantment> key) {
        return lookup.get(key).map(stack::getEnchantmentLevel).orElse(0);
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
