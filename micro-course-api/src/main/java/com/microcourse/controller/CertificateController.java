package com.microcourse.controller;

import com.microcourse.dto.CertificateVO;
import com.microcourse.dto.R;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.service.CertificateService;
import com.microcourse.util.SecurityUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public R<List<CertificateVO>> getMyCertificates(
            @RequestParam(name = "type", required = false) String type) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (type != null && !type.isBlank()) {
            return R.ok(certificateService.getMyCertificates(userId, type));
        }
        return R.ok(certificateService.getMyCertificates(userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public R<CertificateVO> getById(@PathVariable Long id) {
        CertificateVO cert = certificateService.getById(id);
        Long certUserId = cert.getUserId();
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!Objects.equals(certUserId, currentUserId) && !SecurityUtil.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return R.ok(cert);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long id) {
        CertificateVO cert = certificateService.getById(id);
        Long certUserId = cert.getUserId();
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!Objects.equals(certUserId, currentUserId) && !SecurityUtil.hasRole("ADMIN")) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        byte[] pdfBytes = certificateService.generateCertificatePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "certificate-" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @PostMapping("/issue")
    @PreAuthorize("hasRole('ADMIN')")
    public R<CertificateVO> issueCertificate(@RequestParam Long courseId) {
        Long userId = SecurityUtil.getCurrentUserId();
        CertificateVO cert = certificateService.issueCertificate(userId, courseId);
        return R.ok(cert);
    }
}
