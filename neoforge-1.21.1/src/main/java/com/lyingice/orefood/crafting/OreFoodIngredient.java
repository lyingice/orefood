package com.lyingice.orefood.crafting;

import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodItems;
import com.lyingice.orefood.item.OreFoodItem;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * 「某一种特定的矿物牛排」——一个自定义配方配料（NeoForge 1.21.1）。
 *
 * <p><b>为什么必须自己写一个？</b>本模组只有<b>一个</b>物品
 * （{@code orefood:ore_food}），铜/铁/金/钻石/下界合金牛排全靠物品栈上的
 * {@code orefood:definition} 组件区分。而原版配料的匹配逻辑是
 * {@code itemstack.is(物品)} —— <b>只看物品类型，不看组件</b>
 * （1.20.1 甚至连 NBT 都不看）。所以用原版配料根本无法表达
 * 「中间那一格必须是铜牛排」：五种牛排物品 id 一模一样，
 * 配方会互相冲突、也无法阻止玩家用钻石牛排去合成铜牛排。
 *
 * <p>NeoForge 的 {@link ICustomIngredient} 正是为这种需求准备的。
 *
 * <p>数据包里的写法：
 * <pre>{@code
 * { "type": "orefood:ore_food", "definition": "orefood:copper_beef" }
 * }</pre>
 *
 * @param definition 要匹配的定义 id，对应数据包条目里的 {@code id} 字段
 */
public record OreFoodIngredient(ResourceLocation definition) implements ICustomIngredient {

    public static final MapCodec<OreFoodIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(OreFoodIngredient::definition)
    ).apply(instance, OreFoodIngredient::new));

    /** 只有物品栈带着「同样是这一种」的定义时才算匹配。 */
    @Override
    public boolean test(ItemStack stack) {
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        return definition != null && definition.hasId(this.definition);
    }

    /**
     * 展示用的物品栈（配方书 / JEI 之类会读它）。
     *
     * <p>这里返回的是<b>没有带定义</b>的裸物品，因为构造带定义的物品栈需要读注册表，
     * 而本方法拿不到 {@code RegistryAccess}。原版文档明确说这些栈「用于展示，
     * 不必详尽或完全精确」，所以可以接受。
     */
    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(new ItemStack(OreFoodItems.ORE_FOOD.get()));
    }

    /** 必须返回 {@code false}：本配料依赖组件才能匹配，不能让加载器把它简化成物品集合。 */
    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return OreFoodIngredients.ORE_FOOD.get();
    }
}
