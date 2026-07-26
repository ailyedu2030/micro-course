package com.microcourse.service.impl;

import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.AuthQueryService;
import com.microcourse.service.UserAvatarStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

/**
 * 用户头像文件存储服务实现。
 */
@Service
public class UserAvatarStorageServiceImpl implements UserAvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(UserAvatarStorageServiceImpl.class);

    private final AuthQueryService authQueryService;

    public UserAvatarStorageServiceImpl(AuthQueryService authQueryService) {
        this.authQueryService = authQueryService;
    }

    @Override
    public StoredAvatar storeAvatar(Long userId, MultipartFile file, String currentAvatarUrl) {
        String contentType = validateContentType(file);
        validateSize(file);
        authQueryService.validateImageMagic(file);

        String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像目录创建失败");
        }

        String ext = ".jpg";
        if ("image/png".equals(contentType)) {
            ext = ".png";
        } else if ("image/webp".equals(contentType)) {
            ext = ".webp";
        }

        String filename = userId + "_" + System.currentTimeMillis() + ext;
        File dest = new File(uploadDir + filename);
        try {
            file.transferTo(dest);
        } catch (Exception e) {
            log.error("头像上传失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像上传失败");
        }

        String previousAvatarFilename = extractManagedAvatarFilename(currentAvatarUrl);
        return new StoredAvatar("/api/files/avatars/" + filename, previousAvatarFilename);
    }

    @Override
    public void deleteByUrl(String avatarUrl) {
        String filename = extractManagedAvatarFilename(avatarUrl);
        if (filename == null) {
            return;
        }
        cleanupPreviousAvatar(filename);
    }

    @Override
    public void cleanupPreviousAvatar(String previousAvatarFilename) {
        if (previousAvatarFilename == null) {
            return;
        }
        String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
        try {
            File oldFile = new File(uploadDir + previousAvatarFilename);
            if (oldFile.exists() && !oldFile.delete()) {
                log.warn("[Auth] 旧头像文件删除失败 oldFile={}", previousAvatarFilename);
            }
        } catch (Exception e) {
            log.warn("[Auth] 清理旧头像文件失败 oldFile={}", previousAvatarFilename, e);
        }
    }

    private String validateContentType(MultipartFile file) {
        String contentType = file.getContentType();
        boolean contentTypeOk = contentType != null
                && java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(contentType);
        if (contentTypeOk) {
            return contentType;
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null) {
            String lower = originalName.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (lower.endsWith(".png")) {
                return "image/png";
            }
            if (lower.endsWith(".webp")) {
                return "image/webp";
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "仅支持 JPEG、PNG、WebP 格式的图片");
    }

    private void validateSize(MultipartFile file) {
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像文件大小不能超过 2MB");
        }
    }

    private String extractManagedAvatarFilename(String avatarUrl) {
        if (avatarUrl != null && avatarUrl.startsWith("/api/files/avatars/")) {
            return avatarUrl.substring("/api/files/avatars/".length());
        }
        return null;
    }
}
