package com.lyingice.orefood.logic;

import com.lyingice.orefood.FoodEnchantmentRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * 「五个食物附魔分别会施加哪些状态效果」——<b>硬编码</b>（Forge 1.20.1）。
 *
 * <p>1.20.1 的附魔本身就是代码注册的，也没有 1.21 那套附魔效果组件，
 * 所以这里同样直接写死，不引入数据包——与那个版本的附魔体系保持一致。
 * <b>只有 1.21.1 / 26.1.2 是数据包驱动</b>（各自注册附魔效果组件）。
 *
 * <p>数值全部来自共享层 {@link FoodEnchantmentRules}，与另外两个平台一致。
 * 本类被两处共用，从而保证<b>提示框显示的</b>与实际吃下去生效的完全一致：
 * <ul>
 *   <li>{@link OreFoodConsume}：真正把效果加到生物身上；</li>
 *   <li>{@code OreFoodTooltip}：把同样的效果渲染成原版药水那样的提示行。</li>
 * </ul>
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
            effects.add(new MobEffectInstance(MobEffects.HEAL, 1, amplifier));
            effects.add(new MobEffectInstance(
                    MobEffects.REGENERATION,
                    FoodEnchantmentRules.regenerationDuration(recoveryLevel),
                    amplifier));
        }

        if (adrenalineLevel > 0) {
            int amplifier = FoodEnchantmentRules.potionAmplifier(adrenalineLevel);
            effects.add(new MobEffectInstance(
                    MobEffects.DAMAGE_BOOST,
                    FoodEnchantmentRules.strengthDuration(adrenalineLevel),
                    amplifier));
            effects.add(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    FoodEnchantmentRules.speedDuration(adrenalineLevel),
                    amplifier));
        }

        if (fitnessLevel > 0) {
            int duration = FoodEnchantmentRules.fitnessDuration(fitnessLevel);
            // 抗性提升固定 II 级（amplifier 1 = 40% 减伤），不随附魔等级变化。
            effects.add(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE, duration, FoodEnchantmentRules.RESISTANCE_AMPLIFIER));
            // 生命吸收跟随附魔等级。
            effects.add(new MobEffectInstance(
                    MobEffects.ABSORPTION, duration, FoodEnchantmentRules.potionAmplifier(fitnessLevel)));
        }

        if (feastLevel > 0) {
            // 饱和效果固定 0 级，只随等级延长时长。
            effects.add(new MobEffectInstance(
                    MobEffects.SATURATION, FoodEnchantmentRules.feastDuration(feastLevel), 0));
        }

        return List.copyOf(effects);
    }

    private FoodEnchantmentEffects() {
        throw new AssertionError("No instances.");
    }
}
