package com.microcourse.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserBatchImportDTO;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Department;
import com.microcourse.entity.Major;
import com.microcourse.entity.OperationLog;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.listener.UserBatchImportListener;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.OperationLogService;
import com.microcourse.service.UserService;
import com.microcourse.util.RedisUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final ClassesRepository classesRepository;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;


    public UserServiceImpl(UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           DepartmentRepository departmentRepository,
                           MajorRepository majorRepository,
                           ClassesRepository classesRepository,
                           RedisUtil redisUtil,
                           OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.classesRepository = classesRepository;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
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

        // 列表端脱敏（/api/users 端点）
        vos.forEach(vo -> {
            vo.setRealName(maskRealName(vo.getRealName()));
            vo.setEmail(maskEmail(vo.getEmail()));
            vo.setPhone(maskPhone(vo.getPhone()));
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

        Integer oldStatus = user.getStatus();
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
                // 将当前用户的所有 Token 加入黑名单，使立即失效
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            case 3: // DELETED（软删除，180天保留）
                user.setStatus(3);
                user.setDeletedAt(LocalDateTime.now());
                // 将当前用户的所有 Token 加入黑名单，使立即失效
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            default:
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        user.setUpdatedAt(LocalDateTime.now());
        // Phase 6: 需通过 WebSocket/Redis PubSub 通知所有实例使该用户 Token 失效
        userRepository.updateById(user);

        // 记录操作日志
        OperationLog log = new OperationLog();
        log.setUserId(user.getId());
        log.setAction("STATUS_CHANGE");
        log.setTargetType("USER");
        log.setTargetId(user.getId());
        log.setDetail("{\"field\":\"status\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        log.setIp("0.0.0.0");
        log.setSuccess(true);
        operationLogService.log(log);
    }

    @Override
    public BatchImportResultVO batchImportUsers(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        UserBatchImportListener listener = new UserBatchImportListener(passwordEncoder);
        try {
            EasyExcel.read(file.getInputStream(), UserBatchImportDTO.class, listener).sheet().doRead();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
        }

        errors.addAll(listener.getErrors());
        List<UserBatchImportDTO> rows = listener.getRows();

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM);
        }

        // 检查 usernames 唯一性（在同一批次内）
        Set<String> seenUsernames = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            UserBatchImportDTO row = rows.get(i);
            if (seenUsernames.contains(row.getUsername())) {
                errors.add("第 " + (listener.getRows().indexOf(row) + 1) + " 行：用户名 '" + row.getUsername() + "' 在批次中重复");
            }
            seenUsernames.add(row.getUsername());
        }

        // 逐条插入，收集错误
        List<User> usersToInsert = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            UserBatchImportDTO row = rows.get(i);
            try {
                // 检查数据库中用户名是否已存在
                if (userRepository.findByUsername(row.getUsername()).isPresent()) {
                    errors.add("第 " + (i + 1) + " 行：用户名 '" + row.getUsername() + "' 已存在");
                    failCount++;
                    continue;
                }

                User user = new User();
                user.setUsername(row.getUsername());
                user.setPassword(passwordEncoder.encode(listener.getDefaultPassword()));
                user.setRealName(row.getRealName());
                user.setEmail(row.getEmail());
                user.setStatus(1);

                // 解析 role，默认 STUDENT
                if (row.getRole() != null && !row.getRole().trim().isEmpty()) {
                    try {
                        user.setRole(UserRole.valueOf(row.getRole().toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        errors.add("第 " + (i + 1) + " 行：角色 '" + row.getRole() + "' 不合法，应为 STUDENT/TEACHER/ADMIN/ACADEMIC");
                        failCount++;
                        continue;
                    }
                } else {
                    user.setRole(UserRole.STUDENT);
                }

                user.setDepartmentId(row.getDepartmentId());
                user.setMajorId(row.getMajorId());
                user.setClassId(row.getClassId());
                user.setCreatedAt(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());

                usersToInsert.add(user);
                successCount++;
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 行：处理失败，请检查数据格式");
                failCount++;
            }
        }

        // 批量插入
        if (!usersToInsert.isEmpty()) {
            for (User user : usersToInsert) {
                userRepository.insert(user);
            }
        }

        // failCount = 总行数 - 成功数
        failCount = (listener.getErrors().size() + rows.size()) - successCount;
        if (failCount < 0) {
            failCount = 0;
        }

        BatchImportResultVO result = new BatchImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setErrors(errors);
        return result;
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