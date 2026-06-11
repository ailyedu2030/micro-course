package com.microcourse.controller;

import com.microcourse.dto.CertificateVO;
import com.microcourse.dto.R;
import com.microcourse.service.CertificateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    public R<List<CertificateVO>> getMyCertificates(Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<CertificateVO> certificates = certificateService.getMyCertificates(userId);
        return R.ok(certificates);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public R<String> downloadCertificate(@PathVariable Long id, Authentication authentication) {
        Long userId = extractUserId(authentication);
        String html = certificateService.generateCertificateHtml(id, userId);
        return R.ok(html);
    }

    private Long extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        return Long.parseLong(principal.toString());
    }
}