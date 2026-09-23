package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.item.OreFoodItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册（Forge 1.20.1）。
 *
 * <p><b>只注册一个物品</b> {@code orefood:ore_food}。铁/金/钻石/下界合金牛排
 * 全都不是独立物品，而是「同一个物品 + 不同的 NBT 定义」，
 * 由数据包通过动态注册表声明（见 {@link OreFoodDefinition}）。
 *
 * <p>注册时给的食物属性是<b>刻意的零值</b>（nutrition 0、saturation 0、alwaysEat）：
 * 它只负责让物品「可被食用」，真正的营养由 Mixin 在吃下去的那一刻按定义加算
 * （见 {@code com.lyingice.orefood.mixin.LivingEntityEatMixin}）。
 *
 * <p><b>Forge 1.20.1 与 NeoForge 的写法差异（本文件是最小对照）</b>：
 * <ul>
 *   <li>建注册表：{@code DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)}
 *       —— NeoForge 用 {@code DeferredRegister.createItems(MOD_ID)}；</li>
 *   <li>注册项类型：{@code RegistryObject&lt;Item&gt;}
 *       —— NeoForge 用 {@code DeferredItem&lt;Item&gt;}；</li>
 *   <li>插标签页：{@code event.getEntries().putAfter(...)} / {@code accept(...)}
 *       —— NeoForge 是 {@code event.insertAfter(...)} / {@code accept(...)}；</li>
 *   <li>食物属性：{@code saturationMod(float)}
 *       —— 1.21.1 起改名为 {@code saturationModifier(float)}。</li>
 * </ul>
 */
public final class OreFoodItems {

    /** 物品注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OreFoodConstants.MOD_ID);

    /**
     * 矿物食品。
     *
     * <ul>
     *   <li>{@code stacksTo(1)}：带耐久的物品堆叠会让「整堆共享一份耐久」，语义混乱；</li>
     *   <li>{@code durability(1)}：只为让物品被判定为「可损坏」，真正的上限由
     *       {@link OreFoodItem#getMaxDamage(ItemStack)} 按定义给出；</li>
     *   <li>食物属性全零 + {@code alwaysEat()}：见类注释。</li>
     * </ul>
     */
    public static final RegistryObject<OreFoodItem> ORE_FOOD = ITEMS.register(
            "ore_food",
            () -> new OreFoodItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(1)
                    .food(new FoodProperties.Builder()
                            .nutrition(0)
                            .saturationMod(0.0F)
                            .alwaysEat()
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
                event.accept(stackFor(holder.key().location(), holder.value()),
                        CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
    }

    /**
     * 一块牛排该带哪些 NBT——「一块牛排长什么样」的<b>唯一</b>权威来源。
     *
     * <p><b>为什么单独提出来</b>：配方语法糖也要用它，而配方 JSON 在反序列化之前就要展开，
     * 那种时机不该构造 {@code ItemStack}（26.1.2 上会直接抛
     * {@code NullPointerException: Components not bound yet}，见那边的注释）。
     *
     * @param id         定义 id，同时决定显示名（{@code item.<命名空间>.<路径>}）
     * @param definition 牛排定义
     */
    public static CompoundTag nbtFor(ResourceLocation id, OreFoodDefinition definition) {
        CompoundTag tag = OreFoodDefinition.toTag(definition);
        // 1.20.1 没有 ITEM_NAME 数据组件，自定义名称走 NBT 的 display.Name。
        CompoundTag display = new CompoundTag();
        display.putString("Name", Component.Serializer.toJson(Component.translatable(nameKey(id))));
        tag.put("display", display);
        if (definition.modelData() > 0) {
            // 1.20.1 也没有 ITEM_MODEL 组件，外观靠 NBT 的 CustomModelData +
            // 模型文件里的 overrides 列表来切（见 assets/orefood/models/item/ore_food.json）。
            tag.putInt("CustomModelData", definition.modelData());
        }
        return tag;
    }

    /** 按定义造一个矿物牛排物品栈；NBT 取自 {@link #nbtFor}，与配方产物完全一致。 */
    public static ItemStack stackFor(ResourceLocation id, OreFoodDefinition definition) {
        ItemStack stack = new ItemStack(ORE_FOOD.get());
        stack.setTag(nbtFor(id, definition));
        return stack;
    }

    /** @return 该定义对应的语言文件键，例如 {@code item.orefood.iron_beef}。 */
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
