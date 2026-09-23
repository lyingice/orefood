package com.lyingice.orefood.crafting;

import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodItems;
import com.lyingice.orefood.item.OreFoodItem;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

/**
 * 「某一种特定的矿物牛排」——一个自定义配方配料（NeoForge 26.1.2）。
 *
 * <p><b>为什么必须自己写一个？</b>本模组只有<b>一个</b>物品
 * （{@code orefood:ore_food}），铜/铁/金/钻石/下界合金牛排全靠物品栈上的
 * {@code orefood:definition} 组件区分。而原版配料的匹配逻辑是
 * {@code itemstack.is(物品)} —— <b>只看物品类型，不看组件</b>。
 * 所以用原版配料根本无法表达「中间那一格必须是铜牛排」。
 *
 * <p>数据包里的写法：
 * <pre>{@code
 * { "type": "orefood:ore_food", "definition": "orefood:copper_beef" }
 * }</pre>
 *
 * @param definition 要匹配的定义 id，对应数据包条目里的 {@code id} 字段
 */
public record OreFoodIngredient(Identifier definition) implements ICustomIngredient {

    public static final MapCodec<OreFoodIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("definition").forGetter(OreFoodIngredient::definition)
    ).apply(instance, OreFoodIngredient::new));

    /** 只有物品栈带着「同样是这一种」的定义时才算匹配。 */
    @Override
    public boolean test(ItemStack stack) {
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        return definition != null && definition.hasId(this.definition);
    }

    /**
     * 本配料可能接受的物品（配方书 / JEI 之类会读它）。
     *
     * <p>只返回物品本身、不含具体是哪一种牛排，因为构造带定义的物品栈需要读注册表，
     * 而本方法拿不到 {@code RegistryAccess}。原版文档明确说返回值「不必完全精确」。
     *
     * <p>注意 26.1.2 这个方法叫 {@code items()} 且返回 {@code Stream<Holder<Item>>}；
     * 1.21.1 那边叫 {@code getItems()} 且返回 {@code Stream<ItemStack>}。
     */
    @Override
    public Stream<Holder<Item>> items() {
        return Stream.of(OreFoodItems.ORE_FOOD.get().builtInRegistryHolder());
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
