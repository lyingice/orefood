package com.lyingice.orefood.forge;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.init.OreFoodEnchantments;
import com.lyingice.orefood.init.OreFoodItems;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge 1.20.1 的模组入口。
 *
 * <p>这里只做装配，业务逻辑分别在：
 * <ul>
 *   <li>{@link OreFoodItems}：五种牛排物品（各自一个真实物品 id）；</li>
 *   <li>{@link OreFoodEnchantments}：五个附魔（1.20.1 只能静态注册）；</li>
 *   <li>{@code com.lyingice.orefood.logic.OreFoodConsume} 配合
 *       {@code com.lyingice.orefood.mixin.LivingEntityEatMixin}：食用结算与耐久消耗；</li>
 *   <li>{@code com.lyingice.orefood.client.OreFoodTooltip}：提示框。</li>
 * </ul>
 *
 * <p><b>1.2.0 删掉的东西</b>：动态注册表（{@code DataPackRegistryEvent}）、配方语法糖的
 * 注册表暂存（{@code AddReloadListenerEvent}）、自定义配料注册（{@code FMLCommonSetupEvent}）
 * —— 五种牛排改真物品后这些全都不需要了。
 *
 * <p>与 NeoForge 不同，Forge 1.20.1 的 {@code mods.toml} 支持
 * {@code clientSideOnly=true}。本模组按双端模组处理——服务端要算营养与效果，
 * 客户端要画提示框。
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

        // 让五种牛排出现在创造模式「食物与饮品」页。
        modEventBus.addListener(OreFoodItems::addToCreativeTab);

        // 启动自检：打印五种牛排的实际定义，以及每个附魔在三个入口上的开关状态。
        // 数值现在写在代码里（编译期就能发现笔误），这行日志主要用于
        // 「加了新口味之后确认它真的注册上、数值真的是想要的」。
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            OreFoodItems.ALL.forEach(item -> LOGGER.info("  {} -> {}", item.getId(), item.get().definition()));
            logEnchantmentEntries();
        });

        LOGGER.info("{} v{} initialized on Forge (Minecraft 1.20.1); shared package {}",
                OreFoodConstants.MOD_NAME,
                OreFoodConstants.MOD_VERSION,
                OreFoodConstants.PACKAGE_ROOT);
    }

    /**
     * 启动自检：打印每个食物附魔在三个入口上的开关状态。
     *
     * <p>1.21 起这些开关改由数据包标签表达（{@code in_enchanting_table} /
     * {@code tradeable} / {@code treasure}）；1.20.1 没有那套标签，
     * 靠 {@code FoodEnchantment} 覆写下面三个方法实现，这里把结果打出来便于核对。
     * 写错的后果是<b>静默</b>的——附魔会安静地哪都拿不到，不会报错。
     */
    private static void logEnchantmentEntries() {
        for (OreFoodEnchantments.Entry entry : OreFoodEnchantments.entries()) {
            Enchantment enchantment = entry.enchantment().get();
            LOGGER.info("  {} -> discoverable={}, tradeable={}, treasure={}",
                    entry.id(),
                    enchantment.isDiscoverable(),
                    enchantment.isTradeable(),
                    enchantment.isTreasureOnly());
        }
    }
}
