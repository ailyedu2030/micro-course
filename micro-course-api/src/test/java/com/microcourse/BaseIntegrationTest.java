package com.microcourse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类
 * 提供通用的测试环境和工具方法
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    protected String baseUrl;

    @BeforeAll
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }
}