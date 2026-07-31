package com.microcourse.repository;

import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRepository 自定义 SQL 隔离测试（含 apiKeyHash 测试）。
 * <p>依赖 p0-seed.sql 提供的基础数据：user(id=1 admin, id=6 teacher, id=7 student)。</p>
 *
 * <h3>【现象】findByApiKeyHash(S-004 安全增强) 等自定义 SQL 缺乏隔离测试</h3>
 * <h3>【根因】用户查询涉及安全认证（apiKeyHash 替换明文 apiKey），错误可能导致认证绕过</h3>
 * <h3>【验证】mvn test -Dtest='UserRepositoryTest' PASS</h3>
 * <h3>【防止再发】所有自定义 SQL 被隔离测试覆盖，特别是安全相关方法</h3>
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@Sql(scripts = "/sql/p0-seed.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    // ==================== findByUsername ====================

    @Test
    @DisplayName("findByUsername: 找到活跃用户")
    void findByUsername_returnsActiveUser() {
        Optional<User> user = userRepository.findByUsername("admin");
        assertTrue(user.isPresent());
        assertEquals("admin", user.get().getUsername());
    }

    @Test
    @DisplayName("findByUsername: 不存在用户返回 empty")
    void findByUsername_notFound_returnsEmpty() {
        Optional<User> user = userRepository.findByUsername("nonexistent_user_xyz");
        assertFalse(user.isPresent());
    }

    @Test
    @DisplayName("findByUsername: 区分大小写")
    void findByUsername_caseSensitive() {
        Optional<User> user = userRepository.findByUsername("Admin");
        assertFalse(user.isPresent(), "PostgreSQL 字符串比较默认区分大小写");
    }

    // ==================== selectByIdIncludingDeleted ====================

    @Test
    @DisplayName("selectByIdIncludingDeleted: 查到正常用户")
    void selectByIdIncludingDeleted_returnsActiveUser() {
        User user = userRepository.selectByIdIncludingDeleted(1L);
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
    }

    @Test
    @DisplayName("selectByIdIncludingDeleted: 不存在返回 null")
    void selectByIdIncludingDeleted_notFound_returnsNull() {
        User user = userRepository.selectByIdIncludingDeleted(99999L);
        assertNull(user);
    }

    // ==================== restoreToActive ====================

    @Test
    @DisplayName("restoreToActive: 恢复用户为 ACTIVE")
    void restoreToActive_restoresUser() {
        // 先软删除用户 7（设置 deleted_at）
        User student = userRepository.selectByIdIncludingDeleted(7L);
        assertNotNull(student);
        student.setDeletedAt(LocalDateTime.now());
        userRepository.updateById(student);

        int affected = userRepository.restoreToActive(7L);
        assertEquals(1, affected);
        User restored = userRepository.selectByIdIncludingDeleted(7L);
        assertNull(restored.getDeletedAt(), "deleted_at 应被清空");
    }

    @Test
    @DisplayName("restoreToActive: 不存在用户返回 0")
    void restoreToActive_notFound_returnsZero() {
        int affected = userRepository.restoreToActive(99999L);
        assertEquals(0, affected);
    }

    // ==================== findByApiKey（plaintext — 已废弃，V324 迁移清空 api_key 列） ====================

    @Test
    @DisplayName("findByApiKey(plaintext) 已废弃：V324 清空明文后永远返回 empty")
    void findByApiKey_returnsEmpty_afterV324Migration() {
        // P0-S004-2 修复: V324 迁移清空 api_key 明文列后，明文查找永远不命中。
        // 此测试验证 V324 修复实际生效（防止回归）。
        User user = createUserWithApiKey("plaintext_lookup_user", "test-api-key-12345");
        userRepository.insert(user);
        // api_key_hash 已自动写入，但 api_key 列被 User.setApiKey 设为 null
        Optional<User> byPlain = userRepository.findByApiKey("test-api-key-12345");
        assertFalse(byPlain.isPresent(), "明文 api_key 列 NULL，findByApiKey 应返回 empty（V324 修复生效）");
    }

    @Test
    @DisplayName("findByApiKey: 不存在的明文 key 返回 empty（保留兼容测试）")
    void findByApiKey_noMatch_returnsEmpty() {
        Optional<User> found = userRepository.findByApiKey("nonexistent-api-key");
        assertFalse(found.isPresent());
    }

    // ==================== findByApiKeyHash（S-004 安全增强） ====================

    @Test
    @DisplayName("findByApiKeyHash: 通过 hash 查找到用户")
    void findByApiKeyHash_returnsUser() {
        String apiKey = "secure-api-key-67890";
        String hash = DigestUtils.sha256Hex(apiKey);
        User user = createUserWithApiKey("hash_user", apiKey);
        userRepository.insert(user);

        Optional<User> found = userRepository.findByApiKeyHash(hash);
        assertTrue(found.isPresent());
        assertEquals("hash_user", found.get().getUsername());
    }

    @Test
    @DisplayName("findByApiKeyHash: hash 不匹配返回 empty")
    void findByApiKeyHash_noMatch_returnsEmpty() {
        String hash = DigestUtils.sha256Hex("wrong-api-key");
        Optional<User> found = userRepository.findByApiKeyHash(hash);
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("findByApiKeyHash: null hash 返回 empty")
    void findByApiKeyHash_nullHash_returnsEmpty() {
        Optional<User> found = userRepository.findByApiKeyHash("0000000000000000000000000000000000000000000000000000000000000000");
        assertFalse(found.isPresent());
    }

    // ==================== helper ====================

    private User createUserWithApiKey(String username, String apiKey) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("test-hash-placeholder");
        user.setRealName("Test User");
        user.setRole(UserRole.TEACHER);
        user.setStatus(1);
        user.setCasBound(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setVersion(0);
        // setApiKey 会自动计算 apiKeyHash
        user.setApiKey(apiKey);
        return user;
    }
}
