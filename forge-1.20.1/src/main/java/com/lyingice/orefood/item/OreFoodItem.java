package com.lyingice.orefood.item;

import com.lyingice.orefood.data.OreFoodDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 矿物牛排物品（Forge 1.20.1）。
 *
 * <p>每一种牛排都是这个类的<b>独立实例</b>，构造时带上自己的 {@link OreFoodDefinition}
 * （见 {@code OreFoodItems}）。全模组的食用结算、耐久消耗、附魔能力、提示框都通过
 * {@link #definitionOf(ItemStack)} 取定义，因此<b>任何</b>继承本类的物品
 * ——包括 KubeJS 脚本注册的——都会自动拥有完整的矿物牛排行为。
 *
 * <p><b>名字不再需要覆写</b>：1.1.x 里五种牛排共用一个物品，只能从定义 id 派生显示名；
 * 现在每种牛排是独立物品，原版按物品描述 id（{@code item.orefood.<路径>}）取名即可，
 * 铁砧改名等原版行为也自然正确。
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
     * <p>定义里 {@code durability} 为 0 表示<b>没有耐久设定</b>，此时退回物品的默认值。
     * 那种牛排既不消耗耐久也不消耗本体（见 {@code OreFoodConsume#consume}），
     * 所以这里的返回值不会被用到——伤害值恒为 0，耐久条与「耐久 x/y」提示行都不会出现。
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return this.definition.infiniteDurability()
                ? super.getMaxDamage(stack)
                : this.definition.durability();
    }

    /** 按定义给出附魔能力（铜/铁 1、金 5、钻石 10、下界合金 15）。 */
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return this.definition.enchantmentValue();
    }

    /**
     * 铁砧里用对应矿物材料修复（钻石牛排用钻石修，下界合金牛排用下界合金锭）。
     *
     * <p>1.20.1 没有 1.21 的 {@code minecraft:repairable} 组件，只能覆写这个方法；
     * 材料 id 来自 {@code MineralTier#repairItemId()}，与合成配方用的是同一份数据。
     *
     * @param toRepair 被修复的食物
     * @param repair   铁砧第二个槽里的材料
     * @return 材料是否与这个食物同等级
     */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        if (this.definition.infiniteDurability()) {
            // 无限耐久的食物根本不会掉耐久，没有可修的东西。
            return false;
        }
        ResourceLocation materialId = ResourceLocation.tryParse(this.definition.repairItemId());
        Item material = materialId == null ? null : ForgeRegistries.ITEMS.getValue(materialId);
        return material != null && repair.is(material);
    }
}
