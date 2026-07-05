package com.microcourse.controller;

import com.microcourse.dto.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * P1C-031: 服务端时间接口 — 解决前后端"今天"定义不一致的问题。
 * 前端调用此接口获取服务端日期，避免因 JVM 时区与客户端时区差异导致跨日判断错误。
 */
@RestController
@RequestMapping("/api")
public class ServerTimeController {

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @GetMapping("/server-time")
    public R<Map<String, Object>> getServerTime() {
        LocalDate today = LocalDate.now(CN_ZONE);
        Map<String, Object> result = new HashMap<>();
        result.put("date", today.format(DATE_FMT));
        result.put("timestamp", System.currentTimeMillis());
        result.put("timezone", "Asia/Shanghai");
        return R.ok(result);
    }
}
