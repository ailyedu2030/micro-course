package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Department;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final ClassesRepository classesRepository;

    public UserServiceImpl(UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           DepartmentRepository departmentRepository,
                           MajorRepository majorRepository,
                           ClassesRepository classesRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.classesRepository = classesRepository;
    }

    @Override
    public PageResult<UserVO> pageUsers(UserPageQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(query.getKeyword() != null, User::getUsername, query.getKeyword())
                .or()
                .like(query.getKeyword() != null, User::getRealName, query.getKeyword())
                .or()
                .like(query.getKeyword() != null, User::getEmail, query.getKeyword());
        wrapper.eq(query.getRole() != null, User::getRole, query.getRole());
        wrapper.eq(query.getStatus() != null, User::getStatus, query.getStatus());
        wrapper.eq(query.getDepartmentId() != null, User::getDepartmentId, query.getDepartmentId());
        wrapper.eq(query.getMajorId() != null, User::getMajorId, query.getMajorId());
        wrapper.eq(query.getClassId() != null, User::getClassId, query.getClassId());
        wrapper.isNull(User::getDeletedAt);
        wrapper.orderByDesc(User::getCreatedAt);

        Page<User> ipage = userRepository.selectPage(
                new Page<>(query.getPage() + 1, query.getSize()),
                wrapper
        );

        List<UserVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<UserVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage(query.getPage());
        result.setSize(query.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public UserVO getUserById(Long id) {
        User user = userRepository.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return convertToVO(user);
    }

    @Override
    public UserVO createUser(UserCreateRequest request) {
        // 检查用户名唯一
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setRole(request.getRole());
        user.setDepartmentId(request.getDepartmentId());
        user.setMajorId(request.getMajorId());
        user.setClassId(request.getClassId());
        user.setStatus(1); // 启用
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.insert(user);
        return convertToVO(user);
    }

    @Override
    public UserVO updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getDepartmentId() != null) {
            user.setDepartmentId(request.getDepartmentId());
        }
        if (request.getMajorId() != null) {
            user.setMajorId(request.getMajorId());
        }
        if (request.getClassId() != null) {
            user.setClassId(request.getClassId());
        }
        if (request.getGrade() != null) {
            user.setGrade(request.getGrade());
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        return convertToVO(user);
    }

    @Override
    public void updateStatus(Long id, UserStatusRequest request) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Integer newStatus = request.getStatus();
        switch (newStatus) {
            case 0: // INACTIVE（未激活）— 仅设状态，不操作 deleted_at
                user.setStatus(0);
                break;
            case 1: // ACTIVE（启用/恢复）
                user.setStatus(1);
                user.setDeletedAt(null);
                break;
            case 2: // DISABLED（禁用）
                user.setStatus(2);
                break;
            case 3: // DELETED（软删除，180天保留）
                user.setStatus(3);
                user.setDeletedAt(LocalDateTime.now());
                break;
            default:
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        // Phase 6: 启用/删除时需调用 redisUtil.blacklistToken(jti, ttl) 使 JWT 立即失效
    }

    private UserVO convertToVO(User user) {
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
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());

        // 关联名称
        if (user.getDepartmentId() != null) {
            Department dept = departmentRepository.selectById(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }
        if (user.getMajorId() != null) {
            Major major = majorRepository.selectById(user.getMajorId());
            if (major != null) {
                vo.setMajorName(major.getName());
            }
        }
        if (user.getClassId() != null) {
            Classes cls = classesRepository.selectById(user.getClassId());
            if (cls != null) {
                vo.setClassName(cls.getName());
            }
        }

        // statusText
        if (user.getStatus() != null) {
            switch (user.getStatus()) {
                case 0: vo.setStatusText("未激活"); break;
                case 1: vo.setStatusText("正常"); break;
                case 2: vo.setStatusText("禁用"); break;
                case 3: vo.setStatusText("已删除"); break;
                default: vo.setStatusText("未知");
            }
        }

        return vo;
    }
}