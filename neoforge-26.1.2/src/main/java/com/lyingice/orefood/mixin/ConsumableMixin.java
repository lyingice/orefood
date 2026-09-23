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
 */
@Mixin(Consumable.class)
public abstract class ConsumableMixin {

    private static final String ON_CONSUME_METHOD =
            "onConsume(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;";

    /** 在吃下的一刻结算营养、定义自带效果与食物附魔效果。 */
    @Inject(method = ON_CONSUME_METHOD, at = @At("HEAD"))
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
