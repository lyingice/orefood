package com.lyingice.orefood.logic;

import com.lyingice.orefood.FoodEnchantmentRules;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * 「吃下矿物牛排时到底发生了什么」——三件事都在这里（NeoForge 26.1.2）。
 *
 * <ol>
 *   <li><b>按定义加营养</b>：物品注册时的食物属性是零值（见 {@code OreFoodItems}），
 *       真正的饱食度/饱和度在这里按定义补上，并叠加「饱腹」附魔的额外饱食度；</li>
 *   <li><b>施加定义自带的药水效果</b>：来自数据包条目的 {@code effects} 字段；</li>
 *   <li><b>施加食物附魔效果</b>：委托给 {@link FoodEnchantmentEffects}。</li>
 * </ol>
 *
 * <p><b>26.1.2 的食用链路与 1.21.1 完全不同</b>——{@code LivingEntity#eat} 与
 * {@code Player#eat} 已经被整个删除，改由物品组件驱动：
 * <pre>
 *   Item#finishUsingItem
 *     └─ Consumable#onConsume(Level, LivingEntity, ItemStack)
 *          ├─ stack.getAllOfType(ConsumableListener.class).forEach(...)   ← FoodProperties 在这里加饱食度
 *          ├─ onConsumeEffects.forEach(...)
 *          └─ stack.consume(1, user)                                     ← 消耗点 → 改成扣耐久
 * </pre>
 * 所以本模组的 Mixin 也换了目标（{@code ConsumableMixin}）。
 * 饱食度这边仍然是「原版先加 0、我们再补」的路子：
 * {@code FoodProperties#onConsume} 只会把注册时那个零值加进去。
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
     * <p>26.1.2 的 {@code FoodProperties} 只剩下 {@code (nutrition, saturation, canAlwaysEat)}
     * 三个字段，没有饱和度系数，所以饱和度可以直接给出——比 1.20.1 那边省掉了反推。
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

        player.getFoodData().eat(new FoodProperties(nutrition, saturation, true));
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
     * <p>这是 Mixin 在 {@code Consumable#onConsume} 里重定向 {@code ItemStack#consume} 的落点。
     *
     * <p>这个方法是 Mixin 在 {@code Consumable#onConsume} 里重定向
     * {@code ItemStack#consume} 的落点，而那个调用点对<b>所有</b>可食用物品都会经过。
     * 判断依据是 {@code orefood:definition} 组件：取不到定义就原样交还原版，
     * 所以原版食物与其它模组的物品行为完全不变。
     *
     * <p><b>耐久结算完全交给原版 {@link ItemStack#hurtAndBreak}，不自己算。</b>
     * 自己写「伤害 +1、到顶就 shrink」看着等价，实际会丢掉原版耐久子系统的全部内容：
     * <ul>
     *   <li><b>耐久附魔</b>——唯一的判定点在 {@code ItemStack#processDurabilityChange}
     *       （ItemStack.java:485-491）里调用的
     *       {@code EnchantmentHelper.processDurabilityChange}。绕开它就等于让
     *       耐久 I/II/III 完全失效，而附魔台上花掉的等级买到的只是个装饰；</li>
     *   <li>创造模式的「不消耗」守卫；</li>
     *   <li>耐久变化进度触发器；</li>
     *   <li>耐久耗尽时的 {@code shrink} 与破坏回调。</li>
     * </ul>
     *
     * <p>定义里 {@code durability == 0} 表示<b>没有耐久设定</b>：既不消耗耐久，
     * 也<b>不消耗本体</b>——可以理解成「用不掉的矿物食物」，参考马铠
     * （一个不设耐久的物品，装备它不会损耗它）。因为伤害值恒为 0，
     * 这种物品也不会显示耐久条或「耐久 x/y」提示行（原版只在 {@code isDamaged()} 时才画）。
     *
     * @param stack  物品栈
     * @param amount 原版打算消耗的数量（通常是 1）
     * @param entity 食用者，可能为 null
     */
    public static void consume(ItemStack stack, int amount, LivingEntity entity) {
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null) {
            // 不是本模组的食物：原样交还原版。
            stack.consume(amount, entity);
            return;
        }
        if (definition.infiniteDurability()) {
            // 没有耐久设定：吃多少次都不掉。
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
