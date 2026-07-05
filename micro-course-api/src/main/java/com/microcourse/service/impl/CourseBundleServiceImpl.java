package com.microcourse.service.impl;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleUpdateRequest;
import com.microcourse.dto.bundle.BundleItemVO;
import com.microcourse.dto.bundle.BundleVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.entity.Order;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseBundleService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseBundleServiceImpl implements CourseBundleService {

    private static final Logger log = LoggerFactory.getLogger(CourseBundleServiceImpl.class);

    private final CourseBundleRepository bundleRepository;
    private final CourseBundleItemRepository itemRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public CourseBundleServiceImpl(CourseBundleRepository bundleRepository,
                                   CourseBundleItemRepository itemRepository,
                                   CourseRepository courseRepository,
                                   UserRepository userRepository,
                                   OrderRepository orderRepository) {
        this.bundleRepository = bundleRepository;
        this.itemRepository = itemRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BundleVO create(BundleCreateRequest request) {
        CourseBundle bundle = new CourseBundle();
        bundle.setTitle(request.getTitle());
        bundle.setDescription(request.getDescription());
        bundle.setCoverUrl(request.getCoverUrl());
        bundle.setCreatorId(SecurityUtil.getCurrentUserId());
        bundle.setPrice(request.getPrice());
        bundle.setIsFree(request.getIsFree() != null ? request.getIsFree() : (request.getPrice() == null));
        bundle.setStudentCount(0);
        bundle.setStatus(0);
        bundle.setCreatedAt(LocalDateTime.now());
        bundle.setUpdatedAt(LocalDateTime.now());
        bundleRepository.insert(bundle);
        BundleVO vo = toVO(bundle);
        vo.setItems(Collections.emptyList());
        return vo;
    }

    /**
     * P1C-013: 校验套餐价格不应高于所含课程单课价格之和。
     * 若套餐当前没有课程项则跳过校验（创建时无课程项）。
     */
    private void validateBundlePrice(Long bundleId, BigDecimal bundlePrice) {
        if (bundlePrice == null || bundlePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return; // 免费或未定价套餐无需校验
        }
        LambdaQueryWrapper<CourseBundleItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(CourseBundleItem::getBundleId, bundleId);
        List<CourseBundleItem> items = itemRepository.selectList(itemWrapper);
        if (items.isEmpty()) {
            return; // 无课程项时跳过
        }
        Set<Long> courseIds = items.stream()
                .map(CourseBundleItem::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return;
        }
        List<Course> courses = courseRepository.selectBatchIds(courseIds);
        BigDecimal totalPrice = courses.stream()
                .map(c -> c.getPrice() != null ? c.getPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (bundlePrice.compareTo(totalPrice) > 0) {
            throw new BusinessException(ErrorCode.BUNDLE_PRICE_INVALID,
                    "套餐价格 ¥" + bundlePrice + " 不能高于所含课程单课价格之和 ¥" + totalPrice);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BundleVO update(Long id, BundleUpdateRequest request) {
        CourseBundle bundle = bundleRepository.selectById(id);
        if (bundle == null) throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // P1C-013: 校验套餐价格不应高于所含课程单课价格之和
        validateBundlePrice(id, request.getPrice());
        bundle.setTitle(request.getTitle());
        bundle.setDescription(request.getDescription());
        bundle.setCoverUrl(request.getCoverUrl());
        bundle.setPrice(request.getPrice());
        bundle.setIsFree(request.getIsFree() != null ? request.getIsFree() : (request.getPrice() == null));
        bundle.setUpdatedAt(LocalDateTime.now());
        bundleRepository.updateById(bundle);
        return getById(id);
    }

    @Override
    public BundleVO getById(Long id) {
        CourseBundle bundle = bundleRepository.selectById(id);
        if (bundle == null) throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);

        // 学生只能看已上架的套餐；教师/管理员/教务处可看任意状态
        boolean isStudentOnly = SecurityUtil.hasRole("STUDENT")
                && !SecurityUtil.isAdminOrAcademic();
        if (isStudentOnly && (bundle.getStatus() == null || bundle.getStatus() == 0)) {
            throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND, "套餐未上架");
        }

        BundleVO vo = toVO(bundle);

        LambdaQueryWrapper<CourseBundleItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(CourseBundleItem::getBundleId, id).orderByAsc(CourseBundleItem::getSortOrder);
        List<CourseBundleItem> items = itemRepository.selectList(itemWrapper);
        if (items.isEmpty()) {
            vo.setItems(Collections.emptyList());
            return vo;
        }

        Set<Long> courseIds = items.stream()
                .map(CourseBundleItem::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, User> teacherMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseRepository.selectBatchIds(courseIds);
            Set<Long> teacherIds = courses.stream()
                    .map(Course::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
            if (!teacherIds.isEmpty()) {
                List<User> teachers = userRepository.selectBatchIds(teacherIds);
                for (User t : teachers) {
                    teacherMap.put(t.getId(), t);
                }
            }
            for (Course c : courses) {
                courseMap.put(c.getId(), c);
            }
        }

        List<BundleItemVO> itemVOs = new ArrayList<>();
        for (CourseBundleItem item : items) {
            Course course = courseMap.get(item.getCourseId());
            BundleItemVO ivo = new BundleItemVO();
            ivo.setId(item.getId());
            ivo.setBundleId(item.getBundleId());
            ivo.setCourseId(item.getCourseId());
            if (course == null) {
                ivo.setCourseTitle("[课程已删除]");
                log.warn("Bundle {} item {} references deleted course {}", id, item.getId(), item.getCourseId());
            } else {
                ivo.setCourseTitle(course.getTitle());
                ivo.setCourseType(course.getCourseType());
                User teacher = teacherMap.get(course.getTeacherId());
                if (teacher != null) ivo.setTeacherName(teacher.getRealName());
            }
            ivo.setSortOrder(item.getSortOrder());
            ivo.setIsRequired(item.getIsRequired());
            itemVOs.add(ivo);
        }
        vo.setItems(itemVOs);
        return vo;
    }

    @Override
    public PageResult<BundleVO> page(int page, int size) {
        LambdaQueryWrapper<CourseBundle> wrapper = new LambdaQueryWrapper<>();

        if (SecurityUtil.hasRole("STUDENT")) {
            // 学生：仅看已上架
            wrapper.eq(CourseBundle::getStatus, 1);
        } else if (!SecurityUtil.isAdminOrAcademic()) {
            // 教师：仅看自己创建的
            wrapper.eq(CourseBundle::getCreatorId, SecurityUtil.getCurrentUserId());
        }
        // ADMIN/ACADEMIC：看全部
        wrapper.orderByDesc(CourseBundle::getCreatedAt);
        IPage<CourseBundle> ipage = bundleRepository.selectPage(new Page<>(page + 1, size), wrapper);

        Set<Long> creatorIds = ipage.getRecords().stream()
                .map(CourseBundle::getCreatorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> creatorNameMap = new HashMap<>();
        if (!creatorIds.isEmpty()) {
            userRepository.selectBatchIds(creatorIds).forEach(u -> creatorNameMap.put(u.getId(), u.getRealName()));
        }

        List<BundleVO> vos = ipage.getRecords().stream()
                .map(b -> toVO(b, creatorNameMap)).collect(Collectors.toList());
        PageResult<BundleVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCourse(Long bundleId, Long courseId, Integer sortOrder, Boolean isRequired) {
        CourseBundle bundle = bundleRepository.selectById(bundleId);
        if (bundle == null) throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // 验证课程存在且未软删除
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND, "课程不存在或已删除");
        }
        // 防止重复添加（与数据库 uk_cbi_bundle_course_active 部分索引对应）
        LambdaQueryWrapper<CourseBundleItem> dupWrapper = new LambdaQueryWrapper<>();
        dupWrapper.eq(CourseBundleItem::getBundleId, bundleId)
                .eq(CourseBundleItem::getCourseId, courseId);
        if (itemRepository.selectCount(dupWrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课程已在套餐中");
        }
        CourseBundleItem item = new CourseBundleItem();
        item.setBundleId(bundleId);
        item.setCourseId(courseId);
        item.setSortOrder(sortOrder != null ? sortOrder : 0);
        item.setIsRequired(isRequired != null ? isRequired : true);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        try {
            itemRepository.insert(item);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 兜底：并发两管理员同时添加 → check-then-act 竞态，让数据库部分唯一索引挡住
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "该课程已在套餐中");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCourse(Long bundleId, Long itemId) {
        CourseBundleItem item = itemRepository.selectById(itemId);
        if (item == null || !item.getBundleId().equals(bundleId)) {
            throw new BusinessException(ErrorCode.BUNDLE_ITEM_NOT_FOUND);
        }
        CourseBundle bundle = bundleRepository.selectById(bundleId);
        if (bundle != null && !SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        itemRepository.deleteById(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        CourseBundle bundle = bundleRepository.selectById(id);
        if (bundle == null) throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // P1C-057: 状态机校验 — 仅 DRAFT/UNPUBLISHED 可 PUBLISH
        validateBundleStatusTransition(bundle.getStatus(), 1);
        // P1C-013: 上架前校验套餐价格不应高于所含课程单课价格之和
        validateBundlePrice(id, bundle.getPrice());

        // 上架前必须至少有 1 门课程
        LambdaQueryWrapper<CourseBundleItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(CourseBundleItem::getBundleId, id);
        if (itemRepository.selectCount(itemWrapper) == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "套餐至少需要 1 门课程才能上架");
        }
        // 上架前必须至少有 1 门已存在的（未软删）课程，防止学生购买后看到"[课程已删除]"
        List<CourseBundleItem> items = itemRepository.selectList(itemWrapper);
        Set<Long> courseIds = items.stream()
                .map(CourseBundleItem::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long activeCourseCount = 0;
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseRepository.selectBatchIds(courseIds);
            activeCourseCount = courses.stream()
                    .filter(c -> c.getStatus() != null
                            && com.microcourse.enums.CourseStatus.fromCode(c.getStatus()).isSelectable())
                    .count();
        }
        if (activeCourseCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "套餐内所有课程都已下架，无法上架");
        }
        bundle.setStatus(1);
        bundle.setUpdatedAt(LocalDateTime.now());
        bundleRepository.updateById(bundle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        CourseBundle bundle = bundleRepository.selectById(id);
        if (bundle == null) throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        // P1C-057: 状态机校验 — 仅 PUBLISHED 可 UNPUBLISH
        validateBundleStatusTransition(bundle.getStatus(), 0);
        bundle.setStatus(0);
        bundle.setUpdatedAt(LocalDateTime.now());
        bundleRepository.updateById(bundle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CourseBundle bundle = bundleRepository.selectById(id);
        if (bundle == null) {
            throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);
        }
        // 检查是否仍有 PAID 订单 — 有则拒绝删除，避免已购课学生失去套餐可见性
        LambdaQueryWrapper<Order> paidOrderWrapper = new LambdaQueryWrapper<>();
        paidOrderWrapper.eq(Order::getBundleId, id).eq(Order::getStatus, "PAID");
        if (orderRepository.selectCount(paidOrderWrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "该套餐仍有已支付订单，请先下架并联系运营处理");
        }
        // 同时拒绝 PENDING 订单（防止用户付完款后套餐突然消失）
        paidOrderWrapper = new LambdaQueryWrapper<>();
        paidOrderWrapper.eq(Order::getBundleId, id).eq(Order::getStatus, "PENDING");
        if (orderRepository.selectCount(paidOrderWrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "该套餐有待支付订单，请等待支付完成或取消后再删除");
        }
        LambdaQueryWrapper<CourseBundleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseBundleItem::getBundleId, id);
        itemRepository.delete(wrapper);
        bundleRepository.deleteById(id);
    }

    @Override
    public boolean isUserEnrolledInBundle(Long userId, Long bundleId) {
        // 仅检查是否存在 PAID 订单——订单一旦 PAID 即视作已购买，
        // 课程后续添加/移除不影响购买事实。
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getBundleId, bundleId)
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, "PAID");
        Long paidCount = orderRepository.selectCount(orderWrapper);
        return paidCount != null && paidCount > 0;
    }

    /**
     * P1C-057: 套餐状态机转换校验。
     * 合法转换规则：DRAFT(0)/UNPUBLISHED(0) → PUBLISHED(1)，PUBLISHED(1) → UNPUBLISHED(0)。
     */
    private void validateBundleStatusTransition(Integer currentStatus, Integer targetStatus) {
        if (currentStatus == null) {
            currentStatus = 0;
        }
        if (currentStatus.equals(targetStatus)) {
            String desc = statusDescription(currentStatus);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "套餐已" + desc + "，无需操作");
        }
        if (currentStatus == 0 && targetStatus == 1) {
            return;
        }
        if (currentStatus == 1 && targetStatus == 0) {
            return;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                "不允许的状态转换：当前状态 " + statusDescription(currentStatus)
                + " 不能转为 " + statusDescription(targetStatus));
    }

    private String statusDescription(Integer status) {
        if (status == null || status == 0) return "未上架";
        if (status == 1) return "已上架";
        return "未知(" + status + ")";
    }

    private BundleVO toVO(CourseBundle bundle) {
        BundleVO vo = new BundleVO();
        vo.setId(bundle.getId());
        vo.setTitle(bundle.getTitle());
        vo.setDescription(bundle.getDescription());
        vo.setCoverUrl(bundle.getCoverUrl());
        vo.setCreatorId(bundle.getCreatorId());
        vo.setPrice(bundle.getPrice());
        vo.setIsFree(bundle.getIsFree());
        vo.setStudentCount(bundle.getStudentCount());
        vo.setStatus(bundle.getStatus());
        vo.setCreatedAt(bundle.getCreatedAt());
        vo.setUpdatedAt(bundle.getUpdatedAt());

        if (bundle.getCreatorId() != null) {
            User creator = userRepository.selectById(bundle.getCreatorId());
            if (creator != null) vo.setCreatorName(creator.getRealName());
        }
        return vo;
    }

    private BundleVO toVO(CourseBundle bundle, Map<Long, String> creatorNameMap) {
        BundleVO vo = new BundleVO();
        vo.setId(bundle.getId());
        vo.setTitle(bundle.getTitle());
        vo.setDescription(bundle.getDescription());
        vo.setCoverUrl(bundle.getCoverUrl());
        vo.setCreatorId(bundle.getCreatorId());
        vo.setPrice(bundle.getPrice());
        vo.setIsFree(bundle.getIsFree());
        vo.setStudentCount(bundle.getStudentCount());
        vo.setStatus(bundle.getStatus());
        vo.setCreatedAt(bundle.getCreatedAt());
        vo.setUpdatedAt(bundle.getUpdatedAt());
        vo.setCreatorName(creatorNameMap.get(bundle.getCreatorId()));
        return vo;
    }
}
