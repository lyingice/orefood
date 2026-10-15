package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.OreFoodTable;
import com.lyingice.orefood.data.OreFoodDefinition;
import com.lyingice.orefood.data.PotionEffectSpec;
import com.lyingice.orefood.item.OreFoodItem;
import java.util.ArrayList;
import java.util.List;
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
 * <p><b>2.0</b>：矿物食品从「五种牛排」扩成 <b>5 矿物 × 10 食物 = 50 个物品</b>。
 * 它们不再手写，而是由 {@link OreFoodTable} 的两层循环生成——数值规则与清单都在那边，
 * 这里只负责「按 id 注册物品、把定义塞进去、放进创造栏」。
 *
 * <p>物品 id 就是 {@code <矿物>_<食物>}（例如 {@code copper_beef}、{@code netherite_salmon}），
 * 与贴图、模型、配方、语言键一一对应。
 *
 * <p>附魔能力走 {@code Item#getEnchantmentValue(ItemStack)} 覆写（1.21.1 还没有组件版）。
 *
 * <p>注册时给的食物属性是<b>刻意的零值</b>（nutrition 0、saturation 0、alwaysEdible）：
 * 它只负责让物品「可被食用」，真正的营养由 Mixin 在吃下去的那一刻按定义加算
 * （见 {@code OreFoodConsume}）。
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
     *   <li>{@code durability(1)}：只为让耐久相关组件存在，真正的上限由
     *       {@link OreFoodItem#getMaxDamage(ItemStack)} 按定义给出；</li>
     *   <li>食物属性全零 + {@code alwaysEdible}：见类注释。</li>
     * </ul>
     *
     * <p>注意定义是在 {@code supplier} 里构造的——那时物品与状态效果都已完成注册。
     */
    private static DeferredItem<OreFoodItem> register(OreFoodTable.ItemKey key) {
        return ITEMS.registerItem(key.id(), properties -> {
            properties
                    .stacksTo(1)
                    .durability(1)
                    .rarity(Rarity.COMMON)
                    .food(new FoodProperties.Builder()
                            .nutrition(0)
                            .saturationModifier(0.0F)
                            .alwaysEdible()
                            .build());
            return new OreFoodItem(properties, definition(key));
        });
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
                List.copyOf(effects));
    }

    /** 把 50 个矿物食品放进「食物与饮品」标签页。 */
    public static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!CreativeModeTabs.FOOD_AND_DRINKS.equals(event.getTabKey())) {
            return;
        }
        for (DeferredItem<OreFoodItem> item : ALL) {
            event.accept(new ItemStack(item.get()));
        }
    }

    /** 把物品注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private OreFoodItems() {
        throw new AssertionError("No instances.");
    }
}
