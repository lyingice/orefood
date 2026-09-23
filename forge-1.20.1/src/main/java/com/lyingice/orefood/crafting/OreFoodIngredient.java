package com.lyingice.orefood.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodItems;
import com.lyingice.orefood.item.OreFoodItem;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

/**
 * 「某一种特定的矿物牛排」——一个自定义配方配料（Forge 1.20.1）。
 *
 * <p><b>为什么必须自己写一个？</b>本模组只有<b>一个</b>物品
 * （{@code orefood:ore_food}），铜/铁/金/钻石/下界合金牛排全靠物品栈上的
 * {@code orefood} NBT 标签区分。而原版配料的匹配逻辑是
 * {@code itemstack.is(物品)} —— <b>只看物品类型，连 NBT 都不看</b>
 * （见 {@code Ingredient#test}）。所以用原版配料根本无法表达
 * 「中间那一格必须是铜牛排」：五种牛排物品 id 一模一样，配方会互相冲突。
 *
 * <p>数据包里的写法：
 * <pre>{@code
 * { "type": "orefood:ore_food", "definition": "orefood:copper_beef" }
 * }</pre>
 *
 * <p>与两个 NeoForge 平台的差异：那边实现 {@code ICustomIngredient} 接口，
 * 这边是继承 {@link AbstractIngredient} 并提供一个 {@link IIngredientSerializer}。
 */
public class OreFoodIngredient extends AbstractIngredient {

    private final ResourceLocation definition;

    public OreFoodIngredient(ResourceLocation definition) {
        this.definition = definition;
    }

    /** @return 本配料要求匹配的定义 id。 */
    public ResourceLocation definition() {
        return this.definition;
    }

    /** 只有物品栈带着「同样是这一种」的定义时才算匹配。 */
    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null) {
            return false;
        }
        OreFoodDefinition definition = OreFoodItem.definitionOf(stack);
        return definition != null && definition.hasId(this.definition);
    }

    /**
     * 展示用的物品栈（配方书 / JEI 之类会读它）。
     *
     * <p>这里返回的是<b>没有带定义</b>的裸物品，因为构造带定义的物品栈需要读注册表，
     * 而本方法拿不到 {@code RegistryAccess}。
     */
    @Override
    public ItemStack[] getItems() {
        return new ItemStack[] {new ItemStack(OreFoodItems.ORE_FOOD.get())};
    }

    /** 必须返回 {@code false}：本配料依赖 NBT 才能匹配，不能被简化成物品集合。 */
    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IIngredientSerializer<? extends Ingredient> getSerializer() {
        return OreFoodIngredients.SERIALIZER;
    }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", OreFoodConstants.MOD_ID + ":ore_food");
        json.addProperty("definition", this.definition.toString());
        return json;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OreFoodIngredient ingredient && ingredient.definition.equals(this.definition);
    }

    @Override
    public int hashCode() {
        return this.definition.hashCode();
    }
}
