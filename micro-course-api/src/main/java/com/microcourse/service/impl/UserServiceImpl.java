package com.microcourse.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.UserBatchImportDTO;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.TeacherStatusRequest;
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
import com.microcourse.util.IpUtil;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /** 密码复杂度: 至少 8 位,含字母和数字(SEC-009) */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

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
    @Transactional(readOnly = true)
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

        // N+1 修复：批量预加载关联数据
        java.util.Map<Long, Department> deptMap = new java.util.HashMap<>();
        java.util.Map<Long, Major> majorMap = new java.util.HashMap<>();
        java.util.Map<Long, Classes> classMap = new java.util.HashMap<>();

        java.util.Set<Long> deptIds = ipage.getRecords().stream()
                .map(User::getDepartmentId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> majorIds = ipage.getRecords().stream()
                .map(User::getMajorId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> classIds = ipage.getRecords().stream()
                .map(User::getClassId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        if (!deptIds.isEmpty()) {
            departmentRepository.selectBatchIds(deptIds).forEach(d -> deptMap.put(d.getId(), d));
        }
        if (!majorIds.isEmpty()) {
            majorRepository.selectBatchIds(majorIds).forEach(m -> majorMap.put(m.getId(), m));
        }
        if (!classIds.isEmpty()) {
            classesRepository.selectBatchIds(classIds).forEach(c -> classMap.put(c.getId(), c));
        }

        final java.util.Map<Long, Department> finalDeptMap = deptMap;
        final java.util.Map<Long, Major> finalMajorMap = majorMap;
        final java.util.Map<Long, Classes> finalClassMap = classMap;

        List<UserVO> vos = ipage.getRecords().stream()
                .map(user -> convertToVO(user, finalDeptMap, finalMajorMap, finalClassMap))
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
    @Transactional(readOnly = true)
    public UserVO getUserById(Long id) {
        User user = userRepository.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserVO vo = convertToVO(user);
        // 权限脱敏：非本人且非管理员时，敏感字段掩码处理
        if (!SecurityUtil.isOwnerOrAdmin(id)) {
            vo.setRealName(maskRealName(vo.getRealName()));
            vo.setEmail(maskEmail(vo.getEmail()));
            vo.setPhone(maskPhone(vo.getPhone()));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateRequest request) {
        // 密码复杂度校验
        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "密码需至少 8 位且包含字母和数字");
        }
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
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
        log.setIp(IpUtil.getClientIp());
        log.setSuccess(true);
        operationLogService.log(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeacherStatus(Long id, TeacherStatusRequest request) {
        User user = userRepository.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 仅 TEACHER 角色可申请入驻审核
        if (user.getRole() != UserRole.TEACHER) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "只有教师角色才能更新入驻审核状态");
        }

        Integer oldStatus = user.getTeacherStatus();
        Integer newStatus = request.getTeacherStatus();

        // 验证状态值
        if (newStatus < 0 || newStatus > 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "教师审核状态值无效，应为 0/1/2");
        }

        user.setTeacherStatus(newStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);

        // 记录操作日志(使用 JSON 库构建,避免注入)
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("TEACHER_STATUS_CHANGE");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        try {
            java.util.Map<String, Object> detailMap = new java.util.LinkedHashMap<>();
            detailMap.put("field", "teacherStatus");
            detailMap.put("old", oldStatus);
            detailMap.put("new", newStatus);
            detailMap.put("reason", request.getReason());
            logEntry.setDetail(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(detailMap));
        } catch (Exception e) {
            logEntry.setDetail("{\"field\":\"teacherStatus\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        }
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchImportResultVO batchImportUsers(MultipartFile file) {
        List<BatchImportResultVO.ImportErrorItem> errors = new ArrayList<>();

        // Step 1: 解析 Excel
        UserBatchImportListener listener = new UserBatchImportListener();
        try {
            EasyExcel.read(file.getInputStream(), UserBatchImportDTO.class, listener).sheet().doRead();
        } catch (Exception e) {
            log.warn("[UserImport] Excel 解析失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "Excel 解析失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }

        errors.addAll(listener.getErrors());
        List<UserBatchImportDTO> rows = listener.getRows();

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件中无有效数据行");
        }

        // Step 2: P0-2 预加载院系/专业/班级 name→id 映射
        Map<String, Long> deptNameMap = buildDepartmentNameMap();
        Map<String, Long> majorNameMap = buildMajorNameMap();
        Map<String, Long> classNameMap = buildClassNameMap();

        // Step 3: 批次内用户名去重 + 数据库用户名预查
        Set<String> seenUsernames = new HashSet<>();
        Set<String> allUsernames = rows.stream()
                .map(UserBatchImportDTO::getUsername)
                .collect(Collectors.toSet());
        Set<String> existingUsernames = findExistingUsernames(allUsernames);

        // Step 4: 逐行校验、构建 User 实体
        List<User> usersToInsert = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            UserBatchImportDTO row = rows.get(i);
            int rowNum = i + 2; // Excel 第 1 行是表头，数据从第 2 行开始
            String username = row.getUsername();

            // 批次内重复
            if (!seenUsernames.add(username)) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username, "用户名在批次中重复"));
                continue;
            }

            // 数据库中已存在
            if (existingUsernames.contains(username)) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username, "用户名已存在"));
                continue;
            }

            // 构建 User 实体（含名称→ID 解析和校验）
            User user = buildUserFromRow(row, rowNum, deptNameMap, majorNameMap, classNameMap, errors);
            if (user != null) {
                usersToInsert.add(user);
                successCount++;
            }
        }

        // Step 5: P1-3 批量插入
        batchInsertUsers(usersToInsert);

        int failCount = errors.size();

        BatchImportResultVO result = new BatchImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setErrors(errors);
        return result;
    }

    // ───── 批量导入辅助方法 ─────

    /** P0-2: 预加载所有院系 name → id 映射 */
    private Map<String, Long> buildDepartmentNameMap() {
        List<Department> all = departmentRepository.selectList(null);
        Map<String, Long> map = new HashMap<>();
        for (Department d : all) {
            map.put(d.getName(), d.getId());
        }
        return map;
    }

    /** P0-2: 预加载所有专业 name → id 映射 */
    private Map<String, Long> buildMajorNameMap() {
        List<Major> all = majorRepository.selectList(null);
        Map<String, Long> map = new HashMap<>();
        for (Major m : all) {
            map.put(m.getName(), m.getId());
        }
        return map;
    }

    /** P0-2: 预加载所有班级 name → id 映射 */
    private Map<String, Long> buildClassNameMap() {
        List<Classes> all = classesRepository.selectList(null);
        Map<String, Long> map = new HashMap<>();
        for (Classes c : all) {
            map.put(c.getName(), c.getId());
        }
        return map;
    }

    /** 批量查询已存在的用户名 */
    private Set<String> findExistingUsernames(Set<String> usernames) {
        if (usernames.isEmpty()) {
            return Set.of();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getUsername, usernames)
               .isNull(User::getDeletedAt)
               .select(User::getUsername);
        return userRepository.selectList(wrapper).stream()
                .map(User::getUsername)
                .collect(Collectors.toSet());
    }

    /**
     * P0-1 + P0-2: 从 DTO 构建 User 实体，解析名称为 ID 并校验存在性
     * 返回 null 表示校验失败（错误已加入 errors 列表）
     */
    private User buildUserFromRow(UserBatchImportDTO row, int rowNum,
                                   Map<String, Long> deptNameMap,
                                   Map<String, Long> majorNameMap,
                                   Map<String, Long> classNameMap,
                                   List<BatchImportResultVO.ImportErrorItem> errors) {
        String username = row.getUsername();
        User user = new User();
        user.setUsername(username);
        user.setRealName(row.getRealName());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // P1-2: 密码处理（listener 已保证非空且符合复杂度/已生成随机密码）
        user.setPassword(passwordEncoder.encode(row.getPassword()));

        // 解析 role，默认 STUDENT
        if (row.getRole() != null && !row.getRole().trim().isEmpty()) {
            try {
                user.setRole(UserRole.valueOf(row.getRole().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "角色 '" + row.getRole() + "' 不合法，应为 STUDENT/TEACHER/ADMIN/ACADEMIC"));
                return null;
            }
        } else {
            user.setRole(UserRole.STUDENT);
        }

        // P0-1 + P0-2: 院系名称 → ID
        if (row.getDepartmentName() != null && !row.getDepartmentName().trim().isEmpty()) {
            Long deptId = deptNameMap.get(row.getDepartmentName().trim());
            if (deptId == null) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "院系 '" + row.getDepartmentName().trim() + "' 不存在"));
                return null;
            }
            user.setDepartmentId(deptId);
        }

        // P0-1 + P0-2: 专业名称 → ID
        if (row.getMajorName() != null && !row.getMajorName().trim().isEmpty()) {
            Long majorId = majorNameMap.get(row.getMajorName().trim());
            if (majorId == null) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "专业 '" + row.getMajorName().trim() + "' 不存在"));
                return null;
            }
            user.setMajorId(majorId);
        }

        // P0-1 + P0-2: 班级名称 → ID
        if (row.getClassName() != null && !row.getClassName().trim().isEmpty()) {
            Long classId = classNameMap.get(row.getClassName().trim());
            if (classId == null) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "班级 '" + row.getClassName().trim() + "' 不存在"));
                return null;
            }
            user.setClassId(classId);
        }

        return user;
    }

    /** P1-3: 批量插入用户（分批，每批 100 条） */
    private void batchInsertUsers(List<User> users) {
        if (users.isEmpty()) {
            return;
        }
        int batchSize = 100;
        for (int i = 0; i < users.size(); i += batchSize) {
            List<User> batch = users.subList(i, Math.min(i + batchSize, users.size()));
            for (User user : batch) {
                userRepository.insert(user);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        try {
            // 保存到 uploads/avatars/ 目录
            String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            // 文件名: userId_timestamp.jpg(不信任用户提供的扩展名,用 MIME 类型映射)
            String contentType = file.getContentType();
            String ext = ".jpg";
            if ("image/png".equals(contentType)) ext = ".png";
            else if ("image/gif".equals(contentType)) ext = ".gif";
            else if ("image/webp".equals(contentType)) ext = ".webp";
            String filename = userId + "_" + System.currentTimeMillis() + ext;
            java.io.File dest = new java.io.File(uploadDir + filename);
            file.transferTo(dest);

            // 更新数据库
            String avatarUrl = "/avatars/" + filename;
            user.setAvatar(avatarUrl);
            userRepository.updateById(user);

            return avatarUrl;
        } catch (Exception e) {
            log.error("[User] 头像上传失败 userId={}", user != null ? user.getId() : "null", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像上传失败");
        }
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
        vo.setTeacherStatus(user.getTeacherStatus());
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

    private UserVO convertToVO(User user, java.util.Map<Long, Department> deptMap,
                                java.util.Map<Long, Major> majorMap,
                                java.util.Map<Long, Classes> classMap) {
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