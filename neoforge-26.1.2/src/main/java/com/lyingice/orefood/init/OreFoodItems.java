package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.OreFoodTable;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.item.enchantment.Repairable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品注册（NeoForge 26.1.2）。
 *
 * <p><b>2.0</b>：矿物食品从「五种牛排」扩成 <b>5 矿物 × 10 食物 = 50 个物品</b>。
 * 它们不再手写，而是由 {@link OreFoodTable} 的两层循环生成——数值规则与清单都在那边，
 * 这里只负责「按 id 注册物品、把定义塞进去、放进创造栏」。
 *
 * <p>物品 id 就是 {@code <矿物>_<食物>}，与贴图、模型、配方、语言键一一对应。
 *
 * <p>附魔能力与物品模型在这个版本都是数据组件，注册时一并写好（见 {@code register}）。
 *
 * <p>注册时按定义写入<b>真实食物属性</b>：26.1.2 的 {@link FoodProperties} 是三字段 record，
 * 第二参 {@code saturation} 就是<b>绝对饱和度</b>，直接构造即可精确表达定义值。
 * 基础营养由 {@code FoodProperties#onConsume}（{@code ConsumableListener}）在食用时结算，
 * Mixin 只额外补「饱腹」附魔加成（见 {@code OreFoodConsume} / {@code ConsumableMixin}）。
 */
public final class OreFoodItems {

    /** 物品注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(OreFoodConstants.MOD_ID);

    /**
     * 全部矿物食品，顺序与 {@link OreFoodTable#all()} 一致（外层矿物、内层食物）——
     * 创造栏与启动自检都按这个顺序。
     */
    public static final List<DeferredItem<OreFoodItem>> ALL;

    static {
        List<DeferredItem<OreFoodItem>> items = new ArrayList<>(OreFoodTable.all().size());
        for (OreFoodTable.ItemKey key : OreFoodTable.all()) {
            items.add(register(key));
        }
        ALL = List.copyOf(items);
    }

    /**
     * 注册一个矿物食品。
     *
     * <ul>
     *   <li>{@code stacksTo(1)}：带耐久的物品堆叠会让「整堆共享一份耐久」；</li>
     *   <li>{@code durability(1)}：只为让 {@code MAX_DAMAGE} / {@code DAMAGE} 组件存在，
     *       真正的上限由 {@link OreFoodItem#getMaxDamage(ItemStack)} 按定义给出；</li>
     *   <li>{@code ITEM_MODEL}：按物品 id 指向自己的模型文件；</li>
     *   <li>{@code ENCHANTABLE}：26.1.2 的附魔能力走组件，0 时不能设（构造器要求正数）；</li>
     *   <li>食物属性按定义写入（record 直构，第二参是绝对饱和度）：见类注释。</li>
     * </ul>
     *
     * <p>注意定义是在 {@code supplier} 里构造的——那时物品与状态效果都已完成注册。
     */
    private static DeferredItem<OreFoodItem> register(OreFoodTable.ItemKey key) {
        Identifier id = Identifier.fromNamespaceAndPath(OreFoodConstants.MOD_ID, key.id());
        return ITEMS.registerItem(key.id(), properties -> {
            OreFoodDefinition definition = definition(key);
            Item.Properties props = properties
                    .stacksTo(1)
                    .durability(1)
                    .rarity(Rarity.COMMON)
                    .component(DataComponents.ITEM_MODEL, id)
                    // 26.1.2 的 record 第二参是绝对饱和度，直构即精确。
                    .food(new FoodProperties(definition.nutrition(), definition.saturation(), true));
            if (definition.enchantmentValue() > 0) {
                // Enchantable 的构造器要求 value > 0，所以 0 时不能设。
                props = props.component(DataComponents.ENCHANTABLE,
                        new Enchantable(definition.enchantmentValue()));
            }
            // 铁砧里的修复材料：钻石牛排用钻石修、铜牛排用铜锭修。
            Item repairMaterial = resolveItem(definition.repairItemId());
            if (repairMaterial != null) {
                props = props.component(DataComponents.REPAIRABLE,
                        new Repairable(HolderSet.direct(repairMaterial.builtInRegistryHolder())));
            }
            if (definition.fireResistant()) {
                // 下界合金与其它下界合金制品一样，掉进岩浆或火里不会被烧毁。
                props.fireResistant();
            }
            return new OreFoodItem(props, definition);
        });
    }

    /**
     * @param itemId 物品 id，例如 {@code minecraft:diamond}
     * @return 对应的物品；id 为空或无效时返回 {@code null}
     */
    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(itemId);
        return id == null ? null : BuiltInRegistries.ITEM.get(id).map(holder -> holder.value()).orElse(null);
    }

    /** @return 该组合的完整定义（营养 / 饱和度 / 耐久 / 附魔能力 / 负面效果）。 */
    private static OreFoodDefinition definition(OreFoodTable.ItemKey key) {
        List<PotionEffectSpec> effects = new ArrayList<>();
        for (OreFoodTable.Debuff debuff : OreFoodTable.debuffs(key.tier(), key.base())) {
            effects.add(PotionEffectSpec.of(debuff));
        }
        return new OreFoodDefinition(
                OreFoodTable.nutrition(key.tier(), key.base()),
                OreFoodTable.saturation(key.tier(), key.base()),
                key.tier().durability(),
                key.tier().enchantmentValue(),
                List.copyOf(effects),
                // 铁砧修复材料：与矿物等级对应（钻石牛排用钻石修）。
                key.tier().repairItemId(),
                // 下界合金牛排抗火，掉进岩浆/火里不会被烧毁。
                key.tier().fireResistant());
    }

    /**
     * 把 50 个矿物食品放进「食物与饮品」标签页，并**各自插到对应原版食物的后面**：
     * 熟牛排 → 铜/铁/金/钻/下界合金牛排 → 面包 → 铜/铁/金/钻/下界合金面包 → ……
     *
     * <p>每次以**上一个插入的物品**为锚点（而不是始终拿原版食物），否则连续 insertAfter
     * 同一个目标会让顺序反过来。
     *
     * <p><b>NeoForge 的两个断言</b>（见 {@code BuildCreativeModeTabContentsEvent} 源码）：
     * <ul>
     *   <li>{@code assertTargetExists}——锚点不在本页时 {@code insertAfter} 直接抛
     *       {@code IllegalArgumentException}；</li>
     *   <li>{@code assertNewEntryDoesNotAlreadyExists}——<b>要加进本页的物品必须还不在本页里</b>。
     *       所以绝不能「插完再 accept 兜底」：同一个物品第二次加进去就会崩
     *       （2.0 曾因此在打开物品栏时崩溃）。1.20.1 的 Forge 没有这两个断言
     *       （{@code putAfter} 是覆盖语义），两边不能写成一样的。</li>
     * </ul>
     * 因此这里先查 {@code getParentEntries()} / {@code getSearchEntries()} 确认锚点在不在，
     * 不在就退化成追加到末尾（并记一条 WARN）。
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.FOOD_AND_DRINKS.equals(event.getTabKey())) {
            return;
        }
        ObjectSortedSet<ItemStack> parentEntries = event.getParentEntries();
        ObjectSortedSet<ItemStack> searchEntries = event.getSearchEntries();
        int inserted = 0;
        int appended = 0;
        for (OreFoodTable.TabInsertion insertion : OreFoodTable.creativeTabPlan()) {
            ItemStack previous = stackOf(insertion.anchorItemId());
            for (String itemId : insertion.itemIds()) {
                ItemStack stack = stackOf(itemId);
                if (stack == null) {
                    continue;
                }
                if (previous != null && parentEntries.contains(previous) && searchEntries.contains(previous)) {
                    event.insertAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    inserted++;
                } else {
                    com.mojang.logging.LogUtils.getLogger().warn(
                            "[orefood] 创造栏锚点 {} 不在本页，{} 退化为追加到末尾",
                            insertion.anchorItemId(), itemId);
                    event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                    appended++;
                }
                previous = stack;
            }
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "[orefood] 创造栏：插入 {} 个矿物食品，退化追加 {} 个", inserted, appended);
    }

    /**
     * @return 该物品 id 对应的栈；id 无效时返回 {@code null}。
     *
     * <p>注意 26.1.2 的 {@code Registry#get(Identifier)} 返回 {@code Optional<Holder.Reference>}
     * （1.20.1 / 1.21.1 是直接给值），所以要再取一次 {@code value()}。
     */
    private static ItemStack stackOf(String itemId) {
        Identifier key = Identifier.tryParse(itemId);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(key)
                .map(holder -> new ItemStack(holder.value()))
                .orElse(null);
    }

    /** 把物品注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private OreFoodItems() {
        throw new AssertionError("No instances.");
    }
}
