package com.microcourse.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.dto.CoursePricingInfoVO;
import com.microcourse.dto.CoursePricingRequest;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CoursePricingService;
import com.microcourse.util.CourseCacheConstants;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.XssSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CoursePricingServiceImpl implements CoursePricingService {

    private static final Logger LOG = LoggerFactory.getLogger(CoursePricingServiceImpl.class);

    // 常量已迁移至 CourseCacheConstants

    private final ObjectMapper objectMapper;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RedisUtil redisUtil;

    public CoursePricingServiceImpl(ObjectMapper objectMapper,
                                     CourseRepository courseRepository,
                                     UserRepository userRepository,
                                     DepartmentRepository departmentRepository,
                                     RedisUtil redisUtil) {
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.redisUtil = redisUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePricing(Long courseId, CoursePricingRequest request) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        Long userId = SecurityUtil.getCurrentUserId();
        if (!course.getTeacherId().equals(userId) && !SecurityUtil.isAdmin())
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        course.setPrice(request.getBasePrice());
        course.setListPrice(request.getBasePrice());
        course.setFreeAccessScope(request.getFreeAccessScope());
        course.setFreeDeptIds(request.getFreeDeptIds());
        course.setDiscountScope(request.getDiscountScope());
        course.setDiscountPercent(request.getDiscountPercent());
        if ("REJECTED".equals(course.getPricingStatus()) || "APPROVED".equals(course.getPricingStatus())) {
            course.setPricingStatus("DRAFT");
            course.setPricingReviewedAt(null);
            course.setPricingReviewedBy(null);
        }
        course.setUpdatedAt(LocalDateTime.now());
        courseRepository.updateById(course);
        evictCourseCache(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPricingForReview(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        Long userId = SecurityUtil.getCurrentUserId();
        if (!course.getTeacherId().equals(userId) && !SecurityUtil.isAdmin())
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        if (!"DRAFT".equals(course.getPricingStatus()) && !"REJECTED".equals(course.getPricingStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前定价状态 " + course.getPricingStatus() + " 不允许提交审核");
        }
        course.setPricingStatus("PENDING");
        course.setUpdatedAt(LocalDateTime.now());
        int affected = courseRepository.updateById(course);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "提交定价审核失败，请重试");
        }
        evictCourseCache(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPricing(Long courseId, boolean approved, String reason) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isAdminOrAcademic())
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        if (!"PENDING".equals(course.getPricingStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "当前定价状态 " + course.getPricingStatus() + " 不允许审核");
        }
        if (approved) {
            if (course.getListPrice() == null || course.getListPrice().compareTo(BigDecimal.ZERO) == 0) {
                course.setListPrice(course.getPrice());
            }
            course.setPricingStatus("APPROVED");
        } else {
            if (reason == null || reason.trim().length() < 2) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "驳回原因不能为空");
            }
            course.setPricingStatus("REJECTED");
            course.setRejectReason(XssSanitizer.sanitizePlainText(reason));
        }
        course.setPricingReviewedAt(LocalDateTime.now());
        course.setPricingReviewedBy(SecurityUtil.getCurrentUserId());
        course.setUpdatedAt(LocalDateTime.now());
        int affected = courseRepository.updateById(course);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "审核定价失败，请重试");
        }
        evictCourseCache(courseId);
    }

    @Override
    public Map<String, Object> getPricingForAdopter(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        Long adopterTeacherId = SecurityUtil.getCurrentUserId();
        User adopter = userRepository.selectById(adopterTeacherId);
        if (adopter == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);

        Map<String, Object> result = new HashMap<>();
        BigDecimal basePrice = course.getPrice() != null ? course.getPrice() : BigDecimal.ZERO;
        result.put("basePrice", basePrice);
        result.put("sameSchool", false);
        result.put("sameDept", false);
        result.put("finalPrice", basePrice);
        result.put("feeNote", "");

        if (adopter.getDepartmentId() != null) {
            Department adopterDept = departmentRepository.selectById(adopter.getDepartmentId());
            if (adopterDept != null) {
                if ("same_department".equals(course.getFreeAccessScope()) && course.getFreeDeptIds() != null) {
                    java.util.List<Long> deptIds = parseDeptIds(course.getFreeDeptIds());
                    if (deptIds.contains(adopter.getDepartmentId())) {
                        result.put("finalPrice", BigDecimal.ZERO);
                        result.put("freeAccess", true);
                        result.put("feeNote", "免费（同院系）");
                        return result;
                    }
                }
                if ("same_college".equals(course.getFreeAccessScope())) {
                    result.put("finalPrice", BigDecimal.ZERO);
                    result.put("freeAccess", true);
                    result.put("feeNote", "免费（同学院）");
                    return result;
                }
                if ("same_school".equals(course.getDiscountScope())) {
                    result.put("sameSchool", true);
                    long percent = course.getDiscountPercent() != null ? course.getDiscountPercent() : 70;
                    BigDecimal finalPrice = basePrice.multiply(BigDecimal.valueOf(100 - percent))
                            .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
                    result.put("finalPrice", finalPrice);
                    result.put("feeNote", percent + "% 折扣 (同校)");
                    return result;
                }
            }
        }
        result.put("feeNote", "跨院系选课，按原价计费");
        return result;
    }

    @Override
    public CoursePricingInfoVO getMyPricing(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);

        CoursePricingInfoVO vo = new CoursePricingInfoVO();
        BigDecimal listPrice = course.getListPrice() != null ? course.getListPrice()
                : (course.getPrice() != null ? course.getPrice() : BigDecimal.ZERO);
        vo.setListPrice(listPrice);
        vo.setFreeAccessScope(course.getFreeAccessScope());
        vo.setFreeAccessScopeLabel(getFreeAccessScopeLabel(course.getFreeAccessScope()));
        vo.setDiscountScope(course.getDiscountScope());
        vo.setDiscountPercent(course.getDiscountPercent());

        if ("REJECTED".equals(course.getPricingStatus())) {
            vo.setFinalPrice(listPrice);
            vo.setFree(false);
            vo.setFeeNote("定价已被驳回，请联系教师或管理员");
            return vo;
        }

        if (Boolean.TRUE.equals(course.getIsFree()) || (listPrice.compareTo(BigDecimal.ZERO) == 0)) {
            vo.setFinalPrice(BigDecimal.ZERO);
            vo.setFree(true);
            vo.setFeeNote("免费课程");
            return vo;
        }

        Long userId;
        try {
            userId = SecurityUtil.getCurrentUserId();
        } catch (Exception e) {
            vo.setFinalPrice(listPrice);
            vo.setFree(false);
            vo.setFeeNote("登录后查看您的专属价格");
            return vo;
        }

        User user = userRepository.selectById(userId);
        if (user == null || user.getDepartmentId() == null) {
            vo.setFinalPrice(listPrice);
            vo.setFree(false);
            vo.setFeeNote("");
            return vo;
        }

        if ("same_department".equals(course.getFreeAccessScope()) && course.getFreeDeptIds() != null) {
            java.util.List<Long> deptIds = parseDeptIds(course.getFreeDeptIds());
            if (deptIds.contains(user.getDepartmentId())) {
                vo.setFinalPrice(BigDecimal.ZERO);
                vo.setFree(true);
                vo.setFeeNote("免费（同院系）");
                return vo;
            }
        }

        if ("same_college".equals(course.getFreeAccessScope())) {
            vo.setFinalPrice(BigDecimal.ZERO);
            vo.setFree(true);
            vo.setFeeNote("免费（同学院）");
            return vo;
        }

        // 同校免费优先于折扣：免费 > 折扣
        if ("same_school".equals(course.getFreeAccessScope())) {
            vo.setFinalPrice(BigDecimal.ZERO);
            vo.setFree(true);
            vo.setFeeNote("免费（同校）");
            return vo;
        }

        if ("same_school".equals(course.getDiscountScope())) {
            long percent = course.getDiscountPercent() != null ? course.getDiscountPercent() : 70;
            BigDecimal finalPrice = listPrice.multiply(BigDecimal.valueOf(100 - percent))
                    .divide(BigDecimal.valueOf(100), RoundingMode.HALF_UP);
            vo.setFinalPrice(finalPrice);
            vo.setFree(false);
            vo.setFeeNote(percent + "% 折扣（同校）");
            return vo;
        }

        vo.setFinalPrice(listPrice);
        vo.setFree(false);
        vo.setFeeNote("");
        return vo;
    }

    private void evictCourseCache(Long courseId) {
        try {
            redisUtil.delete(CourseCacheConstants.COURSE_CACHE_PREFIX + courseId);
        } catch (Exception e) {
            LOG.warn("[evictCourseCache] Redis 驱逐失败 courseId={}", courseId, e);
        }
    }

    private String getFreeAccessScopeLabel(String scope) {
        if ("same_department".equals(scope)) return "同院系免费";
        if ("same_college".equals(scope)) return "同学院免费";
        if ("same_school".equals(scope)) return "同校免费";
        return "";
    }

    private java.util.List<Long> parseDeptIds(String freeDeptIds) {
        if (freeDeptIds == null || freeDeptIds.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(freeDeptIds, new TypeReference<>() {});
        } catch (Exception e) {
            LOG.warn("[parseDeptIds] JSON解析失败, fallback 到手动解析: {}", freeDeptIds, e);
            try {
                String[] parts = freeDeptIds.replace("[", "").replace("]", "")
                        .replace("\"", "").split(",");
                java.util.List<Long> ids = new java.util.ArrayList<>();
                for (String p : parts) {
                    try { ids.add(Long.parseLong(p.trim())); } catch (NumberFormatException ignored) {}
                }
                return ids;
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }
    }
}
