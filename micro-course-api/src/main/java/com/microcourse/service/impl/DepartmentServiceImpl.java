package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.DepartmentCreateRequest;
import com.microcourse.dto.DepartmentUpdateRequest;
import com.microcourse.dto.DepartmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Department;
import com.microcourse.entity.Major;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.service.DepartmentService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository,
                                  MajorRepository majorRepository) {
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
    }

    @Override
    public PageResult<DepartmentVO> page(int page, int size) {
        IPage<Department> ipage = departmentRepository.selectPage(
                new Page<>(page + 1, size),  // MyBatis-Plus uses 1-based page, controller sends 0-based
                new LambdaQueryWrapper<Department>()
                        .orderByAsc(Department::getSortOrder)
        );
        List<DepartmentVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO).collect(Collectors.toList());
        PageResult<DepartmentVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public DepartmentVO getById(Long id) {
        Department department = departmentRepository.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        return convertToVO(department);
    }

    @Override
    public DepartmentVO create(DepartmentCreateRequest request) {
        Department department = new Department();
        department.setName(request.getName());
        department.setCode(request.getCode());
        department.setParentId(request.getParentId());
        department.setSortOrder(request.getSortOrder());
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        departmentRepository.insert(department);
        return convertToVO(department);
    }

    @Override
    public DepartmentVO update(Long id, DepartmentUpdateRequest request) {
        Department department = departmentRepository.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        if (request.getName() != null) {
            department.setName(request.getName());
        }
        if (request.getCode() != null) {
            department.setCode(request.getCode());
        }
        if (request.getParentId() != null) {
            department.setParentId(request.getParentId());
        }
        if (request.getSortOrder() != null) {
            department.setSortOrder(request.getSortOrder());
        }
        department.setUpdatedAt(LocalDateTime.now());
        departmentRepository.updateById(department);
        return convertToVO(department);
    }

    @Override
    public void delete(Long id) {
        Department department = departmentRepository.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        long count = majorRepository.selectCount(
                new LambdaQueryWrapper<Major>()
                        .eq(Major::getDepartmentId, id)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_MAJORS);
        }
        departmentRepository.deleteById(id);
    }

    private DepartmentVO convertToVO(Department department) {
        DepartmentVO vo = new DepartmentVO();
        vo.setId(department.getId());
        vo.setName(department.getName());
        vo.setCode(department.getCode());
        vo.setParentId(department.getParentId());
        vo.setSortOrder(department.getSortOrder());
        vo.setCreatedAt(department.getCreatedAt());
        // parentName
        if (department.getParentId() != null) {
            Department parent = departmentRepository.selectById(department.getParentId());
            if (parent != null) {
                vo.setParentName(parent.getName());
            }
        }
        // children (Phase 3.4)
        vo.setChildren(null);
        return vo;
    }
}