package com.lyingice.orefood.data;

import com.lyingice.orefood.OreFoodConstants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
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
 * <p>JSON 格式与另外两个平台<b>完全一致</b>：
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
 * <p><b>与 1.21.1 / 26.1.2 的差异</b>：1.20.1 没有数据组件（那是 1.20.5 才引入的），
 * 物品栈靠 <b>NBT</b> 携带定义。NBT 的读写直接复用同一个 {@link #CODEC} + {@code NbtOps}，
 * 因此数据包的 JSON 与物品栈的 NBT 永远共用一套字段名。
 *
 * @param id               这份定义自己的 id，例如 {@code orefood:iron_beef}。
 *                         <b>配方靠它区分「同一个物品的不同变体」</b>——
 *                         原版配方只按物品类型匹配，五种牛排物品 id 完全相同，
 *                         没有这个字段就没法表达「中间放上一级牛排」
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
                    new ResourceLocation(OreFoodConstants.MOD_ID, "ore_food"));

    /** 物品栈 NBT 里存放定义的那个复合标签名。 */
    public static final String STACK_TAG = OreFoodConstants.MOD_ID;

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
     * @return 该定义是否「没有耐久设定」（{@code durability} 为 0）。
     *         这种牛排既不消耗耐久，也<b>不消耗本体</b>——用不掉，参考马铠。
     */
    public boolean infiniteDurability() {
        return this.durability <= 0;
    }

    /** @return 该定义是否就是指定的那份（用 {@link #id} 比较）。 */
    public boolean hasId(ResourceLocation other) {
        return this.id.isPresent() && this.id.get().equals(other);
    }

    /**
     * 把定义编码成一个复合标签（<b>不依赖物品栈</b>）。
     *
     * <p>配方语法糖要用它：配方 JSON 在反序列化之前就要展开，那种时机不该构造
     * {@code ItemStack}（26.1.2 上会直接抛
     * {@code NullPointerException: Components not bound yet}，见那边 {@code OreFoodItems} 的注释）。
     */
    public static CompoundTag toTag(OreFoodDefinition definition) {
        CompoundTag tag = new CompoundTag();
        CODEC.encodeStart(NbtOps.INSTANCE, definition)
                .result()
                .ifPresent(encoded -> tag.put(STACK_TAG, encoded));
        return tag;
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
        return new OreFoodDefinition(Optional.empty(), 0, 0.0F, 0, 0, 0, List.of());
    }
}
