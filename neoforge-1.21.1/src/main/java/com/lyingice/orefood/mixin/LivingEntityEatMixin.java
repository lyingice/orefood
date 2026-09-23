package com.lyingice.orefood.mixin;

import com.lyingice.orefood.logic.OreFoodConsume;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 食用矿物牛排时的两个改动（Minecraft 1.21.1）。
 *
 * <p>目标方法是 {@code LivingEntity#eat(Level, ItemStack, FoodProperties)}——
 * 三参重载是<b>非 final</b> 的，而二参重载 {@code eat(Level, ItemStack)} 是 final，
 * 注入后者会直接失败。
 *
 * <p>1.21.1 的食用链路：
 * <pre>
 *   Player#eat(Level, ItemStack, FoodProperties)      ← 玩家覆写：先 getFoodData().eat(...)
 *     └─ LivingEntity#eat(Level, ItemStack, FoodProperties)   ← 本 Mixin 的目标
 *          ├─ addEatEffect(foodProperties)            ← 原版食物自带效果
 *          ├─ food.consume(1, this)                   ← 消耗点 → 改成扣耐久
 *          └─ gameEvent(GameEvent.EAT)
 * </pre>
 *
 * <p>用的是 {@code @Inject(HEAD)} + {@code @Redirect} 而不是
 * {@code @Inject(cancellable = true)} 整体取消：整体取消会把音效、统计、
 * 原版食物效果与 {@code GameEvent.EAT} 一起丢掉。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEatMixin {

    private static final String EAT_METHOD =
            "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;";

    /** 在吃下的一刻结算营养、定义自带效果与食物附魔效果。 */
    @Inject(method = EAT_METHOD, at = @At("HEAD"))
    private void orefood$applyOreFoodEffects(
            Level level, ItemStack food, FoodProperties foodProperties,
            CallbackInfoReturnable<ItemStack> callback) {
        OreFoodConsume.onConsume((LivingEntity) (Object) this, food);
    }

    /** 把「消耗一份物品」换成「扣一点耐久」。 */
    @Redirect(
            method = EAT_METHOD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void orefood$consumeDurabilityInstead(ItemStack stack, int amount, LivingEntity entity) {
        OreFoodConsume.consume(stack, amount, entity);
    }
}
