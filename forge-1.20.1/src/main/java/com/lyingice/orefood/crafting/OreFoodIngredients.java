package com.lyingice.orefood.crafting;

import com.google.gson.JsonObject;
import com.lyingice.orefood.OreFoodConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;

/**
 * 注册本模组的自定义配料类型（Forge 1.20.1）。
 *
 * <p>注册名 {@code orefood:ore_food} 就是数据包里写的 {@code "type"}：
 * <pre>{@code
 * { "type": "orefood:ore_food", "definition": "orefood:copper_beef" }
 * }</pre>
 *
 * <p>与两个 NeoForge 平台的差异：那边走 {@code DeferredRegister} +
 * {@code IngredientType}；这边是一个 {@link IIngredientSerializer}
 * 加一次 {@link CraftingHelper#register} 调用。
 */
public final class OreFoodIngredients {

    /** JSON 里 {@code "definition"} 字段的名字。 */
    private static final String DEFINITION_FIELD = "definition";

    /**
     * 配料类型 id，同时也是数据包 JSON 里 {@code "type"} 的值。
     *
     * <p>提成常量是因为 {@link OreFoodRecipeSugar} 在展开语法糖时也要写这个值——
     * 两处各写一遍字符串迟早会写歪。
     */
    public static final ResourceLocation ID =
            new ResourceLocation(OreFoodConstants.MOD_ID, "ore_food");

    /** 本模组唯一的配料序列化器。 */
    public static final IIngredientSerializer<OreFoodIngredient> SERIALIZER =
            new IIngredientSerializer<>() {
                @Override
                public OreFoodIngredient parse(FriendlyByteBuf buffer) {
                    return new OreFoodIngredient(buffer.readResourceLocation());
                }

                @Override
                public OreFoodIngredient parse(JsonObject json) {
                    return new OreFoodIngredient(
                            new ResourceLocation(GsonHelper.getAsString(json, DEFINITION_FIELD)));
                }

                @Override
                public void write(FriendlyByteBuf buffer, OreFoodIngredient ingredient) {
                    buffer.writeResourceLocation(ingredient.definition());
                }
            };

    /**
     * 把配料类型注册进 Forge 的配料表。
     *
     * <p>必须在 {@code FMLCommonSetupEvent} 里调用——1.20.1 的
     * {@code CraftingHelper.register} 是往一张全局表里塞，Forge 要求这类
     * 「影响数据包解析的注册」都发生在那之前。
     */
    public static void register() {
        CraftingHelper.register(ID, SERIALIZER);
    }

    private OreFoodIngredients() {
        throw new AssertionError("No instances.");
    }
}
