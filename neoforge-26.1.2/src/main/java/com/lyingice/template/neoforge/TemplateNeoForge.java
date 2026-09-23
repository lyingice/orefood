package com.lyingice.template.neoforge;

import com.lyingice.template.TemplateConstants;
import com.lyingice.template.init.TemplateItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge / Minecraft 26.1.2 的模组入口。
 *
 * <p>NeoForge 允许在 {@code @Mod} 构造器上直接注入 {@link IEventBus}；
 * 物品注册表（{@link TemplateItems}）就挂在这个总线上。Forge 1.20.1 没有这个便利，
 * 只能从 {@code FMLJavaModLoadingContext} 取总线。
 *
 * <p>26.1.2 与 1.21.1 的两处硬性差异（都已在构建侧处理好，本类只需知道）：
 *
 * <ul>
 *   <li>数据生成 run type 改名为 {@code clientData} / {@code serverData}
 *       （见 {@code build.gradle} 的 runs 段）；继续写 {@code data()} 只会在运行期报
 *       "Trying to prepare unknown run: data"；</li>
 *   <li>{@code FMLEnvironment.dist} 字段改为方法 {@code FMLEnvironment.getDist()}，
 *       {@code ResourceLocation} 改名为 {@code Identifier}。</li>
 * </ul>
 *
 * <p>另外注意：编译期常量会被 javac 内联，所以引用别处的
 * {@code public static final String} 常量<b>不会</b>触发那个类的加载 ——
 * 这正是「只引用一个常量」在专用服务端上安全、而「调用一个方法」不安全的原因。

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

        LOGGER.info("{} v{} initialized on NeoForge (Minecraft 26.1.2); shared package {}",
                TemplateConstants.MOD_NAME,
                TemplateConstants.MOD_VERSION,
                TemplateConstants.PACKAGE_ROOT);
    }
}
