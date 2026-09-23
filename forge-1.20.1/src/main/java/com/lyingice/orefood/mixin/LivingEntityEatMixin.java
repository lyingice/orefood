package com.lyingice.orefood.mixin;

import com.lyingice.orefood.logic.OreFoodConsume;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 食用矿物牛排时的两个改动（Forge 1.20.1）。
 *
 * <p>1.20.1 的食用链路比 1.21.1 简单一档——没有三参重载，也没有 {@code Consumable} 组件：
 * <pre>
 *   Player#eat(Level, ItemStack)                 ← 玩家覆写：先 getFoodData().eat(item, stack, this)
 *     └─ LivingEntity#eat(Level, ItemStack)      ← 本 Mixin 的目标
 *          ├─ addEatEffect(stack, level, this)   ← 原版食物自带效果
 *          ├─ stack.shrink(1)                    ← 消耗点 → 改成扣耐久
 *          └─ gameEvent(GameEvent.EAT)
 * </pre>
 *
 * <p>注意 {@code LivingEntity#eat} 的签名只有两个参数，而且消耗点用的是
 * {@code ItemStack#shrink(int)}——1.21.1 换成了 {@code ItemStack#consume(int, LivingEntity)}，
 * 26.1.2 更是整个链路搬进了 {@code Consumable} 组件。这是三个版本 Mixin 无法共用一份代码的原因。
 *
 * <p>用 {@code @Inject(HEAD)} + {@code @Redirect} 而不是 {@code @Inject(cancellable = true)}
 * 整体取消：整体取消会把音效、统计、原版食物效果与 {@code GameEvent.EAT} 一起丢掉。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEatMixin {

    private static final String EAT_METHOD =
            "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)"
                    + "Lnet/minecraft/world/item/ItemStack;";

    /** 在吃下的一刻结算营养、定义自带效果与食物附魔效果。 */
    @Inject(method = EAT_METHOD, at = @At("HEAD"))
    private void orefood$applyOreFoodEffects(
            Level level, ItemStack food, CallbackInfoReturnable<ItemStack> callback) {
        OreFoodConsume.onConsume((LivingEntity) (Object) this, food);
    }

    /** 把「消耗一份物品」换成「扣一点耐久」。 */
    @Redirect(
            method = EAT_METHOD,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void orefood$consumeDurabilityInstead(ItemStack stack, int amount) {
        OreFoodConsume.consume(stack, amount, (LivingEntity) (Object) this);
    }
}
