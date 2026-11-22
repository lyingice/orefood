package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.OreFoodTable;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.MutableHashedLinkedMap;
import java.util.List;
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
 * <p><b>2.0</b>：矿物食品从「五种牛排」扩成 <b>5 矿物 × 10 食物 = 50 个物品</b>。
 * 它们不再手写，而是由 {@link OreFoodTable} 的两层循环生成——数值规则与清单都在那边，
 * 这里只负责「按 id 注册物品、把定义塞进去、放进创造栏」。
 *
 * <p>物品 id 就是 {@code <矿物>_<食物>}（例如 {@code copper_beef}、{@code netherite_salmon}），
 * 与贴图、模型、配方、语言键一一对应。
 *
 * <p>注册时按定义写入<b>真实食物属性</b>（饱和度以系数表示，M = S/(2N)，与原版
 * {@code FoodData} 语义一致，浮点往返最多 1 ulp 误差）；基础营养由原版
 * {@code Player.eat} 结算，Mixin 只补「饱腹」附魔加成
 * （见 {@code com.lyingice.orefood.mixin.LivingEntityEatMixin} 与 {@code OreFoodConsume}）。
 */
public final class OreFoodItems {

    /** 物品注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, OreFoodConstants.MOD_ID);

    /**
     * 全部矿物食品，顺序与 {@link OreFoodTable#all()} 一致（外层矿物、内层食物）——
     * 创造栏与启动自检都按这个顺序。
     */
    public static final List<RegistryObject<OreFoodItem>> ALL;

    static {
        List<RegistryObject<OreFoodItem>> items = new ArrayList<>(OreFoodTable.all().size());
        for (OreFoodTable.ItemKey key : OreFoodTable.all()) {
            items.add(register(key));
        }
        ALL = List.copyOf(items);
    }

    /**
     * 注册一个矿物食品。
     *
     * <ul>
     *   <li>{@code stacksTo(1)}：带耐久的物品堆叠会让「整堆共享一份耐久」，语义混乱；</li>
     *   <li>{@code durability(1)}：只为让物品被判定为「可损坏」，真正的上限由
     *       {@link OreFoodItem#getMaxDamage(ItemStack)} 按定义给出；</li>
     *   <li>食物属性按定义写真实值 + {@code alwaysEat()}：见类注释。</li>
     * </ul>
     *
     * <p>注意定义是在 {@code supplier} 里构造的——那时物品与状态效果都已完成注册。
     * 定义只算一次，食物属性与 {@link OreFoodItem} 共用同一份。
     */
    private static RegistryObject<OreFoodItem> register(OreFoodTable.ItemKey key) {
        return ITEMS.register(key.id(), () -> {
            OreFoodDefinition definition = definition(key);
            return new OreFoodItem(foodProperties(key, definition), definition);
        });
    }

    /**
     * 按定义生成真实食物属性。
     *
     * <p>饱和度以原版系数表示：{@code FoodData} 的语义是
     * {@code saturation = nutrition × saturationModifier × 2}，所以系数写作
     * {@code S / (2N)}；{@code nutrition} 为 0 时系数也给 0，避免除零。
     *
     * @param key        该组合（用来判断是否需要抗火）
     * @param definition 该组合的完整定义（营养与饱和度）
     * @return 带真实营养与饱和度系数的物品属性
     */
    private static Item.Properties foodProperties(
            OreFoodTable.ItemKey key, OreFoodDefinition definition) {
        int nutrition = definition.nutrition();
        float saturationModifier = nutrition > 0 ? definition.saturation() / (nutrition * 2.0F) : 0.0F;
        Item.Properties properties = new Item.Properties()
                .stacksTo(1)
                .durability(1)
                .food(new FoodProperties.Builder()
                        .nutrition(nutrition)
                        .saturationMod(saturationModifier)
                        .alwaysEat()
                        .build());
        if (key.tier().fireResistant()) {
            // 下界合金与其它下界合金制品一样，掉进岩浆或火里不会被烧毁。
            properties.fireResistant();
        }
        return properties;
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
     * <p>Forge 1.20.1 的事件没有 {@code insertAfter}，但给了可变的 {@link #getEntries()}
     * （{@code MutableHashedLinkedMap}），用它的 {@code putAfter} 达到同样效果。
     *
     * <p>每次以**上一个插入的物品**为锚点（而不是始终拿原版食物），否则连续 putAfter
     * 同一个目标会让顺序反过来。原版食物不在这一页时退化为追加到末尾。
     */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.FOOD_AND_DRINKS.equals(event.getTabKey())) {
            return;
        }
        MutableHashedLinkedMap<ItemStack, CreativeModeTab.TabVisibility> entries = event.getEntries();
        int inserted = 0;
        int appended = 0;
        for (OreFoodTable.TabInsertion insertion : OreFoodTable.creativeTabPlan()) {
            ItemStack previous = stackOf(insertion.anchorItemId());
            for (String itemId : insertion.itemIds()) {
                ItemStack stack = stackOf(itemId);
                if (stack == null) {
                    continue;
                }
                if (previous != null && entries.contains(previous)) {
                    entries.putAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
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

    /** @return 该物品 id 对应的栈；id 无效时返回 {@code null}。 */
    private static ItemStack stackOf(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        Item item = key == null ? null : BuiltInRegistries.ITEM.get(key);
        return item == null ? null : new ItemStack(item);
    }

    /** 把物品注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private OreFoodItems() {
        throw new AssertionError("No instances.");
    }
}
