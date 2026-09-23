package com.lyingice.template.init;

import com.lyingice.template.TemplateConstants;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册（NeoForge 26.1.2）。
 *
 * <p>与 Forge 1.20.1 的同类文件 {@code forge-1.20.1/.../init/TemplateItems.java}
 * 逐行对照着看，差异一目了然：
 *
 * <ul>
 *   <li>{@code DeferredRegister.createItems(MOD_ID)} 取代
 *       {@code DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)}；</li>
 *   <li>{@code DeferredItem<Item>} 取代 {@code RegistryObject<Item>}；</li>
 *   <li>{@code registerItem(name, Item.Properties -> Item)} 是 NeoForge 的便利重载，
 *       它会把物品 id 交给 {@code Item.Properties}，所以属性设置不会影响 id 绑定；</li>
 *   <li>标签页插入用 {@code event.insertAfter(...)}，Forge 则是
 *       {@code event.getEntries().putAfter(...)}。</li>
 * </ul>

 * <p><b>本版本的资源差异</b>：26.1.2 的物品模型不再靠
 * {@code models/item/<id>.json} 自动映射，而是要在
 * {@code assets/template/items/<id>.json} 里显式指向模型
 * （那个目录在本模块的 {@code src/main/resources} 下，不在共享资源层里）。
 * 这是 26.1 相对 1.21.1 最容易漏掉、且只在运行期才报错的改动之一。
 */
public final class TemplateItems {

    /** 物品注册表；mod id 与 {@link TemplateConstants#MOD_ID} 一致。 */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(TemplateConstants.MOD_ID);

    /**
     * 示例物品。
     *
     * <p>这里直接用原版 {@link Item}，不自定义子类 —— 模板保持最小。
     */
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerItem(
            "example_item",
            Item::new);

    /**
     * 把示例物品接在「材料」标签页里某件原版物品的后面。
     *
     * <p>原版没有单独的「示例物品」分类，这里挑木棍作为锚点，便于一眼看到效果。
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.INGREDIENTS.equals(event.getTabKey())) {
            return;
        }

        ItemStack anchor = new ItemStack(Items.STICK);
        ItemStack stack = new ItemStack(EXAMPLE_ITEM.get());
        event.insertAfter(anchor, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    /** 把注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private TemplateItems() {
        throw new AssertionError("No instances.");
    }
}
