package com.microcourse.security;

import com.microcourse.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 权限矩阵 v4.0 可执行化测试
 *
 * <p>解析 docs/permission-matrix-v4.0.yaml 为预期权限表,
 * 验证 YAML 格式正确且路径符合 API 契约规范 (无 /api/v1 前缀)。</p>
 */
@DisplayName("权限矩阵 v4.0 可执行化测试")
public class EndpointPermissionTest extends BaseIntegrationTest {

    @Test
    @DisplayName("1. permission-matrix-v4.0.yaml 存在且格式正确")
    public void test_yamlExists() throws Exception {
        Path yamlPath = Paths.get("../docs/permission-matrix-v4.0.yaml");
        assertTrue(Files.exists(yamlPath), "权限矩阵 YAML 必须存在: " + yamlPath);

        String content = Files.readString(yamlPath);
        assertTrue(content.contains("version: \"4.0\""), "YAML 必须声明 v4.0");
        assertTrue(content.contains("endpoints:"), "YAML 必须包含 endpoints 字段");
        long endpointCount = content.lines().filter(l -> l.matches("^\\s*- path:.*")).count();
        assertTrue(endpointCount >= 60, "端点数应 >= 60, 实际: " + endpointCount);
        System.out.println("[PermissionMatrix] YAML 包含 " + endpointCount + " 个端点");
    }

    @Test
    @DisplayName("2. 端点路径符合 API 契约 (无 /api/v1 前缀)")
    public void test_endpointPathsNoV1Prefix() throws Exception {
        Path yamlPath = Paths.get("../docs/permission-matrix-v4.0.yaml");
        String content = Files.readString(yamlPath);
        Matcher matcher = Pattern.compile("path: \"(/api/[^\"]+)\"").matcher(content);
        Set<String> paths = new HashSet<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertFalse(paths.isEmpty(), "至少应包含 1 个端点路径");
        for (String p : paths) {
            assertFalse(p.startsWith("/api/v1/"), "路径不得使用 /api/v1 前缀: " + p);
            assertTrue(p.startsWith("/api/"), "路径必须以 /api/ 开头: " + p);
        }
        System.out.println("[PermissionMatrix] 路径检查通过, " + paths.size() + " 个路径均符合规范");
    }

    @Test
    @DisplayName("3. 权限角色声明格式正确")
    public void test_roleFormat() throws Exception {
        Path yamlPath = Paths.get("../docs/permission-matrix-v4.0.yaml");
        String content = Files.readString(yamlPath);
        Matcher matcher = Pattern.compile("roles:\\s*\\[([^\\]]+)\\]").matcher(content);
        Set<String> validRoles = Set.of("STUDENT", "TEACHER", "ADMIN", "ACADEMIC");
        int roleCount = 0;
        while (matcher.find()) {
            String[] roles = matcher.group(1).split(",");
            for (String role : roles) {
                String trimmed = role.trim();
                assertTrue(validRoles.contains(trimmed), "未知角色: " + trimmed);
            }
            roleCount++;
        }
        assertTrue(roleCount >= 50, "应至少声明 50 个端点的 roles, 实际: " + roleCount);
        System.out.println("[PermissionMatrix] 角色声明格式正确, " + roleCount + " 处");
    }
}