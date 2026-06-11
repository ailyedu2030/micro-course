package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CourseCategoryCreateRequest;
import com.microcourse.dto.CourseCategoryUpdateRequest;
import com.microcourse.dto.CourseCategoryVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.CourseCategory;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.service.CourseCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseCategoryServiceImpl implements CourseCategoryService {

    private final CourseCategoryRepository courseCategoryRepository;

    public CourseCategoryServiceImpl(CourseCategoryRepository courseCategoryRepository) {
        this.courseCategoryRepository = courseCategoryRepository;
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
        List<CourseCategoryVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO).collect(Collectors.toList());
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
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return convertToVO(category);
    }

    @Override
    @Transactional
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
    @Transactional
    public CourseCategoryVO update(Long id, CourseCategoryUpdateRequest request) {
        CourseCategory category = courseCategoryRepository.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
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
    @Transactional
    public void delete(Long id) {
        CourseCategory category = courseCategoryRepository.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        courseCategoryRepository.deleteById(id);
    }

    private CourseCategoryVO convertToVO(CourseCategory category) {
        CourseCategoryVO vo = new CourseCategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setParentId(category.getParentId());
        vo.setLevel(category.getLevel());
        vo.setSortOrder(category.getSortOrder());
        vo.setCreatedAt(category.getCreatedAt());
        // children: 查询 level=2 且 parent_id = 当前分类
        List<CourseCategory> childrenList = courseCategoryRepository.selectList(
                new LambdaQueryWrapper<CourseCategory>()
                        .eq(CourseCategory::getParentId, category.getId())
                        .eq(CourseCategory::getLevel, 2)
                        .orderByAsc(CourseCategory::getSortOrder)
        );
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