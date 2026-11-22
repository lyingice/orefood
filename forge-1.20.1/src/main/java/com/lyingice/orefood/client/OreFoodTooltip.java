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
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


/**
 * 物品提示框：把「这块牛排吃下去会得到什么」按原版药水的样式画出来（Forge 1.20.1）。
 *
 * <p>渲染直接复用原版的 {@link PotionUtils#addPotionTooltip}，所以效果名、罗马数字等级
 * （{@code potion.potency.<amplifier>}）与时长格式全都与原版药水一致，不需要任何自定义语言键。
 *
 * <p>与 1.21.1 侧的差异：
 * <ul>
 *   <li>本类在 {@code net.minecraftforge.event.entity.player} 下，
 *       NeoForge 是 {@code net.neoforged.neoforge.event.entity.player}；</li>
 *   <li>1.20.1 的构造器没有 {@code Item.TooltipContext}，因此也没有 {@code tickRate()}
 *       —— 时长按 20 tick/秒的默认值格式化；</li>
 *   <li>{@code addPotionTooltip} 是「追加到列表末尾」，所以先放进临时列表再 {@code addAll(1, ...)} 插入。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(
        modid = OreFoodConstants.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
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

        FoodEnchantmentLevels levels = FoodEnchantmentLevels.of(stack);

        List<MobEffectInstance> effects = new ArrayList<>(FoodEnchantmentEffects.effectsFor(
                levels.recovery(), levels.adrenaline(), levels.fitness(), levels.feast()));
        // 数据包声明在牛排上的药水效果同样预览，保持「所见即所得」。
        definition.effects().forEach(spec -> effects.add(spec.toInstance()));

        if (effects.isEmpty()) {
            return;
        }

        List<Component> lines = new ArrayList<>(effects.size() + 2);
        PotionUtils.addPotionTooltip(effects, lines, 1.0F);
        event.getToolTip().addAll(INSERT_AFTER_NAME, lines);
    }

    private OreFoodTooltip() {
        throw new AssertionError("No instances.");
    }
}
