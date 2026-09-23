package com.lyingice.orefood.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodComponents;
import com.lyingice.orefood.init.OreFoodItems;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.ModifyRecipeJsonsEvent;

/**
 * 配方 JSON 的「语法糖」展开（NeoForge 26.1.2）。
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
 *     "#": { "item": "minecraft:diamond" },
 *     "X": { "item": "orefood:golden_beef" }        // ← 定义 id，不是物品 id
 *   },
 *   "pattern": [ "###", "#X#", "###" ],
 *   "result": { "id": "orefood:diamond_beef" }       // ← 定义 id
 * }
 * }</pre>
 * 加载时由本类展开成原版认得的写法。
 *
 * <p><b>为什么这一版连 Mixin 都不用</b>：NeoForge 26.1.2 提供了官方钩子
 * {@link ModifyRecipeJsonsEvent}，在配方 JSON 读盘之后、反序列化之前触发，
 * 直接给出可原地修改的 {@code Map<Identifier, JsonElement>}，并且通过
 * {@link ModifyRecipeJsonsEvent#lookupOrThrow(ResourceKey)} 就能拿到数据包注册表。
 * 于是另外两个平台要自己搭的东西（1.21.1 注入 {@code RecipeManager#apply}、
 * 1.20.1 还要额外暂存注册表访问），这里全是现成的。
 *
 * <p><b>为什么配料也必须改写</b>：26.1.2 的 {@code Ingredient} 内部是
 * {@code HolderSet<Item>}——一组<b>物品类型</b>。原版 codec 只能产出
 * {@code Holder.Reference}（未注册 id 直接报错），而且列表还受
 * {@code ensureHomogenous} 限制，压根没有合法的「非注册 holder」兜底路径。
 * 就算能绕，匹配时也只比物品类型。唯一出路是把
 * {@code {"item": "orefood:copper_beef"}} 换成我们自己的
 * {@link OreFoodIngredient} 写法。
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

    /** 标签形式的配料前缀，例如 {@code "#minecraft:logs"}。 */
    private static final String TAG_PREFIX = "#";

    /**
     * 自定义配料的判别字段名。
     *
     * <p><b>这里与 1.21.1 不同</b>：1.21.1 的
     * {@code NeoForgeExtraCodecs.dispatchMapOrElse} 判的是 {@code "type"}，
     * 而 26.1.2 的 {@code IngredientCodecs} 判的是 {@code "neoforge:ingredient_type"}。
     * 写错的话配方会静默落到原版配料分支然后解析失败。
     */
    private static final String INGREDIENT_TYPE_FIELD = "neoforge:ingredient_type";

    /** 值是「字符 → 配料」映射的字段名（有序配方）。 */
    private static final String KEYED_INGREDIENTS = "key";

    /** 值是「配料数组」的字段名（无序配方）。 */
    private static final String LISTED_INGREDIENTS = "ingredients";

    /** 值是「单个配料」的字段名（锻造模板/基底/添加物）。 */
    private static final String[] SINGLE_INGREDIENTS = {"template", "base", "addition"};

    /**
     * 就地展开一批配方 JSON。
     *
     * <p>由 {@link ModifyRecipeJsonsEvent} 的监听器调用。
     *
     * @param event NeoForge 在配方反序列化之前抛出的事件，携带原始 JSON 与注册表访问
     */
    public static void expand(ModifyRecipeJsonsEvent event) {
        HolderGetter<OreFoodDefinition> lookup = event.lookupOrThrow(OreFoodDefinition.REGISTRY_KEY).getter();

        for (JsonElement element : event.getRecipeJsons().values()) {
            if (element instanceof JsonObject recipe) {
                expandRecipe(recipe, lookup);
            }
        }
    }

    /** 展开一条配方里的产物与全部配料。 */
    private static void expandRecipe(JsonObject recipe, HolderGetter<OreFoodDefinition> lookup) {
        expandResult(recipe, lookup);

        JsonElement keyed = recipe.get(KEYED_INGREDIENTS);
        if (keyed instanceof JsonObject keys) {
            // 先收集再写回：直接边遍历 entrySet 边改值虽然对 LinkedTreeMap 是安全的，
            // 但没必要依赖这个实现细节。
            Map<String, JsonElement> replacements = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : keys.entrySet()) {
                JsonElement replaced = expandIngredient(entry.getValue(), lookup);
                if (replaced != null) {
                    replacements.put(entry.getKey(), replaced);
                }
            }
            replacements.forEach(keys::add);
        }

        JsonElement listed = recipe.get(LISTED_INGREDIENTS);
        if (listed instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                JsonElement replaced = expandIngredient(array.get(i), lookup);
                if (replaced != null) {
                    array.set(i, replaced);
                }
            }
        }

        for (String field : SINGLE_INGREDIENTS) {
            JsonElement ingredient = recipe.get(field);
            if (ingredient != null) {
                JsonElement replaced = expandIngredient(ingredient, lookup);
                if (replaced != null) {
                    recipe.add(field, replaced);
                }
            }
        }
    }

    /**
     * 展开配料：把裸定义 id 换成自定义配料写法。
     *
     * <p><b>26.1.2 的配料写法与 1.21.1 完全不同</b>，这点极易踩坑：
     * <pre>
     *   1.21.1:  { "item": "minecraft:iron_ingot" }      ← 对象
     *   26.1.2:  "minecraft:iron_ingot"                  ← 裸字符串
     *   26.1.2:  "#minecraft:wooden_tool_materials"      ← 标签也是裸字符串
     * </pre>
     * 因为 26.1.2 的 {@code Ingredient} 内部换成了 {@code HolderSet<Item>}，
     * 由 {@code HolderSetCodec} 解析——它只认「标签字符串」或「元素 id 数组」，
     * 对象形式 {@code {"item": ...}} 会直接解析失败（连原版物品也一样失败）。
     *
     * <p>字符串换成对象意味着<b>必须替换父节点</b>，不能就地修改，所以本方法返回
     * 替换后的元素；不需要替换时返回 {@code null}。
     */
    private static JsonElement expandIngredient(JsonElement element, HolderGetter<OreFoodDefinition> lookup) {
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return null;
        }

        String raw = primitive.getAsString();
        if (raw.startsWith(TAG_PREFIX)) {
            // 标签不是物品 id，直接放过。
            return null;
        }

        Identifier id = Identifier.tryParse(raw);
        if (id == null || findDefinition(lookup, id) == null) {
            return null;
        }

        JsonObject custom = new JsonObject();
        custom.addProperty(INGREDIENT_TYPE_FIELD, OreFoodIngredients.ORE_FOOD.getId().toString());
        custom.addProperty("definition", id.toString());
        return custom;
    }

    /**
     * 展开产物：把定义 id 换成「真实物品 + 定义组件」的完整物品栈 JSON。
     *
     * <p>这里必须查注册表把定义<b>内容</b>取出来写进去——物品栈上的组件存的是
     * 完整定义而不是注册表 id（见 {@link OreFoodComponents}），这样
     * {@code OreFoodItem#getMaxDamage(ItemStack)} 之类拿不到注册表的地方才能取值。
     */
    private static void expandResult(JsonObject recipe, HolderGetter<OreFoodDefinition> lookup) {
        JsonElement result = recipe.get(RESULT);
        if (result == null) {
            return;
        }

        Identifier id = stackIdOf(result);
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
     * 从产物 JSON 里取出「可能是一个定义 id」的那个字符串。
     *
     * <p>两种写法都要认：
     * <ul>
     *   <li>对象形式 {@code {"id": "..."}}——有形状/无形状/锻造；</li>
     *   <li>字符串形式 {@code "..."}——熔炉类配方允许直接写物品 id。</li>
     * </ul>
     *
     * @return 解析出的 id；形状不认识或 id 非法时返回 {@code null}
     */
    private static Identifier stackIdOf(JsonElement result) {
        if (result instanceof JsonPrimitive primitive && primitive.isString()) {
            return Identifier.tryParse(primitive.getAsString());
        }
        if (result instanceof JsonObject object) {
            JsonElement id = object.get(STACK_ID);
            if (id != null && id.isJsonPrimitive()) {
                return Identifier.tryParse(id.getAsString());
            }
        }
        return null;
    }

    /** 按 id 查定义；不存在时返回 {@code null}。 */
    private static OreFoodDefinition findDefinition(HolderGetter<OreFoodDefinition> lookup, Identifier id) {
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
