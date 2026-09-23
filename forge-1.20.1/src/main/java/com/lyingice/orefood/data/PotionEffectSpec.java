package com.lyingice.orefood.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 数据包条目里的一条「食用后附带的状态效果」（Forge 1.20.1）。
 *
 * <p>JSON 写法保持与 1.21.1 侧完全一致，{@code effect} 直接写效果 id：
 * <pre>{@code
 * { "effect": "minecraft:regeneration", "duration": 100, "amplifier": 0, "probability": 1.0 }
 * }</pre>
 *
 * <p><b>与 1.21.1 的关键差异</b>：1.20.1 的 {@code MobEffect} 上<b>没有</b> {@code CODEC}
 * 静态字段（Codec 化是 1.21 才铺开的），而且效果值是裸对象而不是 {@code Holder}。
 * 所以这里自己用 {@code ResourceLocation} 拼一个 codec：解析时查
 * {@code BuiltInRegistries.MOB_EFFECT}，编码时反查 id。
 *
 * @param effect      要施加的状态效果
 * @param duration    持续时间（tick）
 * @param amplifier   效果等级，0 表示游戏内显示的 I 级
 * @param probability 施加概率，0.0 ~ 1.0
 */
public record PotionEffectSpec(MobEffect effect, int duration, int amplifier, float probability) {

    /** 默认持续时间（tick）——10 秒，仅在 JSON 省略 duration 时生效。 */
    public static final int DEFAULT_DURATION = 200;

    /** 把「效果 id 字符串」映射到 {@code MobEffect} 实例的 codec。 */
    public static final Codec<MobEffect> EFFECT_CODEC = ResourceLocation.CODEC.comapFlatMap(
            id -> {
                MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
                // 注意：1.20.1 这一代的 DFU 只接受 Supplier<String>，
                // 写成 error("字面量") 会编译失败。
                return effect != null
                        ? DataResult.success(effect)
                        : DataResult.error(() -> "Unknown mob effect: " + id);
            },
            effect -> BuiltInRegistries.MOB_EFFECT.getKey(effect));

    public static final Codec<PotionEffectSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EFFECT_CODEC.fieldOf("effect").forGetter(PotionEffectSpec::effect),
            Codec.INT.optionalFieldOf("duration", DEFAULT_DURATION).forGetter(PotionEffectSpec::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(PotionEffectSpec::amplifier),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("probability", 1.0F).forGetter(PotionEffectSpec::probability)
    ).apply(instance, PotionEffectSpec::new));

    /** @return 可直接施加到生物身上的效果实例。 */
    public MobEffectInstance toInstance() {
        return new MobEffectInstance(this.effect, this.duration, this.amplifier);
    }
}
