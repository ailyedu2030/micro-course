package com.microcourse.service.impl;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.RegisterRequest;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.User;
import com.microcourse.enums.UserStatus;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.entity.OperationLog;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.AuthCasLoginService;
import com.microcourse.service.AuthQueryService;
import com.microcourse.service.AuthService;
import com.microcourse.service.CasTicketValidationService;
import com.microcourse.service.OperationLogService;
import com.microcourse.service.UserAvatarStorageService;
import com.microcourse.util.IpUtil;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import com.microcourse.util.LogSanitizer;
import com.microcourse.util.XssSanitizer;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    /** token 过期秒数（前端告知用，非 JWT 签发的 exp） */
    private static final int TOKEN_EXPIRES_IN_SECONDS = 7200;

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;
    private final HttpServletRequest httpServletRequest;
    private final AdminSettingService adminSettingService;
    private final AuthQueryService queryService;
    private final CasTicketValidationService casTicketValidationService;
    private final AuthCasLoginService authCasLoginService;
    private final UserAvatarStorageService userAvatarStorageService;

    public AuthServiceImpl(UserRepository userRepository, JwtUtil jwtUtil,
                           BCryptPasswordEncoder passwordEncoder, RedisUtil redisUtil,
                           OperationLogService operationLogService,
                           HttpServletRequest httpServletRequest,
                           AdminSettingService adminSettingService,
                           AuthQueryService queryService,
                           CasTicketValidationService casTicketValidationService,
                           AuthCasLoginService authCasLoginService,
                           UserAvatarStorageService userAvatarStorageService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
        this.httpServletRequest = httpServletRequest;
        this.adminSettingService = adminSettingService;
        this.queryService = queryService;
        this.casTicketValidationService = casTicketValidationService;
        this.authCasLoginService = authCasLoginService;
        this.userAvatarStorageService = userAvatarStorageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse register(RegisterRequest request) {
        // P1C-002: 校验注册开关 — 从 admin_settings 查询 registration_enabled
        String regEnabled = adminSettingService.getByKey("registration_enabled");
        if (regEnabled != null && !"true".equalsIgnoreCase(regEnabled)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "当前系统已关闭自助注册，请联系管理员");
        }

        // Step 1: 校验用户名唯一性
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // Step 2: 密码复杂度已在 RegisterRequest @Pattern 校验，此处为双重保险
        String password = request.getPassword();
        if (password == null || password.length() < 8 || password.length() > 32
                || !java.util.regex.Pattern.compile("(?=.*[A-Za-z])(?=.*\\d)").matcher(password).find()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "密码需 8-32 位且包含字母和数字");
        }

        // Step 3: 创建学生用户（status=1 ACTIVE，无需管理员审核）
        User user = new User();
        // P1-I-06: XSS 清洗用户名字段
        user.setUsername(XssSanitizer.sanitizePlainText(request.getUsername()));
        user.setPassword(passwordEncoder.encode(password));
        user.setRealName(request.getUsername()); // 默认使用用户名作为姓名
        user.setRole(UserRole.STUDENT);
        user.setStatus(1); // ACTIVE
        /* ---- 【I-17 修复】注册显式设置 casBound=false ---- */
        /* 【根因】register() 创建的 user 没有显式设置 casBound，entity 中为 null */
        /*        DB 默认 FALSE 但实体语义不清晰 */
        /* 【修复】在注册时显式设置 casBound=false */
        /* 【防止再发】所有布尔类型字段初始化时必须显式赋值 */
        user.setCasBound(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.insert(user);

        log.info("[Register] 学生自助注册成功 username={} userId={}", LogSanitizer.sanitizeForLog(request.getUsername()), user.getId());

        // Step 4: 自动登录 - 生成 JWT(首次注册无旧 token,tokenGen=0)
        String accessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), 0L);

        // Step 5: 记录操作日志
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("REGISTER");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);

        // Step 6: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(TOKEN_EXPIRES_IN_SECONDS);
        response.setTokenType("Bearer");
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        try {
            // Step 0: IP 级别防暴 — 同一 IP 连续失败 20 次封禁 15 分钟
            String clientIp = IpUtil.getClientIp();
            if (clientIp != null) {
                int ipFailureCount = queryService.getLoginFailureCount("ip:" + clientIp);
                if (ipFailureCount >= 20) {
                    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                }
            }

            // Step 1: 检查登录失败次数
            int failureCount = queryService.getLoginFailureCount(request.getUsername());
            if (failureCount >= 5) {
                throw new BusinessException(ErrorCode.LOGIN_LOCKED);
            }

            // Step 2: 查询用户
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> {
                        queryService.incrLoginFailureQuietly(request.getUsername());
                        if (clientIp != null) queryService.incrLoginFailureQuietly("ip:" + clientIp);
                        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                    });

            // Step 3: 验证密码
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                queryService.incrLoginFailureQuietly(request.getUsername());
                if (clientIp != null) queryService.incrLoginFailureQuietly("ip:" + clientIp);
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            // Step 4: 验证用户状态
            if (user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            if (user.getStatus() != null && user.getStatus().intValue() == UserStatus.DISABLED.getCode()) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
            if (user.getStatus() != null && user.getStatus().intValue() == UserStatus.DELETED.getCode()) {
                throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
            }

            // Step 5: 登录成功，清除失败计数
            queryService.clearLoginFailureQuietly(request.getUsername());
            if (clientIp != null) {
                queryService.clearLoginFailureQuietly("ip:" + clientIp);
            }

            // Step 6: 递增 token 代数(旧 refreshToken 失效) + 生成 JWT
            long newTokenGen = redisUtil.incrementTokenGeneration(user.getId());
            if (newTokenGen == 0) newTokenGen = 1; // 兜底:确保从 1 开始
            String accessToken = jwtUtil.generateToken(
                    user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId(), newTokenGen);

            // Step 7: 更新 lastLoginAt
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.updateById(user);

            // Step 8: 记录操作日志
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(user.getId());
            logEntry.setAction("LOGIN");
            logEntry.setTargetType("USER");
            logEntry.setTargetId(user.getId());
            logEntry.setIp(IpUtil.getClientIp());
            logEntry.setSuccess(true);
            logEntry.setDetail("{\"method\":\"AuthController.login\",\"path\":\"POST /api/auth/login\",\"status\":200}");
            logEntry.setDurationMs(0);
            operationLogService.log(logEntry);

            // Step 9: 构建响应
            LoginResponse response = new LoginResponse();
            response.setAccessToken(accessToken);
            response.setRefreshToken(refreshToken);
            response.setExpiresIn(TOKEN_EXPIRES_IN_SECONDS);
            response.setTokenType("Bearer");
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Login error for user: {}", LogSanitizer.sanitizeForLog(request.getUsername()), e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "登录服务异常，请稍后重试");
        }
    }

    @Override
    public UserVO getCurrentUser() {
        return queryService.getCurrentUser();
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        // ===== P0-S04 修复:先检查黑名单(不验证 JWT 签名),再验证签名 =====
        // Step -1: 提取 jti 不验证签名 — 若已黑名单则立即拒绝,避免在作废 token 上浪费签名验证
        String preJti = jwtUtil.getJtiUnverified(refreshToken);
        if (preJti != null && redisUtil.isTokenBlacklisted(preJti)) {
            log.warn("[Auth] 黑名单中的 refreshToken 被使用 jti={}", preJti);
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        // Step 0: IP 级别防刷新 — 同一 IP 每小时最多刷新 20 次(P0-L02 修复:独立 key,不与登录失败计数共享)
        String clientIp = IpUtil.getClientIp();
        if (clientIp != null) {
            int refreshCount = queryService.getRefreshCount(clientIp);
            if (refreshCount >= 20) {
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
        }
        // Step 0.5: 记录 refresh 尝试(独立计数器)
        if (clientIp != null) queryService.incrRefreshCountQuietly(clientIp);

        // Step 1: 验证 refreshToken 签名 + 过期
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        // Step 2: 从 refreshToken 提取 userId
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);

        // Step 2.5: 校验 token 代数 — 若用户已重新登录(login 递增了代数),旧 refreshToken 失效
        try {
            long tokenGen = jwtUtil.getTokenGeneration(refreshToken);
            long currentGen = redisUtil.getTokenGeneration(userId);
            if (currentGen > 0 && tokenGen < currentGen) {
                log.warn("[Auth] 旧代 refreshToken 被使用 userId={} tokenGen={} currentGen={}",
                        userId, tokenGen, currentGen);
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Auth] token generation 检查失败 userId={}", userId, e);
        }

        // Step 3: 查找用户
        User user = userRepository.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus().intValue() != UserStatus.ACTIVE.getCode()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // Step 4: 生成新的 accessToken + refreshToken(携带当前 token 代数)
        long currentTokenGen = Math.max(redisUtil.getTokenGeneration(userId), 0L);
        String newAccessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId(), currentTokenGen);
        // Step 4.5: 作废旧 refreshToken(旋转机制,防重放攻击)
        String oldJti = jwtUtil.getJtiFromToken(refreshToken);
        long remainingTtl = jwtUtil.getExpirationRemainingSeconds(refreshToken);
        if (oldJti != null && remainingTtl > 0) {
            redisUtil.blacklistToken(oldJti, remainingTtl);
            log.info("[Auth] refresh 轮换: 旧 jti={} 已加入黑名单 ttl={}", oldJti, remainingTtl);
        }
        // Step 5: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(TOKEN_EXPIRES_IN_SECONDS);
        response.setTokenType("Bearer");
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout() {
        // Step 1: 从 SecurityContextHolder 获取当前 userId
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal == null) {
            log.info("Logout: no principal found in security context");
            //仍尝试从 Authorization header 黑名单 token
            blacklistCurrentToken();
            SecurityContextHolder.clearContext();
            return;
        }
        Long userId = (Long) principal;
        // Step 2: 获取用户信息以清除登录失败计数
        User user = userRepository.selectById(userId);
        if (user != null) {
            // Step 3: 清除登录失败计数(ERR-003 修复:即使 Redis 故障也不应静默,需记录便于排查)
            try {
                redisUtil.clearLoginFailure(user.getUsername());
                log.info("Logout: cleared login failure count for user {}", user.getUsername());
            } catch (Exception e) {
                log.warn("[Auth] 登出时清除登录失败计数失败 userId={} username={}", userId, user.getUsername(), e);
            }

            // Step 3.5: 记录操作日志
            OperationLog logEntry = new OperationLog();
            logEntry.setUserId(user.getId());
            logEntry.setAction("LOGOUT");
            logEntry.setTargetType("USER");
            logEntry.setTargetId(user.getId());
            logEntry.setIp(IpUtil.getClientIp());
            logEntry.setSuccess(true);
            operationLogService.log(logEntry);
        }
        // Step 4: 黑名单当前 token
        blacklistCurrentToken();
        // Step 5: 清除 SecurityContext
        SecurityContextHolder.clearContext();
    }

    /**
     * 从当前请求的 Authorization header 提取 token 并加入黑名单
     */
    private void blacklistCurrentToken() {
        String authHeader = httpServletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String jti = jwtUtil.getJtiFromToken(token);
            long remainingTtl = jwtUtil.getExpirationRemainingSeconds(token);
            if (jti != null && remainingTtl > 0) {
                redisUtil.blacklistToken(jti, remainingTtl);
                log.info("Logout: blacklisted token jti={} ttl={}",
                        com.microcourse.util.LogSanitizer.sanitizeForLog(jti), remainingTtl);
            }
        }
    }

    @Override
    public LoginResponse casLogin(String ticket, String state) {
        String casUsername = casTicketValidationService.validateTicket(ticket);
        return authCasLoginService.loginOrRegister(casUsername);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileRequest request) {
        // P1-I: 至少需要一个字段
        if (request.getRealName() == null && request.getEmail() == null
                && request.getPhone() == null && request.getGender() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "至少需要更新一个字段（realName/email/phone/gender）");
        }
        Long userId = queryService.getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getRealName() != null && !request.getRealName().isEmpty()) {
            user.setRealName(XssSanitizer.sanitizePlainText(request.getRealName()));
        }
        // R8 修复：email/phone 空字符串视为"未设置"，避免批量导入的空 email
        // 触发 uk_users_email 部分唯一约束（WHERE email IS NOT NULL）
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            user.setEmail(XssSanitizer.sanitizePlainText(request.getEmail()));
        } else {
            // 显式置 null：让 email 不存库，从而不参与唯一约束
            user.setEmail(null);
        }
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            user.setPhone(XssSanitizer.sanitizePlainText(request.getPhone()));
        } else {
            user.setPhone(null);
        }
        if (request.getGender() != null && !request.getGender().isEmpty()) {
            user.setGender(request.getGender());
        }
        userRepository.updateById(user);
        return queryService.getCurrentUser();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        Long userId = queryService.getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        // P1I-004: 新旧密码相同校验
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "新密码不能与旧密码相同");
        }
        // 密码复杂度校验
        if (!java.util.regex.Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$")
                .matcher(request.getNewPassword()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "密码需 8-32 位且包含字母和数字");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.updateById(user);

        // P0 安全修复 v1.7.0: 修改密码后立即失效当前 JWT,
        // 防止攻击者持有的旧 token 在 2h 过期窗口内继续使用 → 账号接管风险
        blacklistCurrentJwt();

        // P1I-003: 修改密码后批量作废该用户所有活跃 Token（含 refreshToken），
        // 确保其他设备上的旧 token 同时失效，强制全设备重新登录
        blacklistAllUserTokens(userId);
    }

    /**
     * 将当前请求的 JWT 加入 Redis 黑名单,TTL = token 剩余有效期。
     * 下次该 token 命中 JwtAuthenticationFilter 时会被拒绝 (1004: token已失效)。
     */
    private void blacklistCurrentJwt() {
        if (httpServletRequest == null) {
            return;
        }
        String authHeader = httpServletRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return;
        }
        String token = authHeader.substring(7);
        String jti = jwtUtil.getJtiFromToken(token);
        long ttl = jwtUtil.getExpirationRemainingSeconds(token);
        if (jti != null && ttl > 0) {
            redisUtil.blacklistToken(jti, ttl);
        }
    }

    /**
     * P1I-003: 批量作废该用户所有活跃 Token（用户级黑名单）。
     * TTL = max(accessToken 有效期 7200s, refreshToken 有效期 604800s)，
     * 确保 refreshToken 在有效期内也会被拒绝。
     */
    private void blacklistAllUserTokens(Long userId) {
        // 使用 refreshToken 的最大 TTL（7天），确保所有 token 在整个生命周期内不可用
        redisUtil.blacklistUserTokens(userId, 604800L);
        log.info("[Auth] 已批量作废用户 userId={} 的所有 Token", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(MultipartFile file) {
        Long userId = queryService.getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        UserAvatarStorageService.StoredAvatar storedAvatar =
                userAvatarStorageService.storeAvatar(userId, file, user.getAvatar());
        try {
            user.setAvatar(storedAvatar.getAvatarUrl());
            userRepository.updateById(user);
            userAvatarStorageService.cleanupPreviousAvatar(storedAvatar.getPreviousAvatarFilename());
            return storedAvatar.getAvatarUrl();
        } catch (BusinessException e) {
            userAvatarStorageService.deleteByUrl(storedAvatar.getAvatarUrl());
            throw e;
        } catch (Exception e) {
            userAvatarStorageService.deleteByUrl(storedAvatar.getAvatarUrl());
            log.error("头像上传失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像上传失败");
        }
    }

    @Override
    public void resetLoginLockout() {
        queryService.resetLoginLockout();
    }
}
