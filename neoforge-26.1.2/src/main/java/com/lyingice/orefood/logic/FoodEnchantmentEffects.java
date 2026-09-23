package com.lyingice.orefood.logic;

import com.lyingice.orefood.FoodEnchantmentRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 「五个食物附魔分别会施加哪些状态效果」——纯计算，不碰世界（NeoForge 26.1.2）。
 *
 * <p>这个类被两处共用，从而保证<b>提示框显示的</b>与实际吃下去生效的完全一致：
 * <ul>
 *   <li>{@link OreFoodConsume}：真正把效果加到生物身上；</li>
 *   <li>{@code OreFoodTooltip}：把同样的效果渲染成原版药水那样的提示行。</li>
 * </ul>
 *
 * <p><b>26.1.2 改了四个效果常量的名字</b>（这是本文件与 1.21.1 侧唯一的差别，
 * 数值本身来自共享层 {@link FoodEnchantmentRules}，三个版本完全一致）：
 * <ul>
 *   <li>速度 {@code MOVEMENT_SPEED} → {@code SPEED}</li>
 *   <li>力量 {@code DAMAGE_BOOST} → {@code STRENGTH}</li>
 *   <li>瞬间治疗 {@code HEAL} → {@code INSTANT_HEALTH}</li>
 *   <li>抗性提升 {@code DAMAGE_RESISTANCE} → {@code RESISTANCE}</li>
 * </ul>
 * 生命恢复 / 生命吸收 / 饱和三个名字三个版本都一样。
 */
public final class FoodEnchantmentEffects {

    /**
     * 按各附魔等级算出应施加的效果。
     *
     * @param recoveryLevel   强效恢复等级，0 表示没有
     * @param adrenalineLevel 肾上腺素等级
     * @param fitnessLevel    强身健体等级
     * @param feastLevel      盛宴等级
     * @return 效果列表；没有附魔时返回空列表
     */
    public static List<MobEffectInstance> effectsFor(
            int recoveryLevel, int adrenalineLevel, int fitnessLevel, int feastLevel) {
        List<MobEffectInstance> effects = new ArrayList<>(8);

        if (recoveryLevel > 0) {
            int amplifier = FoodEnchantmentRules.potionAmplifier(recoveryLevel);
            // 瞬间治疗是瞬发效果，原版给它的持续时间就是 1 tick。
            effects.add(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 1, amplifier));
            effects.add(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    FoodEnchantmentRules.regenerationDuration(recoveryLevel), amplifier));
        }

        if (adrenalineLevel > 0) {
            int amplifier = FoodEnchantmentRules.potionAmplifier(adrenalineLevel);
            effects.add(new MobEffectInstance(
                    MobEffects.STRENGTH,
                    FoodEnchantmentRules.strengthDuration(adrenalineLevel), amplifier));
            effects.add(new MobEffectInstance(
                    MobEffects.SPEED,
                    FoodEnchantmentRules.speedDuration(adrenalineLevel), amplifier));
        }

        if (fitnessLevel > 0) {
            int duration = FoodEnchantmentRules.fitnessDuration(fitnessLevel);
            // 抗性提升固定 II 级（amplifier 1 = 40% 减伤），不随附魔等级变化。
            effects.add(new MobEffectInstance(
                    MobEffects.RESISTANCE, duration, FoodEnchantmentRules.RESISTANCE_AMPLIFIER));
            // 生命吸收跟随附魔等级。
            effects.add(new MobEffectInstance(
                    MobEffects.ABSORPTION, duration, FoodEnchantmentRules.potionAmplifier(fitnessLevel)));
        }

        if (feastLevel > 0) {
            effects.add(new MobEffectInstance(
                    MobEffects.SATURATION,
                    FoodEnchantmentRules.feastDuration(feastLevel),
                    FoodEnchantmentRules.potionAmplifier(feastLevel)));
        }

        return effects;
    }

    private FoodEnchantmentEffects() {
        throw new AssertionError("No instances.");
    }
}
