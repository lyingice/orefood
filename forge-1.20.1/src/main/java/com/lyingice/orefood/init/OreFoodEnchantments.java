package com.lyingice.orefood.init;

import com.lyingice.orefood.FoodEnchantment;
import com.lyingice.orefood.OreFoodConstants;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 五种食物附魔的注册（Forge 1.20.1）。
 *
 * <p><b>这里与 1.21.1 / 26.1.2 是本质不同的两条路</b>：1.21 起附魔是数据包驱动的
 * 动态注册表，模组只需放 JSON；而 1.20.1 的附魔是硬编码的静态注册表，
 * 必须在这里逐个 {@code register}。
 *
 * <p><b>「只对食物生效」怎么实现</b>：1.20.1 <b>不存在</b>加载器提供的通用食物标签
 * （{@code forge:food} 不是 Forge 定义的，1.20.1 的原版也只有
 * {@code piglike_food} / {@code fox_food} / {@code sniffer_food} 这类零散标签，
 * 没有「所有食物」这个标签）。因此这里改用 Forge 的
 * {@link EnchantmentCategory#create}，用 {@code Item#isEdible()} 判定——
 * 等价于「凡是能吃的都能附」，比某个具体标签覆盖得更全。
 * 为了让依赖标签的其它模组也能看到本模组的牛排，数据包里另外提供了
 * {@code forge:food} 与 {@code orefood:foods} 两个标签。
 */
public final class OreFoodEnchantments {

    /** 「能吃的物品」这一附魔类别；Forge 允许在枚举上扩展自定义类别。 */
    public static final EnchantmentCategory FOOD_CATEGORY =
            EnchantmentCategory.create(OreFoodConstants.MOD_ID + "_food", isEdible());

    /** 附魔注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, OreFoodConstants.MOD_ID);

    /** 强效恢复：瞬间治疗 + 生命恢复，最大 4 级。 */
    public static final RegistryObject<Enchantment> POWERFUL_RECOVERY =
            register(FoodEnchantment.POWERFUL_RECOVERY);

    /** 肾上腺素：力量 + 速度，最大 3 级。 */
    public static final RegistryObject<Enchantment> ADRENALINE =
            register(FoodEnchantment.ADRENALINE);

    /** 强身健体：生命吸收 + 抗性提升，最大 5 级。 */
    public static final RegistryObject<Enchantment> FITNESS =
            register(FoodEnchantment.FITNESS);

    /** 饱腹：额外饱食度，最大 3 级。 */
    public static final RegistryObject<Enchantment> SATIETY =
            register(FoodEnchantment.SATIETY);

    /** 盛宴：饱和效果（宝藏附魔），最大 3 级。 */
    public static final RegistryObject<Enchantment> FEAST =
            register(FoodEnchantment.FEAST);

    /**
     * 附魔 id 与注册对象的配对，供启动自检遍历用。
     *
     * <p>注意声明顺序：必须排在五个注册对象之后，否则静态初始化时拿到的是 null。
     */
    private static final List<Entry> ENTRIES = List.of(
            new Entry(id(FoodEnchantment.POWERFUL_RECOVERY), POWERFUL_RECOVERY),
            new Entry(id(FoodEnchantment.ADRENALINE), ADRENALINE),
            new Entry(id(FoodEnchantment.FITNESS), FITNESS),
            new Entry(id(FoodEnchantment.SATIETY), SATIETY),
            new Entry(id(FoodEnchantment.FEAST), FEAST));

    /** @return 五个附魔的 id 与注册对象的配对。 */
    public static List<Entry> entries() {
        return ENTRIES;
    }

    /** 一对「附魔 id + 注册对象」。 */
    public record Entry(ResourceLocation id, RegistryObject<Enchantment> enchantment) {
    }

    private static ResourceLocation id(FoodEnchantment spec) {
        return new ResourceLocation(OreFoodConstants.MOD_ID, spec.path());
    }

    private static Predicate<Item> isEdible() {
        return Item::isEdible;
    }

    private static RegistryObject<Enchantment> register(FoodEnchantment spec) {
        return ENCHANTMENTS.register(spec.path(), () -> new com.lyingice.orefood.enchantment.FoodEnchantment(
                spec.treasure() ? Enchantment.Rarity.VERY_RARE : Enchantment.Rarity.COMMON,
                FOOD_CATEGORY,
                spec.maxLevel(),
                spec.treasure()));
    }

    /** 把附魔注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        ENCHANTMENTS.register(modEventBus);
    }

    private OreFoodEnchantments() {
        throw new AssertionError("No instances.");
    }
}
