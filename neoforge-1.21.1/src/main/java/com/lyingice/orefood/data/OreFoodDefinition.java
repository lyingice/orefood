package com.lyingice.orefood.data;

import com.lyingice.orefood.OreFoodConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
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
 * 都来自这份定义。物品栈上携带定义的 id（见 {@code OreFoodComponents#DEFINITION}），
 * 运行时再回注册表取值；因此数据包改一行数值、重进世界就会生效，
 * 已经发出去的同种牛排也跟着变。
 *
 * <p>JSON 示例（{@code data/<pack>/orefood/ore_food/iron_steak.json}）：
 * <pre>{@code
 * {
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
 * @param nutrition        饱食度
 * @param saturation       饱和度
 * @param durability       最大耐久；<b>0 表示无限耐久</b>（吃了不掉耐久）
 * @param enchantmentValue 附魔能力
 * @param effects          食用后附带的状态效果，可为空
 */
public record OreFoodDefinition(
        int nutrition,
        float saturation,
        int durability,
        int enchantmentValue,
        List<PotionEffectSpec> effects) {

    /** 动态注册表的 id，条目就放在 {@code data/<pack>/orefood/ore_food/} 下。 */
    public static final ResourceKey<Registry<OreFoodDefinition>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(
                    ResourceLocation.fromNamespaceAndPath(OreFoodConstants.MOD_ID, "ore_food"));

    public static final Codec<OreFoodDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("nutrition", 0).forGetter(OreFoodDefinition::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.0F).forGetter(OreFoodDefinition::saturation),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("durability", 0).forGetter(OreFoodDefinition::durability),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("enchantment_value", 0).forGetter(OreFoodDefinition::enchantmentValue),
            PotionEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(OreFoodDefinition::effects)
    ).apply(instance, OreFoodDefinition::new));

    /**
     * 网络同步用的 codec。
     *
     * <p>注册动态注册表时提供它，客户端才能读到定义——代价是<b>客户端也必须装本模组</b>，
     * 否则会被拒绝进入服务器。牛排的数值与提示框都要在客户端算，所以这里必须传。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, OreFoodDefinition> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OreFoodDefinition::nutrition,
            ByteBufCodecs.FLOAT, OreFoodDefinition::saturation,
            ByteBufCodecs.VAR_INT, OreFoodDefinition::durability,
            ByteBufCodecs.VAR_INT, OreFoodDefinition::enchantmentValue,
            PotionEffectSpec.STREAM_CODEC.apply(ByteBufCodecs.list()), OreFoodDefinition::effects,
            OreFoodDefinition::new
    );

    /** @return 该定义是否为「无限耐久」——吃了不掉耐久，也就永不损坏。 */
    public boolean infiniteDurability() {
        return this.durability <= 0;
    }

    /** @return 一个空定义，用于注册表缺失时的兜底，避免各处判空。 */
    public static OreFoodDefinition empty() {
        return new OreFoodDefinition(0, 0.0F, 0, 0, List.of());
    }
}
