package com.lyingice.orefood.client;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import com.lyingice.orefood.logic.FoodEnchantmentEffects;
import com.lyingice.orefood.logic.FoodEnchantmentLevels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 物品提示框：把「这块牛排吃下去会得到什么」按原版药水的样式画出来（NeoForge 26.1.2）。
 *
 * <p>事件类名与包名和 1.21.1 <b>完全一致</b>（{@code ItemTooltipEvent} 在 26.1.2 没有改名），
 * 渲染同样复用原版的 {@link PotionContents#addPotionTooltip}。
 *
 * <p>26.1.2 的一个坑：{@code Item.TooltipContext#registries()} 被标注为
 * <b>可为 null</b>（某些脱离世界的渲染场景，例如 {@code ItemStack} 的默认组件预览）。
 * 拿不到注册表时读不出附魔等级，此时直接放弃渲染，而不是抛 NPE。
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

        HolderLookup.Provider registries = event.getContext().registries();
        if (registries == null) {
            return;
        }

        FoodEnchantmentLevels levels = FoodEnchantmentLevels.of(stack, registries);

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
