package com.lyingice.orefood.mixin;

import com.lyingice.orefood.logic.OreFoodConsume;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 食用矿物牛排时的两个改动（NeoForge 26.1.2）。
 *
 * <p><b>这个 Mixin 的目标与另外两个版本完全不同，无法共用代码。</b>
 * 26.1.2 把食用流程整个搬进了物品组件 {@link Consumable}，
 * {@code LivingEntity#eat} 与 {@code Player#eat} 都已不复存在：
 * <pre>
 *   Item#finishUsingItem
 *     └─ Consumable#onConsume(Level, LivingEntity, ItemStack)   ← 本 Mixin 的目标
 *          ├─ stack.getAllOfType(ConsumableListener.class)      ← FoodProperties 在这里加饱食度
 *          ├─ onConsumeEffects.forEach(...)                     ← 物品自带的食用效果
 *          ├─ user.gameEvent(...)
 *          └─ stack.consume(1, user)                            ← 消耗点 → 改成扣耐久
 * </pre>
 *
 * <p>另外注意 26.1.2 有一个 1.21.1 没有的坑：当 {@code consume_seconds} 为 0 时，
 * {@code Consumable#startConsuming} 会<b>立刻</b>调用 {@code onConsume}，
 * 而那条路径在客户端也会走。本模组的牛排是 1.6 秒的定时食用，
 * 且 {@code OreFoodConsume#onConsume} 内部已自行判端，因此不受影响。
 *
 * <p><b>为什么不能再注入 HEAD</b>：注册时已经写入真实的 {@code FoodProperties}，
 * 而基础营养是在方法体内的 {@code stack.getAllOfType(ConsumableListener.class)}
 * 阶段才加上的。若仍在 HEAD 结算「饱腹」附魔加成，它会先于基础值进入
 * {@code FoodData#eat}——饱和度会被 clamp 到当时的 {@code foodLevel}，
 * 接近满饱食度时结果与另外两个平台不一致。因此注入点改到
 * {@code stack.consume(1, user)} 调用之前：此时基础营养已加、物品栈尚未被消耗，
 * 附魔等级仍然读得到；即便这一口恰好把物品吃坏，加成也已经先结算完毕。
 */
@Mixin(Consumable.class)
public abstract class ConsumableMixin {

    private static final String ON_CONSUME_METHOD =
            "onConsume(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;";

    /**
     * 在基础营养结算之后、物品栈被消耗之前，叠加「饱腹」附魔加成与定义自带效果。
     *
     * <p>注入点是 {@code Consumable#onConsume} 里唯一的 {@code ItemStack#consume} 调用
     * （默认 shift 为 BEFORE）。选择它而不是 HEAD，是为了让
     * {@code FoodProperties#onConsume}（{@code ConsumableListener}）先把基础营养加进去，
     * 附魔加成再叠加——顺序影响饱和度 clamp 到 {@code foodLevel} 的结果。
     *
     * <p>{@code CallbackInfoReturnable} 参数<b>必须保留</b>，即使这里完全不使用它：
     * Mixin 会校验处理器描述符，目标方法有返回值时缺了这个参数会直接抛
     * {@code InvalidInjectionException: Invalid descriptor ...}，
     * 而且是<b>运行期</b>才炸（编译期看不出来）。
     */
    @Inject(method = ON_CONSUME_METHOD,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void orefood$applyOreFoodEffects(
            Level level, LivingEntity user, ItemStack stack,
            CallbackInfoReturnable<ItemStack> callback) {
        OreFoodConsume.onConsume(user, stack);
    }

    /** 把「消耗一份物品」换成「扣一点耐久」。 */
    @Redirect(
            method = ON_CONSUME_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void orefood$consumeDurabilityInstead(ItemStack stack, int amount, LivingEntity entity) {
        OreFoodConsume.consume(stack, amount, entity);
    }
}
