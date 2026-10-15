package com.lyingice.orefood;

import com.lyingice.orefood.OreFoodTable.Debuff;
import com.lyingice.orefood.OreFoodTable.FoodBase;
import com.lyingice.orefood.OreFoodTable.ItemKey;
import com.lyingice.orefood.OreFoodTable.MineralTier;
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
 * <p><b>最重要的两组断言</b>：
 * <ul>
 *   <li>1.x 时代就存在的五个牛排，数值必须<b>一个比特都不变</b>（改成公式推导之后
 *       最容易悄悄漂移的就是它们）；</li>
 *   <li>负面效果只延长时长、不动效果等级（2.0 的明确设计）。</li>
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
    @DisplayName("负面效果：只有河豚与腐肉有，且只延长时长不动等级")
    void debuffsFollowDesign() {
        for (ItemKey key : OreFoodTable.all()) {
            boolean expected = key.base() == FoodBase.PUFFERFISH || key.base() == FoodBase.ROTTEN_FLESH;
            assertEquals(expected, !OreFoodTable.debuffs(key.tier(), key.base()).isEmpty(),
                    key.id() + " 的负面效果有无不对");
        }

        // 铁级 = 原版强度。
        List<Debuff> iron = OreFoodTable.debuffs(MineralTier.IRON, FoodBase.PUFFERFISH);
        assertEquals(3, iron.size());
        assertEquals("minecraft:poison", iron.get(0).effectId());
        assertEquals(1200, iron.get(0).duration());
        assertEquals(1, iron.get(0).amplifier());
        assertEquals(1.0F, iron.get(0).probability(), 0.0F);

        // 腐蚀：饥饿 600t、80% 概率、0 级。
        List<Debuff> rotten = OreFoodTable.debuffs(MineralTier.IRON, FoodBase.ROTTEN_FLESH);
        assertEquals(1, rotten.size());
        assertEquals(600, rotten.get(0).duration());
        assertEquals(0.8F, rotten.get(0).probability(), 0.0F);

        // 下界合金：时长 ×2.5，等级与概率不变。
        List<Debuff> netherite = OreFoodTable.debuffs(MineralTier.NETHERITE, FoodBase.PUFFERFISH);
        assertEquals(3000, netherite.get(0).duration());
        assertEquals(1, netherite.get(0).amplifier(), "下界合金不应该提升效果等级");
        assertEquals(750, netherite.get(1).duration());
        assertEquals(2, netherite.get(1).amplifier(), "下界合金不应该提升效果等级");

        // 铜：时长 ×0.5。
        assertEquals(600, OreFoodTable.debuffs(MineralTier.COPPER, FoodBase.PUFFERFISH).get(0).duration());
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
