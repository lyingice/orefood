package com.lyingice.orefood.neoforge;

import com.lyingice.orefood.FoodEnchantment;
import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.init.OreFoodItems;
import com.lyingice.orefood.item.OreFoodItem;
import java.util.stream.Collectors;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge / Minecraft 26.1.2 的模组入口。
 *
 * <p>这里只做装配，业务逻辑分别在：
 * <ul>
 *   <li>{@link OreFoodItems}：五种牛排物品（各自一个真实物品 id）；</li>
 *   <li>{@code com.lyingice.orefood.logic.OreFoodConsume} 配合
 *       {@code com.lyingice.orefood.mixin.ConsumableMixin}：食用结算与耐久消耗；</li>
 *   <li>{@code com.lyingice.orefood.client.OreFoodTooltip}：提示框。</li>
 * </ul>
 *
 * <p><b>1.2.0 删掉的东西</b>：动态注册表（{@code DataPackRegistryEvent}）、数据组件
 * （{@code OreFoodComponents}）、自定义配料、配方语法糖（{@code ModifyRecipeJsonsEvent}）
 * 与 JEI 变体插件——五种牛排改真物品后这些全都不需要了。
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
        // 附魔效果组件：让「附魔吃下去给什么效果」完全由附魔 JSON 决定。
        com.lyingice.orefood.data.OreFoodEffects.EFFECT_COMPONENTS.register(modEventBus);

        // 让五种牛排出现在创造模式「食物与饮品」页。
        modEventBus.addListener(OreFoodItems::addToCreativeTab);
        // AppleSkin 联动：事件只在客户端触发，未装 AppleSkin 时内部什么都不做。
        modEventBus.addListener(
                com.lyingice.orefood.client.OreFoodAppleSkinSetup::onClientSetup);

        // 启动自检：打印五种牛排的实际定义，以及每个附魔实际挂在哪些原版附魔标签下。
        // 数值现在写在代码里（编译期就能发现笔误），这行日志主要用于
        // 「加了新口味之后确认它真的注册上、数值真的是想要的」。
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            OreFoodItems.ALL.forEach(
                    item -> LOGGER.info("  {} -> {}", item.getId(), item.get().definition()));
            logFoodPropertiesCheck();
            logEnchantmentEntries(event);
        });

        LOGGER.info("{} v{} initialized on NeoForge (Minecraft 26.1.2); shared package {}",
                OreFoodConstants.MOD_NAME,
                OreFoodConstants.MOD_VERSION,
                OreFoodConstants.PACKAGE_ROOT);
    }

    /**
     * 启动自检：核对每个物品注册的 FOOD 组件与定义是否一致。
     *
     * <p>26.1.2 的 {@link FoodProperties} 是 record，注册时直接用定义值构造，
     * 因此这里可以要求 nutrition 与 saturation <b>精确相等</b>（{@code Float.compare}）。
     * 一致就说明定义确实进入了物品属性——精妙背包等要求 nutrition &gt;= 1 的模组、
     * 以及 AppleSkin 都能直接读到真实数值。
     */
    private static void logFoodPropertiesCheck() {
        int total = OreFoodItems.ALL.size();
        int matched = 0;
        for (DeferredItem<OreFoodItem> item : OreFoodItems.ALL) {
            OreFoodDefinition definition = item.get().definition();
            FoodProperties food = new ItemStack(item.get()).get(DataComponents.FOOD);
            if (food == null) {
                LOGGER.error("[orefood] 食物属性自检失败：{} 未注册 FOOD 组件（定义值 nutrition={} saturation={}）",
                        item.getId(), definition.nutrition(), definition.saturation());
                continue;
            }
            if (food.nutrition() != definition.nutrition()
                    || Float.compare(food.saturation(), definition.saturation()) != 0) {
                LOGGER.error(
                        "[orefood] 食物属性自检失败：{} 注册值 nutrition={} saturation={}，定义值 nutrition={} saturation={}",
                        item.getId(), food.nutrition(), food.saturation(),
                        definition.nutrition(), definition.saturation());
                continue;
            }
            matched++;
        }
        if (matched == total) {
            LOGGER.info("[orefood] 食物属性自检：{}/{} 与定义一致", matched, total);
        }
    }

    /**
     * 启动自检：打印每个食物附魔实际挂在哪些原版附魔标签下。
     *
     * <p>1.21 起「能不能在附魔台刷出 / 能不能交易 / 会不会出现在战利品里」
     * <b>完全由数据包标签决定</b>（{@code in_enchanting_table} / {@code tradeable} /
     * {@code on_random_loot}），而这些标签是「非宝藏附魔 + 少数宝藏附魔」的组合，
     * 写错了不会有任何报错——附魔会安静地哪都拿不到。有这么一行就能立刻看出来。
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
            // 食用效果来自数据包（附魔 JSON 里的 orefood:food_effects），0 条意味着数据包没加载上。
            int foodEffects = enchantments.get(id)
                    .map(holder -> com.lyingice.orefood.data.OreFoodEffects
                            .specsOf(holder.value()).size())
                    .orElse(0);
            LOGGER.info("  {} -> [{}] 食用效果 {} 条", spec.fullId(), tags, foodEffects);
        }
    }
}
