package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodComponents;
import com.lyingice.orefood.init.OreFoodItems;
import net.minecraft.network.chat.Component;
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
     * 按定义给出显示名。
     *
     * <p>名字<b>从定义 id 派生</b>，而不是必须烘焙进物品栈的组件。这样无论物品栈是怎么来的
     * ——创造栏、合成产物、{@code /give}、别的模组发的——只要带着定义就显示正确名字。
     * 曾经创造栏设名字而配方产物只抄了定义组件，结果合成出来的牛排叫「矿物食品」。
     *
     * <p>原版的回退链是「自定义名（铁砧）→ 物品名组件 → 本方法」，所以铁砧改名仍然优先。
     */
    @Override
    public Component getName(ItemStack stack) {
        OreFoodDefinition definition = definitionOf(stack);
        if (definition != null && definition.id().isPresent()) {
            return Component.translatable(OreFoodItems.nameKey(definition.id().get()));
        }
        return super.getName(stack);
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
}
