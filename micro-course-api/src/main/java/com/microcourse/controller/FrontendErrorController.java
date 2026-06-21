package com.microcourse.controller;

import com.microcourse.dto.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/frontend-errors")
public class FrontendErrorController {

    private static final Logger log = LoggerFactory.getLogger(FrontendErrorController.class);

    @PostMapping
    public R<Void> report(@RequestBody Map<String, Object> body) {
        log.warn("[FrontendError] message={}, url={}, line={}",
                body.get("message"), body.get("url"), body.get("line"));
        if (log.isDebugEnabled()) {
            log.debug("[FrontendError] stack={}", body.get("stack"));
        }
        return R.ok();
    }
}
