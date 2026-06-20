package com.microcourse.controller;

import com.microcourse.dto.CertificateVO;
import com.microcourse.service.CertificateService;
import com.microcourse.util.SecurityUtil;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CertificateVO>> getMyCertificates() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(certificateService.getMyCertificates(userId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CertificateVO> getById(@PathVariable Long id) {
        CertificateVO cert = certificateService.getById(id);
        return ResponseEntity.ok(cert);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadCertificate(@PathVariable Long id) {
        byte[] pdfBytes = certificateService.generateCertificatePdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "certificate-" + id + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @PostMapping("/issue")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CertificateVO> issueCertificate(@RequestParam Long courseId) {
        Long userId = SecurityUtil.getCurrentUserId();
        CertificateVO cert = certificateService.issueCertificate(userId, courseId);
        return ResponseEntity.ok(cert);
    }
}
