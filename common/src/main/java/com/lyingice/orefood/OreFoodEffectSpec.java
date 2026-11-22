package com.lyingice.orefood;

/**
 * 一条「附魔效果」定义：某个状态效果在附魔等级 L 下如何施加。
 *
 * <p><b>纯 JDK</b>：本类不含任何 Minecraft 类型，也不含 Codec（那是平台的活）。
 * 三个平台各自把它序列化/反序列化，数据形状完全一致——
 * 1.21.1 与 26.1.2 走「附魔效果组件」，1.20.1 走自定义数据包注册表。
 *
 * <p>数据包里的样子（两处写法相同）：
 * <pre>{@code
 * {
 *   "effect": "minecraft:regeneration",
 *   "duration":  { "base": 0, "per_level": 100 },
 *   "amplifier": { "base": 0, "per_level": 1 },
 *   "probability": 1.0
 * }
 * }</pre>
 *
 * @param effectId    状态效果 id，例如 {@code minecraft:regeneration}
 * @param duration    持续时间（tick）的线性表达：{@code base + per_level × 等级}
 * @param amplifier   效果等级的线性表达：{@code base + per_level × (等级 - 1)}
 * @param probability 触发概率 0.0 ~ 1.0
 */
public record OreFoodEffectSpec(String effectId, Linear duration, Linear amplifier, float probability) {

    /**
     * 一次线性换算 {@code base + perLevel × n}。
     *
     * @param base     n 为 0 时的值
     * @param perLevel 每加 1 递增多少
     */
    public record Linear(int base, int perLevel) {

        /** 常量（不随等级变化），例如「抗性提升固定 II 级」就是 {@code constant(1)}。 */
        public static Linear constant(int value) {
            return new Linear(value, 0);
        }

        /**
         * @param n 倍率下标
         * @return {@code base + perLevel × n}
         */
        public int resolve(int n) {
            return this.base + this.perLevel * n;
        }
    }

    /**
     * 持续时间（tick）：{@code base + per_level × 等级}。附魔 I 级代入 1。
     *
     * @param level 附魔等级，1..maxLevel
     */
    public int durationFor(int level) {
        return this.duration.resolve(level);
    }

    /**
     * 药水 amplifier：{@code base + per_level × (等级 - 1)}。
     *
     * <p>与 {@link FoodEnchantmentRules#potionAmplifier(int)} 的约定一致：
     * 附魔 I 级对应 amplifier 0（游戏内显示为 I 级）。
     *
     * @param level 附魔等级，1..maxLevel
     */
    public int amplifierFor(int level) {
        return this.amplifier.resolve(Math.max(0, level - 1));
    }
}
