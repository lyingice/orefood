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
 * 与 AppleSkin 的联动（NeoForge 26.1.2）：把「饱腹」附魔加成叠加进它的预览。
 *
 * <p>本模组注册时已把定义里的真实数值写进 {@link FoodProperties}，AppleSkin 读到的
 * 基础饥饿值/饱和度原生就是正确的；它唯一看不到的是食用时才结算的「饱腹」附魔加成。
 * 这里通过 AppleSkin 的 {@link FoodValuesEvent} 只在饱腹等级 &gt; 0 时叠加那份加成，
 * 并保留基础的 {@code canAlwaysEat}，使显示与实际食用一致。
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
                base.canAlwaysEat());
    }

    private OreFoodAppleSkinIntegration() {
    }
}
