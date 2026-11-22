package com.lyingice.orefood;

import java.util.ArrayList;
import java.util.List;

/**
 * 矿物食品的数值表——「某个物品该有多少营养 / 耐久 / 附带什么效果」的<b>唯一权威来源</b>。
 *
 * <p>纯 JDK：本类被三个平台共同编译，也被 {@code common/src/test} 直接断言（三个构建各跑一遍）。
 * 它<b>不引用任何 Minecraft 类型</b>，所以负面效果与创造栏锚点都只用字符串 id 描述，
 * 平台侧再解析成实物（见各平台 {@code PotionEffectSpec#of} 与 {@code OreFoodItems#addToCreativeTab}）。
 *
 * <p><b>数值规则</b>：
 * <ul>
 *   <li>营养 = 基底食物的营养 × 矿物倍率，四舍五入，最小 1；</li>
 *   <li>饱和度 = 基底食物的饱和度 × 矿物倍率，量化到 1 位小数；</li>
 *   <li>耐久与附魔能力<b>只由矿物等级决定</b>，与食物无关；</li>
 *   <li>负面效果<b>只存在于基底自带负面的食物</b>（河豚 / 腐肉）：
 *       时长随矿物等级放大，效果等级只有下界合金会动（见 {@link #debuffs}）。</li>
 * </ul>
 *
 * <p>基底食物的营养/饱和度/自带效果取自原版 {@code Foods}（1.20.1 与 1.21.1 一致），
 * 用「熟」版本：牛排用熟牛肉、鸡肉用熟鸡肉……与 1.x 时代只有牛排时的取法保持一致。
 *
 * <p><b>清单只在这里</b>：资源文件（模型 / 配方 / 标签 / 语言）由
 * {@code tools/src/com/lyingice/orefood/tools/GenerateOreFoodAssets.java} 读本类生成，
 * 那个生成器只额外维护中英文名。
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
     * @param repairItemId     铁砧修复用的材料 id（原版物品）；空串表示不可修复
     * @param fireResistant    是否抗火（掉落物不会被岩浆与火烧毁），只有下界合金是
     */
    public enum MineralTier {
        COPPER("copper", 0.75F, 0.75F, 8, 1, 0.5F, "minecraft:copper_ingot", false),
        IRON("iron", 1.0F, 1.0F, 16, 1, 1.0F, "minecraft:iron_ingot", false),
        GOLDEN("golden", 1.0F, 1.25F, 32, 5, 1.5F, "minecraft:gold_ingot", false),
        DIAMOND("diamond", 1.25F, 1.5625F, 64, 10, 2.0F, "minecraft:diamond", false),
        NETHERITE("netherite", 1.5F, 1.875F, 128, 15, 2.5F, "minecraft:netherite_ingot", true);

        private final String key;
        private final float nutritionScale;
        private final float saturationScale;
        private final int durability;
        private final int enchantmentValue;
        private final float debuffScale;
        private final String repairItemId;
        private final boolean fireResistant;

        MineralTier(String key, float nutritionScale, float saturationScale,
                int durability, int enchantmentValue, float debuffScale, String repairItemId,
                boolean fireResistant) {
            this.key = key;
            this.nutritionScale = nutritionScale;
            this.saturationScale = saturationScale;
            this.durability = durability;
            this.enchantmentValue = enchantmentValue;
            this.debuffScale = debuffScale;
            this.repairItemId = repairItemId;
            this.fireResistant = fireResistant;
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

        /**
         * @return 铁砧里用来修复该等级食物的材料 id，例如钻石等级是 {@code minecraft:diamond}；
         *         空串表示不可修复。
         */
        public String repairItemId() {
            return this.repairItemId;
        }

        /**
         * @return 是否抗火。只有下界合金是 {@code true}——与原版下界合金制品一致，
         *         掉进岩浆或火里不会被烧毁。
         */
        public boolean fireResistant() {
            return this.fireResistant;
        }
    }

    /**
     * 食物基底——决定合成用的原版材料、基础数值、自带负面效果，
     * 以及创造栏里「插到哪个原版食物后面」。
     *
     * @param key        物品 id 后缀，例如 {@code beef}
     * @param nutrition  原版对应食物的营养
     * @param saturation 原版对应食物的饱和度（= 营养 × saturationModifier × 2）
     * @param baseItemId 合成与创造栏锚点用的原版物品 id
     * @param debuffs    原版自带的负面效果（没有就是空列表）
     */
    public enum FoodBase {
        BEEF("beef", 8, 12.8F, "minecraft:cooked_beef", List.of()),
        BREAD("bread", 5, 6.0F, "minecraft:bread", List.of()),
        CHICKEN("chicken", 6, 7.2F, "minecraft:cooked_chicken", List.of()),
        COD("cod", 5, 6.0F, "minecraft:cooked_cod", List.of()),
        MUTTON("mutton", 6, 9.6F, "minecraft:cooked_mutton", List.of()),
        PORKCHOP("porkchop", 8, 12.8F, "minecraft:cooked_porkchop", List.of()),
        PUFFERFISH("pufferfish", 1, 0.2F, "minecraft:pufferfish", List.of(
                new Debuff("minecraft:poison", 1200, 1, 1.0F),
                new Debuff("minecraft:hunger", 300, 2, 1.0F),
                new Debuff("minecraft:nausea", 300, 0, 1.0F))),
        RABBIT("rabbit", 5, 6.0F, "minecraft:cooked_rabbit", List.of()),
        ROTTEN_FLESH("rotten_flesh", 4, 0.8F, "minecraft:rotten_flesh", List.of(
                new Debuff("minecraft:hunger", 600, 0, 0.8F))),
        SALMON("salmon", 6, 9.6F, "minecraft:cooked_salmon", List.of());

        private final String key;
        private final int nutrition;
        private final float saturation;
        private final String baseItemId;
        private final List<Debuff> debuffs;

        FoodBase(String key, int nutrition, float saturation, String baseItemId, List<Debuff> debuffs) {
            this.key = key;
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.baseItemId = baseItemId;
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

        /** @return 合成用的原版物品 id（同时也是创造栏里的插入锚点）。 */
        public String baseItemId() {
            return this.baseItemId;
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

        /** @return 物品 id（不带命名空间），例如 {@code copper_beef}。 */
        public String id() {
            return this.tier.key() + "_" + this.base.key();
        }
    }

    /**
     * 创造栏里的一组插入：{@code anchorItemId} 后面紧跟 {@code itemIds} 里的物品。
     *
     * @param anchorItemId 原版食物（锚点）的完整物品 id
     * @param itemIds      要插在它后面的矿物食品（完整物品 id，按矿物等级从低到高）
     */
    public record TabInsertion(String anchorItemId, List<String> itemIds) {
    }

    private static final List<ItemKey> ALL = createAll();

    private static final List<TabInsertion> CREATIVE_TAB_PLAN = createCreativeTabPlan();

    /**
     * @return 全部组合，<b>外层矿物、内层食物</b>——物品注册与生成脚本共用这个顺序
     */
    public static List<ItemKey> all() {
        return ALL;
    }

    /**
     * @return 创造栏计划：每种原版食物后面紧跟它的五个矿物版本
     *         （牛排 → 铜/铁/金/钻/下界合金牛排 → 面包 → ……），
     *         顺序为 <b>外层食物、内层矿物</b>
     */
    public static List<TabInsertion> creativeTabPlan() {
        return CREATIVE_TAB_PLAN;
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

    private static List<TabInsertion> createCreativeTabPlan() {
        List<TabInsertion> plan = new ArrayList<>(FoodBase.values().length);
        for (FoodBase base : FoodBase.values()) {
            List<String> ids = new ArrayList<>(MineralTier.values().length);
            for (MineralTier tier : MineralTier.values()) {
                ids.add(OreFoodConstants.MOD_ID + ":" + tier.key() + "_" + base.key());
            }
            plan.add(new TabInsertion(base.baseItemId(), List.copyOf(ids)));
        }
        return List.copyOf(plan);
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
     * 该组合的负面效果。
     *
     * <p><b>时长</b>：按矿物倍率延长（铜 ×0.5 → 下界合金 ×2.5）。
     *
     * <p><b>等级</b>：只有<b>下界合金</b>会动，且只动「本来就是 I 级」的那些——
     * 腐肉的饥饿 I → II、河豚的反胃 I → II；已经是 II / III 级的中毒与饥饿<b>不再叠加</b>。
     * 想让下界合金把所有效果都 +1，把下面那个 {@code amplifier == 0} 判断去掉即可。
     *
     * @return 该组合的负面效果；没有就是空列表
     */
    public static List<Debuff> debuffs(MineralTier tier, FoodBase base) {
        if (base.debuffs().isEmpty()) {
            return List.of();
        }
        List<Debuff> scaled = new ArrayList<>(base.debuffs().size());
        for (Debuff debuff : base.debuffs()) {
            int amplifier = debuff.amplifier();
            if (tier == MineralTier.NETHERITE && amplifier == 0) {
                amplifier += 1;
            }
            scaled.add(new Debuff(
                    debuff.effectId(),
                    Math.round(debuff.duration() * tier.debuffScale()),
                    amplifier,
                    debuff.probability()));
        }
        return List.copyOf(scaled);
    }

    private OreFoodTable() {
        throw new AssertionError("No instances.");
    }
}
