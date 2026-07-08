package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.hermes.HermesWebhookRequest;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.HermesCourseSyncService;
import com.microcourse.service.HermesCourseSyncService.HermesSyncResult;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Hermes 课程同步 Webhook。
 *
 * <p>认证方式：每个教师在个人设置里生成自己的 API Key，
 * 调用方在 {@code X-API-Key} Header 中传入。
 * 服务端用 API Key 反查 {@code users} 表，得到调用方教师身份。</p>
 */
@RestController
@RequestMapping("/api/hermes/webhook")
public class HermesWebhookController {

    private static final Logger log = LoggerFactory.getLogger(HermesWebhookController.class);

    private final HermesCourseSyncService syncService;
    private final UserRepository userRepository;

    public HermesWebhookController(HermesCourseSyncService syncService,
                                   UserRepository userRepository) {
        this.syncService = syncService;
        this.userRepository = userRepository;
    }

    @PostMapping("/courses")
    public R<HermesSyncResult> receiveCourse(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                              @Valid @RequestBody HermesWebhookRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[HermesWebhook] Missing X-API-Key header");
            throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
        }

        Optional<User> callerOpt = userRepository.findByApiKey(apiKey);
        if (callerOpt.isEmpty()) {
            log.warn("[HermesWebhook] API key not found or user inactive");
            throw new BusinessException(ErrorCode.HERMES_INVALID_API_KEY);
        }
        User caller = callerOpt.get();

        HermesSyncResult result = syncService.upsertCourse(request, caller.getId());
        log.info("[HermesSync] userId={} username={} hermesCourseId={} action={}",
                caller.getId(), caller.getUsername(), request.getHermesCourseId(), result.getAction());
        return R.ok(result);
    }
}