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
 *   <li><b>食物属性以定义为准</b>（覆盖脚本 {@code .food(...)}），真实数值注册进
 *       {@code FoodProperties}；饱腹附魔加成由食用结算补
 *       （见 {@code OreFoodConsume}）。这样 AppleSkin、精妙背包等读取
 *       {@code getFoodProperties()} 的模组能看到正确数值，又不会与脚本值叠加；</li>
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
        OreFoodDefinition definition = parseDefinition();
        return new OreFoodItem(createItemProperties().food(foodProperties(definition)), definition);
    }

    /**
     * 强制不可堆叠、可损坏；脚本里写的 {@code .maxStackSize(...)} 会被这里覆盖，
     * 脚本 {@code .food(...)} 则在 {@link #createObject()} 里被定义值覆盖——
     * 见类注释的两条硬约束。
     */
    @Override
    public Item.Properties createItemProperties() {
        return super.createItemProperties()
                .stacksTo(1)
                .durability(1);
    }

    /**
     * 按定义生成真实食物属性（覆盖脚本 {@code .food(...)} 的值）。
     *
     * <p>饱和度以原版系数表示：{@code saturation = nutrition × saturationModifier × 2}，
     * 所以系数写作 {@code S / (2N)}；{@code nutrition} 为 0 时系数也给 0，避免除零。
     */
    private static FoodProperties foodProperties(OreFoodDefinition definition) {
        int nutrition = definition.nutrition();
        float saturationModifier = nutrition > 0 ? definition.saturation() / (nutrition * 2.0F) : 0.0F;
        return new FoodProperties.Builder()
                .nutrition(nutrition)
                .saturationMod(saturationModifier)
                .alwaysEat()
                .build();
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
