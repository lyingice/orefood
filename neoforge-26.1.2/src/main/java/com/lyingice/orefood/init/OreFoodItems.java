package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册（NeoForge 26.1.2）。
 *
 * <p><b>只注册一个物品</b> {@code orefood:ore_food}。铁/金/钻石/下界合金牛排
 * 全都不是独立物品，而是「同一个物品 + 不同的 {@code orefood:definition} 组件」，
 * 由数据包通过动态注册表声明（见 {@link OreFoodDefinition}）。
 *
 * <p>注册时给的食物属性是<b>刻意的零值</b>：它只负责让物品「可被食用」，
 * 真正的营养由 Mixin 在吃下去的那一刻按定义加算
 * （见 {@code com.lyingice.orefood.mixin.ConsumableMixin}）。
 *
 * <p><b>与 1.21.1 侧的三处差异</b>：
 * <ul>
 *   <li>{@code ResourceLocation} 改名 {@link Identifier}，
 *       且 {@code ResourceKey#location()} 改名 {@code identifier()}；</li>
 *   <li>附魔能力不再由 {@code Item#getEnchantmentValue} 提供，改为
 *       {@code minecraft:enchantable} 数据组件（{@link Enchantable}），
 *       所以要在造物品栈时写进组件；</li>
 *   <li>物品模型需要额外的 {@code assets/orefood/items/ore_food.json}
 *       指向真正的模型文件（1.21.4 起的新格式，26.1.2 沿用）。</li>
 * </ul>
 */
public final class OreFoodItems {

    /** 物品注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreFoodConstants.MOD_ID);

    /**
     * 矿物食品。
     *
     * <ul>
     *   <li>{@code stacksTo(1)}：带耐久的物品堆叠会让「整堆共享一份耐久」，语义混乱；</li>
     *   <li>{@code durability(1)}：只为让 {@code MAX_DAMAGE} / {@code DAMAGE} 组件存在，
     *       真正的上限由 {@link OreFoodItem#getMaxDamage(ItemStack)} 按定义给出；</li>
     *   <li>食物属性全零 + {@code alwaysEdible()}：见类注释。</li>
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
     * <p>此处能拿到动态注册表，是因为创造标签页重建时数据包已经加载完毕。
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.FOOD_AND_DRINKS.equals(event.getTabKey())) {
            return;
        }

        HolderLookup.RegistryLookup<OreFoodDefinition> lookup =
                event.getParameters().holders().lookupOrThrow(OreFoodDefinition.REGISTRY_KEY);

        lookup.listElements().forEach(holder ->
                event.accept(stackFor(holder.key().identifier(), holder.value())));
    }

    /**
     * 一块牛排该带哪些组件——「一块牛排长什么样」的<b>唯一</b>权威来源。
     *
     * <p><b>为什么单独提出来</b>：配方语法糖也要用它，而配方 JSON 是在
     * {@code ModifyRecipeJsonsEvent} 里展开的——那个时机<b>不能构造 {@code ItemStack}</b>。
     * 26.1.2 的 {@code ItemStack} 构造器会去读 {@code Holder.Reference#components()}，
     * 而此时物品的组件还没绑定，直接抛
     * {@code NullPointerException: Components not bound yet}（实测崩过，整个服务器起不来）。
     * 所以这里返回组件补丁，由调用方决定是套到物品栈上还是直接编码成 JSON。
     *
     * @param id         定义 id，同时决定显示名（{@code item.<命名空间>.<路径>}）与外观
     * @param definition 牛排定义
     */
    public static DataComponentPatch componentsFor(Identifier id, OreFoodDefinition definition) {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();
        builder.set(OreFoodComponents.DEFINITION.get(), definition);
        builder.set(DataComponents.ITEM_NAME, Component.translatable(nameKey(id)));
        // 26.1.2 有原生的 ITEM_MODEL 组件，直接按定义 id 指向
        // assets/orefood/items/<路径>.json —— 比 custom_model_data 分发直观得多，
        // 也不需要数据包额外给出 model_data（那个字段是给 1.20.1 / 1.21.1 用的）。
        builder.set(DataComponents.ITEM_MODEL, id);
        if (definition.enchantmentValue() > 0) {
            // Enchantable 的构造器要求 value > 0，所以 0 时不设组件。
            builder.set(DataComponents.ENCHANTABLE, new Enchantable(definition.enchantmentValue()));
        }
        return builder.build();
    }

    /** 按定义造一个矿物牛排物品栈；组件取自 {@link #componentsFor}，与配方产物完全一致。 */
    public static ItemStack stackFor(Identifier id, OreFoodDefinition definition) {
        ItemStack stack = new ItemStack(ORE_FOOD.get());
        stack.applyComponents(componentsFor(id, definition));
        return stack;
    }

    /** @return 该定义对应的语言文件键，例如 {@code item.orefood.iron_beef}。 */
    public static String nameKey(Identifier id) {
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
