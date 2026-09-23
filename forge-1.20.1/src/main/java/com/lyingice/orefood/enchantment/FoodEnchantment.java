package com.lyingice.orefood.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * 一个食物附魔（Forge 1.20.1）。
 *
 * <p>1.20.1 的附魔是<b>静态注册的抽象类</b>——不像 1.21.1 / 26.1.2 那样由数据包 JSON 定义，
 * 所以每个附魔只能是一个实例，最大等级之类的参数只能从构造器传进来。
 * 五个附魔共用这一个类，差别全在构造参数里（见 {@code OreFoodEnchantments}）。
 *
 * <p>注意本模组的附魔效果<b>不在这里实现</b>：1.20.1 的 {@code Enchantment} 只有
 * 少量可供覆写的钩子（{@code doPostAttack}、{@code doPostHurt} 等），
 * 没有任何一个是「物品被吃下」。所以吃下时的效果统一由
 * {@code logic.OreFoodConsume} 配合 {@code mixin.LivingEntityEatMixin} 施加。
 */
public class FoodEnchantment extends Enchantment {

    private final int maxLevel;
    private final boolean treasure;

    public FoodEnchantment(Rarity rarity, EnchantmentCategory category, int maxLevel, boolean treasure) {
        // 食物拿在手上，槽位声明为主手/副手即可；本模组不使用 tick 类槽位效果。
        super(rarity, category, new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND});
        this.maxLevel = maxLevel;
        this.treasure = treasure;
    }

    @Override
    public int getMaxLevel() {
        return this.maxLevel;
    }

    /**
     * 宝藏附魔（仅「盛宴」）：不出现在附魔台。
     *
     * <p>1.20.1 的附魔是静态注册的，没有 1.21+ 那套数据包标签，
     * 「能不能附魔台出 / 能不能交易」全靠覆写本类这几个方法表达。
     * 与 1.21.1 / 26.1.2 侧标签的对应关系：
     * <ul>
     *   <li>{@code isTreasureOnly()} ↔ {@code #minecraft:treasure}</li>
     *   <li>{@code isDiscoverable()} ↔ {@code #minecraft:in_enchanting_table}</li>
     *   <li>{@code isTradeable()} ↔ {@code #minecraft:tradeable}</li>
     * </ul>
     */
    @Override
    public boolean isTreasureOnly() {
        return this.treasure;
    }

    /**
     * 所有食物附魔都可交易——宝藏附魔也一样。
     *
     * <p>对照原版：{@code mending} 是宝藏附魔，但图书管理员照卖不误；
     * 1.21+ 的 {@code #minecraft:tradeable} 标签正是「非宝藏附魔 + 少数几个宝藏附魔」。
     * 本模组的「盛宴」也列在那个标签里，所以这里必须返回 {@code true}。
     */
    @Override
    public boolean isTradeable() {
        return true;
    }

    /** 只有非宝藏附魔能在附魔台刷出来。 */
    @Override
    public boolean isDiscoverable() {
        return !this.treasure;
    }

    /** 与 1.21.1 侧 JSON 的 {@code min_cost.base=1, per_level_above_first=11} 保持一致。 */
    @Override
    public int getMinCost(int level) {
        return 1 + Math.max(0, level - 1) * 11;
    }

    /** 与 1.21.1 侧 JSON 的 {@code max_cost} 保持一致。 */
    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 20;
    }
}
