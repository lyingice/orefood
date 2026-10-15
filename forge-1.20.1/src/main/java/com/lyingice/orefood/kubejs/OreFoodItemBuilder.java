package com.lyingice.orefood.kubejs;

import com.google.gson.JsonElement;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * KubeJS 的「矿物牛排物品」构建器（Forge 1.20.1）。
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
 * <p>这样注册出来的物品是 {@link OreFoodItem} 的实例，<b>直接继承全部矿物牛排行为</b>：
 * 按定义加营养、吃一口扣耐久、附魔效果与提示框、按定义给耐久上限与附魔能力。
 *
 * <p><b>两条硬约束</b>（都在本类里强制，脚本改不动）：
 * <ul>
 *   <li><b>食物属性零值</b>：真实数值由 {@code OreFoodConsume} 在吃下时按定义加。
 *       如果这里跟着脚本写的食物属性走，营养就会翻倍；</li>
 *   <li><b>不可堆叠 + 可损坏</b>：带耐久的物品堆叠会让整堆共享一份耐久，
 *       而 {@code durability(1)} 只是让物品被判定为「可损坏」（真实上限由定义给出）。</li>
 * </ul>
 */
public class OreFoodItemBuilder extends ItemBuilder {

    /** 脚本里 .definition(...) 传进来的定义 JSON。 */
    private transient JsonElement definitionJson;

    public OreFoodItemBuilder(ResourceLocation id) {
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
        return new OreFoodItem(createItemProperties(), parseDefinition());
    }

    /**
     * 强制零值食物、不可堆叠、可损坏；脚本里写的 {.food(...)} / {.maxStackSize(...)}
     * 会被这里覆盖——见类注释的两条硬约束。
     */
    @Override
    public Item.Properties createItemProperties() {
        return super.createItemProperties()
                .stacksTo(1)
                .durability(1)
                .food(new FoodProperties.Builder()
                        .nutrition(0)
                        .saturationMod(0.0F)
                        .alwaysEat()
                        .build());
    }

    /** @return 解析后的定义；脚本没写 .definition(...) 时给一份零值定义。 */
    private OreFoodDefinition parseDefinition() {
        if (this.definitionJson == null || this.definitionJson.isJsonNull()) {
            return new OreFoodDefinition(0, 0.0F, 0, 0, List.of());
        }
        return OreFoodDefinition.CODEC.parse(JsonOps.INSTANCE, this.definitionJson)
                .result()
                .orElseThrow(() -> new IllegalArgumentException(
                        "orefood: .definition(...) 解析失败，请检查字段名与取值：" + this.definitionJson));
    }
}
