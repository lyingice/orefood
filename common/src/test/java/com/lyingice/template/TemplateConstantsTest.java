package com.lyingice.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 共享层常量的单元测试。
 *
 * <p>放在 {@code common/src/test/java}，三个平台模块各自编译并运行，一份断言生效三次 ——
 * 这样共享层的任何改动都会被三个构建分别校验。
 *
 * <p>本类只依赖 JDK 与 JUnit，不引用任何 Minecraft 类型，因此三个平台都能跑。
 */
class TemplateConstantsTest {

    @Test
    @DisplayName("模组 id 符合加载器命名规则")
    void modIdSatisfiesLoaderNamingRules() {
        // 加载器要求 modId 为小写字母、数字与下划线，且以字母开头。
        assertTrue(
                TemplateConstants.MOD_ID.matches("[a-z][a-z0-9_]*"),
                "mod_id 必须是 [a-z][a-z0-9_]*，实际为 " + TemplateConstants.MOD_ID);
        // 改名时同步改这里：它与 gradle.properties 的 mod_id 必须一致。
        assertEquals("template", TemplateConstants.MOD_ID);
    }

    @Test
    @DisplayName("模组名称与版本号非空")
    void nameAndVersionArePresent() {
        assertTrue(TemplateConstants.MOD_NAME != null && !TemplateConstants.MOD_NAME.isEmpty());
        assertTrue(
                TemplateConstants.MOD_VERSION.matches("\\d+\\.\\d+\\.\\d+.*"),
                "mod_version 应当形如 1.0.0，实际为 " + TemplateConstants.MOD_VERSION);
    }

    @Test
    @DisplayName("主包名与本类所在包一致")
    void packageRootMatchesActualPackage() {
        // 常量写错（改名时漏改）会让 ServiceLoader / 反射查找静默失败，这里提前拦住。
        assertEquals(TemplateConstants.class.getPackage().getName(), TemplateConstants.PACKAGE_ROOT);
    }

    @Test
    @DisplayName("常量类不可实例化")
    void constantsClassIsNotInstantiable() throws Exception {
        Constructor<TemplateConstants> constructor = TemplateConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        InvocationTargetException thrown =
                assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertTrue(thrown.getCause() instanceof AssertionError);
    }
}
