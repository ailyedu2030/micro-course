package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CreateReportRequest;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.ReviewReportVO;
import com.microcourse.dto.ReviewReportActionRequest;
import com.microcourse.entity.CourseReview;
import com.microcourse.entity.DiscussionComment;
import com.microcourse.entity.DiscussionPost;
import com.microcourse.entity.ReviewReport;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.ReviewReportRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.ReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 举报处理 Service 实现
 */
@Service
public class ReportServiceImpl implements ReportService {

    private final ReviewReportRepository reviewReportRepository;
    private final UserRepository userRepository;
    private final CourseReviewRepository courseReviewRepository;
    private final DiscussionPostRepository discussionPostRepository;
    private final DiscussionCommentRepository discussionCommentRepository;

    public ReportServiceImpl(ReviewReportRepository reviewReportRepository,
                             UserRepository userRepository,
                             CourseReviewRepository courseReviewRepository,
                             DiscussionPostRepository discussionPostRepository,
                             DiscussionCommentRepository discussionCommentRepository) {
        this.reviewReportRepository = reviewReportRepository;
        this.userRepository = userRepository;
        this.courseReviewRepository = courseReviewRepository;
        this.discussionPostRepository = discussionPostRepository;
        this.discussionCommentRepository = discussionCommentRepository;
    }

    @Override
    @Transactional
    public void create(Long userId, CreateReportRequest req) {
        String type = req.getReportedItemType();
        if (!List.of("REVIEW", "DISCUSSION_POST", "DISCUSSION_COMMENT").contains(type)) {
            throw new BusinessException(ErrorCode.REPORT_INVALID_TYPE);
        }

        ReviewReport report = new ReviewReport();
        report.setReporterId(userId);
        report.setReportedItemType(type);
        report.setReportedItemId(req.getReportedItemId());
        report.setReason(req.getReason());
        report.setStatus(0);
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        reviewReportRepository.insert(report);
    }

    @Override
    public PageResult<ReviewReportVO> pageByAdmin(int page, int size, Integer status) {
        LambdaQueryWrapper<ReviewReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ReviewReport::getCreatedAt);
        if (status != null) {
            wrapper.eq(ReviewReport::getStatus, status);
        }

        IPage<ReviewReport> p = reviewReportRepository.selectPage(new Page<>(page, size), wrapper);

        // 收集所有用户ID
        List<ReviewReport> records = p.getRecords();
        List<Long> userIds = records.stream()
                .map(ReviewReport::getReporterId)
                .collect(Collectors.toList());
        userIds.addAll(records.stream()
                .map(ReviewReport::getReviewerId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList()));

        // 批量查询用户
        Map<Long, String> userNameMap;
        if (userIds.isEmpty()) {
            userNameMap = Map.of();
        } else {
            List<User> users = userRepository.selectBatchIds(userIds.stream().distinct().collect(Collectors.toList()));
            userNameMap = users.stream().collect(Collectors.toMap(User::getId, User::getRealName));
        }

        List<ReviewReportVO> voList = records.stream().map(r -> {
            ReviewReportVO vo = new ReviewReportVO();
            vo.setId(r.getId());
            vo.setReporterId(r.getReporterId());
            vo.setReporterName(userNameMap.getOrDefault(r.getReporterId(), "未知用户"));
            vo.setReportedItemType(r.getReportedItemType());
            vo.setReportedItemId(r.getReportedItemId());
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setStatusText(getStatusText(r.getStatus()));
            vo.setReviewerId(r.getReviewerId());
            vo.setReviewerName(r.getReviewerId() != null ? userNameMap.getOrDefault(r.getReviewerId(), "未知用户") : null);
            vo.setReviewNotes(r.getReviewNotes());
            vo.setCreatedAt(r.getCreatedAt());
            vo.setUpdatedAt(r.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList());

        return PageResult.of(voList, p.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void review(Long reportId, ReviewReportActionRequest req, Long reviewerId) {
        ReviewReport report = reviewReportRepository.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ErrorCode.REPORT_NOT_FOUND);
        }
        if (report.getStatus() != 0) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_REVIEWED);
        }

        String action = req.getAction();
        report.setReviewerId(reviewerId);
        report.setReviewNotes(req.getReviewNotes());
        report.setUpdatedAt(LocalDateTime.now());

        if ("DISMISS".equals(action)) {
            // 驳回: 内容保留, 标记已驳回
            report.setStatus(1);
            reviewReportRepository.updateById(report);

        } else if ("REMOVE".equals(action)) {
            // 通过并删除: 标记已处理, 删除被举报内容
            report.setStatus(2);
            reviewReportRepository.updateById(report);
            deleteReportedContent(report.getReportedItemType(), report.getReportedItemId());

        } else {
            throw new BusinessException(ErrorCode.REPORT_INVALID_ACTION);
        }
    }

    /**
     * 根据类型物理删除被举报内容（软删除 handled by @TableLogic）
     */
    private void deleteReportedContent(String itemType, Long itemId) {
        switch (itemType) {
            case "REVIEW":
                courseReviewRepository.deleteById(itemId);
                break;
            case "DISCUSSION_POST":
                discussionPostRepository.deleteById(itemId);
                break;
            case "DISCUSSION_COMMENT":
                discussionCommentRepository.deleteById(itemId);
                break;
            default:
                throw new BusinessException(ErrorCode.REPORT_INVALID_TYPE);
        }
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待处理";
            case 1: return "已驳回";
            case 2: return "已处理";
            default: return "未知";
        }
    }
}
