package com.microcourse.service.impl;

import com.microcourse.dto.ChangePasswordRequest;
import com.microcourse.dto.LoginRequest;
import com.microcourse.dto.LoginResponse;
import com.microcourse.dto.UpdateProfileRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.entity.OperationLog;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.AuthService;
import com.microcourse.service.OperationLogService;
import com.microcourse.util.IpUtil;
import com.microcourse.util.JwtUtil;
import com.microcourse.util.RedisUtil;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RedisUtil redisUtil;
    private final OperationLogService operationLogService;
    private final HttpServletRequest httpServletRequest;
    private final AdminSettingService adminSettingService;
    private final RestTemplate restTemplate;

    /** 登录失败次数本地缓存兜底(Redis 不可用时使用),带自动过期 SEC-006 **/
    private final java.util.Map<String, LocalLoginFailureEntry> localLoginFailCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    public AuthServiceImpl(UserRepository userRepository, JwtUtil jwtUtil,
                           BCryptPasswordEncoder passwordEncoder, RedisUtil redisUtil,
                           OperationLogService operationLogService,
                           HttpServletRequest httpServletRequest,
                           AdminSettingService adminSettingService) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.redisUtil = redisUtil;
        this.operationLogService = operationLogService;
        this.httpServletRequest = httpServletRequest;
        this.adminSettingService = adminSettingService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request) {
        try {
            // Step 1: 检查登录失败次数
            int failureCount = getLoginFailureCount(request.getUsername());
            if (failureCount >= 5) {
                throw new BusinessException(ErrorCode.LOGIN_LOCKED);
            }

            // Step 2: 查询用户
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> {
                        incrLoginFailureQuietly(request.getUsername());
                        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
                    });

            // Step 3: 验证密码
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                incrLoginFailureQuietly(request.getUsername());
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }

            // Step 4: 验证用户状态
            if (user.getStatus() == 0) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
            }
            if (user.getStatus() == 2) {
                throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
            }
            if (user.getStatus() == 3) {
                throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
            }

            // Step 5: 登录成功，清除失败计数
            clearLoginFailureQuietly(request.getUsername());

            // Step 6: 生成 JWT
            String accessToken = jwtUtil.generateToken(
                    user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
            String refreshToken = jwtUtil.generateRefreshToken(user.getId());

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
            operationLogService.log(logEntry);

            // Step 9: 构建响应
            LoginResponse response = new LoginResponse();
            response.setAccessToken(accessToken);
            response.setRefreshToken(refreshToken);
            response.setExpiresIn(7200);
            response.setTokenType("Bearer");
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Login error for user: {}", request.getUsername(), e);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Override
    public UserVO getCurrentUser() {
        // Phase 3.1: 从 SecurityContextHolder 获取当前 userId
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Long userId = (Long) principal;
        // Phase 3.2: 查询用户
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return convertToUserVO(user);
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        // Step 1: 验证 refreshToken 有效性
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        // Step 2: 从 refreshToken 提取 userId + jti
        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        // Step 2.5: 检查旧 refreshToken 是否已被轮换(防重放)
        try {
            String jti = jwtUtil.getJtiFromToken(refreshToken);
            if (jti != null && redisUtil.isTokenBlacklisted(jti)) {
                log.warn("[Auth] 旧 refreshToken 被重复使用 userId={} jti={}, 疑似 token 被盗用", userId, jti);
                throw new BusinessException(ErrorCode.TOKEN_INVALID);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[Auth] refresh token 黑名单检查失败 userId={}", userId, e);
        }
        // Step 3: 查找用户
        User user = userRepository.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // Step 4: 生成新的 accessToken + refreshToken
        String newAccessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());
        // Step 4.5: 作废旧 refreshToken(旋转机制,防重放攻击)
        try {
            String oldJti = jwtUtil.getJtiFromToken(refreshToken);
            long remainingTtl = jwtUtil.getExpirationRemainingSeconds(refreshToken);
            if (oldJti != null && remainingTtl > 0) {
                redisUtil.blacklistToken(oldJti, remainingTtl);
                log.info("[Auth] refresh 轮换: 旧 jti={} 已加入黑名单 ttl={}", oldJti, remainingTtl);
            }
        } catch (Exception e) {
            log.warn("[Auth] 旧 refreshToken 黑名单失败", e);
        }
        // Step 5: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(7200);
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
            try {
                String jti = jwtUtil.getJtiFromToken(token);
                long remainingTtl = jwtUtil.getExpirationRemainingSeconds(token);
                if (jti != null && remainingTtl > 0) {
                    redisUtil.blacklistToken(jti, remainingTtl);
                    log.info("Logout: blacklisted token jti=" + jti + " ttl=" + remainingTtl);
                }
            } catch (Exception e) {
                log.warn("Logout: failed to blacklist token: " + e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse casLogin(String ticket, String state) {
        // Step 1: 从 admin_settings 读取 CAS 配置
        String casServerUrl = adminSettingService.getByKey("cas_server_url");
        String casServiceUrl = adminSettingService.getByKey("cas_service_url");

        if (casServerUrl == null || casServerUrl.isBlank()) {
            throw new BusinessException(ErrorCode.CAS_NOT_CONFIGURED, "CAS服务地址未配置，请在系统设置中配置 cas_server_url");
        }
        if (casServiceUrl == null || casServiceUrl.isBlank()) {
            throw new BusinessException(ErrorCode.CAS_NOT_CONFIGURED, "CAS回调地址未配置，请在系统设置中配置 cas_service_url");
        }

        // Step 2: 调用 CAS serviceValidate 端点验证票据
        String encodedServiceUrl = URLEncoder.encode(casServiceUrl, StandardCharsets.UTF_8);
        // 去除末尾斜杠
        String baseUrl = casServerUrl.endsWith("/") ? casServerUrl.substring(0, casServerUrl.length() - 1) : casServerUrl;
        String validateUrl = baseUrl + "/serviceValidate?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8)
                + "&service=" + encodedServiceUrl;

        log.info("[CAS] 验证票据 validateUrl={}", validateUrl);

        String xmlResponse;
        try {
            xmlResponse = restTemplate.getForObject(validateUrl, String.class);
        } catch (Exception e) {
            log.error("[CAS] 调用 CAS serviceValidate 失败 url={}", validateUrl, e);
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "无法连接CAS服务器，请稍后重试或联系管理员", e);
        }

        if (xmlResponse == null || xmlResponse.isBlank()) {
            log.error("[CAS] CAS 返回空响应");
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS服务器返回空响应");
        }

        log.debug("[CAS] CAS 响应 XML: {}", xmlResponse);

        // Step 3: 解析 CAS XML 响应提取用户名
        String casUsername = parseCasUsername(xmlResponse);
        log.info("[CAS] 票据验证成功，CAS用户名={}", casUsername);

        // Step 4: 查找或自动注册用户
        User user = userRepository.findByUsername(casUsername).orElse(null);
        if (user == null) {
            // 自动注册 CAS 用户（默认学生角色）
            user = new User();
            user.setUsername(casUsername);
            user.setRole(UserRole.STUDENT);
            user.setCasBound(true);
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.insert(user);
            log.info("[CAS] 自动注册CAS用户 username={} userId={}", casUsername, user.getId());
        } else {
            // 已有用户，标记 CAS 绑定
            if (!Boolean.TRUE.equals(user.getCasBound())) {
                user.setCasBound(true);
                user.setUpdatedAt(LocalDateTime.now());
                userRepository.updateById(user);
            }
        }

        // Step 4.5: 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "账号未激活");
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getStatus() != null && user.getStatus() == 3) {
            throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
        }

        // Step 5: 生成 JWT
        String accessToken = jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getRole(), user.getDepartmentId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // Step 6: 更新 lastLoginAt
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        // Step 7: 记录操作日志
        OperationLog logEntry = new OperationLog();
        logEntry.setUserId(user.getId());
        logEntry.setAction("CAS_LOGIN");
        logEntry.setTargetType("USER");
        logEntry.setTargetId(user.getId());
        logEntry.setIp(IpUtil.getClientIp());
        logEntry.setSuccess(true);
        operationLogService.log(logEntry);

        // Step 8: 构建响应
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(7200);
        response.setTokenType("Bearer");
        return response;
    }

    /**
     * 解析 CAS serviceValidate XML 响应，提取用户名。
     *
     * 成功响应示例：
     * <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
     *   <cas:authenticationSuccess>
     *     <cas:user>zhangsan</cas:user>
     *   </cas:authenticationSuccess>
     * </cas:serviceResponse>
     *
     * 失败响应示例：
     * <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
     *   <cas:authenticationFailure code='INVALID_TICKET'>
     *     Ticket ST-xxx not recognized
     *   </cas:authenticationFailure>
     * </cas:serviceResponse>
     */
    private String parseCasUsername(String xmlResponse) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 安全配置：禁用外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document doc = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xmlResponse)));

            // 先检查是否有认证失败
            NodeList failureNodes = doc.getElementsByTagNameNS("http://www.yale.edu/tp/cas", "authenticationFailure");
            if (failureNodes.getLength() > 0) {
                String failureMsg = failureNodes.item(0).getTextContent().trim();
                log.warn("[CAS] 票据验证失败: {}", failureMsg);
                throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS票据验证失败: " + failureMsg);
            }

            // 提取用户名
            NodeList userNodes = doc.getElementsByTagNameNS("http://www.yale.edu/tp/cas", "user");
            if (userNodes.getLength() > 0) {
                String username = userNodes.item(0).getTextContent().trim();
                if (!username.isEmpty()) {
                    return username;
                }
            }

            // 兜底：尝试不带命名空间解析（兼容某些非标准 CAS 实现）
            NodeList userNodesNoNs = doc.getElementsByTagName("cas:user");
            if (userNodesNoNs.getLength() > 0) {
                String username = userNodesNoNs.item(0).getTextContent().trim();
                if (!username.isEmpty()) {
                    return username;
                }
            }

            log.error("[CAS] 无法从响应中提取用户名, XML={}", xmlResponse);
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS响应中未找到用户信息");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CAS] 解析CAS XML响应失败", e);
            throw new BusinessException(ErrorCode.CAS_VALIDATION_FAILED, "CAS认证响应格式异常，请联系管理员", e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileRequest request) {
        Long userId = getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
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
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        userRepository.updateById(user);
        return convertToUserVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        Long userId = getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        // 密码复杂度校验
        if (!java.util.regex.Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$")
                .matcher(request.getNewPassword()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "密码需至少 8 位且包含字母和数字");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(MultipartFile file) {
        Long userId = getCurrentUserId();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 文件类型校验
        String contentType = file.getContentType();
        if (contentType == null || !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 JPEG、PNG、WebP 格式的图片");
        }
        // 文件大小校验（≤2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像文件大小不能超过 2MB");
        }
        try {
            String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
            java.io.File dir = new java.io.File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String ext = ".jpg";
            if ("image/png".equals(contentType)) {
                ext = ".png";
            } else if ("image/webp".equals(contentType)) {
                ext = ".webp";
            }
            String filename = userId + "_" + System.currentTimeMillis() + ext;
            java.io.File dest = new java.io.File(uploadDir + filename);
            file.transferTo(dest);

            String avatarUrl = "/api/files/avatars/" + filename;
            user.setAvatar(avatarUrl);
            userRepository.updateById(user);
            return avatarUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("头像上传失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像上传失败");
        }
    }

    /** 本地登录失败条目,含过期时间 **/
    private static class LocalLoginFailureEntry {
        int count;
        long expiresAtNanos;
        LocalLoginFailureEntry() { this.count = 0; this.expiresAtNanos = System.nanoTime() + 30L * 60 * 1_000_000_000L; }
        boolean isExpired() { return System.nanoTime() > expiresAtNanos; }
    }

    /** SEC-006: Redis 限流兜底 — 含本地缓存 + 日志可见 **/
    private int getLoginFailureCount(String username) {
        try {
            return redisUtil.getLoginFailureCount(username);
        } catch (Exception e) {
            log.warn("[Auth] Redis 不可用,回退本地限流缓存 username={}", username);
            LocalLoginFailureEntry entry = localLoginFailCache.get(username);
            if (entry == null || entry.isExpired()) return 0;
            return entry.count;
        }
    }

    private void incrLoginFailureQuietly(String username) {
        try {
            redisUtil.incrLoginFailure(username);
        } catch (Exception e) {
            log.warn("[Auth] Redis incrLoginFailure 失败 username={}", username);
            LocalLoginFailureEntry entry = localLoginFailCache.computeIfAbsent(username,
                    k -> new LocalLoginFailureEntry());
            if (entry.isExpired()) { localLoginFailCache.put(username, new LocalLoginFailureEntry()); entry = localLoginFailCache.get(username); }
            if (entry != null) entry.count++;
        }
    }

    private void clearLoginFailureQuietly(String username) {
        try {
            redisUtil.clearLoginFailure(username);
        } catch (Exception e) {
            log.warn("[Auth] Redis clearLoginFailure 失败 username={}", username);
            localLoginFailCache.remove(username);
        }
    }

    private Long getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Object principal = authentication.getPrincipal();
        if (principal == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return (Long) principal;
    }

    private UserVO convertToUserVO(User user) {
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
        vo.setStudentNo(user.getStudentNo());
        vo.setTeacherNo(user.getTeacherNo());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}