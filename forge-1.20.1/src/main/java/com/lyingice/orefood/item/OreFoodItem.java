package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 矿物食品——全模组唯一的物品，所有牛排都是它的带 NBT 变体（Forge 1.20.1）。
 *
 * <p>「哪种牛排」由物品栈 NBT 里的 {@code orefood} 复合标签决定
 * （见 {@link OreFoodDefinition}），因此<b>耐久上限与附魔能力都是按物品栈算的</b>。
 * 原版的 {@code Item#getMaxDamage()} 是物品级的，这里改为覆写 Forge 的
 * {@code ItemStack} 敏感重载 {@code IForgeItem#getMaxDamage(ItemStack)}。
 */
public class OreFoodItem extends Item {

    public OreFoodItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 取物品栈上的牛排定义。
     *
     * @return 定义；若该物品栈没有携带定义（例如用 {@code /give} 直接给的空壳），返回 {@code null}
     */
    public static OreFoodDefinition definitionOf(ItemStack stack) {
        return OreFoodDefinition.readFromStack(stack);
    }

    /**
     * 按定义给出最大耐久。
     *
     * <p>定义里 {@code durability} 为 0 表示无限耐久，此时退回物品的默认值——
     * 实际效果是「吃了不掉耐久」，永远不会损坏。
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        OreFoodDefinition definition = definitionOf(stack);
        if (definition != null && !definition.infiniteDurability()) {
            return definition.durability();
        }
        return super.getMaxDamage(stack);
    }

    /** 按定义给出附魔能力（铁 1 / 金 5 / 钻石 10 / 下界合金 15）。 */
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        OreFoodDefinition definition = definitionOf(stack);
        return definition != null ? definition.enchantmentValue() : super.getEnchantmentValue(stack);
    }
}
