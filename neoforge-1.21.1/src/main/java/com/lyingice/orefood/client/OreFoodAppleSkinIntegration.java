package com.lyingice.orefood.client;

import com.lyingice.orefood.FoodEnchantmentRules;
import com.lyingice.orefood.item.OreFoodItem;
import com.lyingice.orefood.logic.FoodEnchantmentLevels;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import squeek.appleskin.api.event.FoodValuesEvent;

/**
 * 与 AppleSkin 的联动（NeoForge 1.21.1）：让它显示本模组食物的真实恢复量。
 *
 * <p>基础数值由注册时的真实 {@link FoodProperties} 原生提供，本事件只在「饱腹」附魔
 * 等级 &gt; 0 时叠加加成预览（保留 {@code canAlwaysEat} / {@code eatSeconds} / {@code effects}
 * 等全部其它字段），使 AppleSkin 的显示与实际食用一致。
 *
 * <p>注意 3.x 的 AppleSkin 用 {@link FoodProperties} 而不是 1.20.1 的 {@code FoodValues}。
 *
 * <p><b>本类只在 AppleSkin 已安装时被加载</b>（见 {@link OreFoodAppleSkinSetup}）。
 */
public final class OreFoodAppleSkinIntegration {

    /** 由 {@link OreFoodAppleSkinSetup} 在确认 AppleSkin 存在后调用。 */
    public static void register() {
        NeoForge.EVENT_BUS.register(OreFoodAppleSkinIntegration.class);
        com.mojang.logging.LogUtils.getLogger().info(
                "[orefood] 检测到 AppleSkin：矿物食品的饥饿值与饱和度按其真实数值显示");
    }

    @SubscribeEvent
    public static void onFoodValues(FoodValuesEvent event) {
        ItemStack stack = event.itemStack;
        if (OreFoodItem.definitionOf(stack) == null) {
            return;
        }
        Player player = event.player;
        if (player == null) {
            return;
        }
        int bonus = FoodEnchantmentRules.satietyBonus(
                FoodEnchantmentLevels.of(stack, player.level().registryAccess()).satiety());
        if (bonus <= 0) {
            return;
        }
        FoodProperties base = stack.get(DataComponents.FOOD);
        if (base == null) {
            return;
        }
        event.modifiedFoodProperties = new FoodProperties(
                base.nutrition() + bonus,
                base.saturation() + bonus * 2.0F,
                base.canAlwaysEat(),
                base.eatSeconds(),
                base.usingConvertsTo(),
                base.effects());
    }

    private OreFoodAppleSkinIntegration() {
    }
}
