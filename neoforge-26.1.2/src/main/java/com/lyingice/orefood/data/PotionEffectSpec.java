package com.lyingice.orefood.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 数据包条目里的一条「食用后附带的状态效果」（NeoForge 26.1.2）。
 *
 * <p>JSON 写法与 1.20.1 / 1.21.1 侧完全一致，{@code effect} 直接写效果 id：
 * <pre>{@code
 * { "effect": "minecraft:regeneration", "duration": 100, "amplifier": 0, "probability": 1.0 }
 * }</pre>
 *
 * @param effect      要施加的状态效果
 * @param duration    持续时间（tick）
 * @param amplifier   效果等级，0 表示游戏内显示的 I 级
 * @param probability 施加概率，0.0 ~ 1.0
 */
public record PotionEffectSpec(Holder<MobEffect> effect, int duration, int amplifier, float probability) {

    /** 默认持续时间（tick）——10 秒，仅在 JSON 省略 duration 时生效。 */
    public static final int DEFAULT_DURATION = 200;

    public static final Codec<PotionEffectSpec> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MobEffect.CODEC.fieldOf("effect").forGetter(PotionEffectSpec::effect),
            Codec.INT.optionalFieldOf("duration", DEFAULT_DURATION).forGetter(PotionEffectSpec::duration),
            Codec.INT.optionalFieldOf("amplifier", 0).forGetter(PotionEffectSpec::amplifier),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("probability", 1.0F).forGetter(PotionEffectSpec::probability)
    ).apply(instance, PotionEffectSpec::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PotionEffectSpec> STREAM_CODEC = StreamCodec.composite(
            MobEffect.STREAM_CODEC, PotionEffectSpec::effect,
            ByteBufCodecs.VAR_INT, PotionEffectSpec::duration,
            ByteBufCodecs.VAR_INT, PotionEffectSpec::amplifier,
            ByteBufCodecs.FLOAT, PotionEffectSpec::probability,
            PotionEffectSpec::new
    );

    /** @return 可直接施加到生物身上的效果实例。 */
    public MobEffectInstance toInstance() {
        return new MobEffectInstance(this.effect, this.duration, this.amplifier);
    }
}
