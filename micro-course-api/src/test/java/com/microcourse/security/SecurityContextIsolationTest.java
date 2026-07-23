package com.microcourse.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityContextIsolationTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("child thread must not inherit request authentication")
    void childThreadMustNotInheritAuthentication() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(99L, "N/A");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AtomicReference<Object> childPrincipal = new AtomicReference<>("UNSET");
        CountDownLatch latch = new CountDownLatch(1);

        Thread child = new Thread(() -> {
            try {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                childPrincipal.set(auth == null ? null : auth.getPrincipal());
            } finally {
                latch.countDown();
            }
        }, "security-context-isolation-test");
        child.start();

        assertTrue(latch.await(5, TimeUnit.SECONDS), "child thread should finish in time");
        assertNull(childPrincipal.get(), "authentication must stay inside the request thread");
    }
}
