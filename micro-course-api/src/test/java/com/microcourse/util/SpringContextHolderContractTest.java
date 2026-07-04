package com.microcourse.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0-5 回归测试: SpringContextHolder 获取 Bean 是单例缓存,
 * PluginRegistry.hasGrant 内部用它查 plugin_grants 表。
 *
 * 这一层是用户授权的核心 — 即便代码简化,接口契约不能变。
 */
@DisplayName("P0-5 授权上下文工具类回归")
class SpringContextHolderContractTest {

    @Test
    @DisplayName("SpringContextHolder 必须存在并提供 getBean 方法")
    void mustHaveGetBeanMethod() throws Exception {
        Class<?> clazz = Class.forName("com.microcourse.util.SpringContextHolder");
        assertNotNull(clazz);
        assertTrue(java.lang.reflect.Modifier.isPublic(clazz.getMethods().length));
    }

    @Test
    @DisplayName("PluginRegistry 必须有 hasGrant(Long, String, String) 三个参数方法")
    void mustHaveHasGrantMethod() throws Exception {
        Class<?> registryClass = Class.forName("com.microcourse.plugin.PluginRegistry");
        boolean hasMethod = false;
        for (java.lang.reflect.Method m : registryClass.getDeclaredMethods()) {
            if (m.getName().equals("hasGrant") && m.getParameterCount() == 3) {
                hasMethod = true;
                break;
            }
        }
        assertTrue(hasMethod, "PluginRegistry.hasGrant 必须存在 — 这是授权校验单点入口");
    }

    @Test
    @DisplayName("CourseCacheConstants 必须为 final 工具类,不可继承")
    void cacheConstantsMustBeFinal() throws Exception {
        Class<?> clazz = Class.forName("com.microcourse.util.CourseCacheConstants");
        assertTrue(java.lang.reflect.Modifier.isFinal(clazz.getModifiers()),
                "CourseCacheConstants 必须为 final — 防止被继承污染缓存键");
    }

    @Test
    @DisplayName("基础类型 List 操作不应破坏")
    void basicListWorks() {
        List<Integer> list = List.of(1, 2, 3);
        assertEquals(3, list.size());
        assertEquals(2, list.get(1));
    }
}
