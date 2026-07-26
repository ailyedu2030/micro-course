package com.microcourse.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 用户头像文件存储服务。
 */
public interface UserAvatarStorageService {

    StoredAvatar storeAvatar(Long userId, MultipartFile file, String currentAvatarUrl);

    void deleteByUrl(String avatarUrl);

    void cleanupPreviousAvatar(String previousAvatarFilename);

    final class StoredAvatar {
        private final String avatarUrl;
        private final String previousAvatarFilename;

        public StoredAvatar(String avatarUrl, String previousAvatarFilename) {
            this.avatarUrl = avatarUrl;
            this.previousAvatarFilename = previousAvatarFilename;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public String getPreviousAvatarFilename() {
            return previousAvatarFilename;
        }
    }
}
