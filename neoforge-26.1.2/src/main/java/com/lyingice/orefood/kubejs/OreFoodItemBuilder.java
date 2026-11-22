package com.lyingice.orefood.kubejs;

import com.google.gson.JsonElement;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantable;

/**
 * KubeJS 的「矿物牛排物品」构建器（NeoForge 26.1.2）。
 *
 * <p>脚本写法：
 * <pre>{@code
 * StartupEvents.registry('item', event => {
 *   event.create('mypack:redstone_beef', 'orefood:ore_food_item')
 *     .definition({ nutrition: 6, saturation: 9.6, durability: 12, enchantment_value: 2 })
 *     .texture('mypack:item/redstone_beef')
 * })
 * }</pre>
 *
 * <p>这样注册出来的物品是 {@link OreFoodItem} 的实例，<b>直接继承全部矿物牛排行为</b>。
 *
 * <p><b>这个平台多两件事要做</b>（都在本类里自动完成）：
 * <ul>
 *   <li>{@code ITEM_MODEL} 组件 —— 26.1.2 的物品模型由组件指向；</li>
 *   <li>{@code ENCHANTABLE} 组件 —— 26.1.2 的附魔能力走组件，且构造器要求正数。</li>
 * </ul>
 *
 * <p><b>两条硬约束</b>（都在本类里强制，脚本改不动）：食物属性<b>以定义为准</b>
 * （父类先应用脚本的 {@code .food(...)}，随后本类用定义值覆盖，真实数值注册进
 * {@code FoodProperties}；「饱腹」附魔加成由食用结算补）、不可堆叠 + 可损坏。
 *
 * <p>NeoForge 平台还要提醒作者：本模组的附魔只对 {@code #c:foods} 里的物品生效，
 * 脚本里记得写 {@code .tag('c:foods')}。
 */
public class OreFoodItemBuilder extends ItemBuilder {

    /** 脚本里 .definition(...) 传进来的定义 JSON。 */
    private transient JsonElement definitionJson;

    public OreFoodItemBuilder(Identifier id) {
        super(id);
    }

    /**
     * 脚本 API：声明这一种牛排的数值。字段名与 1.1.x 的数据包定义 JSON 一致。
     *
     * @param value 对象字面量；KubeJS 会把脚本对象转成 {@link JsonElement}
     */
    public OreFoodItemBuilder definition(JsonElement value) {
        this.definitionJson = value;
        return this;
    }

    @Override
    public Item createObject() {
        OreFoodDefinition definition = parseDefinition();
        Item.Properties props = createItemProperties()
                .stacksTo(1)
                .durability(1)
                // 用定义值覆盖 super.createItemProperties() 里脚本的 .food(...)，
                // 防止「脚本值 + 定义值」叠加；26.1.2 的 record 第二参是绝对饱和度。
                .food(new FoodProperties(definition.nutrition(), definition.saturation(), true))
                // 26.1.2 的物品模型按 ITEM_MODEL 组件走：指向 assets/<ns>/items/<路径>.json。
                .component(DataComponents.ITEM_MODEL, this.id);
        if (definition.enchantmentValue() > 0) {
            // Enchantable 的构造器要求 value > 0，所以 0 时不能设。
            props = props.component(DataComponents.ENCHANTABLE,
                    new Enchantable(definition.enchantmentValue()));
        }
        return new OreFoodItem(props, definition);
    }

    /**
     * 只保留父类逻辑（含脚本的 {@code .food(...)}）；食物属性稍后在
     * {@link #createObject()} 里用定义值覆盖，不可堆叠与可损坏也在那里设置。
     */
    @Override
    public Item.Properties createItemProperties() {
        return super.createItemProperties();
    }

    /** @return 解析后的定义；脚本没写 .definition(...) 时给一份零值定义。 */
    private OreFoodDefinition parseDefinition() {
        if (this.definitionJson == null || this.definitionJson.isJsonNull()) {
            return new OreFoodDefinition(0, 0.0F, 0, 0, List.of(), "", false);
        }
        return OreFoodDefinition.CODEC.parse(JsonOps.INSTANCE, this.definitionJson)
                .result()
                .orElseThrow(() -> new IllegalArgumentException(
                        "orefood: .definition(...) 解析失败，请检查字段名与取值：" + this.definitionJson));
    }
}
