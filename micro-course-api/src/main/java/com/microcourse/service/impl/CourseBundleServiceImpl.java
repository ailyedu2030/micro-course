package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.bundle.BundleCreateRequest;
import com.microcourse.dto.bundle.BundleItemVO;
import com.microcourse.dto.bundle.BundleVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseBundle;
import com.microcourse.entity.CourseBundleItem;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseBundleItemRepository;
import com.microcourse.repository.CourseBundleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseBundleService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseBundleServiceImpl implements CourseBundleService {

    private final CourseBundleRepository bundleRepository;
    private final CourseBundleItemRepository itemRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseBundleServiceImpl(CourseBundleRepository bundleRepository,
                                   CourseBundleItemRepository itemRepository,
                                   CourseRepository courseRepository,
                                   UserRepository userRepository) {
        this.bundleRepository = bundleRepository;
        this.itemRepository = itemRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
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
        return toVO(bundle);
    }

    @Override
    public BundleVO getById(Long id) {
        CourseBundle bundle = bundleRepository.selectById(id);
        if (bundle == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);

        BundleVO vo = toVO(bundle);

        LambdaQueryWrapper<CourseBundleItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(CourseBundleItem::getBundleId, id).orderByAsc(CourseBundleItem::getSortOrder);
        List<CourseBundleItem> items = itemRepository.selectList(itemWrapper);
        if (items.isEmpty()) {
            vo.setItems(Collections.emptyList());
            return vo;
        }

        // N+1 修复：批量预加载 course 和 teacher
        Set<Long> courseIds = items.stream()
                .map(CourseBundleItem::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Course> courseMap = new HashMap<>();
        Map<Long, User> teacherMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseRepository.selectBatchIds(courseIds);
            for (Course c : courses) {
                courseMap.put(c.getId(), c);
                if (c.getTeacherId() != null && !teacherMap.containsKey(c.getTeacherId())) {
                    User teacher = userRepository.selectById(c.getTeacherId());
                    if (teacher != null) teacherMap.put(teacher.getId(), teacher);
                }
            }
        }

        List<BundleItemVO> itemVOs = new ArrayList<>();
        for (CourseBundleItem item : items) {
            Course course = courseMap.get(item.getCourseId());
            if (course == null) continue;
            BundleItemVO ivo = new BundleItemVO();
            ivo.setId(item.getId());
            ivo.setBundleId(item.getBundleId());
            ivo.setCourseId(item.getCourseId());
            ivo.setCourseTitle(course.getTitle());
            ivo.setCourseType(course.getCourseType());
            ivo.setSortOrder(item.getSortOrder());
            ivo.setIsRequired(item.getIsRequired());
            User teacher = teacherMap.get(course.getTeacherId());
            if (teacher != null) ivo.setTeacherName(teacher.getRealName());
            itemVOs.add(ivo);
        }
        vo.setItems(itemVOs);
        return vo;
    }

    @Override
    public PageResult<BundleVO> page(int page, int size) {
        LambdaQueryWrapper<CourseBundle> wrapper = new LambdaQueryWrapper<>();
        if (!SecurityUtil.isAdmin()) {
            wrapper.eq(CourseBundle::getCreatorId, SecurityUtil.getCurrentUserId());
        }
        wrapper.orderByDesc(CourseBundle::getCreatedAt);
        IPage<CourseBundle> ipage = bundleRepository.selectPage(new Page<>(page + 1, size), wrapper);

        // N+1 修复：批量预加载 creator 名称
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
        if (bundle == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        CourseBundleItem item = new CourseBundleItem();
        item.setBundleId(bundleId);
        item.setCourseId(courseId);
        item.setSortOrder(sortOrder != null ? sortOrder : 0);
        item.setIsRequired(isRequired != null ? isRequired : true);
        item.setCreatedAt(LocalDateTime.now());
        itemRepository.insert(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCourse(Long bundleId, Long itemId) {
        CourseBundleItem item = itemRepository.selectById(itemId);
        if (item == null || !item.getBundleId().equals(bundleId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "套餐课程项不存在或不属于该套餐");
        }
        CourseBundle bundle = bundleRepository.selectById(bundleId);
        if (bundle != null && !SecurityUtil.isOwnerOrAdmin(bundle.getCreatorId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        itemRepository.deleteById(itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (bundleRepository.selectById(id) == null) {
            throw new BusinessException(ErrorCode.BUNDLE_NOT_FOUND);
        }
        // 先删子表再删主表（避免 FK 约束冲突）
        LambdaQueryWrapper<CourseBundleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseBundleItem::getBundleId, id);
        itemRepository.delete(wrapper);
        bundleRepository.deleteById(id);
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
        vo.setCreatorName(creatorNameMap.get(bundle.getCreatorId()));
        return vo;
    }
}
