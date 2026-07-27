package com.microcourse.controller;

import com.microcourse.dto.FrontendErrorReportRequest;
import com.microcourse.dto.R;
import com.microcourse.util.LogSanitizer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/frontend-errors")
public class FrontendErrorController {

    private static final Logger log = LoggerFactory.getLogger(FrontendErrorController.class);

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<Void> report(@Valid @RequestBody FrontendErrorReportRequest body) {
        // P1 安全修复: 用户可控 message/url/line 先清理控制字符再写日志，防止日志注入
        log.warn("[FrontendError] message={}, url={}, line={}",
                LogSanitizer.sanitizeForLog(body.getMessage() != null ? body.getMessage() : ""),
                LogSanitizer.sanitizeForLog(body.getUrl() != null ? body.getUrl() : ""),
                LogSanitizer.sanitizeForLog(body.getLine() != null ? body.getLine() : ""));
        if (log.isDebugEnabled()) {
            log.debug("[FrontendError] stack={}", LogSanitizer.sanitizeForLog(body.getStack() != null ? body.getStack() : ""));
        }
        return R.ok();
    }
}
