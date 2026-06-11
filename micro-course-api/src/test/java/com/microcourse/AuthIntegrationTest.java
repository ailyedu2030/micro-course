package com.microcourse;

import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.R;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class AuthIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");
        ResponseEntity<R> resp = restTemplate.postForEntity("/api/auth/login", req, R.class);
        assertEquals(200, resp.getStatusCodeValue());
    }
}