package com.microcourse.service;

import com.microcourse.dto.storage.UploadResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 申报图片存储服务。
 */
public interface StorageApplicationImageStorageService {

    /**
     * 校验并保存签章图片。
     *
     * @param proposalId 申报 ID
     * @param file 图片文件
     * @param type 图片类型
     * @return 上传结果
     */
    UploadResultVO storeImage(Long proposalId, MultipartFile file, String type);
}
