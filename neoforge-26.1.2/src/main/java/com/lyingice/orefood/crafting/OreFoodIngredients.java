package com.lyingice.orefood.crafting;

import com.lyingice.orefood.OreFoodConstants;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 注册本模组的自定义配料类型（NeoForge 26.1.2）。
 *
 * <p>数据包里的写法：
 * <pre>{@code
 * { "neoforge:ingredient_type": "orefood:ore_food", "definition": "orefood:copper_beef" }
 * }</pre>
 *
 * <p><b>判别字段名与 1.21.1 不同，这点极易踩坑</b>：26.1.2 的
 * {@code IngredientCodecs} 按 {@code "neoforge:ingredient_type"} 派发，
 * 而 1.21.1 的 {@code NeoForgeExtraCodecs.dispatchMapOrElse} 按 {@code "type"} 派发。
 * 在 26.1.2 写 {@code "type"} 不会报错，只会静默落到原版配料分支然后解析失败。
 *
 * <p>与 1.21.1 侧的注册 API 逐字相同——{@code DeferredRegister} +
 * {@code IngredientType} 这套接口在 26.1.2 没有改名。
 */
public final class OreFoodIngredients {

    /** 自定义配料类型注册表；它属于 NeoForge 自己的注册表，不是原版的。 */
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, OreFoodConstants.MOD_ID);

    /** 「某一种特定的矿物牛排」配料。 */
    public static final DeferredHolder<IngredientType<?>, IngredientType<OreFoodIngredient>> ORE_FOOD =
            INGREDIENT_TYPES.register("ore_food", () -> new IngredientType<>(OreFoodIngredient.CODEC));

    /** 把配料类型注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        INGREDIENT_TYPES.register(modEventBus);
    }

    private OreFoodIngredients() {
        throw new AssertionError("No instances.");
    }
}
