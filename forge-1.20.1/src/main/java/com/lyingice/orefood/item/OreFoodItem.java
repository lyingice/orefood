package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodItems;
import net.minecraft.network.chat.Component;
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
     * 按定义给出显示名。
     *
     * <p>名字<b>从定义 id 派生</b>，而不是必须烘焙进物品栈的 NBT。这样无论物品栈是怎么来的
     * ——创造栏、合成产物、{@code /give}、别的模组发的——只要带着定义就显示正确名字。
     * 曾经创造栏设名字而配方产物只抄了定义 NBT，结果合成出来的牛排叫「矿物食品」。
     *
     * <p>原版的回退链是「{@code display.Name}（铁砧改名）→ 本方法」，所以铁砧改名仍然优先。
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

    /** 按定义给出附魔能力（铁 1 / 金 5 / 钻石 10 / 下界合金 15）。 */
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        OreFoodDefinition definition = definitionOf(stack);
        return definition != null ? definition.enchantmentValue() : super.getEnchantmentValue(stack);
    }
}
