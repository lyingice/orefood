package com.lyingice.orefood;

/**
 * 食物附魔的数值规则——<b>纯函数，无任何游戏类型依赖</b>。
 *
 * <p>三个平台的「吃下食物时施加什么效果」逻辑都调用这里，
 * 保证同一附魔在三个版本上的数值完全一致。改动数值只改这一处。
 *
 * <p>约定：{@code level} 一律指<b>附魔等级</b>（1..maxLevel），不是药水 amplifier。
 * 两者的换算见 {@link #potionAmplifier(int)}。
 */
public final class FoodEnchantmentRules {

    /**
     * 抗性提升的固定 amplifier。
     *
     * <p>原版抗性提升每级减免 20% 伤害，因此想要「40% 抗性」必须用 amplifier 1
     * （游戏内显示为「抗性提升 II」）。这个值不随附魔等级变化。
     */
    public static final int RESISTANCE_AMPLIFIER = 1;

    /**
     * 附魔等级 → 药水 amplifier。
     *
     * <p>附魔 I 级对应 amplifier 0（显示为「I 级」）。游戏内显示的罗马数字
     * 来自 {@code potion.potency.<amplifier>}，所以 amplifier 0 就是 I 级。
     *
     * @param enchantmentLevel 附魔等级，1..maxLevel
     * @return 药水 amplifier，0..maxLevel-1
     */
    public static int potionAmplifier(int enchantmentLevel) {
        return Math.max(0, enchantmentLevel - 1);
    }

    /** 强效恢复：生命恢复时长（tick）= 等级 × 100。 */
    public static int regenerationDuration(int enchantmentLevel) {
        return enchantmentLevel * 100;
    }

    /** 肾上腺素：力量时长（tick）= 等级 × 100。 */
    public static int strengthDuration(int enchantmentLevel) {
        return enchantmentLevel * 100;
    }

    /** 肾上腺素：速度时长（tick）= 等级 × 200。 */
    public static int speedDuration(int enchantmentLevel) {
        return enchantmentLevel * 200;
    }

    /** 强身健体：生命吸收与抗性提升的时长（tick）= 等级 × 400。 */
    public static int fitnessDuration(int enchantmentLevel) {
        return enchantmentLevel * 400;
    }

    /**
     * 饱腹：在原版食物营养之外<b>追加</b>的饱食度点数 = 等级 × 2。
     *
     * <p>注意原版饱和度的计算方式是 {@code saturation += nutrition * saturationModifier * 2}，
     * 本模组按「追加 n×2 点饱食度」的语义实现：饱食度 +n×2，
     * 同时按相同比例补上饱和度（见 {@link #satietyBonusSaturation(int)}）。
     */
    public static int satietyBonus(int enchantmentLevel) {
        return enchantmentLevel * 2;
    }

    /**
     * 饱腹追加的饱食度所对应的饱和度。
     *
     * <p>取与原版一致的换算：饱和度 = 饱食度 × 1.0 × 2。
     */
    public static float satietyBonusSaturation(int enchantmentLevel) {
        return satietyBonus(enchantmentLevel) * 2.0F;
    }

    /** 盛宴：饱和效果时长（tick）= 等级 × 1 分钟（1200 tick）。 */
    public static int feastDuration(int enchantmentLevel) {
        return enchantmentLevel * 20 * 60;
    }

    private FoodEnchantmentRules() {
        throw new AssertionError("No instances.");
    }
}
