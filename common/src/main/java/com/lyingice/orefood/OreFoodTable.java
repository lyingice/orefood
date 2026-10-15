package com.lyingice.orefood;

import java.util.ArrayList;
import java.util.List;

/**
 * 矿物食品的数值表——「某个物品该有多少营养 / 耐久 / 附带什么效果」的<b>唯一权威来源</b>。
 *
 * <p>纯 JDK：本类被三个平台共同编译，也被 {@code common/src/test} 直接断言（三个构建各跑一遍）。
 * 它<b>不引用任何 Minecraft 类型</b>，所以负面效果只用字符串 id 描述，平台侧再解析成
 * {@code MobEffect} / {@code Holder<MobEffect>}（见各平台 {@code PotionEffectSpec#of}）。
 *
 * <p><b>2.0 的规则</b>：
 * <ul>
 *   <li>营养 = 基底食物的营养 × 矿物倍率，四舍五入，最小 1；</li>
 *   <li>饱和度 = 基底食物的饱和度 × 矿物倍率，量化到 1 位小数；</li>
 *   <li>耐久与附魔能力<b>只由矿物等级决定</b>，与食物无关；</li>
 *   <li>负面效果<b>只存在于基底自带负面的食物</b>（河豚 / 腐肉），
 *       且随矿物等级<b>只延长持续时间、不提升效果等级</b>。</li>
 * </ul>
 *
 * <p>基底食物的营养/饱和度取自原版 {@code Foods}（1.20.1 与 1.21.1 一致），
 * 用「熟」版本：牛排用熟牛肉、鸡肉用熟鸡肉……与 1.x 时代只有牛排时的取法保持一致。
 *
 * <p><b>清单在 {@code tools/generate-ore-food-assets.ps1} 里另有一份</b>（那个脚本生成模型、
 * 语言文件与配方，读不到 Java 常量）。改动矿物/食物清单时两边都要改，
 * 而 {@link #all()} 的顺序就是创造栏与物品注册的顺序。
 */
public final class OreFoodTable {

    /**
     * 矿物等级。
     *
     * @param key              物品 id 前缀，例如 {@code copper}（铜牛排 = {@code copper_beef}）
     * @param nutritionScale   营养倍率
     * @param saturationScale  饱和度倍率
     * @param durability       「能吃几次」，0 表示没有耐久设定
     * @param enchantmentValue 附魔能力
     * @param debuffScale      负面效果的时长倍率（只影响时长，不动效果等级）
     */
    public enum MineralTier {
        COPPER("copper", 0.75F, 0.75F, 8, 1, 0.5F),
        IRON("iron", 1.0F, 1.0F, 16, 1, 1.0F),
        GOLDEN("golden", 1.0F, 1.25F, 32, 5, 1.5F),
        DIAMOND("diamond", 1.25F, 1.5625F, 64, 10, 2.0F),
        NETHERITE("netherite", 1.5F, 1.875F, 128, 15, 2.5F);

        private final String key;
        private final float nutritionScale;
        private final float saturationScale;
        private final int durability;
        private final int enchantmentValue;
        private final float debuffScale;

        MineralTier(String key, float nutritionScale, float saturationScale,
                int durability, int enchantmentValue, float debuffScale) {
            this.key = key;
            this.nutritionScale = nutritionScale;
            this.saturationScale = saturationScale;
            this.durability = durability;
            this.enchantmentValue = enchantmentValue;
            this.debuffScale = debuffScale;
        }

        /** @return 物品 id 前缀。 */
        public String key() {
            return this.key;
        }

        /** @return 营养倍率。 */
        public float nutritionScale() {
            return this.nutritionScale;
        }

        /** @return 饱和度倍率。 */
        public float saturationScale() {
            return this.saturationScale;
        }

        /** @return 「能吃几次」。 */
        public int durability() {
            return this.durability;
        }

        /** @return 附魔能力。 */
        public int enchantmentValue() {
            return this.enchantmentValue;
        }

        /** @return 负面效果的时长倍率。 */
        public float debuffScale() {
            return this.debuffScale;
        }
    }

