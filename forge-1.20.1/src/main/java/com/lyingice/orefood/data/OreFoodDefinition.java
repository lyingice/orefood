package com.lyingice.orefood.data;

import com.lyingice.orefood.OreFoodConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * 「矿物牛排」的数据定义——由数据包通过自定义动态注册表加载（Forge 1.20.1）。
 *
 * <p>JSON 格式与 1.21.1 侧<b>完全一致</b>：
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
 * <p><b>与 1.21.1 的关键差异</b>：1.20.1 没有数据组件（Data Components 是 1.20.5 才引入的），
 * 所以物品栈靠 <b>NBT</b> 携带定义。NBT 的读写直接复用同一个 {@link #CODEC} +
 * {@code NbtOps}，因此数据包的 JSON 与物品栈的 NBT 永远共用一套字段名，
 * 不会出现两边字段对不上的情况。
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
                    new ResourceLocation(OreFoodConstants.MOD_ID, "ore_food"));

    /** 物品栈 NBT 里存放定义的那个复合标签名。 */
    public static final String STACK_TAG = OreFoodConstants.MOD_ID;

    public static final Codec<OreFoodDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("nutrition", 0).forGetter(OreFoodDefinition::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.0F).forGetter(OreFoodDefinition::saturation),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("durability", 0).forGetter(OreFoodDefinition::durability),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("enchantment_value", 0).forGetter(OreFoodDefinition::enchantmentValue),
            PotionEffectSpec.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(OreFoodDefinition::effects)
    ).apply(instance, OreFoodDefinition::new));

    /** @return 该定义是否为「无限耐久」——吃了不掉耐久，也就永不损坏。 */
    public boolean infiniteDurability() {
        return this.durability <= 0;
    }

    /** 把定义写进物品栈的 NBT。 */
    public static void writeToStack(ItemStack stack, OreFoodDefinition definition) {
        CODEC.encodeStart(NbtOps.INSTANCE, definition)
                .result()
                .ifPresent(tag -> stack.getOrCreateTag().put(STACK_TAG, tag));
    }

    /**
     * 从物品栈的 NBT 读回定义。
     *
     * @return 定义；该物品栈没有定义时返回 {@code null}
     */
    public static OreFoodDefinition readFromStack(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(STACK_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        return CODEC.parse(NbtOps.INSTANCE, tag.getCompound(STACK_TAG)).result().orElse(null);
    }

    /** @return 一个空定义，用于兜底，避免各处判空。 */
    public static OreFoodDefinition empty() {
        return new OreFoodDefinition(0, 0.0F, 0, 0, List.of());
    }
}
