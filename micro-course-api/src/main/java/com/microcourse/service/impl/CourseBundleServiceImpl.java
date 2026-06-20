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
import java.util.ArrayList;
import java.util.List;
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

        List<BundleItemVO> itemVOs = new ArrayList<>();
        for (CourseBundleItem item : items) {
            Course course = courseRepository.selectById(item.getCourseId());
            if (course == null) continue;
            BundleItemVO ivo = new BundleItemVO();
            ivo.setId(item.getId());
            ivo.setBundleId(item.getBundleId());
            ivo.setCourseId(item.getCourseId());
            ivo.setCourseTitle(course.getTitle());
            ivo.setCourseType(course.getCourseType());
            ivo.setSortOrder(item.getSortOrder());
            ivo.setIsRequired(item.getIsRequired());
            User teacher = userRepository.selectById(course.getTeacherId());
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

        List<BundleVO> vos = ipage.getRecords().stream().map(this::toVO).collect(Collectors.toList());
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
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
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
        bundleRepository.deleteById(id);
        LambdaQueryWrapper<CourseBundleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseBundleItem::getBundleId, id);
        itemRepository.delete(wrapper);
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
}
