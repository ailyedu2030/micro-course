package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CourseReviewLogVO;
import com.microcourse.entity.CourseReviewLog;
import com.microcourse.entity.User;
import com.microcourse.repository.CourseReviewLogRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseReviewLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程审核日志服务实现
 */
@Service
public class CourseReviewLogServiceImpl implements CourseReviewLogService {

    private final CourseReviewLogRepository repository;
    private final UserRepository userRepository;

    public CourseReviewLogServiceImpl(CourseReviewLogRepository repository,
                                       UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseReviewLogVO> listByCourse(Long courseId) {
        LambdaQueryWrapper<CourseReviewLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseReviewLog::getCourseId, courseId)
                .orderByDesc(CourseReviewLog::getCreatedAt);

        List<CourseReviewLog> logs = repository.selectList(wrapper);

        // N+1 修复：批量预加载 reviewer 信息
        Set<Long> reviewerIds = logs.stream()
                .map(CourseReviewLog::getReviewerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> reviewerNameMap = Map.of();
        if (!reviewerIds.isEmpty()) {
            reviewerNameMap = userRepository.selectBatchIds(reviewerIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getRealName));
        }

        final Map<Long, String> finalNameMap = reviewerNameMap;
        return logs.stream().map(log -> {
            CourseReviewLogVO vo = new CourseReviewLogVO();
            vo.setId(log.getId());
            vo.setCourseId(log.getCourseId());
            vo.setReviewerId(log.getReviewerId());
            vo.setReviewerName(finalNameMap.getOrDefault(log.getReviewerId(), "未知"));
            vo.setAction(log.getAction());
            vo.setReason(log.getReason());
            vo.setPreviousStatus(log.getPreviousStatus());
            vo.setNewStatus(log.getNewStatus());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }
}
