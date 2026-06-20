package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Certificate;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CertificateRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CertificateService;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository certificateRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    public CertificateServiceImpl(CertificateRepository certificateRepository,
                                  CourseRepository courseRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  UserRepository userRepository) {
        this.certificateRepository = certificateRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.microcourse.dto.CertificateVO> getMyCertificates(Long userId) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Certificate::getUserId, userId)
                .orderByDesc(Certificate::getIssuedAt);
        List<Certificate> certs = certificateRepository.selectList(wrapper);
        return certs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
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
                .eq(Certificate::getCourseId, courseId);
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
        cert.setCertCode(generateCertCode(userId, courseId));
        cert.setIssuedAt(LocalDateTime.now());
        certificateRepository.insert(cert);

        return convertToVO(cert);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCertificate(Long userId, Long courseId) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Certificate::getUserId, userId)
                .eq(Certificate::getCourseId, courseId);
        return certificateRepository.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateCertificatePdf(Long certificateId) {
        Certificate cert = certificateRepository.selectById(certificateId);
        if (cert == null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_FOUND);
        }

        Course courseEntity = courseRepository.selectById(cert.getCourseId());
        User user = userRepository.selectById(cert.getUserId());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 60, 60, 60, 60);
        PdfWriter.getInstance(doc, out);
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

        doc.close();
        return out.toByteArray();
    }

    private String generateCertCode(Long userId, Long courseId) {
        return String.format("MC-%d-%d-%s",
                userId, courseId,
                UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    private com.microcourse.dto.CertificateVO convertToVO(Certificate cert) {
        com.microcourse.dto.CertificateVO vo = new com.microcourse.dto.CertificateVO();
        vo.setId(cert.getId());
        vo.setUserId(cert.getUserId());
        vo.setCourseId(cert.getCourseId());
        vo.setCertCode(cert.getCertCode());
        vo.setIssuedAt(cert.getIssuedAt());

        if (cert.getCourseId() != null) {
            Course course = courseRepository.selectById(cert.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getTitle());
            }
        }
        if (cert.getUserId() != null) {
            User user = userRepository.selectById(cert.getUserId());
            if (user != null) {
                vo.setStudentName(user.getRealName());
            }
        }
        return vo;
    }
}
