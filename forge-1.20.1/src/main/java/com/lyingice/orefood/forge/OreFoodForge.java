package com.lyingice.orefood.forge;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.init.OreFoodItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 的模组入口。
 *
 * <p>本类是平台模块中最薄的一层：它只负责装配，业务逻辑放在共享层
 * （{@code ../common/src/main/java}）里，由本构建直接编译进同一个 jar。
 *
 * <p>与 NeoForge 不同，Forge 1.20.1 的 {@code mods.toml} 支持
 * {@code clientSideOnly=true}（见该文件中的注释）。本模组默认按双端模组处理。
 */
@Mod(OreFoodConstants.MOD_ID)
public final class OreFoodForge {

    public static final Logger LOGGER = LoggerFactory.getLogger(OreFoodConstants.MOD_NAME);

    /** 从主类构造器里取共享层的物品注册表工厂，避免本类直接依赖注册实现细节。 */
    public OreFoodForge() {
        // 1.20.1 的 @Mod 构造器拿不到总线，只能从 FMLJavaModLoadingContext 取；
        // 两个 NeoForge 平台是直接注入到构造器参数里的。
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 物品注册表必须挂到 mod 事件总线，DeferredRegister 才会真正注册。
        OreFoodItems.register(modEventBus);
        // 把示例物品接进创造模式标签页（见 OreFoodItems#addToCreativeTab）。
        modEventBus.addListener(OreFoodItems::addToCreativeTab);

        LOGGER.info("{} v{} initialized on Forge (Minecraft 1.20.1); shared package {}",
                OreFoodConstants.MOD_NAME,
                OreFoodConstants.MOD_VERSION,
                OreFoodConstants.PACKAGE_ROOT);
    }
}
