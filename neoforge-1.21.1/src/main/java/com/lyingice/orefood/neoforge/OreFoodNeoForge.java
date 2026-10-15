package com.lyingice.orefood.neoforge;

import com.lyingice.orefood.FoodEnchantment;
import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.init.OreFoodItems;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge / Minecraft 1.21.1 的模组入口。
 *
 * <p>这里只做装配，业务逻辑分别在：
 * <ul>
 *   <li>{@link OreFoodItems}：五种牛排物品（各自一个真实物品 id）；</li>
 *   <li>{@code com.lyingice.orefood.logic.OreFoodConsume} 配合
 *       {@code com.lyingice.orefood.mixin.LivingEntityEatMixin}：食用结算与耐久消耗；</li>
 *   <li>{@code com.lyingice.orefood.client.OreFoodTooltip}：提示框。</li>
 * </ul>
 *
 * <p><b>1.2.0 删掉的东西</b>：动态注册表（{@code DataPackRegistryEvent}）、数据组件
 * （{@code OreFoodComponents}）、自定义配料、配方语法糖与 JEI 变体插件——五种牛排
 * 改真物品后这些全都不需要了。
 *
 * <p><b>本类绝不能直接引用任何客户端专属类。</b>NeoForge 没有 Forge 1.20.1 的
 * {@code clientSideOnly} 开关，{@code @Mod} 类在专用服务端上照样被加载；
 * 一旦解析到建立在 {@code @OnlyIn(Dist.CLIENT)} 之上的类，就会抛
 * {@code Attempted to load class ... for invalid dist DEDICATED_SERVER}。
 */
@Mod(OreFoodConstants.MOD_ID)
public final class OreFoodNeoForge {

    public static final Logger LOGGER = LoggerFactory.getLogger(OreFoodConstants.MOD_NAME);

    public OreFoodNeoForge(IEventBus modEventBus) {
        OreFoodItems.register(modEventBus);

        // 让五种牛排出现在创造模式「食物与饮品」页。
        modEventBus.addListener(OreFoodItems::addToCreativeTab);

        // 启动自检：打印五种牛排的实际定义，以及每个附魔实际挂在哪些原版附魔标签下。
        // 数值现在写在代码里（编译期就能发现笔误），这行日志主要用于
        // 「加了新口味之后确认它真的注册上、数值真的是想要的」。
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            OreFoodItems.ALL.forEach(item -> LOGGER.info("  {} -> {}", item.getId(), item.get().definition()));
            logEnchantmentEntries(event);
        });

        LOGGER.info("{} v{} initialized on NeoForge (Minecraft 1.21.1); shared package {}",
                OreFoodConstants.MOD_NAME,
                OreFoodConstants.MOD_VERSION,
                OreFoodConstants.PACKAGE_ROOT);
    }

    /**
     * 启动自检：打印每个食物附魔实际挂在哪些原版附魔标签下。
     *
     * <p>1.21 起「能不能在附魔台刷出 / 能不能交易 / 会不会出现在战利品里」
     * <b>完全由数据包标签决定</b>（{@code in_enchanting_table} / {@code tradeable} /
     * {@code on_random_loot}），而这些标签是「非宝藏附魔 + 少数宝藏附魔」的组合，
     * 写错了不会有任何报错——附魔会安静地哪都拿不到。有这么一行就能立刻看出来。
     *
     * <p>最关键的是每个非宝藏附魔身上必须出现 {@code non_treasure}：
     * 原版的 {@code in_enchanting_table} / {@code tradeable} / {@code on_random_loot}
     * 都是<b>引用</b> {@code #minecraft:non_treasure} 的，进了它就等于全都有了。
     */
    private static void logEnchantmentEntries(ServerStartedEvent event) {
        Registry<Enchantment> enchantments = event.getServer().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        for (FoodEnchantment spec : FoodEnchantment.values()) {
            ResourceKey<Enchantment> key = ResourceKey.create(
                    Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(OreFoodConstants.MOD_ID, spec.path()));
            String tags = enchantments.getHolder(key)
                    .map(holder -> holder.tags()
                            .map(tag -> tag.location().getPath())
                            .sorted()
                            .collect(Collectors.joining(", ")))
                    .orElse("<NOT REGISTERED>");
            LOGGER.info("  {} -> [{}]", spec.fullId(), tags);
        }
    }
}
