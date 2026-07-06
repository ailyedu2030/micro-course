package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.enums.EnrollmentStatus;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.UserQueryService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private static final Logger log = LoggerFactory.getLogger(UserQueryServiceImpl.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final ClassesRepository classesRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public UserQueryServiceImpl(UserRepository userRepository,
                                DepartmentRepository departmentRepository,
                                MajorRepository majorRepository,
                                ClassesRepository classesRepository,
                                EnrollmentRepository enrollmentRepository,
                                CourseRepository courseRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.classesRepository = classesRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserVO> pageUsers(UserPageQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // LIKE 通配符转义,防 DF-002 LIKE 注入
        String escapedKw = query.getKeyword() != null
                ? query.getKeyword().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                : null;
        wrapper.like(escapedKw != null, User::getUsername, escapedKw)
                .or()
                .like(escapedKw != null, User::getRealName, escapedKw)
                .or()
                .like(escapedKw != null, User::getEmail, escapedKw);
        wrapper.eq(query.getRole() != null, User::getRole, query.getRole());
        wrapper.eq(query.getStatus() != null, User::getStatus, query.getStatus());
        wrapper.eq(query.getTeacherStatus() != null, User::getTeacherStatus, query.getTeacherStatus());
        wrapper.eq(query.getDepartmentId() != null, User::getDepartmentId, query.getDepartmentId());
        wrapper.eq(query.getMajorId() != null, User::getMajorId, query.getMajorId());
        wrapper.eq(query.getClassId() != null, User::getClassId, query.getClassId());
        // TEACHER 角色过滤：仅返回任课学生（Service 层防御深度）
        // Controller 层通过 query.inUserIds 设置过滤；此段作为服务层兜底
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            List<Long> scopeUserIds = query.getInUserIds();
            if (scopeUserIds == null || scopeUserIds.isEmpty()) {
                // 调用方未设置范围 — 内部计算
                Long tid = SecurityUtil.getCurrentUserId();
                scopeUserIds = courseRepository.selectList(
                        new LambdaQueryWrapper<Course>()
                                .eq(Course::getTeacherId, tid)
                                .isNull(Course::getDeletedAt)
                                .select(Course::getId)
                ).stream().map(Course::getId).collect(Collectors.toList());
                if (!scopeUserIds.isEmpty()) {
                    scopeUserIds = enrollmentRepository.selectList(
                            new LambdaQueryWrapper<Enrollment>()
                                    .in(Enrollment::getCourseId, scopeUserIds)
                                    .isNull(Enrollment::getDeletedAt)
                                    .select(Enrollment::getUserId)
                    ).stream().map(Enrollment::getUserId).distinct().collect(Collectors.toList());
                }
            }
            if (scopeUserIds == null || scopeUserIds.isEmpty()) {
                scopeUserIds = Collections.singletonList(-1L);
            }
            wrapper.in(User::getId, scopeUserIds);
        } else if (query.getInUserIds() != null && !query.getInUserIds().isEmpty()) {
            wrapper.in(User::getId, query.getInUserIds());
        }
        wrapper.isNull(User::getDeletedAt);
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> ipage = userRepository.selectPage(
                new Page<>(query.getPage() + 1, query.getSize()),
                wrapper
        );

        // N+1 修复：批量预加载关联数据
        Map<Long, Department> deptMap = new HashMap<>();
        Map<Long, Major> majorMap = new HashMap<>();
        Map<Long, Classes> classMap = new HashMap<>();

        Set<Long> deptIds = ipage.getRecords().stream()
                .map(User::getDepartmentId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> majorIds = ipage.getRecords().stream()
                .map(User::getMajorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> classIds = ipage.getRecords().stream()
                .map(User::getClassId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!deptIds.isEmpty()) {
            departmentRepository.selectBatchIds(deptIds).forEach(d -> deptMap.put(d.getId(), d));
        }
        if (!majorIds.isEmpty()) {
            majorRepository.selectBatchIds(majorIds).forEach(m -> majorMap.put(m.getId(), m));
        }
        if (!classIds.isEmpty()) {
            classesRepository.selectBatchIds(classIds).forEach(c -> classMap.put(c.getId(), c));
        }

        Map<Long, Department> finalDeptMap = deptMap;
        Map<Long, Major> finalMajorMap = majorMap;
        Map<Long, Classes> finalClassMap = classMap;

        List<UserVO> vos = ipage.getRecords().stream()
                .map(user -> convertToVO(user, finalDeptMap, finalMajorMap, finalClassMap))
                .collect(Collectors.toList());

        // 列表端脱敏（/api/users 端点）
        // R12 数据隔离分级：校内透明、跨级隔离
        //   - ADMIN / ACADEMIC：完整（学院管理需要）
        //   - TEACHER：完整（任课管理需要）
        //   - 当前用户本人：完整
        //   - 其他（学生看学生等）：脱敏
        Long currentUserId = SecurityUtil.getCurrentUserId();
        boolean canSeeReal = SecurityUtil.isAdmin()
                || SecurityUtil.hasRole("ACADEMIC")
                || SecurityUtil.hasRole("TEACHER");
        vos.forEach(vo -> {
            if (canSeeReal || (currentUserId != null && currentUserId.equals(vo.getId()))) {
                // 完整字段，不脱敏
            } else {
                vo.setRealName(maskRealName(vo.getRealName()));
                vo.setEmail(maskEmail(vo.getEmail()));
                vo.setPhone(maskPhone(vo.getPhone()));
            }
        });


        PageResult<UserVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public UserVO getUserById(Long id) {
        User user = userRepository.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserVO vo = convertToVO(user);
        // P1-C: TEACHER 仅能查看自己课程中的学生，否则抛 NO_PERMISSION
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()
                && !SecurityUtil.isOwnerOrAdmin(id)) {
            User targetUser = user;
            if (targetUser != null && com.microcourse.enums.UserRole.STUDENT.equals(targetUser.getRole())) {
                long count = enrollmentRepository.countByTeacherAndStudent(
                        SecurityUtil.getCurrentUserId(), id,
                        EnrollmentStatus.LEGACY_ENROLLED_VALUE,
                        EnrollmentStatus.APPROVED.getValue(),
                        EnrollmentStatus.COMPLETED.getValue());
                if (count == 0) {
                    throw new BusinessException(ErrorCode.NO_PERMISSION, "您只能查看自己课程中的学生");
                }
            } else {
                throw new BusinessException(ErrorCode.NO_PERMISSION);
            }
        }
        // R12 数据隔离分级：
        //   - 校内管理岗（ADMIN/ACADEMIC/TEACHER）：完整可见（学号/工号/手机/邮箱）
        //   - 本人：完整可见
        //   - 其他（如学生查学生）：姓名/手机/邮箱脱敏
        boolean canSeeReal = SecurityUtil.isAdmin()
                || SecurityUtil.hasRole("ACADEMIC")
                || SecurityUtil.hasRole("TEACHER")
                || SecurityUtil.isOwnerOrAdmin(id);
        if (!canSeeReal) {
            vo.setRealName(maskRealName(vo.getRealName()));
            vo.setEmail(maskEmail(vo.getEmail()));
            vo.setPhone(maskPhone(vo.getPhone()));
            // 学号/工号属强标识字段，非校内管理岗查看他人时一律隐藏
            vo.setStudentNo(null);
            vo.setTeacherNo(null);
        }
        return vo;
    }

    private UserVO convertToVO(User user) {
        // N+1 修复：收集单用户的关联 ID 后批量查询，委托给 Map 版 convertToVO
        Map<Long, Department> deptMap = new HashMap<>();
        Map<Long, Major> majorMap = new HashMap<>();
        Map<Long, Classes> classMap = new HashMap<>();

        if (user.getDepartmentId() != null) {
            Department dept = departmentRepository.selectById(user.getDepartmentId());
            if (dept != null) {
                deptMap.put(dept.getId(), dept);
            }
        }
        if (user.getMajorId() != null) {
            Major major = majorRepository.selectById(user.getMajorId());
            if (major != null) {
                majorMap.put(major.getId(), major);
            }
        }
        if (user.getClassId() != null) {
            Classes cls = classesRepository.selectById(user.getClassId());
            if (cls != null) {
                classMap.put(cls.getId(), cls);
            }
        }

        return convertToVO(user, deptMap, majorMap, classMap);
    }

    private UserVO convertToVO(User user, Map<Long, Department> deptMap,
                                Map<Long, Major> majorMap,
                                Map<Long, Classes> classMap) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setMajorId(user.getMajorId());
        vo.setClassId(user.getClassId());
        vo.setGrade(user.getGrade());
        vo.setEnrollmentYear(user.getEnrollmentYear());
        vo.setGraduationYear(user.getGraduationYear());
        vo.setCasBound(user.getCasBound());
        vo.setStudentNo(user.getStudentNo());
        vo.setTeacherNo(user.getTeacherNo());
        vo.setPoliticalStatus(user.getPoliticalStatus());
        vo.setStatus(user.getStatus());
        vo.setTeacherStatus(user.getTeacherStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());

        // 关联名称（使用预加载的 Map）
        if (user.getDepartmentId() != null) {
            Department dept = deptMap.get(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }
        if (user.getMajorId() != null) {
            Major major = majorMap.get(user.getMajorId());
            if (major != null) {
                vo.setMajorName(major.getName());
            }
        }
        if (user.getClassId() != null) {
            Classes cls = classMap.get(user.getClassId());
            if (cls != null) {
                vo.setClassName(cls.getName());
            }
        }

        // statusText
        if (user.getStatus() != null) {
            UserStatus us = UserStatus.fromCode(user.getStatus());
            if (us != null) {
                switch (us) {
                    case INACTIVE: vo.setStatusText("未激活"); break;
                    case ACTIVE:   vo.setStatusText("正常"); break;
                    case DISABLED: vo.setStatusText("禁用"); break;
                    case DELETED:  vo.setStatusText("已删除"); break;
                    default:       vo.setStatusText("未知");
                }
            } else {
                vo.setStatusText("未知");
            }
        }

        return vo;
    }

    private static String maskRealName(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.charAt(0) + "**";
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf("@");
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
