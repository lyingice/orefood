package com.lyingice.orefood.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodComponents;
import com.lyingice.orefood.init.OreFoodItems;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * 配方 JSON 的「语法糖」展开（NeoForge 1.21.1）。
 *
 * <p><b>解决的问题</b>：本模组只有<b>一个</b>物品 {@code orefood:ore_food}，
 * 五种牛排靠物品栈上的 {@code orefood:definition} 组件区分。于是数据包作者
 * 想写一条配方时，必须把整份定义抄进 {@code result}、还得给中间那一格写
 * 自定义配料类型——既啰嗦又会和定义文件不同步。
 *
 * <p>这里让作者可以<b>直接写定义 id</b>：
 * <pre>{@code
 * {
 *   "type": "minecraft:crafting_shaped",
 *   "key": {
 *     "#": { "item": "minecraft:gold_ingot" },
 *     "X": { "item": "orefood:copper_beef" }        // ← 定义 id，不是物品 id
 *   },
 *   "pattern": [ "###", "#X#", "###" ],
 *   "result": { "id": "orefood:golden_beef" }        // ← 定义 id
 * }
 * }</pre>
 * 加载时由本类展开成原版认得的写法。
 *
 * <p><b>为什么必须在「反序列化之前」改写原始 JSON</b>：改写发生在
 * {@code RecipeManager#apply} 的最开头（见 {@code RecipeManagerMixin}），
 * 此时拿到的还是磁盘上读来的 {@code JsonElement}，任何配方类型都还没解析。
 * 于是<b>一个拦截点就覆盖了有形状/无形状/熔炉/锻造/切石全部配方类型</b>，
 * 而且不需要替换任何 codec 字段——那些 {@code static final} codec 在下游类
 * 初始化时就被按值捕获了，运行期替换字段是无效的。
 *
 * <p><b>为什么配料也必须改写</b>：原版配料的匹配语义是
 * {@code Ingredient#test} 里的 {@code itemstack.is(stack.getItem())}——
 * <b>只比物品类型，不看组件</b>。五种牛排物品 id 完全一样，所以就算把
 * 定义 id 解析成了带组件的物品栈，配料依然区分不出层级。唯一出路是把
 * {@code {"item": "orefood:copper_beef"}} 换成我们自己的
 * {@link OreFoodIngredient} 写法，让匹配走组件比较。
 *
 * <p><b>安全边界</b>：只有当那个 id <b>真的在 {@code orefood:ore_food}
 * 动态注册表里存在</b>时才改写。其它任何 id（原版物品、别的模组的物品）
 * 一律原样放过，所以本类不可能影响别的配方。
 */
public final class OreFoodRecipeSugar {

    /** 产物字段名。 */
    private static final String RESULT = "result";

    /** 物品栈 JSON 里表示物品 id 的字段名（1.21 起是 {@code id}，1.20.1 是 {@code item}）。 */
    private static final String STACK_ID = "id";

    /** 配料 JSON 里表示物品 id 的字段名。 */
    private static final String INGREDIENT_ITEM = "item";

    /** 值是「字符 → 配料」映射的字段名（有序配方）。 */
    private static final String KEYED_INGREDIENTS = "key";

    /** 值是「配料数组」的字段名（无序配方）。 */
    private static final String LISTED_INGREDIENTS = "ingredients";

    /** 值是「单个配料」的字段名（锻造模板/基底/添加物）。 */
    private static final String[] SINGLE_INGREDIENTS = {"template", "base", "addition"};

    /**
     * 就地展开一批配方 JSON。
     *
     * <p>调用点在 {@code RecipeManager#apply} 的 HEAD——原版在那之后才逐个解析。
     * 本方法直接修改传入的 {@link JsonElement}，原版随后读到的就是展开后的版本。
     *
     * @param recipeJsons 配方 id → 原始 JSON，来自 {@code RecipeManager}
     * @param registries  注册表访问，用来把定义 id 查成 {@link OreFoodDefinition}
     */
    public static void expand(Map<ResourceLocation, JsonElement> recipeJsons, HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<OreFoodDefinition> lookup =
                registries.lookupOrThrow(OreFoodDefinition.REGISTRY_KEY);

        for (JsonElement element : recipeJsons.values()) {
            if (element instanceof JsonObject recipe) {
                expandRecipe(recipe, lookup);
            }
        }
    }

    /** 展开一条配方里的产物与全部配料。 */
    private static void expandRecipe(JsonObject recipe, HolderLookup.RegistryLookup<OreFoodDefinition> lookup) {
        expandResult(recipe, lookup);

        JsonElement keyed = recipe.get(KEYED_INGREDIENTS);
        if (keyed instanceof JsonObject keys) {
            for (JsonElement ingredient : keys.asMap().values()) {
                expandIngredient(ingredient, lookup);
            }
        }

        JsonElement listed = recipe.get(LISTED_INGREDIENTS);
        if (listed instanceof JsonArray array) {
            for (JsonElement ingredient : array) {
                expandIngredient(ingredient, lookup);
            }
        }

        for (String field : SINGLE_INGREDIENTS) {
            JsonElement ingredient = recipe.get(field);
            if (ingredient != null) {
                expandIngredient(ingredient, lookup);
            }
        }
    }

    /**
     * 展开产物：把定义 id 换成「真实物品 + 定义组件」的完整物品栈 JSON。
     *
     * <p>这里必须查注册表把定义<b>内容</b>取出来写进去——物品栈上的组件存的是
     * 完整定义而不是注册表 id（见 {@link OreFoodComponents}），这样
     * {@code OreFoodItem#getMaxDamage(ItemStack)} 之类拿不到注册表的地方才能取值。
     */
    private static void expandResult(JsonObject recipe, HolderLookup.RegistryLookup<OreFoodDefinition> lookup) {
        JsonElement result = recipe.get(RESULT);
        if (result == null) {
            return;
        }

        ResourceLocation id = stackIdOf(result);
        if (id == null) {
            return;
        }

        OreFoodDefinition definition = findDefinition(lookup, id);
        if (definition == null) {
            return;
        }

        recipe.add(RESULT, stackJson(definition));
    }

    /**
     * 展开配料：把 {@code {"item": "<定义 id>"}} 换成自定义配料写法。
     *
     * <p>就地修改传入的对象（而不是替换父节点），因为配料的 JSON 形状始终是对象，
     * 不需要换类型。
     */
    private static void expandIngredient(JsonElement element, HolderLookup.RegistryLookup<OreFoodDefinition> lookup) {
        if (!(element instanceof JsonObject ingredient)) {
            return;
        }

        JsonElement item = ingredient.get(INGREDIENT_ITEM);
        if (item == null || !item.isJsonPrimitive()) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
        if (id == null || findDefinition(lookup, id) == null) {
            return;
        }

        // 换掉判别字段：原版会按 "type" 派发到我们注册的 IngredientType。
        ingredient.remove(INGREDIENT_ITEM);
        ingredient.addProperty("type", OreFoodIngredients.ORE_FOOD.getId().toString());
        ingredient.addProperty("definition", id.toString());
    }

    /**
     * 从产物 JSON 里取出「可能是一个定义 id」的那个字符串。
     *
     * <p>两种写法都要认：
     * <ul>
     *   <li>对象形式 {@code {"id": "..."}}——有形状/无形状/锻造/切石；</li>
     *   <li>字符串形式 {@code "..."}——熔炉类配方允许直接写物品 id。</li>
     * </ul>
     *
     * @return 解析出的 id；形状不认识或 id 非法时返回 {@code null}
     */
    private static ResourceLocation stackIdOf(JsonElement result) {
        if (result instanceof JsonPrimitive primitive && primitive.isString()) {
            return ResourceLocation.tryParse(primitive.getAsString());
        }
        if (result instanceof JsonObject object) {
            JsonElement id = object.get(STACK_ID);
            if (id != null && id.isJsonPrimitive()) {
                return ResourceLocation.tryParse(id.getAsString());
            }
        }
        return null;
    }

    /** 按 id 查定义；不存在时返回 {@code null}。 */
    private static OreFoodDefinition findDefinition(
            HolderLookup.RegistryLookup<OreFoodDefinition> lookup, ResourceLocation id) {
        return lookup.get(ResourceKey.create(OreFoodDefinition.REGISTRY_KEY, id))
                .map(holder -> holder.value())
                .orElse(null);
    }

    /** 造出「真实物品 + 定义组件」的完整物品栈 JSON。 */
    private static JsonObject stackJson(OreFoodDefinition definition) {
        JsonObject components = new JsonObject();
        components.add(
                OreFoodComponents.DEFINITION.getId().toString(),
                OreFoodDefinition.CODEC.encodeStart(JsonOps.INSTANCE, definition).getOrThrow());

        JsonObject stack = new JsonObject();
        stack.addProperty(STACK_ID, OreFoodItems.ORE_FOOD.getId().toString());
        stack.add("components", components);
        return stack;
    }

    private OreFoodRecipeSugar() {
        throw new AssertionError("No instances.");
    }
}
