package com.microcourse.controller;

import com.microcourse.config.GrayReleaseFilter;
import com.microcourse.dto.R;
import com.microcourse.enums.FeatureFlag;
import com.microcourse.service.GrayReleaseService;
import com.microcourse.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 灰度发布诊断端点 (F10-D2)
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@code GET /api/gray-release/status} - 当前用户灰度状态 + 所有 flag 状态
 * </ul>
 *
 * <h3>权限</h3>
 * 仅 ADMIN（运维诊断用，普通用户不暴露灰度状态）— 防止用户探测灰度名单。
 *
 * <h3>【现象】</h3>
 * 原 {@code gray-release.sh} add/roll-out 写入 Redis 但 {@code micro-course-api} 不读取，
 * 灰度白名单与功能开关实际不改变用户可见行为（F10-D2 P2）。
 *
 * <h3>【根因】</h3>
 * 后端无灰度状态查询接口，运维只能 SSH 到服务器跑 redis-cli 查看原始数据。
 *
 * <h3>【修复】</h3>
 * 本端点 + {@link GrayReleaseService} 完整对接 {@code gray-release.sh} 脚本。
 * 运维在控制台 add/roll-out 后，可调本端点确认状态。
 *
 * <h3>示例响应</h3>
 * <pre>
 * GET /api/gray-release/status
 * {
 *   "userId": 1,
 *   "isGrayUser": true,
 *   "flags": {
 *     "MICRO_SPECIALTY_CLASS_IMPORT": true,
 *     "NEW_PAYMENT_FLOW": false,
 *     "AI_NARRATION_BATCH_GEN": false
 *   }
 * }
 * </pre>
 *
 * @author F10-D2 Phase 9 (2026-08-18)
 */
@RestController
@RequestMapping("/api/gray-release")
@PreAuthorize("hasRole('ADMIN')")
public class GrayReleaseController {

    private final GrayReleaseService grayReleaseService;

    public GrayReleaseController(GrayReleaseService grayReleaseService) {
        this.grayReleaseService = grayReleaseService;
    }

    /**
     * 当前用户灰度状态 + 所有 flag 状态
     */
    @GetMapping("/status")
    public R<Map<String, Object>> getStatus(HttpServletRequest request) {
        Long userId = SecurityUtil.getCurrentUserIdOpt();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("isGrayUser", Boolean.TRUE.equals(request.getAttribute(GrayReleaseFilter.ATTR_IS_GRAY_USER)));

        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            flags.put(flag.name().toLowerCase(), grayReleaseService.isFeatureEnabled(flag));
        }
        result.put("flags", flags);

        return R.ok(result);
    }
}