package com.microcourse.service;

import com.microcourse.dto.BatchImportResultVO;
import org.springframework.web.multipart.MultipartFile;

public interface UserBatchImportService {

    BatchImportResultVO batchImportUsers(MultipartFile file);
}
