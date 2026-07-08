package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.HermesCourseSyncService;
import com.microcourse.service.HermesCourseSyncService.HermesSyncResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hermes/webhook")
public class HermesWebhookController {

    private static final Logger log = LoggerFactory.getLogger(HermesWebhookController.class);

    private final HermesCourseSyncService syncService;
    private final String hermesApiKey;

    public HermesWebhookController(HermesCourseSyncService syncService,
                                   @Value("${hermes.api-key:}") String hermesApiKey) {
        this.syncService = syncService;
        this.hermesApiKey = hermesApiKey;
    }

    @PostMapping("/courses")
    public R<HermesSyncResult> receiveCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @Valid @RequestBody HermesWebhookRequest request) {
        if (hermesApiKey == null || hermesApiKey.isEmpty()) {
            log.error("[HermesWebhook] Hermes API key not configured");
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (apiKey == null || !hermesApiKey.equals(apiKey)) {
            log.warn("[HermesWebhook] Invalid API key received");
            throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
        }

        HermesSyncResult result = syncService.upsertCourse(request);
        return R.ok(result);
    }
}
