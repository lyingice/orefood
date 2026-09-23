package com.lyingice.orefood.logic;

import com.lyingice.orefood.FoodEnchantmentRules;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 「吃下矿物牛排时到底发生了什么」——三件事都在这里（Forge 1.20.1）。
 *
 * <ol>
 *   <li><b>按定义加营养</b>：物品注册时的食物属性是零值（见 {@code OreFoodItems}），
 *       真正的饱食度/饱和度在这里按定义补上，并叠加「饱腹」附魔的额外饱食度；</li>
 *   <li><b>施加定义自带的药水效果</b>：来自数据包条目的 {@code effects} 字段；</li>
 *   <li><b>施加食物附魔效果</b>：委托给 {@link FoodEnchantmentEffects}，
 *       与提示框共用同一份计算。</li>
 * </ol>
 *
 * <p><b>1.20.1 与 1.21.1 的两处实质差异</b>：
 * <ul>
 *   <li>{@code level.isClientSide} 在 1.20.1 是<b>字段</b>，1.21.1 起改成了方法
 *       {@code level.isClientSide()}；</li>
 *   <li>1.20.1 的 {@code FoodData#eat(int, float)} 第二个参数是<b>饱和度系数</b>
 *       （最终饱和度 = 饱食度 × 系数 × 2），不是饱和度本身，所以这里要做一次反推。</li>
 * </ul>
 */
public final class OreFoodConsume {

    /**
     * 食用结算。必须在<b>服务端</b>调用（客户端调用会被本方法自行挡掉）。
     *
     * <p><b>只处理本模组的物品</b>：Mixin 挂在原版的吃入口上，那个入口对
     * <i>所有</i>可食用物品都会走一遍，因此这里必须先按物品类型挡一道，
     * 否则原版食物（乃至其它模组的食物）也会被本模组的逻辑碰到。
     *
     * @param entity 吃下食物的生物
     * @param stack  被吃的那一份物品栈
     */
    public static void onConsume(LivingEntity entity, ItemStack stack) {
        if (!(stack.getItem() instanceof OreFoodItem)) {
            return;
        }
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null || entity.level().isClientSide) {
            return;
        }

        FoodEnchantmentLevels levels = FoodEnchantmentLevels.of(stack);

        applySatiety(entity, definition, levels);
        applyDefinitionEffects(entity, definition);
        for (MobEffectInstance effect : FoodEnchantmentEffects.effectsFor(
                levels.recovery(), levels.adrenaline(), levels.fitness(), levels.feast())) {
            entity.addEffect(effect);
        }
    }

    /**
     * 按定义补上饱食度与饱和度，并叠加「饱腹」附魔。
     *
     * <p>{@code FoodData#eat(int foodLevel, float saturationModifier)} 里饱和度是
     * {@code saturationLevel += foodLevel * saturationModifier * 2}，
     * 所以想要精确的饱和度 S，系数得写成 {@code S / (foodLevel * 2)}。
     */
    private static void applySatiety(
            LivingEntity entity, OreFoodDefinition definition, FoodEnchantmentLevels levels) {
        if (!(entity instanceof Player player)) {
            // 只有玩家有饥饿条；其它生物吃食物不加营养（与原版一致）。
            return;
        }

        int nutrition = definition.nutrition() + FoodEnchantmentRules.satietyBonus(levels.satiety());
        float saturation = definition.saturation() + FoodEnchantmentRules.satietyBonusSaturation(levels.satiety());

        if (nutrition <= 0 && saturation <= 0.0F) {
            return;
        }

        float saturationModifier = nutrition > 0 ? saturation / (nutrition * 2.0F) : 0.0F;
        player.getFoodData().eat(nutrition, saturationModifier);
    }

    /** 施加数据包条目里声明的药水效果。 */
    private static void applyDefinitionEffects(LivingEntity entity, OreFoodDefinition definition) {
        for (PotionEffectSpec spec : definition.effects()) {
            if (spec.probability() >= 1.0F || entity.getRandom().nextFloat() < spec.probability()) {
                entity.addEffect(spec.toInstance());
            }
        }
    }

    /**
     * 消费物品栈——矿物牛排改为<b>扣耐久</b>而不是消失。
     *
     * <p>这是 Mixin 在 {@code LivingEntity#eat} 里重定向 {@code ItemStack#shrink} 的落点。
     *
     * <p><b>只对本模组的物品生效</b>：这个方法是 Mixin 在
     * {@code LivingEntity#eat} 里重定向 {@code ItemStack#shrink} 的落点，
     * 而那个调用点对<b>所有</b>可食用物品都会经过——所以第一件事就是按物品类型挡掉
     * 非本模组的物品，原样交还给原版，绝不波及原版食物或其它模组的物品。
     *
     * <p>其次，扣耐久直接用 {@code ItemStack#hurtAndBreak} 而不是自己改 damage：
     * 它内部已经处理好了「创造模式不消耗」「不可损坏物品」「耐久归零触发 onBroken
     * 并减 1 个」这几件事，自己实现反而容易漏掉。注意它只在服务端生效，
     * 这对我们是好事——消耗本来就该服务端权威。
     *
     * @param stack  物品栈
     * @param amount 原版打算消耗的数量（通常是 1）
     * @param entity 食用者，可能为 null
     */
    public static void consume(ItemStack stack, int amount, LivingEntity entity) {
        if (!(stack.getItem() instanceof OreFoodItem)) {
            // 不是本模组的物品：完全交还原版，行为与没装这个模组时一模一样。
            stack.shrink(amount);
            return;
        }
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null || definition.infiniteDurability()) {
            stack.shrink(amount);
            return;
        }
        if (entity == null) {
            stack.shrink(amount);
            return;
        }
        stack.hurtAndBreak(amount, entity, broken -> { });
    }

    private OreFoodConsume() {
        throw new AssertionError("No instances.");
    }
}
