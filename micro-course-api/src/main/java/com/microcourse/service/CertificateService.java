package com.microcourse.service;

import com.microcourse.dto.CertificateVO;

import java.util.List;

public interface CertificateService {

    List<CertificateVO> getMyCertificates(Long userId);

    List<CertificateVO> getMyCertificates(Long userId, String certType);

    List<CertificateVO> getMyMicroSpecialtyCertificates(Long userId);

    CertificateVO getById(Long id);

    CertificateVO issueCertificate(Long userId, Long courseId);

    CertificateVO issueMicroSpecialtyCertificate(Long userId, Long microSpecialtyId, Long enrollmentId);

    boolean hasCertificate(Long userId, Long courseId);

    byte[] generateCertificatePdf(Long certificateId);

    /** 获取证书并校验是否为当前用户所有或管理员 */
    CertificateVO getByIdWithOwnerCheck(Long id, Long currentUserId);

    /** 下载证书 PDF 并校验是否为当前用户所有或管理员 */
    byte[] downloadCertificateWithOwnerCheck(Long id, Long currentUserId);
}
