package com.lyingice.template;

/**
 * 与加载器无关的全局常量。
 *
 * <p>本类被三个平台构建共同编译，因此<b>不得引用任何 Minecraft / Forge / NeoForge 类型</b>。
 * 共享层只允许出现纯 JDK 代码：一旦这里 import 了游戏类，三个平台里只要有一个
 * 缺少对应符号，整个模块就编不过。
 */
public final class TemplateConstants {

    /**
     * 模组 ID。
     *
     * <p>必须与各平台的 {@code @Mod} 注解、{@code mods.toml} / {@code neoforge.mods.toml}
     * 中的 {@code modId}、以及各 {@code gradle.properties} 中的 {@code mod_id} 完全一致。
     * 加载器要求它是小写字母、数字与下划线，且以字母开头。
     */
    public static final String MOD_ID = "template";

    /** 人类可读的模组名称。 */
    public static final String MOD_NAME = "Template";

    /** 模组版本，与各构建 {@code gradle.properties} 中的 {@code mod_version} 保持一致。 */
    public static final String MOD_VERSION = "1.0.0";

    /** 模组主包名。 */
    public static final String PACKAGE_ROOT = "com.lyingice.template";

    private TemplateConstants() {
        throw new AssertionError("No instances.");
    }
}
