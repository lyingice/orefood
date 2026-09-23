package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 矿物食品——全模组唯一的物品，所有牛排都是它的带组件变体。
 *
 * <p>「哪种牛排」由物品栈上的 {@code orefood:definition} 组件决定，
 * 因此<b>耐久上限与附魔能力都是按物品栈算的</b>，而不是按物品算的。
 * 原版的 {@code Item#getMaxDamage()} 是物品级的（所有实例共用一个值），
 * 这里改为覆写 NeoForge 的 {@code ItemStack} 敏感重载。
 *
 * <p>注意注册时仍然写了 {@code Properties.durability(1)}：它的作用只是让
 * {@code MAX_DAMAGE} / {@code DAMAGE} 两个组件存在，从而让
 * {@code ItemStack#isDamageableItem()} 返回 true。真正的上限由本类决定。
 */
public class OreFoodItem extends Item {

    public OreFoodItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 取物品栈上的牛排定义。
     *
     * @return 定义；若该物品栈没有定义组件（例如用 {@code /give} 直接给的空壳），返回 {@code null}
     */
    public static OreFoodDefinition definitionOf(ItemStack stack) {
        return stack.get(OreFoodComponents.DEFINITION.get());
    }

    /**
     * 按定义给出最大耐久。
     *
     * <p>定义里 {@code durability} 为 0 表示<b>没有耐久设定</b>，此时退回物品的默认值。
     * 那种牛排既不消耗耐久也不消耗本体（见 {@code OreFoodConsume#consume}），
     * 所以这里的返回值不会被用到——伤害值恒为 0，耐久条与「耐久 x/y」提示行都不会出现。
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
