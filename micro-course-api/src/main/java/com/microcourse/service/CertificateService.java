package com.microcourse.service;

import com.microcourse.dto.CertificateVO;
import com.microcourse.dto.PageResult;

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
}
