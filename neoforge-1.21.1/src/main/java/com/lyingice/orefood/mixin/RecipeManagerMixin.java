package com.lyingice.orefood.mixin;

import com.google.gson.JsonElement;
import com.lyingice.orefood.crafting.OreFoodRecipeSugar;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让配方能直接写矿物牛排的<b>定义 id</b>（NeoForge 1.21.1）。
 *
 * <p>{@code RecipeManager#apply} 的入参就是<b>刚从磁盘读出来、还没解析</b>的
 * 原始 JSON 表：
 * <pre>
 *   RecipeManager#apply(Map&lt;ResourceLocation, JsonElement&gt; object, ...)   ← 本 Mixin 注入点
 *     └─ Recipe.CONDITIONAL_CODEC.parse(registryops, entry.getValue())    ← 原版在这里才解析
 * </pre>
 * 在 HEAD 上把定义 id 展开成原版写法，之后原版照常解析即可。
 *
 * <p><b>为什么选这里，而不是替换物品 codec</b>：
 * <ul>
 *   <li>一个点覆盖<b>全部配方类型</b>——原版有 5 个序列化器分别读
 *       {@code ItemStack.STRICT_CODEC} / {@code CODEC} / {@code SIMPLE_ITEM_CODEC}，
 *       逐个拦截既多又漏；</li>
 *   <li>{@code ItemStack.CODEC} 这类 {@code static final} 字段在下游类初始化时
 *       就被<b>按值捕获</b>进各自序列化器的 codec 里了，运行期替换字段没有任何效果
 *       （而且依赖类加载顺序，极脆弱）；</li>
 *   <li>配料侧的匹配语义（{@code Ingredient#test} 只比物品类型）本来就无法靠
 *       解析绕过，必须把配料换成自定义类型——在 JSON 层面做正好一并解决。</li>
 * </ul>
 *
 * <p>注入的是服务端加载链路。配方 JSON 只在服务端（含单人存档的集成服务器）解析，
 * 客户端是收同步包拿到已解析好的配方的，所以这里不需要考虑客户端。
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    /** {@code RecipeManager#apply} 的描述符——参数是原始 JSON 表、资源管理器、性能分析器。 */
    private static final String APPLY =
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;"
                    + "Lnet/minecraft/util/profiling/ProfilerFiller;)V";

    /**
     * 配方解析用的注册表访问。
     *
     * <p>它来自 {@code ReloadableServerResources} 构造时传入的
     * {@code RegistryAccess.Frozen}（RELOADABLE 层），<b>包含数据包动态注册表</b>，
     * 所以能查到 {@code orefood:ore_food} 里的定义。
     */
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    /** 在原版开始解析之前，把定义 id 展开成原版写法。 */
    @Inject(method = APPLY, at = @At("HEAD"))
    private void orefood$expandRecipeSugar(
            Map<ResourceLocation, JsonElement> recipeJsons,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo callback) {
        OreFoodRecipeSugar.expand(recipeJsons, this.registries);
    }
}
