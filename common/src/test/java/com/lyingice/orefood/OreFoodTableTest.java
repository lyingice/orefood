package com.lyingice.orefood;

import com.lyingice.orefood.OreFoodTable.Debuff;
import com.lyingice.orefood.OreFoodTable.FoodBase;
import com.lyingice.orefood.OreFoodTable.ItemKey;
import com.lyingice.orefood.OreFoodTable.MineralTier;
import com.lyingice.orefood.OreFoodTable.TabInsertion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数值表的单元测试。
 *
 * <p>放在 {@code common/src/test/java}，三个平台模块各自编译并运行——一份断言生效三次。
 *
 * <p><b>最重要的三组断言</b>：
 * <ul>
 *   <li>1.x 时代就存在的五个牛排，数值必须<b>一个比特都不变</b>（改成公式推导之后
 *       最容易悄悄漂移的就是它们）；</li>
 *   <li>负面效果：只有河豚/腐肉有，时长随等级放大，<b>只有下界合金把 I 级提到 II 级</b>；</li>
 *   <li>创造栏计划：每种原版食物后面紧跟它的五个矿物版本。</li>
 * </ul>
 */
class OreFoodTableTest {

    @Test
    @DisplayName("现有五个牛排的数值与 1.x 完全一致")
    void legacyBeefValuesAreUnchanged() {
        assertBeef(MineralTier.COPPER, 6, 9.6F, 8, 1);
        assertBeef(MineralTier.IRON, 8, 12.8F, 16, 1);
        assertBeef(MineralTier.GOLDEN, 8, 16.0F, 32, 5);
        assertBeef(MineralTier.DIAMOND, 10, 20.0F, 64, 10);
        assertBeef(MineralTier.NETHERITE, 12, 24.0F, 128, 15);
    }

    private static void assertBeef(MineralTier tier, int nutrition, float saturation,
            int durability, int enchantmentValue) {
        assertEquals(nutrition, OreFoodTable.nutrition(tier, FoodBase.BEEF),
                tier.key() + "_beef 的营养变了");
        assertEquals(saturation, OreFoodTable.saturation(tier, FoodBase.BEEF), 0.0F,
                tier.key() + "_beef 的饱和度变了");
        assertEquals(durability, tier.durability());
        assertEquals(enchantmentValue, tier.enchantmentValue());
    }

    @Test
    @DisplayName("共 50 个组合，id 唯一，顺序是外层矿物内层食物")
    void allCombinationsAreWellFormed() {
        List<ItemKey> all = OreFoodTable.all();
        assertEquals(MineralTier.values().length * FoodBase.values().length, all.size());
        assertEquals(50, all.size());

        Set<String> ids = new HashSet<>();
        for (ItemKey key : all) {
            assertTrue(ids.add(key.id()), "重复的物品 id：" + key.id());
        }

        // 第一个是 copper_beef，第 11 个（跳过 10 种食物）是 iron_beef。
        assertEquals("copper_beef", all.get(0).id());
        assertEquals("iron_beef", all.get(FoodBase.values().length).id());
        assertEquals("netherite_salmon", all.get(all.size() - 1).id());
    }

    @Test
    @DisplayName("每个组合都有合法数值")
    void everyCombinationHasSaneValues() {
        for (ItemKey key : OreFoodTable.all()) {
            int nutrition = OreFoodTable.nutrition(key.tier(), key.base());
            float saturation = OreFoodTable.saturation(key.tier(), key.base());
            assertTrue(nutrition >= 1, key.id() + " 的营养小于 1");
            assertTrue(saturation > 0.0F, key.id() + " 的饱和度不是正数");
            assertTrue(key.tier().durability() > 0, key.id() + " 的耐久不是正数");
        }
    }

    @Test
    @DisplayName("饱和度量化到一位小数")
    void saturationIsQuantised() {
        // 面包 6.0 × 1.5625 = 9.375 → 9.4
        assertEquals(9.4F, OreFoodTable.saturation(MineralTier.DIAMOND, FoodBase.BREAD), 0.0F);
        // 面包 6.0 × 1.875 = 11.25 → 11.3
        assertEquals(11.3F, OreFoodTable.saturation(MineralTier.NETHERITE, FoodBase.BREAD), 0.0F);
        // 河豚 0.2 × 0.75 = 0.15 → 0.2（Math.round(1.5) 进位）
        assertEquals(0.2F, OreFoodTable.saturation(MineralTier.COPPER, FoodBase.PUFFERFISH), 0.0F);
    }

