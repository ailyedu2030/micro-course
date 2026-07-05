package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CourseCategoryCreateRequest;
import com.microcourse.dto.CourseCategoryUpdateRequest;
import com.microcourse.dto.CourseCategoryVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseCategory;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.CourseCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseRepository courseRepository;

    public CourseCategoryServiceImpl(CourseCategoryRepository courseCategoryRepository,
                                     CourseRepository courseRepository) {
        this.courseCategoryRepository = courseCategoryRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseCategoryVO> page(int page, int size) {
        IPage<CourseCategory> ipage = courseCategoryRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<CourseCategory>()
                        .eq(CourseCategory::getLevel, 1)
                        .orderByAsc(CourseCategory::getSortOrder)
        );
        List<CourseCategory> records = ipage.getRecords();
        // 批量预加载所有子分类,避免 convertToVO 逐条查询导致的 N+1
        List<Long> parentIds = records.stream().map(CourseCategory::getId).collect(Collectors.toList());
        Map<Long, List<CourseCategory>> childrenMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            List<CourseCategory> allChildren = courseCategoryRepository.selectList(
                    new LambdaQueryWrapper<CourseCategory>()
                            .in(CourseCategory::getParentId, parentIds)
                            .eq(CourseCategory::getLevel, 2)
                            .orderByAsc(CourseCategory::getSortOrder)
            );
            childrenMap = allChildren.stream().collect(Collectors.groupingBy(CourseCategory::getParentId));
        }
        final Map<Long, List<CourseCategory>> finalChildrenMap = childrenMap;
        List<CourseCategoryVO> vos = records.stream()
                .map(c -> convertToVO(c, finalChildrenMap)).collect(Collectors.toList());
        PageResult<CourseCategoryVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CourseCategoryVO getById(Long id) {
        CourseCategory category = courseCategoryRepository.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }
        return convertToVO(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseCategoryVO create(CourseCategoryCreateRequest request) {
        CourseCategory category = new CourseCategory();
        category.setName(request.getName());
        category.setParentId(request.getParentId());
        category.setLevel(request.getLevel());
        category.setSortOrder(request.getSortOrder());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        courseCategoryRepository.insert(category);
        return convertToVO(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseCategoryVO update(Long id, CourseCategoryUpdateRequest request) {
        CourseCategory category = courseCategoryRepository.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }
        if (request.getName() != null) {
            category.setName(request.getName());
        }
        if (request.getParentId() != null) {
            category.setParentId(request.getParentId());
        }
        if (request.getLevel() != null) {
            category.setLevel(request.getLevel());
        }
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        category.setUpdatedAt(LocalDateTime.now());
        courseCategoryRepository.updateById(category);
        return convertToVO(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CourseCategory category = courseCategoryRepository.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
        }
        // P1C-087: 检查是否有课程引用该分类,防止孤儿引用
        long courseCount = courseRepository.selectCount(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getCategoryId, id));
        if (courseCount > 0) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID,
                    "有 " + courseCount + " 门课程使用该分类，请先修改课程分类后再删除");
        }
        // 检查子分类下是否有课程引用
        List<CourseCategory> children = courseCategoryRepository.selectList(
                new LambdaQueryWrapper<CourseCategory>()
                        .eq(CourseCategory::getParentId, id));
        for (CourseCategory child : children) {
            long childCourseCount = courseRepository.selectCount(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getCategoryId, child.getId()));
            if (childCourseCount > 0) {
                throw new BusinessException(ErrorCode.MS_STATUS_INVALID,
                        "子分类「" + child.getName() + "」下有 " + childCourseCount + " 门课程使用该分类，请先修改课程分类后再删除");
            }
        }
        courseCategoryRepository.deleteById(id);
    }

    /**
     * 单条转换:实时查询子分类(用于 getById / create / update,单条无 N+1 风险)。
     */
    private CourseCategoryVO convertToVO(CourseCategory category) {
        // children: 查询 level=2 且 parent_id = 当前分类
        List<CourseCategory> childrenList = courseCategoryRepository.selectList(
                new LambdaQueryWrapper<CourseCategory>()
                        .eq(CourseCategory::getParentId, category.getId())
                        .eq(CourseCategory::getLevel, 2)
                        .orderByAsc(CourseCategory::getSortOrder)
        );
        return toVO(category, childrenList);
    }

    /**
     * 批量转换:从预加载的 childrenMap 取子分类(用于 page,避免 N+1)。
     */
    private CourseCategoryVO convertToVO(CourseCategory category, Map<Long, List<CourseCategory>> childrenMap) {
        List<CourseCategory> childrenList = childrenMap.getOrDefault(category.getId(), Collections.emptyList());
        return toVO(category, childrenList);
    }

    private CourseCategoryVO toVO(CourseCategory category, List<CourseCategory> childrenList) {
        CourseCategoryVO vo = new CourseCategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        vo.setLevel(category.getLevel());
        vo.setSortOrder(category.getSortOrder());
        vo.setCreatedAt(category.getCreatedAt());
        List<CourseCategoryVO> children = childrenList.stream()
                .map(child -> {
                    CourseCategoryVO childVO = new CourseCategoryVO();
                    childVO.setId(child.getId());
                    childVO.setName(child.getName());
                    childVO.setParentId(child.getParentId());
                    childVO.setLevel(child.getLevel());
                    childVO.setSortOrder(child.getSortOrder());
                    childVO.setCreatedAt(child.getCreatedAt());
                    childVO.setChildren(null);
                    return childVO;
                })
                .collect(Collectors.toList());
        vo.setChildren(children);
        return vo;
    }
}