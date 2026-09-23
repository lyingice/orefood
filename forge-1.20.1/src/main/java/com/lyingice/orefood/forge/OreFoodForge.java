package com.lyingice.orefood.forge;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodEnchantments;
import com.lyingice.orefood.init.OreFoodItems;
import net.minecraft.core.Registry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DataPackRegistryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 的模组入口。
 *
 * <p>这里只做装配，业务逻辑分别在：
 * <ul>
 *   <li>{@link OreFoodItems}：唯一的物品 {@code orefood:ore_food}；</li>
 *   <li>{@link OreFoodEnchantments}：五个附魔（1.20.1 只能静态注册，
 *       与 1.21.1 的「放 JSON 即可」是本质不同的两条路）；</li>
 *   <li>{@code com.lyingice.orefood.logic.OreFoodConsume} 配合
 *       {@code com.lyingice.orefood.mixin.LivingEntityEatMixin}：食用结算与耐久消耗；</li>
 *   <li>{@code com.lyingice.orefood.client.OreFoodTooltip}：提示框。</li>
 * </ul>
 *
 * <p>与 NeoForge 不同，Forge 1.20.1 的 {@code mods.toml} 支持
 * {@code clientSideOnly=true}（见该文件中的注释）。本模组按双端模组处理——
 * 服务端要算营养与效果，客户端要画提示框。
 */
@Mod(OreFoodConstants.MOD_ID)
public final class OreFoodForge {

    public static final Logger LOGGER = LoggerFactory.getLogger(OreFoodConstants.MOD_NAME);

    public OreFoodForge() {
        // 1.20.1 的 @Mod 构造器拿不到总线，只能从 FMLJavaModLoadingContext 取；
        // 两个 NeoForge 平台是直接注入到构造器参数里的。
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        OreFoodItems.register(modEventBus);
        OreFoodEnchantments.register(modEventBus);

        // 自定义动态注册表：牛排定义由数据包写在 data/<pack>/orefood/ore_food/*.json。
        // 第三个参数是「网络 codec」（类型是 Codec，原版同步走 NBT）——提供它就意味着
        // 「客户端也必须装本模组」，创造模式标签页才能在客户端读到数据包定义的每一种牛排。
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(
                OreFoodDefinition.REGISTRY_KEY,
                OreFoodDefinition.CODEC,
                OreFoodDefinition.CODEC));

        // 让数据包里定义的每一种牛排都出现在创造模式「食物与饮品」页。
        modEventBus.addListener(OreFoodItems::addToCreativeTab);

        // 启动诊断：把动态注册表里实际加载到的牛排定义打进日志。
        // 数据包路径或 Codec 写错时原版是**静默**返回空注册表的（不会抛异常），
        // 有这行日志就能一眼看出「数据包到底生效了没有」。
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            Registry<OreFoodDefinition> registry = event.getServer().registryAccess()
                    .registryOrThrow(OreFoodDefinition.REGISTRY_KEY);
            LOGGER.info("Loaded {} ore food definition(s): {}", registry.size(), registry.keySet());
        });

        LOGGER.info("{} v{} initialized on Forge (Minecraft 1.20.1); shared package {}",
                OreFoodConstants.MOD_NAME,
                OreFoodConstants.MOD_VERSION,
                OreFoodConstants.PACKAGE_ROOT);
    }
}
