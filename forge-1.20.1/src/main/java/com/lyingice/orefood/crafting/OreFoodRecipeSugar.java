package com.lyingice.orefood.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodItems;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * 配方 JSON 的「语法糖」展开（Forge 1.20.1）。
 *
 * <p><b>解决的问题</b>：本模组只有<b>一个</b>物品 {@code orefood:ore_food}，
 * 五种牛排靠物品栈上的 {@code orefood} NBT 标签区分。于是数据包作者想写一条配方时，
 * 必须把整份定义抄进 {@code result} 的 {@code nbt}、还得给中间那一格写自定义配料类型——
 * 既啰嗦又会和定义文件不同步。
 *
 * <p>这里让作者可以<b>直接写定义 id</b>：
 * <pre>{@code
 * {
 *   "type": "minecraft:crafting_shaped",
 *   "key": {
 *     "#": { "item": "minecraft:iron_ingot" },
 *     "X": { "item": "orefood:copper_beef" }        // ← 定义 id，不是物品 id
 *   },
 *   "pattern": [ "###", "#X#", "###" ],
 *   "result": { "item": "orefood:iron_beef" }        // ← 定义 id
 * }
 * }</pre>
 * 加载时由本类展开成原版认得的写法。
 *
 * <p><b>为什么必须在「反序列化之前」改写原始 JSON</b>：改写发生在
 * {@code RecipeManager#apply} 的最开头（见 {@code RecipeManagerMixin}），
 * 此时拿到的还是磁盘上读来的 {@code JsonElement}，任何配方类型都还没解析。
 * 于是<b>一个拦截点就覆盖了有形状/无形状/熔炉/锻造全部配方类型</b>，
 * 而且不需要替换任何 codec 字段——1.20.1 的配方解析走的是 Gson 而非 DFU codec，
 * 根本没有统一的物品 codec 可拦。
 *
 * <p><b>为什么配料也必须改写</b>：原版配料的匹配语义是
 * {@code Ingredient#test} 里的 {@code itemstack.is(item)}——<b>只比物品类型，
 * 连 NBT 都不看</b>。五种牛排物品 id 完全一样，所以就算把定义 id 解析成了
 * 带 NBT 的物品栈，配料依然区分不出层级。唯一出路是把
 * {@code {"item": "orefood:copper_beef"}} 换成我们自己的
 * {@link OreFoodIngredient} 写法，让匹配走 NBT 比较。
 *
 * <p><b>安全边界</b>：只有当那个 id <b>真的在 {@code orefood:ore_food}
 * 动态注册表里存在</b>时才改写。其它任何 id（原版物品、别的模组的物品）
 * 一律原样放过，所以本类不可能影响别的配方。
 *
 * <p><b>注册表从哪来</b>：{@code RecipeManager#apply} 的入参里没有注册表访问
 * （它的 {@code this.context} 是 {@code ICondition.IContext}，不是注册表）。
 * 所以改由 {@code AddReloadListenerEvent} 把 {@code RegistryAccess} 暂存进来——
 * 那个事件在 {@code ReloadableServerResources} 组装监听器列表时同步触发，
 * <b>早于任何监听器（含配方）开始运行</b>，见该类第 90-92 行。
 */
public final class OreFoodRecipeSugar {

    /** 产物字段名。 */
    private static final String RESULT = "result";

    /** 物品栈 JSON 里表示物品 id 的字段名（1.20.1 是 {@code item}，1.21 起改成 {@code id}）。 */
    private static final String STACK_ID = "item";

    /** 配料 JSON 里表示物品 id 的字段名。 */
    private static final String INGREDIENT_ITEM = "item";

    /** 值是「字符 → 配料」映射的字段名（有序配方）。 */
    private static final String KEYED_INGREDIENTS = "key";

    /** 值是「配料数组」的字段名（无序配方）。 */
    private static final String LISTED_INGREDIENTS = "ingredients";

    /** 值是「单个配料」的字段名（锻造模板/基底/添加物）。 */
    private static final String[] SINGLE_INGREDIENTS = {"template", "base", "addition"};

    /**
     * 最近一次数据包重载用的注册表访问，由 {@code AddReloadListenerEvent} 写入。
     *
     * <p>用 {@code volatile} 是因为写入发生在资源重载的准备阶段，而读取发生在
     * 配方解析（可能在不同的工作线程上）。
     */
    private static volatile RegistryAccess registryAccess;

    /** 暂存注册表访问；由 {@code OreFoodForge} 在 {@code AddReloadListenerEvent} 里调用。 */
    public static void captureRegistryAccess(RegistryAccess access) {
        registryAccess = access;
    }

    /**
     * 就地展开一批配方 JSON。
     *
     * <p>调用点在 {@code RecipeManager#apply} 的 HEAD——原版在那之后才逐个解析。
     *
     * @param recipeJsons 配方 id → 原始 JSON，来自 {@code RecipeManager}
     */
    public static void expand(Map<ResourceLocation, JsonElement> recipeJsons) {
        RegistryAccess access = registryAccess;
        if (access == null) {
            // 还没经历过一次数据包重载。理论上不会发生（事件早于配方解析），
            // 真发生了就让配方原样解析失败并报错，比静默产出错误物品好。
            return;
        }

        Registry<OreFoodDefinition> registry = access.registry(OreFoodDefinition.REGISTRY_KEY).orElse(null);
        if (registry == null) {
            return;
        }

        for (JsonElement element : recipeJsons.values()) {
            if (element instanceof JsonObject recipe) {
                expandRecipe(recipe, registry);
            }
        }
    }

    /** 展开一条配方里的产物与全部配料。 */
    private static void expandRecipe(JsonObject recipe, Registry<OreFoodDefinition> registry) {
        expandResult(recipe, registry);

        JsonElement keyed = recipe.get(KEYED_INGREDIENTS);
        if (keyed instanceof JsonObject keys) {
            for (JsonElement ingredient : keys.asMap().values()) {
                expandIngredient(ingredient, registry);
            }
        }

        JsonElement listed = recipe.get(LISTED_INGREDIENTS);
        if (listed instanceof JsonArray array) {
            for (JsonElement ingredient : array) {
                expandIngredient(ingredient, registry);
            }
        }

        for (String field : SINGLE_INGREDIENTS) {
            JsonElement ingredient = recipe.get(field);
            if (ingredient != null) {
                expandIngredient(ingredient, registry);
            }
        }
    }

    /**
     * 展开产物：把定义 id 换成「真实物品 + NBT 定义」的完整物品栈 JSON。
     *
     * <p>这里必须查注册表把定义<b>内容</b>取出来写进去——物品栈上的 NBT 存的是
     * 完整定义而不是注册表 id（见 {@link OreFoodDefinition}），这样
     * {@code OreFoodItem#getMaxDamage(ItemStack)} 之类拿不到注册表的地方才能取值。
     */
    private static void expandResult(JsonObject recipe, Registry<OreFoodDefinition> registry) {
        JsonElement result = recipe.get(RESULT);
        if (result == null) {
            return;
        }

        ResourceLocation id = stackIdOf(result);
        if (id == null) {
            return;
        }

        OreFoodDefinition definition = registry.get(id);
        if (definition == null) {
            return;
        }

        recipe.add(RESULT, stackJson(definition));
    }

    /**
     * 展开配料：把 {@code {"item": "<定义 id>"}} 换成自定义配料写法。
     *
     * <p>就地修改传入的对象（而不是替换父节点），因为配料的 JSON 形状始终是对象。
     */
    private static void expandIngredient(JsonElement element, Registry<OreFoodDefinition> registry) {
        if (!(element instanceof JsonObject ingredient)) {
            return;
        }

        JsonElement item = ingredient.get(INGREDIENT_ITEM);
        if (item == null || !item.isJsonPrimitive()) {
            return;
        }

        ResourceLocation id = ResourceLocation.tryParse(item.getAsString());
        if (id == null || registry.get(id) == null) {
            return;
        }

        // 换掉判别字段：1.20.1 的 CraftingHelper 会按 "type" 派发到我们注册的序列化器。
        ingredient.remove(INGREDIENT_ITEM);
        ingredient.addProperty("type", OreFoodIngredients.ID.toString());
        ingredient.addProperty("definition", id.toString());
    }

    /**
     * 从产物 JSON 里取出「可能是一个定义 id」的那个字符串。
     *
     * <p>两种写法都要认：
     * <ul>
     *   <li>对象形式 {@code {"item": "..."}}——有形状/无形状/锻造；</li>
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

    /**
     * 造出「真实物品 + NBT 定义」的完整物品栈 JSON。
     *
     * <p>原版 {@code CraftingHelper.getItemStack} 读的是 {@code item} 与
     * {@code nbt} 两个字段，其中 {@code nbt} 是 <b>SNBT 字符串</b>（不是对象），
     * 所以这里要把定义编码成 NBT 再转成 SNBT 文本。
     */
    private static JsonObject stackJson(OreFoodDefinition definition) {
        Tag encoded = OreFoodDefinition.CODEC
                .encodeStart(NbtOps.INSTANCE, definition)
                .getOrThrow(false, error -> {});

        CompoundTag tag = new CompoundTag();
        tag.put(OreFoodDefinition.STACK_TAG, encoded);

        JsonObject stack = new JsonObject();
        stack.addProperty(STACK_ID, OreFoodItems.ORE_FOOD.getId().toString());
        stack.addProperty("nbt", NbtUtils.structureToSnbt(tag));
        return stack;
    }

    private OreFoodRecipeSugar() {
        throw new AssertionError("No instances.");
    }
}
