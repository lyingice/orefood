package com.lyingice.orefood.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.RegistryInfo;

/**
 * KubeJS 插件：把「矿物牛排物品」注册成一个可用的物品类型（Forge 1.20.1）。
 *
 * <p>注册之后，脚本可以写
 * event.create('mypack:redstone_beef', 'orefood:ore_food_item').definition({...})，
 * 得到的就是一个继承了全部矿物牛排行为的独立物品。
 *
 * <p><b>插件是怎么被发现的</b>：资源根部的 kubejs.plugins.txt 里写着本类全名。
 * 没装 KubeJS 时那个文件不会被任何东西读取，本类也就不会被加载——
 * 所以 KubeJS 只是 compileOnly 依赖，不是硬依赖。
 */
public class OreFoodKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void init() {
        // 类型名用 orefood:ore_food_item（不是内置物品 id，避免与物品注册表混淆）。
        RegistryInfo.ITEM.addType(
                "orefood:ore_food_item",
                OreFoodItemBuilder.class,
                OreFoodItemBuilder::new);
    }
}
