package com.lyingice.orefood.data;

import com.lyingice.orefood.OreFoodConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

/**
 * 「矿物牛排」的数据定义——由数据包通过自定义动态注册表加载。
 *
 * <p>模组本体只注册<b>一个</b>物品（{@code orefood:ore_food}），所有牛排的差异
 * 都来自这份定义。物品栈上直接携带整份定义（见 {@code OreFoodComponents#DEFINITION}）。
 *
 * <p>JSON 示例（{@code data/<pack>/orefood/ore_food/iron_beef.json}）：
 * <pre>{@code
 * {
 *   "id": "orefood:iron_beef",
 *   "nutrition": 8,
 *   "saturation": 12.8,
 *   "durability": 16,
 *   "enchantment_value": 1,
 *   "effects": [
 *     { "effect": "minecraft:regeneration", "duration": 100, "amplifier": 0 }
 *   ]
 * }
 * }</pre>
 *
 * @param id               这份定义自己的 id，例如 {@code orefood:iron_beef}。
 *                         <b>配方靠它区分「同一个物品的不同变体」</b>——
 *                         原版配方只按物品类型匹配，五种牛排物品 id 完全相同，
 *                         没有这个字段就没法表达「中间放上一级牛排」。
 *                         不填也能用，只是不能被配方引用
 * @param nutrition        饱食度
 * @param saturation       饱和度
 * @param durability       最大耐久；<b>0 表示无限耐久</b>（吃了不掉耐久）
 * @param enchantmentValue 附魔能力
 * @param modelData        外观选择用的 custom_model_data 值；0 表示用默认模型
 * @param effects          食用后附带的状态效果，可为空
 */
public record OreFoodDefinition(
        Optional<ResourceLocation> id,
        int nutrition,
        float saturation,
        int durability,
        int enchantmentValue,
        int modelData,
        List<PotionEffectSpec> effects) {

    /** 动态注册表的 id，条目就放在 {@code data/<pack>/orefood/ore_food/} 下。 */
    public static final ResourceKey<Registry<OreFoodDefinition>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(OreFoodConstants.MOD_ID, "ore_food"));

    public static final Codec<OreFoodDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("id").forGetter(OreFoodDefinition::id),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("nutrition", 0).forGetter(OreFoodDefinition::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.0F).forGetter(OreFoodDefinition::saturation),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("durability", 0).forGetter(OreFoodDefinition::durability),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("enchantment_value", 0).forGetter(OreFoodDefinition::enchantmentValue),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("model_data", 0).forGetter(OreFoodDefinition::modelData),
            PotionEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(OreFoodDefinition::effects)
    ).apply(instance, OreFoodDefinition::new));

    /**
     * {@code effects} 字段的网络 codec。
     *
     * <p>必须声明在 {@link #STREAM_CODEC} <b>之前</b>：Java 不允许静态字段前向引用。
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PotionEffectSpec>> EFFECTS_CODEC =
            PotionEffectSpec.STREAM_CODEC.apply(ByteBufCodecs.list());

    /**
     * 网络同步用的 codec。
     *
     * <p>注册动态注册表时提供它，客户端才能读到定义——代价是<b>客户端也必须装本模组</b>，
     * 否则会被拒绝进入服务器。牛排的数值与提示框都要在客户端算，所以这里必须传。
     *
     * <p>这里<b>手写</b>而不是用 {@code StreamCodec.composite}：那个便利方法最多只支持
     * 6 个字段，而本记录有 7 个（加了 {@code id} 之后刚好越界）。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreFoodDefinition> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, definition) -> {
                        buffer.writeOptional(definition.id(),
                                (out, value) -> ResourceLocation.STREAM_CODEC.encode(out, value));
                        buffer.writeVarInt(definition.nutrition());
                        buffer.writeFloat(definition.saturation());
                        buffer.writeVarInt(definition.durability());
                        buffer.writeVarInt(definition.enchantmentValue());
                        buffer.writeVarInt(definition.modelData());
                        EFFECTS_CODEC.encode(buffer, definition.effects());
                    },
                    buffer -> new OreFoodDefinition(
                            buffer.readOptional(b -> ResourceLocation.STREAM_CODEC.decode(b)),
                            buffer.readVarInt(),
                            buffer.readFloat(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            EFFECTS_CODEC.decode(buffer)));

    /** @return 该定义是否为「无限耐久」——吃了不掉耐久，也就永不损坏。 */
    public boolean infiniteDurability() {
        return this.durability <= 0;
    }

    /** @return 该定义是否就是指定的那份（用 {@link #id} 比较）。 */
    public boolean hasId(ResourceLocation other) {
        return this.id.isPresent() && this.id.get().equals(other);
    }

    /** @return 一个空定义，用于注册表缺失时的兜底，避免各处判空。 */
    public static OreFoodDefinition empty() {
        return new OreFoodDefinition(Optional.empty(), 0, 0.0F, 0, 0, 0, List.of());
    }
}
