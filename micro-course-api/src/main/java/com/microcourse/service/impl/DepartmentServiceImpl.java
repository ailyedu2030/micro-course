package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.DepartmentCreateRequest;
import com.microcourse.dto.DepartmentStatsVO;
import com.microcourse.dto.DepartmentUpdateRequest;
import com.microcourse.dto.DepartmentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.DepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    public DepartmentServiceImpl(DepartmentRepository departmentRepository,
                                  MajorRepository majorRepository,
                                  CourseRepository courseRepository,
                                  UserRepository userRepository,
                                  EnrollmentRepository enrollmentRepository) {
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
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
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO create(DepartmentCreateRequest request) {
        // 检查院系名称唯一性
        if (request.getName() != null && !request.getName().isBlank()) {
            long nameCount = departmentRepository.selectCount(
                    new LambdaQueryWrapper<Department>()
                            .eq(Department::getName, request.getName()));
            if (nameCount > 0) {
                throw new BusinessException(ErrorCode.DEPARTMENT_NAME_EXISTS);
            }
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            long codeCount = departmentRepository.selectCount(
                    new LambdaQueryWrapper<Department>()
                            .eq(Department::getCode, request.getCode()));
            if (codeCount > 0) {
                throw new BusinessException(ErrorCode.DEPARTMENT_CODE_EXISTS);
            }
        }
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
    @Transactional(rollbackFor = Exception.class)
    public DepartmentVO update(Long id, DepartmentUpdateRequest request) {
        Department department = departmentRepository.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        // P1-I #11 fix: 检查唯一性约束（排除自身）
        if (request.getName() != null && !request.getName().isBlank()
                && !request.getName().equals(department.getName())) {
            long nameCount = departmentRepository.selectCount(
                    new LambdaQueryWrapper<Department>()
                            .eq(Department::getName, request.getName())
                            .ne(Department::getId, id));
            if (nameCount > 0) {
                throw new BusinessException(ErrorCode.DEPARTMENT_NAME_EXISTS);
            }
            department.setName(request.getName());
        }
        if (request.getCode() != null && !request.getCode().isBlank()
                && !request.getCode().equals(department.getCode())) {
            long codeCount = departmentRepository.selectCount(
                    new LambdaQueryWrapper<Department>()
                            .eq(Department::getCode, request.getCode())
                            .ne(Department::getId, id));
            if (codeCount > 0) {
                throw new BusinessException(ErrorCode.DEPARTMENT_CODE_EXISTS);
            }
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
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Department department = departmentRepository.selectById(id);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        // 检查 FK 引用：majors / classes / users
        long majorCount = majorRepository.selectCount(
                new LambdaQueryWrapper<Major>().eq(Major::getDepartmentId, id));
        if (majorCount > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_MAJORS);
        }
        // 直接 FK 引用：users.department_id
        long userCount = userRepository.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getDepartmentId, id));
        if (userCount > 0) {
            throw new BusinessException(ErrorCode.DEPARTMENT_HAS_USERS,
                    "院系下存在用户，无法删除。请先转移或删除用户");
        }
        departmentRepository.deleteById(id);
    }

    /**
     * Round 5-3 (P1-10): 计算院系统计数据。
     *
     * <p>开课数 = {@code courses.offer_department_id = id}；学生数 = {@code users.department_id = id}；
     * 选课数 = 上述课程在 {@code enrollments} 的累计。全部基于既有表聚合，零新表/列。
     * 院系不存在抛 {@link ErrorCode#DEPARTMENT_NOT_FOUND}（404）。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public DepartmentStatsVO computeStats(Long departmentId) {
        Department department = departmentRepository.selectById(departmentId);
        if (department == null) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        DepartmentStatsVO vo = new DepartmentStatsVO();
        vo.setDepartmentId(departmentId);
        vo.setDepartmentName(department.getName());

        List<Course> courses = courseRepository.selectList(
                new LambdaQueryWrapper<Course>().eq(Course::getOfferDepartmentId, departmentId));
        vo.setCourseCount((long) courses.size());

        long studentCount = userRepository.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getDepartmentId, departmentId));
        vo.setStudentCount(studentCount);

        Set<Long> courseIds = courses.stream()
                .map(Course::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        long enrollmentCount = courseIds.isEmpty() ? 0L
                : enrollmentRepository.selectCount(
                        new LambdaQueryWrapper<Enrollment>().in(Enrollment::getCourseId, courseIds));
        vo.setEnrollmentCount(enrollmentCount);

        return vo;
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