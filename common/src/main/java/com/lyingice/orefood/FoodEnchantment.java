package com.lyingice.orefood;

/**
 * 五种食物附魔的「规格」：id 与最大等级。
 *
 * <p>本类是共享层，<b>只有纯 JDK</b>——附魔在三个平台的注册方式完全不同
 * （1.20.1 是静态注册的 {@code abstract class Enchantment}，1.21.1 / 26.1.2 是
 * 数据包驱动的 {@code record Enchantment}），但「有哪五个附魔、各自几级」
 * 这件事是三个平台共用的同一份真相，因此放在这里。
 *
 * <p>各平台据此生成自己的注册物：
 * <ul>
 *   <li>1.21.1 / 26.1.2：{@code data/orefood/enchantment/<path>.json}；</li>
 *   <li>1.20.1：{@code DeferredRegister} 注册 {@code Enchantment} 子类。</li>
 * </ul>
 */
public enum FoodEnchantment {

    /**
     * 强效恢复：瞬间治疗 + 生命恢复。
     *
     * <p>药水等级跟随附魔等级，而附魔 I 级对应的是 <b>amplifier 0</b>
     * （见 {@link FoodEnchantmentRules#potionAmplifier(int)}）。
     * 生命恢复时长 = 等级 × 100 tick。
     */
    POWERFUL_RECOVERY("powerful_recovery", 4),

    /**
     * 肾上腺素：力量 + 速度。
     *
     * <p>力量时长 = 等级 × 100 tick，速度时长 = 等级 × 200 tick。
     */
    ADRENALINE("adrenaline", 3),

    /**
     * 强身健体：生命吸收 + 抗性提升。
     *
     * <p>抗性提升固定为 II 级（amplifier 1，40% 减伤），不随附魔等级变化；
     * 生命吸收等级跟随附魔等级；两者时长均为 等级 × 400 tick。
     */
    FITNESS("fitness", 5),

    /**
     * 饱腹：额外饱食度。
     *
     * <p>在原版食物营养之上追加 等级 × 2 点饱食度。
     */
    SATIETY("satiety", 3),

    /**
     * 盛宴：饱和效果。
     *
     * <p>持续时间为 等级 × 1 分钟。这是宝藏附魔（仅能通过战利品 / 交易等途径获得）。
     */
    FEAST("feast", 3);

    /** 附魔 id 的路径部分；命名空间固定为 {@link OreFoodConstants#MOD_ID}。 */
    private final String path;

    /** 该附魔的最大等级，取值 1..255。 */
    private final int maxLevel;

    FoodEnchantment(String path, int maxLevel) {
        this.path = path;
        this.maxLevel = maxLevel;
    }

    /** @return 附魔 id 的路径部分，例如 {@code powerful_recovery}。 */
    public String path() {
        return this.path;
    }

    /** @return 该附魔的最大等级。 */
    public int maxLevel() {
        return this.maxLevel;
    }

    /**
     * @return 完整的附魔 id，形如 {@code orefood:powerful_recovery}。
     *         仅供日志与调试使用——构造 {@code ResourceLocation} / {@code Identifier}
     *         请用 {@link #path()}，不要在共享层拼字符串再解析。
     */
    public String fullId() {
        return OreFoodConstants.MOD_ID + ":" + this.path;
    }

    /** @return 该附魔是否为宝藏附魔（仅盛宴是）。 */
    public boolean treasure() {
        return this == FEAST;
    }
}
