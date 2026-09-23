package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册（NeoForge 1.21.1）。
 *
 * <p><b>只注册一个物品</b> {@code orefood:ore_food}。铁/金/钻石/下界合金牛排
 * 全都不是独立物品，而是「同一个物品 + 不同的 {@code orefood:definition} 组件」，
 * 由数据包通过动态注册表声明（见 {@link com.lyingice.orefood.data.OreFoodDefinition}）。
 *
 * <p>注册时给的食物属性是<b>刻意的零值</b>（nutrition 0、saturation 0、alwaysEdible）：
 * 它只负责让物品「可被食用」，真正的营养由 Mixin 在吃下去的那一刻按定义加算
 * （见 {@code com.lyingice.orefood.mixin.LivingEntityEatMixin}）。
 * 这样无论物品栈是从创造栏、{@code /give} 还是别处来，数值都一致。
 */
public final class OreFoodItems {

    /** 物品注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreFoodConstants.MOD_ID);

    /**
     * 矿物食品。
     *
     * <ul>
     *   <li>{@code stacksTo(1)}：带耐久的物品堆叠会让「整堆共享一份耐久」，
     *       语义混乱，因此强制不可堆叠；</li>
     *   <li>{@code durability(1)}：只为让耐久相关组件存在（见类注释）；</li>
     *   <li>食物属性全零 + {@code alwaysEdible}：见类注释。</li>
     * </ul>
     */
    public static final DeferredItem<OreFoodItem> ORE_FOOD = ITEMS.registerItem(
            "ore_food",
            properties -> new OreFoodItem(properties
                    .stacksTo(1)
                    .durability(1)
                    .rarity(Rarity.COMMON)
                    .food(new FoodProperties.Builder()
                            .nutrition(0)
                            .saturationModifier(0.0F)
                            .alwaysEdible()
                            .build())));

    /**
     * 把数据包里定义的每一种牛排都塞进「食物与饮品」标签页。
     *
     * <p>此处能拿到动态注册表，是因为创造标签页重建时数据包已经加载完毕；
     * {@code getParameters().holders()} 就是当时的 {@code HolderLookup.Provider}。
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.FOOD_AND_DRINKS.equals(event.getTabKey())) {
            return;
        }

        HolderLookup.RegistryLookup<OreFoodDefinition> lookup =
                event.getParameters().holders().lookupOrThrow(OreFoodDefinition.REGISTRY_KEY);

        lookup.listElements().forEach(holder ->
                event.accept(stackFor(holder.key().location(), holder.value())));
    }

    /**
     * 按定义造一个矿物牛排物品栈。
     *
     * @param id         定义 id，同时决定显示名（{@code item.<命名空间>.<路径>}）
     * @param definition 牛排定义
     */
    public static ItemStack stackFor(ResourceLocation id, OreFoodDefinition definition) {
        ItemStack stack = new ItemStack(ORE_FOOD.get());
        stack.set(OreFoodComponents.DEFINITION.get(), definition);
        stack.set(DataComponents.ITEM_NAME, Component.translatable(nameKey(id)));
        return stack;
    }

    /** @return 该定义对应的语言文件键，例如 {@code item.orefood.iron_steak}。 */
    public static String nameKey(ResourceLocation id) {
        return "item." + id.getNamespace() + "." + id.getPath();
    }

    /** 把物品注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private OreFoodItems() {
        throw new AssertionError("No instances.");
    }
}
