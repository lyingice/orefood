package com.lyingice.orefood.mixin;

import com.google.gson.JsonElement;
import com.lyingice.orefood.crafting.OreFoodRecipeSugar;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让配方能直接写矿物牛排的<b>定义 id</b>（Forge 1.20.1）。
 *
 * <p>{@code RecipeManager#apply} 的入参就是<b>刚从磁盘读出来、还没解析</b>的原始 JSON 表：
 * <pre>
 *   RecipeManager#apply(Map&lt;ResourceLocation, JsonElement&gt; p_44037_, ...)   ← 本 Mixin 注入点
 *     └─ fromJson(resourcelocation, json, this.context)                     ← 原版在这里才解析
 * </pre>
 * 在 HEAD 上把定义 id 展开成原版写法，之后原版照常解析即可。
 *
 * <p><b>为什么选这里</b>：1.20.1 的配方解析走的是 <b>Gson</b>（{@code JsonObject}）
 * 而不是 DFU codec，所以根本不存在「统一的物品 codec」可以拦——result 走
 * {@code ShapedRecipe#itemStackFromJson} → {@code CraftingHelper.getItemStack}，
 * 配料走 {@code Ingredient#valueFromJson} → {@code ShapedRecipe#itemFromJson}，
 * 熔炉的字符串 result 和切石机又各走一套。逐个拦截要四处以上，而在 JSON 层面
 * 改写<b>一个点就覆盖全部</b>。
 *
 * <p>注入的是服务端加载链路。配方 JSON 只在服务端（含单人存档的集成服务器）解析，
 * 客户端是收同步包拿到已解析好的配方的。
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    /** {@code RecipeManager#apply} 的描述符——参数是原始 JSON 表、资源管理器、性能分析器。 */
    private static final String APPLY =
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lnet/minecraft/util/profiling/ProfilerFiller;)V";

    /**
     * 在原版开始解析之前，把定义 id 展开成原版写法。
     *
     * <p>这里不像 1.21.1 那样需要 {@code @Shadow} 注册表字段：1.20.1 的
     * {@code RecipeManager} 只持有 {@code ICondition.IContext}，拿不到注册表。
     * 注册表改由 {@code AddReloadListenerEvent} 暂存到
     * {@link OreFoodRecipeSugar} 里。
     */
    @Inject(method = APPLY, at = @At("HEAD"))
    private void orefood$expandRecipeSugar(
            Map<ResourceLocation, JsonElement> recipeJsons,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback) {
        OreFoodRecipeSugar.expand(recipeJsons);
    }
}
