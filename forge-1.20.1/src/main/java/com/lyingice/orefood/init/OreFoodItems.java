package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册（Forge 1.20.1）。
 *
 * <p>这是一个最小示例：注册一个 {@code orefood:example_item}，并在「材料」标签页里
 * 把它插到某个原版物品后面。三个平台各写一份同类代码 —— 这正是多加载器架构要付出的成本，
 * 但换来的是共享层可以直接参与每个平台的编译。
 *
 * <h2>Forge 1.20.1 与 NeoForge 的写法差异（本文件是最小对照）</h2>
 * <ul>
 *   <li>建注册表：{@code DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)}
 *       —— NeoForge 用 {@code DeferredRegister.createItems(MOD_ID)}；</li>
 *   <li>注册项类型：{@code RegistryObject&lt;Item&gt;}
 *       —— NeoForge 用 {@code DeferredItem&lt;Item&gt;}；</li>
 *   <li>注册方法：{@code register(String, Supplier)}
 *       —— NeoForge 另有 {@code registerItem(String, Function&lt;Item.Properties, Item&gt;)}；</li>
 *   <li>插标签页：{@code event.getEntries().putAfter(...)}
 *       —— NeoForge 是 {@code event.insertAfter(...)}。</li>
 * </ul>
 *
 * <p>注册表必须挂到 mod 事件总线才会生效，调用点在各平台主类的构造器里。
 */
public final class OreFoodItems {

    /** 物品注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OreFoodConstants.MOD_ID);

    /**
     * 示例物品。
     *
     * <p>这里直接用原版 {@link Item}，不自定义子类 —— 模板保持最小。
     * 需要自定义行为时，把 lambda 换成 {@code () -> new YourItem(new Item.Properties())}，
     * 并把类放在本模块（平台相关）或共享层（纯逻辑）里。
     */
    public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register(
            "example_item",
            () -> new Item(new Item.Properties()));

    /**
     * 把示例物品接在「材料」标签页里某件原版物品的后面。
     *
     * <p>原版没有单独的「示例物品」分类，这里挑木棍作为锚点，便于一眼看到效果。
     * 换成自己的锚点物品时注意：锚点必须确实存在于该标签页里，否则物品不会出现。
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.INGREDIENTS.equals(event.getTabKey())) {
            return;
        }

        ItemStack anchor = new ItemStack(Items.STICK);
        ItemStack stack = new ItemStack(EXAMPLE_ITEM.get());
        event.getEntries().putAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    /** 把注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private OreFoodItems() {
        throw new AssertionError("No instances.");
    }
}
