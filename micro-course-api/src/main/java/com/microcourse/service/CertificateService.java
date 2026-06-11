package com.microcourse.service;

import com.microcourse.dto.CertificateVO;

import java.util.List;

public interface CertificateService {

    /**
     * 获取我的证书列表
     */
    List<CertificateVO> getMyCertificates(Long userId);

    /**
     * 根据ID获取证书详情
     */
    CertificateVO getCertificateById(Long id, Long userId);

    /**
     * 生成证书HTML页面
     */
    String generateCertificateHtml(Long certificateId, Long userId);

    /**
     * 条件达成时自动发放证书
     */
    void autoIssueCertificate(Long userId, Long courseId);
}