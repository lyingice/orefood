package com.lyingice.orefood.client;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import com.lyingice.orefood.logic.FoodEnchantmentEffects;
import com.lyingice.orefood.logic.FoodEnchantmentLevels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 物品提示框：把「这块牛排吃下去会得到什么」按原版药水的样式画出来。
 *
 * <p>渲染直接复用原版的 {@link PotionContents#addPotionTooltip}，所以
 * 效果名、罗马数字等级（{@code potion.potency.<amplifier>}）与时长格式
 * 全都与原版药水一致，不需要自己准备任何语言键。
 *
 * <p>关于客户端隔离：本类只出现在 {@code Dist.CLIENT} 的
 * {@code @EventBusSubscriber} 上，服务端不会加载它。不过实际上这里用的
 * 全是双端都存在的类型（{@code ItemStack} / {@code Component} / {@code MobEffectInstance}），
 * 即使没有这层隔离也不会崩——真正的客户端专属类型（如 {@code Minecraft}）一个都没碰。
 */
@EventBusSubscriber(modid = OreFoodConstants.MOD_ID, value = Dist.CLIENT)
public final class OreFoodTooltip {

    /** 物品提示框的第 0 行永远是物品名，因此从 1 开始插就是「名字之后、附魔之前」。 */
    private static final int INSERT_AFTER_NAME = 1;

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        if (definition == null) {
            return;
        }

        FoodEnchantmentLevels levels = FoodEnchantmentLevels.of(stack, event.getContext().registries());

        List<MobEffectInstance> effects = new ArrayList<>();
        FoodEnchantmentEffects.effectsFor(stack)
                .forEach(effect -> effects.add(effect.instance()));
        // 数据包声明在牛排上的药水效果同样预览，保持「所见即所得」。
        definition.effects().forEach(spec -> effects.add(spec.toInstance()));

        if (effects.isEmpty()) {
            return;
        }

        List<Component> lines = new ArrayList<>(effects.size() + 2);
        PotionContents.addPotionTooltip(effects, lines::add, 1.0F, event.getContext().tickRate());
        event.getToolTip().addAll(INSERT_AFTER_NAME, lines);
    }

    private OreFoodTooltip() {
        throw new AssertionError("No instances.");
    }
}
