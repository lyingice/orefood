package com.lyingice.orefood.init;

import com.lyingice.orefood.OreFoodConstants;
import com.lyingice.orefood.data.OreFoodDefinition;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的数据组件。
 *
 * <p>只有一个组件 {@code orefood:definition}：物品栈靠它携带「这是哪种牛排」。
 *
 * <p>这里刻意存<b>完整定义</b>而不是「注册表 id」：物品栈因此是自描述的，
 * {@code OreFoodItem#getMaxDamage(ItemStack)}、提示框、附魔效果这些只有
 * {@code ItemStack} 没有 {@code RegistryAccess} 的地方才能直接取值。
 * 代价是数据包改数值不会追溯已经发出去的物品——这本来就是原版物品组件的语义。
 */
public final class OreFoodComponents {

    /** 数据组件注册表；mod id 与 {@link OreFoodConstants#MOD_ID} 一致。 */
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, OreFoodConstants.MOD_ID);

    /**
     * 矿物牛排定义组件。
     *
     * <p>必须同时提供 {@code persistent}（存档）与 {@code networkSynchronized}（联机），
     * 否则客户端读不到定义，提示框里的药水效果就渲染不出来。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<OreFoodDefinition>> DEFINITION =
            DATA_COMPONENT_TYPES.register("definition", () -> DataComponentType.<OreFoodDefinition>builder()
                    .persistent(OreFoodDefinition.CODEC)
                    .networkSynchronized(OreFoodDefinition.STREAM_CODEC)
                    .build());

    /** 把数据组件注册表挂到 mod 事件总线；必须在主类构造器里调用一次。 */
    public static void register(IEventBus modEventBus) {
        DATA_COMPONENT_TYPES.register(modEventBus);
    }

    private OreFoodComponents() {
        throw new AssertionError("No instances.");
    }
}
