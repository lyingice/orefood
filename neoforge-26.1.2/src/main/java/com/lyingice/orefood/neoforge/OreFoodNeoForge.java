package com.lyingice.orefood.neoforge;

import com.lyingice.orefood.FoodEnchantment;
import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodComponents;
import com.lyingice.orefood.init.OreFoodItems;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge / Minecraft 26.1.2 的模组入口。
 *
 * <p>这里只做装配，业务逻辑分别在：
 * <ul>
 *   <li>{@link OreFoodItems}：唯一的物品 {@code orefood:ore_food}；</li>
 *   <li>{@link OreFoodComponents}：携带牛排定义的数据组件；</li>
 *   <li>{@code com.lyingice.orefood.logic.OreFoodConsume} 配合
 *       {@code com.lyingice.orefood.mixin.ConsumableMixin}：食用结算与耐久消耗；</li>
 *   <li>{@code com.lyingice.orefood.client.OreFoodTooltip}：提示框。</li>
 * </ul>
 *
 * <p>注意 26.1.2 里 {@code DataPackRegistryEvent} 的包名与三个
 * {@code dataPackRegistry} 重载<b>和 1.21.1 逐字相同</b>——它没有跟着
 * {@code ResourceLocation} 一起改名，唯一的变化在内部
 * （{@code RegistryData} 的第三个参数从 {@code false} 变成了 {@code RegistryValidator.none()}）。
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
        OreFoodComponents.register(modEventBus);

        // 自定义动态注册表：牛排定义由数据包写在 data/<pack>/orefood/ore_food/*.json。
        // 第三个参数是「网络 codec」——类型是 Codec 而不是 StreamCodec（原版同步走 NBT）。
        // 提供它就意味着「客户端也必须装本模组」，创造模式标签页才能在客户端读到定义。
        modEventBus.addListener((DataPackRegistryEvent.NewRegistry event) -> event.dataPackRegistry(
                OreFoodDefinition.REGISTRY_KEY,
                OreFoodDefinition.CODEC,
                OreFoodDefinition.CODEC));

        // 让数据包里定义的每一种牛排都出现在创造模式「食物与饮品」页。
        modEventBus.addListener(OreFoodItems::addToCreativeTab);

        // 启动诊断：把动态注册表里实际加载到的牛排定义打进日志。
        // 数据包路径或 Codec 写错时原版是**静默**返回空注册表的（不会抛异常），
        // 有这行日志就能一眼看出「数据包到底生效了没有」。
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            Registry<OreFoodDefinition> registry = event.getServer().registryAccess()
                    .lookupOrThrow(OreFoodDefinition.REGISTRY_KEY);
            LOGGER.info("Loaded {} ore food definition(s): {}", registry.size(), registry.keySet());
            logEnchantmentEntries(event);
        });

        LOGGER.info("{} v{} initialized on NeoForge (Minecraft 26.1.2); shared package {}",
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
     * 写错了不会有任何报错——附魔会安静地哪都拿不到。
     *
     * <p>最关键的是每个非宝藏附魔身上必须出现 {@code non_treasure}：
     * 原版的 {@code in_enchanting_table} / {@code tradeable} / {@code on_random_loot}
     * 都是<b>引用</b> {@code #minecraft:non_treasure} 的，进了它就等于全都有了。
     */
    private static void logEnchantmentEntries(ServerStartedEvent event) {
        Registry<Enchantment> enchantments = event.getServer().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT);
        for (FoodEnchantment spec : FoodEnchantment.values()) {
            // 26.1.2 只提供 get(Identifier) -> Optional<Holder.Reference>；
            // 按 ResourceKey 取值的入口改名为 getValue 且返回裸对象。
            Identifier id = Identifier.fromNamespaceAndPath(OreFoodConstants.MOD_ID, spec.path());
            String tags = enchantments.get(id)
                    .map(holder -> holder.tags()
                            .map(tag -> tag.location().getPath())
                            .sorted()
                            .collect(Collectors.joining(", ")))
                    .orElse("<NOT REGISTERED>");
            LOGGER.info("  {} -> [{}]", spec.fullId(), tags);
        }
    }
}
