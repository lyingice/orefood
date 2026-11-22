package com.lyingice.orefood.client;

import com.lyingice.orefood.FoodEnchantmentRules;
import com.lyingice.orefood.item.OreFoodItem;
import com.lyingice.orefood.logic.FoodEnchantmentLevels;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import squeek.appleskin.api.event.FoodValuesEvent;
import squeek.appleskin.api.food.FoodValues;

/**
 * 与 AppleSkin 的联动（Forge 1.20.1）：让它显示本模组食物的真实恢复量。
 *
 * <p>基础数值现在由注册时的真实 {@code FoodProperties} 原生提供（AppleSkin 直接读得到），
 * 本事件只补「饱腹」附魔的<b>加成预览</b>：食用时基础营养由原版结算，饱腹加成再由
 * {@code OreFoodConsume} 叠加，这里按同一套规则把两者合起来显示，使预览与实际食用一致。
 *
 * <p><b>本类只在 AppleSkin 已安装时被加载</b>（见 {@link OreFoodAppleSkinSetup}）。
 */
public final class OreFoodAppleSkinIntegration {

    /** 由 {@link OreFoodAppleSkinSetup} 在确认 AppleSkin 存在后调用。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(OreFoodAppleSkinIntegration.class);
        com.mojang.logging.LogUtils.getLogger().info(
                "[orefood] 检测到 AppleSkin：矿物食品的饥饿值与饱和度按其真实数值显示");
    }

    @SubscribeEvent
    public static void onFoodValues(FoodValuesEvent event) {
        ItemStack stack = event.itemStack;
        if (OreFoodItem.definitionOf(stack) == null) {
            return;
        }
        int bonus = FoodEnchantmentRules.satietyBonus(FoodEnchantmentLevels.of(stack).satiety());
        if (bonus <= 0) {
            return;
        }
        FoodProperties base = stack.getFoodProperties(null);
        if (base == null) {
            return;
        }
        int nutrition = base.getNutrition() + bonus;
        float saturation = base.getNutrition() * base.getSaturationModifier() * 2.0F + bonus * 2.0F;
        event.modifiedFoodValues = new FoodValues(
                nutrition, nutrition > 0 ? saturation / (nutrition * 2.0F) : 0.0F);
    }

    private OreFoodAppleSkinIntegration() {
    }
}
