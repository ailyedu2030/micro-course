package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Certificate;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.User;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CertificateRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CertificateService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CertificateServiceImpl implements CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateServiceImpl.class);

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MicroSpecialtyRepository microSpecialtyRepository;
    private final MicroSpecialtyEnrollmentRepository microSpecialtyEnrollmentRepository;
    private final NotificationService notificationService;

    public CertificateServiceImpl(CertificateRepository certificateRepository,
                                  CourseRepository courseRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  UserRepository userRepository,
                                  MicroSpecialtyRepository microSpecialtyRepository,
                                  MicroSpecialtyEnrollmentRepository microSpecialtyEnrollmentRepository,
                                  NotificationService notificationService) {
        this.certificateRepository = certificateRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.microSpecialtyRepository = microSpecialtyRepository;
        this.microSpecialtyEnrollmentRepository = microSpecialtyEnrollmentRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.microcourse.dto.CertificateVO> getMyCertificates(Long userId) {
        return getMyCertificates(userId, "COURSE");
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.microcourse.dto.CertificateVO> getMyCertificates(Long userId, String certType) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Certificate::getUserId, userId)
                .eq(Certificate::getCertType, certType)
                .orderByDesc(Certificate::getIssuedAt);
        List<Certificate> certs = certificateRepository.selectList(wrapper);
        if (certs.isEmpty()) {
            return List.of();
        }

        if ("MICRO_SPECIALTY".equals(certType)) {
            Set<Long> msIds = certs.stream()
                    .map(Certificate::getMicroSpecialtyId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, MicroSpecialty> msMap = msIds.isEmpty() ? java.util.Collections.emptyMap()
                    : microSpecialtyRepository.selectBatchIds(msIds).stream()
                    .collect(Collectors.toMap(MicroSpecialty::getId, ms -> ms));
            Map<Long, User> userMap = java.util.Collections.singletonMap(userId,
                    userRepository.selectById(userId));
            return certs.stream()
                    .map(cert -> convertMicroSpecialtyToVO(cert, msMap, userMap))
                    .collect(Collectors.toList());
        }

        // COURSE (default)
        Set<Long> courseIds = certs.stream()
                .map(Certificate::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Course> courseMap = courseIds.isEmpty() ? java.util.Collections.emptyMap()
                : courseRepository.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        Map<Long, User> userMap = java.util.Collections.singletonMap(userId,
                userRepository.selectById(userId));
        return certs.stream()
                .map(cert -> convertToVO(cert, courseMap, userMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.microcourse.dto.CertificateVO> getMyMicroSpecialtyCertificates(Long userId) {
        return getMyCertificates(userId, "MICRO_SPECIALTY");
    }

    @Override
    @Transactional(readOnly = true)
    public com.microcourse.dto.CertificateVO getById(Long id) {
        Certificate cert = certificateRepository.selectById(id);
        if (cert == null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_FOUND);
        }
        return convertToVO(cert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.microcourse.dto.CertificateVO issueCertificate(Long userId, Long courseId) {
        LambdaQueryWrapper<Certificate> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Certificate::getUserId, userId)
                .eq(Certificate::getCourseId, courseId)
                .eq(Certificate::getCertType, "COURSE");
        Certificate existing = certificateRepository.selectOne(existingWrapper);
        if (existing != null) {
            return convertToVO(existing);
        }

        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        Enrollment enrollment = enrollmentRepository.selectOne(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getUserId, userId)
                        .eq(Enrollment::getCourseId, courseId)
        );
        if (enrollment == null || !Boolean.TRUE.equals(enrollment.getCompleted())) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_ELIGIBLE);
        }

        Certificate cert = new Certificate();
        cert.setUserId(userId);
        cert.setCourseId(courseId);
        cert.setCertType("COURSE");
        cert.setCertCode(generateCertCode(userId, courseId));
        cert.setIssuedAt(LocalDateTime.now());
        try {
            certificateRepository.insert(cert);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            log.warn("[Certificate] 并发签发命中唯一冲突,降级查询已有证书 userId={} courseId={}", userId, courseId);
            LambdaQueryWrapper<Certificate> retryWrapper = new LambdaQueryWrapper<>();
            retryWrapper.eq(Certificate::getUserId, userId)
                        .eq(Certificate::getCourseId, courseId)
                        .eq(Certificate::getCertType, "COURSE");
            Certificate retryExisting = certificateRepository.selectOne(retryWrapper);
            if (retryExisting != null) return convertToVO(retryExisting);
            throw e;
        }

        return convertToVO(cert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public com.microcourse.dto.CertificateVO issueMicroSpecialtyCertificate(Long userId, Long microSpecialtyId, Long enrollmentId) {
        // 幂等检查：同 userId + microSpecialtyId + cert_type 不能重复签发
        LambdaQueryWrapper<Certificate> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Certificate::getUserId, userId)
                .eq(Certificate::getMicroSpecialtyId, microSpecialtyId)
                .eq(Certificate::getCertType, "MICRO_SPECIALTY");
        Certificate existing = certificateRepository.selectOne(existingWrapper);
        if (existing != null) {
            return convertMicroSpecialtyToVO(existing);
        }

        MicroSpecialty ms = microSpecialtyRepository.selectById(microSpecialtyId);
        if (ms == null) {
            throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        }

        MicroSpecialtyEnrollment enrollment = microSpecialtyEnrollmentRepository.selectById(enrollmentId);
        if (enrollment == null) {
            throw new BusinessException(ErrorCode.MS_ENROLLMENT_NOT_FOUND);
        }
        if (!"COMPLETED".equals(enrollment.getStatus())) {
            throw new BusinessException(ErrorCode.MS_CERT_NOT_READY);
        }

        // Retry loop for cert code generation (max 3 attempts)
        String certCode = null;
        Certificate cert = null;
        for (int i = 0; i < 3; i++) {
            certCode = generateMicroSpecialtyCertCode(ms.getCode(), userId);
            cert = new Certificate();
            cert.setUserId(userId);
            cert.setCertType("MICRO_SPECIALTY");
            cert.setMicroSpecialtyId(microSpecialtyId);
            cert.setCertCode(certCode);
            cert.setIssuedAt(LocalDateTime.now());
            try {
                certificateRepository.insert(cert);
                break; // success
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (i == 2) {
                    // last attempt failed, fallback to existing record
                    log.warn("[Certificate] 并发签发微专业证书命中唯一冲突,降级查询 userId={} microSpecialtyId={}", userId, microSpecialtyId);
                    Certificate retry = certificateRepository.selectOne(existingWrapper);
                    if (retry != null) return convertMicroSpecialtyToVO(retry);
                    throw e;
                }
                // continue to regenerate certCode
            }
        }

        // 回写 enrollment.certificate_id
        enrollment.setCertificateId(cert.getId());
        microSpecialtyEnrollmentRepository.updateById(enrollment);

        // 发通知（异步，异常隔离）
        try {
            notificationService.notifyAsync(userId, NotificationType.MS_CERTIFICATE_ISSUED,
                    "微专业证书已颁发",
                    "恭喜您获得微专业「" + ms.getTitle() + "」的结业证书！证书编号：" + certCode,
                    microSpecialtyId);
        } catch (Exception e) {
            log.error("[Certificate] 发送微专业证书通知失败 userId={} microSpecialtyId={}", userId, microSpecialtyId, e);
        }

        return convertMicroSpecialtyToVO(cert);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCertificate(Long userId, Long courseId) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Certificate::getUserId, userId)
                .eq(Certificate::getCourseId, courseId)
                .eq(Certificate::getCertType, "COURSE");
        return certificateRepository.selectCount(wrapper) > 0;
    }

    @Override
    public com.microcourse.dto.CertificateVO getByIdWithOwnerCheck(Long id, Long currentUserId) {
        com.microcourse.dto.CertificateVO cert = getById(id);
        if (!Objects.equals(cert.getUserId(), currentUserId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return cert;
    }

    @Override
    public byte[] downloadCertificateWithOwnerCheck(Long id, Long currentUserId) {
        com.microcourse.dto.CertificateVO cert = getById(id);
        if (!Objects.equals(cert.getUserId(), currentUserId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return generateCertificatePdf(id);
    }

    /**
     * C2-1 修复：PDF 生成（iText）不在事务内执行，避免长事务占用 DB 连接。
     * 3 次 selectById 是快速点查，无需事务保护。
     */
    @Override
    public byte[] generateCertificatePdf(Long certificateId) {
        Certificate cert = certificateRepository.selectById(certificateId);
        if (cert == null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_FOUND);
        }

        if ("MICRO_SPECIALTY".equals(cert.getCertType())) {
            // handle micro-specialty certificate - get micro specialty info
            MicroSpecialty ms = null;
            if (cert.getMicroSpecialtyId() != null) {
                ms = microSpecialtyRepository.selectById(cert.getMicroSpecialtyId());
                if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
            }
            return generateMicroSpecialtyPdf(cert, ms);
        }

        Course courseEntity = courseRepository.selectById(cert.getCourseId());
        User user = userRepository.selectById(cert.getUserId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 60, 60, 60, 60);
        PdfWriter.getInstance(doc, out);
        try {
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 28, Font.BOLD, new Color(51, 102, 153));
            Font subtitleFont = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.DARK_GRAY);
            Font nameFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(0, 0, 0));
            Font bodyFont = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.BLACK);
            Font certCodeFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

            Paragraph title = new Paragraph("微课平台学习证书", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            doc.add(new Paragraph(" "));

            Paragraph subtitle = new Paragraph("Certificate of Completion", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(subtitle);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));

            Paragraph certify = new Paragraph("This is to certify that", bodyFont);
            certify.setAlignment(Element.ALIGN_CENTER);
            doc.add(certify);

            doc.add(new Paragraph(" "));

            String studentName = user != null && user.getRealName() != null
                    ? user.getRealName() : "Student";
            Paragraph name = new Paragraph(studentName, nameFont);
            name.setAlignment(Element.ALIGN_CENTER);
            doc.add(name);

            doc.add(new Paragraph(" "));

            Paragraph completed = new Paragraph("has successfully completed the course", bodyFont);
            completed.setAlignment(Element.ALIGN_CENTER);
            doc.add(completed);

            doc.add(new Paragraph(" "));

            String courseName = courseEntity != null && courseEntity.getTitle() != null
                    ? courseEntity.getTitle() : "Unknown Course";
            Font courseFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(0, 102, 51));
            Paragraph coursePara = new Paragraph(courseName, courseFont);
            coursePara.setAlignment(Element.ALIGN_CENTER);
            doc.add(coursePara);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String issuedDate = cert.getIssuedAt() != null
                    ? cert.getIssuedAt().format(formatter) : LocalDateTime.now().format(formatter);
            Paragraph date = new Paragraph("Issued on: " + issuedDate, bodyFont);
            date.setAlignment(Element.ALIGN_CENTER);
            doc.add(date);

            doc.add(new Paragraph(" "));

            Paragraph code = new Paragraph("Certificate No: " + cert.getCertCode(), certCodeFont);
            code.setAlignment(Element.ALIGN_CENTER);
            doc.add(code);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    private byte[] generateMicroSpecialtyPdf(Certificate cert, MicroSpecialty ms) {
        User user = userRepository.selectById(cert.getUserId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 60, 60, 60, 60);
        PdfWriter.getInstance(doc, out);
        try {
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 28, Font.BOLD, new Color(51, 102, 153));
            Font subtitleFont = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.DARK_GRAY);
            Font nameFont = new Font(Font.HELVETICA, 22, Font.BOLD, new Color(0, 0, 0));
            Font bodyFont = new Font(Font.HELVETICA, 14, Font.NORMAL, Color.BLACK);
            Font certCodeFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);

            Paragraph title = new Paragraph("微课平台微专业结业证书", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            doc.add(new Paragraph(" "));

            Paragraph subtitle = new Paragraph("Micro-Specialty Certificate of Completion", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            doc.add(subtitle);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));

            Paragraph certify = new Paragraph("This is to certify that", bodyFont);
            certify.setAlignment(Element.ALIGN_CENTER);
            doc.add(certify);

            doc.add(new Paragraph(" "));

            String studentName = user != null && user.getRealName() != null
                    ? user.getRealName() : "Student";
            Paragraph name = new Paragraph(studentName, nameFont);
            name.setAlignment(Element.ALIGN_CENTER);
            doc.add(name);

            doc.add(new Paragraph(" "));

            Paragraph completed = new Paragraph("has successfully completed the Micro-Specialty", bodyFont);
            completed.setAlignment(Element.ALIGN_CENTER);
            doc.add(completed);

            doc.add(new Paragraph(" "));

            String msName = ms != null && ms.getTitle() != null
                    ? ms.getTitle() : "Unknown Micro-Specialty";
            Font msFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(0, 102, 51));
            Paragraph msPara = new Paragraph(msName, msFont);
            msPara.setAlignment(Element.ALIGN_CENTER);
            doc.add(msPara);

            if (ms != null && ms.getTotalCredits() != null) {
                doc.add(new Paragraph(" "));
                Paragraph credits = new Paragraph("Total Credits: " + ms.getTotalCredits().toString(), bodyFont);
                credits.setAlignment(Element.ALIGN_CENTER);
                doc.add(credits);
            }

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String issuedDate = cert.getIssuedAt() != null
                    ? cert.getIssuedAt().format(formatter) : LocalDateTime.now().format(formatter);
            Paragraph date = new Paragraph("Issued on: " + issuedDate, bodyFont);
            date.setAlignment(Element.ALIGN_CENTER);
            doc.add(date);

            doc.add(new Paragraph(" "));

            Paragraph code = new Paragraph("Certificate No: " + cert.getCertCode(), certCodeFont);
            code.setAlignment(Element.ALIGN_CENTER);
            doc.add(code);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ============ 私有辅助方法 ============

    private String generateCertCode(Long userId, Long courseId) {
        return String.format("MC-%d-%d-%s",
                userId, courseId,
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private String generateMicroSpecialtyCertCode(String specialtyCode, Long userId) {
        String yyyyMM = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String randomHex = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("MS-%s-%d-%s-%s", specialtyCode, userId, yyyyMM, randomHex);
    }

    // ---- Course 证书 VO 转换 ----

    private com.microcourse.dto.CertificateVO convertToVO(Certificate cert) {
        Course course = cert.getCourseId() != null ? courseRepository.selectById(cert.getCourseId()) : null;
        User user = cert.getUserId() != null ? userRepository.selectById(cert.getUserId()) : null;
        return populateVO(cert, course, user);
    }

    private com.microcourse.dto.CertificateVO convertToVO(Certificate cert,
                                                           Map<Long, Course> courseMap,
                                                           Map<Long, User> userMap) {
        Course course = courseMap.get(cert.getCourseId());
        User user = userMap.get(cert.getUserId());
        return populateVO(cert, course, user);
    }

    private com.microcourse.dto.CertificateVO populateVO(Certificate cert, Course course, User user) {
        com.microcourse.dto.CertificateVO vo = new com.microcourse.dto.CertificateVO();
        vo.setId(cert.getId());
        vo.setUserId(cert.getUserId());
        vo.setCourseId(cert.getCourseId());
        vo.setCertCode(cert.getCertCode());
        vo.setIssuedAt(cert.getIssuedAt());

        if (course != null) {
            vo.setCourseName(course.getTitle());
        }
        if (user != null) {
            vo.setStudentName(user.getRealName());
        }
        return vo;
    }

    // ---- 微专业证书 VO 转换 ----

    private com.microcourse.dto.CertificateVO convertMicroSpecialtyToVO(Certificate cert) {
        MicroSpecialty ms = cert.getMicroSpecialtyId() != null
                ? microSpecialtyRepository.selectById(cert.getMicroSpecialtyId()) : null;
        User user = cert.getUserId() != null ? userRepository.selectById(cert.getUserId()) : null;
        return populateMicroSpecialtyVO(cert, ms, user);
    }

    private com.microcourse.dto.CertificateVO convertMicroSpecialtyToVO(Certificate cert,
                                                                          Map<Long, MicroSpecialty> msMap,
                                                                          Map<Long, User> userMap) {
        MicroSpecialty ms = msMap.get(cert.getMicroSpecialtyId());
        User user = userMap.get(cert.getUserId());
        return populateMicroSpecialtyVO(cert, ms, user);
    }

    private com.microcourse.dto.CertificateVO populateMicroSpecialtyVO(Certificate cert, MicroSpecialty ms, User user) {
        com.microcourse.dto.CertificateVO vo = new com.microcourse.dto.CertificateVO();
        vo.setId(cert.getId());
        vo.setUserId(cert.getUserId());
        vo.setCourseId(cert.getMicroSpecialtyId()); // 复用 courseId 携带 microSpecialtyId
        vo.setCertCode(cert.getCertCode());
        vo.setIssuedAt(cert.getIssuedAt());
        if (ms != null) {
            vo.setCourseName(ms.getTitle());
        }
        if (user != null) {
            vo.setStudentName(user.getRealName());
        }
        return vo;
    }
}
