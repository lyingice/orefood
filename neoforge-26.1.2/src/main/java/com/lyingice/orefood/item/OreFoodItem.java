package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 矿物食品——全模组唯一的物品，所有牛排都是它的带组件变体（NeoForge 26.1.2）。
 *
 * <p>「哪种牛排」由物品栈上的 {@code orefood:definition} 组件决定。
 *
 * <p><b>26.1.2 与 1.21.1 的一处实质差异</b>：附魔能力不再走
 * {@code Item#getEnchantmentValue(ItemStack)}——26.1.2 把它改成了
 * {@code minecraft:enchantable} <b>数据组件</b>（{@code DataComponents.ENCHANTABLE}），
 * 所以这边<b>没有</b>可按物品栈覆写的入口。定义里的 {@code enchantment_value}
 * 由 {@code OreFoodItems#stackFor} 在造物品栈时写进组件（组件本身就是 per-stack 的，
 * 所以「每种牛排附魔能力不同」依然成立）。
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
}
