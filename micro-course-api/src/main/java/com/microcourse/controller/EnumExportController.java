package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.enums.CourseStatus;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.Gender;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 枚举导出端点（P3-9 · 前端枚举/常量与后端共享）。
 *
 * <p>路径：{@code GET /api/enums/export} —— 公开（仅枚举名/序号/code/label，无敏感数据），
 * 在 {@code SecurityConfig} 中 permitAll。</p>
 *
 * <p>用途：前端首屏可选地拉取此端点，把后端枚举的"单一真相"同步到运行时
 * （{@code window.__BACKEND_ENUMS}），消除前端硬编码字符串与后端枚举的漂移。
 * 拉取失败时前端回退到本地 {@code utils/enums.js} fallback，不影响运行。</p>
 *
 * <p>响应遵循平台统一 {@link R} 包装契约：{@code { code, message, data, timestamp }}，
 * 其中 {@code data} 为各枚举名到其枚举项列表的映射。每个枚举项至少含 {@code name}/{@code ordinal}，
 * 并按枚举是否提供对应 getter 容错附加 {@code code}/{@code value}/{@code description}/{@code label}。</p>
 */
@RestController
@RequestMapping("/api/enums")
public class EnumExportController {

    @GetMapping("/export")
    @PreAuthorize("permitAll()")
    public R<Map<String, List<Map<String, Object>>>> export() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

        result.put("UserRole", toList(UserRole.values()));
        result.put("CourseStatus", toList(CourseStatus.values()));
        result.put("EnrollmentStatus", toList(EnrollmentStatus.values()));
        result.put("NotificationType", toList(NotificationType.values()));
        result.put("Gender", toList(Gender.values()));

        return R.ok(result);
    }

    private List<Map<String, Object>> toList(Enum<?>[] values) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Enum<?> v : values) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", v.name());
            entry.put("ordinal", v.ordinal());
            putIfPresent(entry, v, "getCode", "code");
            putIfPresent(entry, v, "getValue", "value");
            putIfPresent(entry, v, "getDescription", "description");
            putIfPresent(entry, v, "getLabel", "label");
            list.add(entry);
        }
        return list;
    }

    /**
     * 容错反射：若枚举提供无参 getter {@code methodName} 则把返回值写入 {@code entry[key]}，
     * 否则静默跳过（不同枚举的 getter 不一致，缺失属正常情况）。
     */
    private void putIfPresent(Map<String, Object> entry, Enum<?> v, String methodName, String key) {
        try {
            Method getter = v.getDeclaringClass().getMethod(methodName);
            Object value = getter.invoke(v);
            if (value != null) {
                entry.put(key, value);
            }
        } catch (ReflectiveOperationException ignored) {
            // getter 不存在或不可访问 —— 跳过该字段
        }
    }
}