    @Test
    @DisplayName("负面效果：只有河豚与腐肉有；时长随等级放大，只有下界合金把 I 级提到 II 级")
    void debuffsFollowDesign() {
        for (ItemKey key : OreFoodTable.all()) {
            boolean expected = key.base() == FoodBase.PUFFERFISH || key.base() == FoodBase.ROTTEN_FLESH;
            assertEquals(expected, !OreFoodTable.debuffs(key.tier(), key.base()).isEmpty(),
                    key.id() + " 的负面效果有无不对");
        }

        // 铁级 = 原版强度：河豚是中毒 II / 饥饿 III / 反胃 I（amplifier 1 / 2 / 0）。
        List<Debuff> iron = OreFoodTable.debuffs(MineralTier.IRON, FoodBase.PUFFERFISH);
        assertEquals(3, iron.size());
        assertEquals("minecraft:poison", iron.get(0).effectId());
        assertEquals(1200, iron.get(0).duration());
        assertEquals(1, iron.get(0).amplifier());
        assertEquals(1.0F, iron.get(0).probability(), 0.0F);

        // 铜：时长 ×0.5，等级一律不动。
        List<Debuff> copper = OreFoodTable.debuffs(MineralTier.COPPER, FoodBase.PUFFERFISH);
        assertEquals(600, copper.get(0).duration());
        assertEquals(1, copper.get(0).amplifier());
        assertEquals(150, copper.get(2).duration());
        assertEquals(0, copper.get(2).amplifier());

        // 下界合金：时长 ×2.5；只有「本来就是 I 级」的反胃升到 II 级。
        List<Debuff> netherite = OreFoodTable.debuffs(MineralTier.NETHERITE, FoodBase.PUFFERFISH);
        assertEquals(3000, netherite.get(0).duration());
        assertEquals(1, netherite.get(0).amplifier(), "中毒已经是 II 级，不该再叠加");
        assertEquals(750, netherite.get(1).duration());
        assertEquals(2, netherite.get(1).amplifier(), "饥饿已经是 III 级，不该再叠加");
        assertEquals(750, netherite.get(2).duration());
        assertEquals(1, netherite.get(2).amplifier(), "反胃是 I 级，下界合金应该升到 II 级");

        // 腐肉：饥饿 I → 下界合金 II；概率与时长照旧。
        List<Debuff> rottenIron = OreFoodTable.debuffs(MineralTier.IRON, FoodBase.ROTTEN_FLESH);
        assertEquals(600, rottenIron.get(0).duration());
        assertEquals(0, rottenIron.get(0).amplifier());
        assertEquals(0.8F, rottenIron.get(0).probability(), 0.0F);
        List<Debuff> rottenNetherite = OreFoodTable.debuffs(MineralTier.NETHERITE, FoodBase.ROTTEN_FLESH);
        assertEquals(1500, rottenNetherite.get(0).duration());
        assertEquals(1, rottenNetherite.get(0).amplifier(), "腐肉饥饿 I 级，下界合金应该升到 II 级");
        assertEquals(0.8F, rottenNetherite.get(0).probability(), 0.0F);
    }

    @Test
    @DisplayName("创造栏计划：每种原版食物后面紧跟它的五个矿物版本")
    void creativeTabPlanIsOrdered() {
        List<TabInsertion> plan = OreFoodTable.creativeTabPlan();
        assertEquals(FoodBase.values().length, plan.size());

        assertEquals("minecraft:cooked_beef", plan.get(0).anchorItemId());
        assertEquals(List.of(
                "orefood:copper_beef",
                "orefood:iron_beef",
                "orefood:golden_beef",
                "orefood:diamond_beef",
                "orefood:netherite_beef"),
                plan.get(0).itemIds());

        assertEquals("minecraft:bread", plan.get(1).anchorItemId());
        assertEquals("orefood:copper_bread", plan.get(1).itemIds().get(0));

        // 每组 5 个、合起来正好覆盖全部 50 个物品且不重复；锚点就是合成用的原版食物。
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < plan.size(); i++) {
            TabInsertion insertion = plan.get(i);
            assertEquals(MineralTier.values().length, insertion.itemIds().size());
            assertEquals(FoodBase.values()[i].baseItemId(), insertion.anchorItemId());
            for (String id : insertion.itemIds()) {
                assertTrue(ids.add(id), "重复的创造栏条目：" + id);
            }
        }
        assertEquals(50, ids.size());
    }

    @Test
    @DisplayName("常量类不可实例化")
    void tableIsNotInstantiable() throws Exception {
        Constructor<OreFoodTable> constructor = OreFoodTable.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(thrown.getCause() instanceof AssertionError);
    }
}
