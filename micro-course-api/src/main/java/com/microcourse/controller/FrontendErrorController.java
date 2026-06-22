package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/frontend-errors")
public class FrontendErrorController {

    private static final Logger log = LoggerFactory.getLogger(FrontendErrorController.class);

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<Void> report(@RequestBody Map<String, Object> body) {
        // P1 安全修复: 用户可控 message/url/line 先清理控制字符再写日志，防止日志注入
        log.warn("[FrontendError] message={}, url={}, line={}",
                LogSanitizer.sanitizeForLog(toStringVal(body.get("message"))),
                LogSanitizer.sanitizeForLog(toStringVal(body.get("url"))),
                LogSanitizer.sanitizeForLog(toStringVal(body.get("line"))));
        if (log.isDebugEnabled()) {
            log.debug("[FrontendError] stack={}", LogSanitizer.sanitizeForLog(toStringVal(body.get("stack"))));
        }
        return R.ok();
    }

    private String toStringVal(Object val) {
        return val != null ? val.toString() : "";
    }
}