    /**
     * 食物基底——决定合成时用的原版材料、基础数值、以及是否自带负面效果。
     *
     * @param key     物品 id 后缀，例如 {@code beef}
     * @param nutrition   原版对应食物的营养
     * @param saturation  原版对应食物的饱和度（= 营养 × saturationModifier × 2）
     * @param debuffs     原版自带的负面效果（没有就是空列表）
     */
    public enum FoodBase {
        BEEF("beef", 8, 12.8F, List.of()),
        BREAD("bread", 5, 6.0F, List.of()),
        CHICKEN("chicken", 6, 7.2F, List.of()),
        COD("cod", 5, 6.0F, List.of()),
        MUTTON("mutton", 6, 9.6F, List.of()),
        PORKCHOP("porkchop", 8, 12.8F, List.of()),
        PUFFERFISH("pufferfish", 1, 0.2F, List.of(
                new Debuff("minecraft:poison", 1200, 1, 1.0F),
                new Debuff("minecraft:hunger", 300, 2, 1.0F),
                new Debuff("minecraft:nausea", 300, 0, 1.0F))),
        RABBIT("rabbit", 5, 6.0F, List.of()),
        ROTTEN_FLESH("rotten_flesh", 4, 0.8F, List.of(
                new Debuff("minecraft:hunger", 600, 0, 0.8F))),
        SALMON("salmon", 6, 9.6F, List.of());

        private final String key;
        private final int nutrition;
        private final float saturation;
        private final List<Debuff> debuffs;

        FoodBase(String key, int nutrition, float saturation, List<Debuff> debuffs) {
            this.key = key;
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.debuffs = debuffs;
        }

        /** @return 物品 id 后缀。 */
        public String key() {
            return this.key;
        }

        /** @return 原版对应食物的营养。 */
        public int nutrition() {
            return this.nutrition;
        }

        /** @return 原版对应食物的饱和度。 */
        public float saturation() {
            return this.saturation;
        }

        /** @return 原版自带的负面效果。 */
        public List<Debuff> debuffs() {
            return this.debuffs;
        }
    }

    /**
     * 一条负面效果（原版数值，叠加矿物倍率前）。
     *
     * @param effectId    状态效果 id，例如 {@code minecraft:poison}
     * @param duration    持续时间（tick）
     * @param amplifier   效果等级，0 表示游戏内显示的 I 级
     * @param probability 触发概率，0.0 ~ 1.0
     */
    public record Debuff(String effectId, int duration, int amplifier, float probability) {
    }

    /** 一个「矿物 × 食物」组合。 */
    public record ItemKey(MineralTier tier, FoodBase base) {

        /** @return 物品 id，例如 {@code copper_beef}。 */
        public String id() {
            return this.tier.key() + "_" + this.base.key();
        }
    }

    private static final List<ItemKey> ALL = createAll();

    /**
     * @return 全部组合，<b>外层矿物、内层食物</b>——物品注册、创造栏与生成脚本共用这个顺序
     */
    public static List<ItemKey> all() {
        return ALL;
    }

    private static List<ItemKey> createAll() {
        List<ItemKey> keys = new ArrayList<>(MineralTier.values().length * FoodBase.values().length);
        for (MineralTier tier : MineralTier.values()) {
            for (FoodBase base : FoodBase.values()) {
                keys.add(new ItemKey(tier, base));
            }
        }
        return List.copyOf(keys);
    }

    /**
     * @return 该组合的营养：基底营养 × 矿物倍率，四舍五入，最小 1
     */
    public static int nutrition(MineralTier tier, FoodBase base) {
        return Math.max(1, Math.round(base.nutrition() * tier.nutritionScale()));
    }

    /**
     * @return 该组合的饱和度：基底饱和度 × 矿物倍率，量化到 1 位小数
     */
    public static float saturation(MineralTier tier, FoodBase base) {
        return Math.round(base.saturation() * tier.saturationScale() * 10.0F) / 10.0F;
    }

    /**
     * @return 该组合的负面效果：持续时间按矿物倍率延长，<b>效果等级与概率保持原版</b>
     */
    public static List<Debuff> debuffs(MineralTier tier, FoodBase base) {
        if (base.debuffs().isEmpty()) {
            return List.of();
        }
        List<Debuff> scaled = new ArrayList<>(base.debuffs().size());
        for (Debuff debuff : base.debuffs()) {
            scaled.add(new Debuff(
                    debuff.effectId(),
                    Math.round(debuff.duration() * tier.debuffScale()),
                    debuff.amplifier(),
                    debuff.probability()));
        }
        return List.copyOf(scaled);
    }

    private OreFoodTable() {
        throw new AssertionError("No instances.");
    }
}
