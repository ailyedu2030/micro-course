package com.microcourse.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.OperationLogVO;
import com.microcourse.entity.OperationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 操作日志装配器：将 OperationLog Entity 转换为 OperationLog VO。
 * 负责 JSON 解析、module 推断、method/path 提取等逻辑。
 */
@Component
public class OperationLogAssembler {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAssembler.class);

    private final ObjectMapper objectMapper;

    public OperationLogAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 OperationLog Entity 转换为 OperationLogVO
     *
     * @param entity     操作日志实体
     * @param userNameMap 用户ID→用户名 映射（可空）
     * @return 操作日志 VO
     */
    public OperationLogVO toVO(OperationLog entity, Map<Long, String> userNameMap) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setAction(entity.getAction());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setIp(entity.getIp());
        vo.setCreatedAt(entity.getCreatedAt());

        // success Boolean → status Integer (1=成功, 0=失败)
        vo.setStatus(entity.getSuccess() != null && entity.getSuccess() ? 1 : 0);

        // duration 从 entity.durationMs 映射
        vo.setDuration(entity.getDurationMs());

        // module 从 action/targetType 推断
        String action = entity.getAction();
        if ("LOGIN".equals(action) || "LOGOUT".equals(action)) {
            vo.setModule("AUTH");
        } else if (entity.getTargetType() != null && !entity.getTargetType().isEmpty()) {
            vo.setModule(entity.getTargetType());
        } else {
            vo.setModule(null);
        }

        // 从批量查询的 Map 中获取 username
        if (entity.getUserId() != null && userNameMap != null) {
            vo.setUsername(userNameMap.getOrDefault(entity.getUserId(), null));
        }

        // detail JSON String → Map，同时提取 method/path/errorMessage
        if (entity.getDetail() != null && !entity.getDetail().isEmpty()) {
            try {
                Map<String, Object> detailMap = objectMapper.readValue(
                        entity.getDetail(), new TypeReference<Map<String, Object>>() {});
                vo.setDetail(detailMap);

                // 从 detail 提取 method 和 path
                if (detailMap.containsKey("method")) {
                    vo.setMethod(String.valueOf(detailMap.get("method")));
                }
                if (detailMap.containsKey("path")) {
                    vo.setPath(String.valueOf(detailMap.get("path")));
                }

                // 如果操作失败，从 detail 中提取 errorMessage
                if (vo.getStatus() == 0 && detailMap.containsKey("errorMessage")) {
                    vo.setErrorMessage(String.valueOf(detailMap.get("errorMessage")));
                }
            } catch (JsonProcessingException e) {
                log.warn("解析操作日志 detail JSON 失败, logId={}, detail={}", entity.getId(), entity.getDetail(), e);
                vo.setDetail(Map.of("raw", entity.getDetail()));
            }
        }

        return vo;
    }
}
