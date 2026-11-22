package com.lyingice.orefood.client;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModList;

/**
 * AppleSkin 联动的客户端入口（NeoForge 26.1.2）。
 *
 * <p><b>为什么要分成两个类</b>：{@link OreFoodAppleSkinIntegration} 的签名里出现了
 * AppleSkin 的类型。如果它被无条件加载，没装 AppleSkin 的客户端会直接
 * {@code NoClassDefFoundError}。把「判断」留在这个类、把「引用」留给那个类，
 * 并保证只有 {@code isLoaded} 为真时才调用它——JVM 的惰性解析会保证那个类
 * 在没装的机器上根本不会被加载。
 *
 * <p>本类本身<b>不引用任何 AppleSkin 类型</b>，所以可以安全地被主类直接挂到
 * 客户端初始化事件上（不用注解，三平台写法一致）。
 */
public final class OreFoodAppleSkinSetup {

    /**
     * 客户端初始化：只有在检测到 AppleSkin 时才去注册监听器。
     *
     * @param event 由主类注册到 mod 事件总线的客户端初始化事件
     */
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("appleskin")) {
            OreFoodAppleSkinIntegration.register();
        }
    }

    private OreFoodAppleSkinSetup() {
    }
}
