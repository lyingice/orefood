package com.lyingice.orefood.kubejs;

import com.google.gson.JsonElement;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * KubeJS 的「矿物牛排物品」构建器（NeoForge 1.21.1）。
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
 *   <li><b>食物属性以定义为准（覆盖脚本 .food(...)）</b>：真实数值注册进 FoodProperties，
 *       饱腹附魔加成由食用结算补。脚本自己写的 .food(...) 会被定义值覆盖，防叠加；</li>
 *   <li><b>不可堆叠 + 可损坏</b>：带耐久的物品堆叠会让整堆共享一份耐久，
 *       而 {@code durability(1)} 只是让物品被判定为「可损坏」（真实上限由定义给出）。</li>
 * </ul>
 *
 * <p>NeoForge 平台还要提醒作者一件事：本模组的附魔只对 {@code #c:foods} 里的物品生效，
 * 所以脚本里记得写 {@code .tag('c:foods')}。
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
        OreFoodDefinition definition = parseDefinition();
        return new OreFoodItem(createItemProperties().food(foodProperties(definition)), definition);
    }

    /** 强制不可堆叠、可损坏；食物属性在 {@link #createObject()} 里按定义覆盖。 */
    @Override
    public Item.Properties createItemProperties() {
        return super.createItemProperties()
                .stacksTo(1)
                .durability(1);
    }

    /** 定义 → 真实食物属性（record 第二参是绝对饱和度，直构精确无换算误差）。 */
    private static FoodProperties foodProperties(OreFoodDefinition definition) {
        return new FoodProperties(
                definition.nutrition(),
                definition.saturation(),
                true,
                1.6F,
                Optional.empty(),
                List.of());
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
