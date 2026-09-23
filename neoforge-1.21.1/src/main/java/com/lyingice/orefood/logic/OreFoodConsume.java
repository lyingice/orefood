package com.lyingice.orefood.logic;

import com.lyingice.orefood.FoodEnchantmentRules;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * 「吃下矿物牛排时到底发生了什么」——三件事都在这里。
 *
 * <ol>
 *   <li><b>按定义加营养</b>：物品注册时的食物属性是零值（见 {@code OreFoodItems}），
 *       真正的饱食度/饱和度在这里按定义补上，并叠加「饱腹」附魔的额外饱食度；</li>
 *   <li><b>施加定义自带的药水效果</b>：来自数据包条目的 {@code effects} 字段；</li>
 *   <li><b>施加食物附魔效果</b>：委托给 {@link FoodEnchantmentEffects}，
 *       与提示框共用同一份计算。</li>
 * </ol>
 *
 * <p><b>为什么不用纯数据驱动？</b>1.21.1 的 {@code EnchantmentEffectComponents} 共 30 个组件，
 * 但<b>没有任何一个</b>以「物品被吃下」为触发点（最接近的 {@code minecraft:tick} 只扫装备槽位）。
 * 所以「吃下时生效」这件事三个版本都只能写代码。
 */
public final class OreFoodConsume {

    /**
     * 食用结算。必须在<b>服务端</b>调用（客户端调用会被本方法自行挡掉）。
     *
     * <p>Mixin 挂在原版的吃入口上，那个入口对<i>所有</i>可食用物品都会走一遍；
     * 这里靠 {@code orefood:definition} 组件是否存在来判断「是不是本模组的食物」——
     * 取不到定义就直接返回，原版食物与其它模组的物品因此完全不受影响。
     *
     * @param entity 吃下食物的生物
     * @param stack  被吃的那一份物品栈
     */
    public static void onConsume(LivingEntity entity, ItemStack stack) {
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null || entity.level().isClientSide()) {
            return;
        }

        FoodEnchantmentLevels levels = FoodEnchantmentLevels.of(stack, entity.level().registryAccess());

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
     * <p>原版 {@code FoodData} 只暴露 {@code eat(FoodProperties)}，没有直接加数值的公开方法，
     * 所以这里造一个临时 {@code FoodProperties} 传进去——比反射或注入私有方法稳。
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

        player.getFoodData().eat(new FoodProperties(
                nutrition, saturation, true, 1.6F, Optional.empty(), List.of()));
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
     * <p>这是 Mixin 在 {@code LivingEntity#eat} 里重定向 {@code ItemStack#consume} 的落点，
     * 而那个调用点对<b>所有</b>可食用物品都会经过。判断依据是 {@code orefood:definition}
     * 组件：取不到定义就原样交还原版，所以原版食物与其它模组的物品行为完全不变。
     *
     * <p><b>耐久结算完全交给原版 {@link ItemStack#hurtAndBreak}，不自己算。</b>
     * 自己写「伤害 +1、到顶就 shrink」看着等价，实际会丢掉原版耐久子系统的全部内容：
     * <ul>
     *   <li><b>耐久附魔</b>——唯一的判定点在 {@code hurtAndBreak} 内部调用的
     *       {@code EnchantmentHelper.processDurabilityChange}。绕开它就等于让
     *       耐久 I/II/III 完全失效，而附魔台上花掉的等级买到的只是个装饰；</li>
     *   <li>创造模式的「不消耗」守卫（{@code hasInfiniteMaterials()}）；</li>
     *   <li>耐久变化进度触发器 {@code ITEM_DURABILITY_CHANGED}；</li>
     *   <li>耐久耗尽时的 {@code shrink} 与破坏回调。</li>
     * </ul>
     *
     * <p>定义里 {@code durability == 0} 表示<b>无限耐久</b>，此时走原版路径
     * （按普通食物消耗掉）——与 1.20.1 / 26.1.2 两个平台保持一致。
     *
     * @param stack  物品栈
     * @param amount 原版打算消耗的数量（通常是 1）
     * @param entity 食用者，可能为 null
     */
    public static void consume(ItemStack stack, int amount, LivingEntity entity) {
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null || definition.infiniteDurability()) {
            // 不是本模组的食物，或者声明了无限耐久：原样交还原版。
            stack.consume(amount, entity);
            return;
        }
        if (!(entity != null && entity.level() instanceof ServerLevel level)) {
            // 耐久是权威状态，只在服务端结算。
            return;
        }

        // 破坏回调在原版是「通知装备槽该物品坏了」；食物没有装备槽，所以是空实现。
        stack.hurtAndBreak(amount, level, entity, broken -> {});
    }

    private OreFoodConsume() {
        throw new AssertionError("No instances.");
    }
}
