package com.microcourse.service.impl;

import com.microcourse.dto.UserVO;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AuthQueryService;
import com.microcourse.util.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证查询服务实现 — 包含 getCurrentUser、登录失败计数、UserVO 转换、图片魔数校验。
 * 提取自 AuthServiceImpl，降低单文件复杂度。
 */
@Service
public class AuthQueryServiceImpl implements AuthQueryService {

    private static final Logger log = LoggerFactory.getLogger(AuthQueryServiceImpl.class);

    private final UserRepository userRepository;
    private final RedisUtil redisUtil;

    /** 登录失败次数本地缓存兜底(Redis 不可用时使用),带自动过期 SEC-006 **/
    private final Map<String, LocalLoginFailureEntry> localLoginFailCache = new ConcurrentHashMap<>();

    public AuthQueryServiceImpl(UserRepository userRepository, RedisUtil redisUtil) {
        this.userRepository = userRepository;
        this.redisUtil = redisUtil;
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
    public Long getCurrentUserId() {
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

    @Override
    public int getLoginFailureCount(String key) {
        try {
            return redisUtil.getLoginFailureCount(key);
        } catch (Exception e) {
            log.warn("[Auth] Redis 不可用,回退本地限流缓存 key={}", key);
            LocalLoginFailureEntry entry = localLoginFailCache.get(key);
            if (entry == null || entry.isExpired()) return 0;
            return entry.count;
        }
    }

    @Override
    public void incrLoginFailureQuietly(String key) {
        try {
            redisUtil.incrLoginFailure(key);
        } catch (Exception e) {
            log.warn("[Auth] Redis incrLoginFailure 失败 key={}", key);
            LocalLoginFailureEntry entry = localLoginFailCache.computeIfAbsent(key,
                    k -> new LocalLoginFailureEntry());
            if (entry.isExpired()) {
                localLoginFailCache.put(key, new LocalLoginFailureEntry());
                entry = localLoginFailCache.get(key);
            }
            if (entry != null) entry.count++;
        }
    }

    @Override
    public void clearLoginFailureQuietly(String key) {
        try {
            redisUtil.clearLoginFailure(key);
        } catch (Exception e) {
            log.warn("[Auth] Redis clearLoginFailure 失败 key={}", key);
            localLoginFailCache.remove(key);
        }
    }

    @Override
    public void resetLoginLockout() {
        localLoginFailCache.clear();
    }

    @Override
    public UserVO convertToUserVO(User user) {
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
        vo.setGraduationYear(user.getGraduationYear());
        vo.setPoliticalStatus(user.getPoliticalStatus());
        vo.setCasBound(user.getCasBound());
        vo.setStatus(user.getStatus());
        vo.setTeacherStatus(user.getTeacherStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    @Override
    public void validateImageMagic(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] magic = new byte[12];
            int read = is.read(magic);
            if (read < 4) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小，无法验证图片格式");
            }
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF
                    && (magic[1] & 0xFF) == 0xD8
                    && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89
                    && magic[1] == 'P'
                    && magic[2] == 'N'
                    && magic[3] == 'G';
            boolean isWebp = (magic[0] & 0xFF) == 'R'
                    && (magic[1] & 0xFF) == 'I'
                    && (magic[2] & 0xFF) == 'F'
                    && (magic[3] & 0xFF) == 'F'
                    && magic.length >= 12
                    && magic[8] == 'W'
                    && magic[9] == 'E'
                    && magic[10] == 'B'
                    && magic[11] == 'P';
            if (!isJpeg && !isPng && !isWebp) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像必须为 JPEG/PNG/WebP 格式（魔数校验失败）");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取头像文件");
        }
    }

    /** 本地登录失败条目,含过期时间 **/
    private static class LocalLoginFailureEntry {
        int count;
        long expiresAtNanos;
        LocalLoginFailureEntry() {
            this.count = 0;
            this.expiresAtNanos = System.nanoTime() + 30L * 60 * 1_000_000_000L;
        }
        boolean isExpired() {
            return System.nanoTime() > expiresAtNanos;
        }
    }
}
