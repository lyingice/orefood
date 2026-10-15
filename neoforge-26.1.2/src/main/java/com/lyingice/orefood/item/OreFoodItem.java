package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 矿物牛排物品（NeoForge 26.1.2）。
 *
 * <p>每一种牛排都是这个类的<b>独立实例</b>，构造时带上自己的 {@link OreFoodDefinition}
 * （见 {@code OreFoodItems}）。全模组的食用结算、耐久消耗、提示框都通过
 * {@link #definitionOf(ItemStack)} 取定义，因此<b>任何</b>继承本类的物品
 * ——包括 KubeJS 脚本注册的——都会自动拥有完整的矿物牛排行为。
 *
 * <p><b>与 1.21.1 的一处实质差异</b>：附魔能力不再走
 * {@code Item#getEnchantmentValue(ItemStack)} —— 26.1.2 把它改成了
 * {@code minecraft:enchantable} <b>数据组件</b>，所以这边<b>没有</b>可按物品栈覆写的入口，
 * 由 {@code OreFoodItems} 在注册物品时写进默认组件。
 *
 * <p><b>名字不再需要覆写</b>：1.1.x 里五种牛排共用一个物品，只能从定义 id 派生显示名；
 * 现在每种牛排是独立物品，原版按物品描述 id（{@code item.orefood.<路径>}）取名即可。
 */
public class OreFoodItem extends Item {

    private final OreFoodDefinition definition;

    public OreFoodItem(Item.Properties properties, OreFoodDefinition definition) {
        super(properties);
        this.definition = definition;
    }

    /** @return 这个物品自带的牛排定义（永远非 {@code null}）。 */
    public OreFoodDefinition definition() {
        return this.definition;
    }

    /**
     * 取物品栈对应的牛排定义。
     *
     * @return 定义；该物品不是矿物牛排时返回 {@code null}（原版食物与别的模组物品照旧）
     */
    public static OreFoodDefinition definitionOf(ItemStack stack) {
        return stack.getItem() instanceof OreFoodItem food ? food.definition : null;
    }

    /**
     * 按定义给出最大耐久。
     *
     * <p>注册时的 {@code durability(1)} 只为让 {@code MAX_DAMAGE} / {@code DAMAGE} 组件存在，
     * 从而让 {@code ItemStack#isDamageableItem()} 返回 true；真正的上限在这里按定义给出。
     *
     * <p>定义里 {@code durability} 为 0 表示<b>没有耐久设定</b>，那时既不消耗耐久也不消耗本体
     * （见 {@code OreFoodConsume#consume}），所以这里的返回值不会被用到。
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return this.definition.infiniteDurability()
                ? super.getMaxDamage(stack)
                : this.definition.durability();
    }
}
