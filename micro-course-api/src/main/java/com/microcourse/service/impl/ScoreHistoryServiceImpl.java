package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.ScoreHistoryVO;
import com.microcourse.entity.ScoreHistory;
import com.microcourse.repository.ScoreHistoryRepository;
import com.microcourse.service.ScoreHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ScoreHistoryServiceImpl implements ScoreHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreHistoryServiceImpl.class);

    private final ScoreHistoryRepository scoreHistoryRepository;

    public ScoreHistoryServiceImpl(ScoreHistoryRepository scoreHistoryRepository) {
        this.scoreHistoryRepository = scoreHistoryRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordChange(Long enrollmentId, String fieldName, String oldValue,
                             String newValue, String changeType, String reason, Long operatorId) {
        try {
            ScoreHistory h = new ScoreHistory();
            h.setEnrollmentId(enrollmentId);
            h.setFieldName(fieldName);
            h.setOldValue(oldValue);
            h.setNewValue(newValue);
            h.setChangeType(changeType);
            h.setReason(reason);
            h.setOperatorId(operatorId);
            h.setCreatedAt(LocalDateTime.now());
            scoreHistoryRepository.insert(h);
        } catch (Exception e) {
            LOG.warn("[ScoreHistory] 记录失败 enrollmentId={} field={}", enrollmentId, fieldName, e);
            // 审计失败不应阻塞主业务，降级为 warn 不抛
        }
    }

    @Override
    public List<ScoreHistoryVO> listByEnrollment(Long enrollmentId) {
        List<ScoreHistory> list = scoreHistoryRepository.selectList(
                new LambdaQueryWrapper<ScoreHistory>()
                        .eq(ScoreHistory::getEnrollmentId, enrollmentId)
                        .orderByDesc(ScoreHistory::getCreatedAt)
        );
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    private ScoreHistoryVO convertToVO(ScoreHistory h) {
        ScoreHistoryVO vo = new ScoreHistoryVO();
        vo.setId(h.getId());
        vo.setEnrollmentId(h.getEnrollmentId());
        vo.setFieldName(h.getFieldName());
        vo.setOldValue(h.getOldValue());
        vo.setNewValue(h.getNewValue());
        vo.setChangeType(h.getChangeType());
        vo.setReason(h.getReason());
        vo.setOperatorId(h.getOperatorId());
        vo.setChangedAt(h.getCreatedAt());
        return vo;
    }
}
