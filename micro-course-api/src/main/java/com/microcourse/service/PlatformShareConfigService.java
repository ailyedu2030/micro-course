package com.microcourse.service;

import com.microcourse.dto.PlatformShareConfigDTO;

import java.util.List;
import java.util.Optional;

public interface PlatformShareConfigService {
    List<PlatformShareConfigDTO> listAll();
    Optional<PlatformShareConfigDTO> findByKey(String configKey);
    PlatformShareConfigDTO upsert(PlatformShareConfigDTO dto);
}
