package com.lyingice.template.neoforge;

import com.lyingice.template.TemplateConstants;
import com.lyingice.template.init.TemplateItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge / Minecraft 1.21.1 的模组入口。
 *
 * <p>NeoForge 允许在 {@code @Mod} 构造器上直接注入 {@link IEventBus}；
 * 物品注册表（{@link TemplateItems}）就挂在这个总线上。Forge 1.20.1 没有这个便利，
 * 只能从 {@code FMLJavaModLoadingContext} 取总线。
 *

 * <p><b>本类绝不能直接引用任何客户端专属类。</b>NeoForge 没有 Forge 1.20.1 的
 * {@code clientSideOnly} 开关，{@code @Mod} 类在专用服务端上照样会被加载；
 * 一旦解析到建立在 {@code @OnlyIn(Dist.CLIENT)} 之上的类，就会抛
 * {@code Attempted to load class ... for invalid dist DEDICATED_SERVER}。
 * 因此客户端专属的装配必须封进一个单独的类，并且只在 Dist.CLIENT 分支里调用它
 * —— 服务端不进入该分支，JVM 的懒解析就不会加载那个类。
 */
@Mod(TemplateConstants.MOD_ID)
public final class TemplateNeoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger(TemplateConstants.MOD_NAME);

    public TemplateNeoForge(IEventBus modEventBus) {
        // 物品注册表必须挂到 mod 事件总线，DeferredRegister 才会真正注册。
        TemplateItems.register(modEventBus);
        // 把示例物品接进创造模式标签页（见 TemplateItems#addToCreativeTab）。
        modEventBus.addListener(TemplateItems::addToCreativeTab);

        LOGGER.info("{} v{} initialized on NeoForge (Minecraft 1.21.1); shared package {}",
                TemplateConstants.MOD_NAME,
                TemplateConstants.MOD_VERSION,
                TemplateConstants.PACKAGE_ROOT);
    }
}
