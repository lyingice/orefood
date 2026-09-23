package com.lyingice.orefood.logic;

import com.lyingice.orefood.FoodEnchantmentRules;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
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
     * <p><b>只对本模组的物品生效</b>：这个方法是 Mixin 在
     * {@code Consumable#onConsume} 里重定向 {@code ItemStack#consume} 的落点，
     * 而那个调用点对<b>所有</b>可食用物品都会经过——所以第一件事就是按物品类型挡掉
     * 非本模组的物品，原样交还给原版，绝不波及原版食物或其它模组的物品。
     *
     * <p>其次两个注意点：
     * <ul>
     *   <li>原版的「创造模式不消耗」守卫在 {@code ItemStack#consume} 内部，
     *       一旦被重定向就绕过了，所以我们自己这条分支必须补一次
     *       {@code hasInfiniteMaterials()}；</li>
     *   <li>定义里 {@code durability == 0} 表示无限耐久，走原版路径（吃多少次都不掉）。</li>
     * </ul>
     *
     * @param stack  物品栈
     * @param amount 原版打算消耗的数量（通常是 1）
     * @param entity 食用者，可能为 null
     */
    public static void consume(ItemStack stack, int amount, LivingEntity entity) {
        if (!(stack.getItem() instanceof OreFoodItem)) {
            // 不是本模组的物品：完全交还原版，行为与没装这个模组时一模一样。
            stack.consume(amount, entity);
            return;
        }
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null || definition.infiniteDurability()) {
            stack.consume(amount, entity);
            return;
        }
        if (entity != null && entity.hasInfiniteMaterials()) {
            return;
        }

        int newDamage = stack.getDamageValue() + amount;
        if (newDamage >= stack.getMaxDamage()) {
            // 耐久耗尽：这一份吃掉就没了。
            stack.shrink(amount);
        } else {
            stack.setDamageValue(newDamage);
        }
    }

    private OreFoodConsume() {
        throw new AssertionError("No instances.");
    }
}
