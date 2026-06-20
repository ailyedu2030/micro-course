package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.MajorCreateRequest;
import com.microcourse.dto.MajorUpdateRequest;
import com.microcourse.dto.MajorVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Department;
import com.microcourse.entity.Major;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.service.MajorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MajorServiceImpl implements MajorService {

    private final MajorRepository majorRepository;
    private final DepartmentRepository departmentRepository;
    private final ClassesRepository classesRepository;

    public MajorServiceImpl(MajorRepository majorRepository,
                            DepartmentRepository departmentRepository,
                            ClassesRepository classesRepository) {
        this.majorRepository = majorRepository;
        this.departmentRepository = departmentRepository;
        this.classesRepository = classesRepository;
    }

    @Override
    public PageResult<MajorVO> page(int page, int size) {
        IPage<Major> ipage = majorRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<Major>()
                        .orderByAsc(Major::getSortOrder)
        );
        List<MajorVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO).collect(Collectors.toList());
        PageResult<MajorVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public MajorVO getById(Long id) {
        Major major = majorRepository.selectById(id);
        if (major == null) {
            throw new BusinessException(ErrorCode.MAJOR_NOT_FOUND);
        }
        return convertToVO(major);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MajorVO create(MajorCreateRequest request) {
        if (departmentRepository.selectById(request.getDepartmentId()) == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        Major major = new Major();
        major.setName(request.getName());
        major.setCode(request.getCode());
        major.setDepartmentId(request.getDepartmentId());
        major.setSortOrder(request.getSortOrder());
        major.setCreatedAt(LocalDateTime.now());
        major.setUpdatedAt(LocalDateTime.now());
        majorRepository.insert(major);
        return convertToVO(major);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MajorVO update(Long id, MajorUpdateRequest request) {
        Major major = majorRepository.selectById(id);
        if (major == null) {
            throw new BusinessException(ErrorCode.MAJOR_NOT_FOUND);
        }
        if (request.getName() != null) {
            major.setName(request.getName());
        }
        if (request.getCode() != null) {
            major.setCode(request.getCode());
        }
        if (request.getDepartmentId() != null) {
            if (!request.getDepartmentId().equals(major.getDepartmentId())) {
                if (departmentRepository.selectById(request.getDepartmentId()) == null) {
                    throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
                }
            }
            major.setDepartmentId(request.getDepartmentId());
        }
        if (request.getSortOrder() != null) {
            major.setSortOrder(request.getSortOrder());
        }
        major.setUpdatedAt(LocalDateTime.now());
        majorRepository.updateById(major);
        return convertToVO(major);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Major major = majorRepository.selectById(id);
        if (major == null) {
            throw new BusinessException(ErrorCode.MAJOR_NOT_FOUND);
        }
        long count = classesRepository.selectCount(
                new LambdaQueryWrapper<Classes>()
                        .eq(Classes::getMajorId, id)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.MAJOR_HAS_CLASSES);
        }
        majorRepository.deleteById(id);
    }

    private MajorVO convertToVO(Major major) {
        MajorVO vo = new MajorVO();
        vo.setId(major.getId());
        vo.setName(major.getName());
        vo.setCode(major.getCode());
        vo.setDepartmentId(major.getDepartmentId());
        vo.setSortOrder(major.getSortOrder());
        vo.setCreatedAt(major.getCreatedAt());
        if (major.getDepartmentId() != null) {
            Department department = departmentRepository.selectById(major.getDepartmentId());
            if (department != null) {
                vo.setDepartmentName(department.getName());
            }
        }
        return vo;
    }
}