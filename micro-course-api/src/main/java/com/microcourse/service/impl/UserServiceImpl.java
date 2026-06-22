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
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.listener.UserBatchImportListener;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.security.UserStatusCheckFilter;
import com.microcourse.service.OperationLogService;
import com.microcourse.service.UserService;
import com.microcourse.util.IpUtil;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.SecurityUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

    /** Self-reference for @Transactional proxy access (via @Lazy constructor injection) */
    private final UserServiceImpl self;

    public UserServiceImpl(UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           DepartmentRepository departmentRepository,
                           MajorRepository majorRepository,
                           ClassesRepository classesRepository,
                           RedisUtil redisUtil,
                           OperationLogService operationLogService,
                           @Lazy UserServiceImpl self) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.classesRepository = classesRepository;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
        this.self = self;
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
            // Round 11-1 数据隔离：学号/工号属强标识字段。ACADEMIC（教务）因学籍管理职责保留可见，
            // 其余角色（如 TEACHER）查看他人时一律隐藏，防止跨角色越权关联学生身份。
            if (!SecurityUtil.hasRole("ACADEMIC")) {
                vo.setStudentNo(null);
                vo.setTeacherNo(null);
            }
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
        // 解析目标状态码（非法码 → 参数错误，避免 500）
        UserStatus newStatus;
        try {
            newStatus = UserStatus.fromCode(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "无效的用户状态值: " + request.getStatus());
        }

        // 先按常规查询（未删除用户）；DELETED 用户被全局逻辑删除过滤，需绕过查询以支持恢复
        User user = userRepository.selectById(id);
        if (user == null) {
            user = userRepository.selectByIdIncludingDeleted(id);
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UserStatus currentStatus = UserStatus.fromCode(user.getStatus());
        Integer oldStatus = user.getStatus();
        Integer newStatusCode = request.getStatus();

        // 幂等：目标与当前一致时直接返回（UX 零退化：重复点击同一操作不报错）
        if (currentStatus == newStatus) {
            return;
        }

        // 前置校验：状态流转合法性（状态机白名单）
        if (currentStatus == null || !currentStatus.canTransitionTo(newStatus)) {
            log.warn("非法用户状态转换: userId={} {} -> {}", id, currentStatus, newStatus);
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "不允许从 " + currentStatus + " 转换到 " + newStatus);
        }

        // DELETED → INACTIVE 恢复：180 天保留窗口检查 + 绕过逻辑删除恢复
        if (currentStatus == UserStatus.DELETED && newStatus == UserStatus.INACTIVE) {
            if (user.getDeletedAt() != null) {
                long daysSinceDeleted = ChronoUnit.DAYS.between(user.getDeletedAt(), LocalDateTime.now());
                if (daysSinceDeleted > 180) {
                    throw new BusinessException(ErrorCode.DELETED_USER_RETENTION_EXPIRED);
                }
            }
            // updateById 会自动追加 deleted_at IS NULL 条件，无法命中已删除行，故走原生恢复 SQL
            userRepository.restoreToInactive(id);
            writeStatusAuditLog(user, oldStatus, newStatusCode);
            // Round 8-2：状态变更后立即清除状态缓存，确保 UserStatusCheckFilter 即时放行恢复后的用户
            evictUserStatusCache(id);
            log.info("用户状态变更(恢复): userId={} {} -> {}", id, currentStatus, newStatus);
            return;
        }

        // 常规状态变更（保留既有 deleted_at 联动与 token 黑名单清理逻辑）
        switch (newStatus) {
            case ACTIVE: // 启用 / 解禁
                user.setStatus(1);
                user.setDeletedAt(null);
                break;
            case DISABLED: // 禁用
                user.setStatus(2);
                // 清理登录失败计数，配合 Token 失效策略
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            case DELETED: // 软删除（180 天保留）
                user.setStatus(3);
                user.setDeletedAt(LocalDateTime.now());
                redisUtil.clearLoginFailure(user.getUsername());
                break;
            default: // INACTIVE 仅 DELETED→INACTIVE 合法，已在恢复分支处理；此处理论不可达
                user.setStatus(newStatusCode);
                break;
        }

        user.setUpdatedAt(LocalDateTime.now());
        // Phase 6: 需通过 WebSocket/Redis PubSub 通知所有实例使该用户 Token 失效
        userRepository.updateById(user); // @Version 自动 CAS

        writeStatusAuditLog(user, oldStatus, newStatusCode);
        // Round 8-2：状态变更后立即清除状态缓存（禁用→立即失效 / 解禁→立即恢复访问），UX 零延迟生效
        evictUserStatusCache(id);
        log.info("用户状态变更: userId={} {} -> {}", id, currentStatus, newStatus);
    }

    /**
     * 简易重载：直接用状态码调用（如 Controller 中 userService.updateStatus(id, 3)）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        UserStatusRequest request = new UserStatusRequest();
        request.setStatus(status);
        updateStatus(id, request);
    }

    /**
     * 清除用户状态缓存（Round 8-2）。
     *
     * <p>用户状态变更后立即失效 {@code mc:user:status:{id}} 缓存，确保
     * {@link UserStatusCheckFilter} 在下一次请求时读到最新状态（禁用/解禁即时生效）。
     * Redis 故障被吞掉，绝不回滚状态变更主事务（缓存最多 30 秒 TTL 后自然失效）。</p>
     */
    private void evictUserStatusCache(Long id) {
        try {
            redisUtil.delete(UserStatusCheckFilter.STATUS_CACHE_PREFIX + id);
        } catch (Exception e) {
            log.warn("清除用户状态缓存失败（不影响状态变更主流程）: userId={}", id, e);
        }
    }

    /** 写入用户状态变更审计日志（抽取以避免局部变量遮蔽类级 Logger）。 */
    private void writeStatusAuditLog(User user, Integer oldStatus, Integer newStatus) {
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("STATUS_CHANGE");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        logEntry.setDetail("{\"field\":\"status\",\"old\":" + oldStatus + ",\"new\":" + newStatus + "}");
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);
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

        // Step 1: 解析 Excel（非事务）
        UserBatchImportListener listener = new UserBatchImportListener();
        try {
            EasyExcel.read(file.getInputStream(), UserBatchImportDTO.class, listener).sheet().doRead();
        } catch (Exception e) {
            log.warn("[UserImport] Excel 解析失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "Excel 文件解析失败，请检查文件格式和内容是否正确", e);
        }

        errors.addAll(listener.getErrors());
        List<UserBatchImportDTO> rows = listener.getRows();

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件中无有效数据行");
        }

        // Step 2: P0-2 预加载院系/专业/班级 name→id 映射（非事务，只读查询）
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
            }
        }

        // 原子性检查：存在任何校验错误时整体不入库，确保"全成功或全失败"语义
        if (!errors.isEmpty()) {
            for (User u : usersToInsert) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        0, u.getUsername(), "批次存在错误行，整体回滚"));
            }
            BatchImportResultVO result = new BatchImportResultVO();
            result.setSuccessCount(0);
            result.setFailCount(errors.size());
            result.setErrors(errors);
            return result;
        }

        // Step 5: 分批入库（每 100 行一个独立事务，长事务修复 DB-P0-04）
        int total = usersToInsert.size();
        int successCount = 0;
        int failedCount = errors.size();

        final int batchSize = 100;
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<User> chunk = usersToInsert.subList(i, end);
            try {
                // 通过 self 代理调用以触发 @Transactional
                if (self != null) {
                    self.batchInsertUsersTransactional(chunk);
                } else {
                    batchInsertUsersTransactional(chunk);
                }
                successCount += chunk.size();
            } catch (Exception e) {
                // 该批次失败时所有用户计入失败，后续批次继续尝试
                failedCount += chunk.size();
                log.error("[UserImport] 批量入库失败 range=[{}-{}), 原因={}", i, end, e.getMessage(), e);
                for (User u : chunk) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(
                            0, u.getUsername(), "入库失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
                }
            }
        }

        BatchImportResultVO result = new BatchImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failedCount);
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

    /**
     * P1-3: 批量插入用户（单批次，由 AOP 代理调用以确保 @Transactional 生效）。
     * 每批最多 100 条，调用方负责通过 self 代理调用本方法以触发事务拦截器。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    protected void batchInsertUsersTransactional(List<User> users) {
        if (users.isEmpty()) {
            return;
        }
        for (User user : users) {
            userRepository.insert(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // SECURITY: 图片魔数校验（JPEG: FFD8FF, PNG: 89504E47）
        validateImageMagic(file);
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
            String avatarUrl = "/api/files/avatars/" + filename;
            // 清理旧头像文件
            if (user.getAvatar() != null && user.getAvatar().startsWith("/api/files/avatars/")) {
                String oldFilename = user.getAvatar().substring("/api/files/avatars/".length());
                java.io.File oldFile = new java.io.File(uploadDir + oldFilename);
                if (oldFile.exists()) oldFile.delete();
            }
            user.setAvatar(avatarUrl);
            userRepository.updateById(user);

            return avatarUrl;
        } catch (Exception e) {
            log.error("[User] 头像上传失败 userId={}", user != null ? user.getId() : "null", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像上传失败");
        }
    }

    private void validateImageMagic(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] magic = new byte[8];
            int read = is.read(magic);
            if (read < 4) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小，无法验证图片格式");
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF && (magic[1] & 0xFF) == 0xD8 && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89 && magic[1] == 'P' && magic[2] == 'N' && magic[3] == 'G';
            if (!isJpeg && !isPng) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像必须为 JPEG 或 PNG 格式（魔数校验失败）");
        } catch (java.io.IOException e) { throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取头像文件"); }
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