package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CertificateVO;
import com.microcourse.entity.Certificate;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseRecord;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CertificateRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRecordRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CertificateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository certificateRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExerciseRecordRepository exerciseRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CertificateServiceImpl(CertificateRepository certificateRepository,
                                  EnrollmentRepository enrollmentRepository,
                                  ExerciseRecordRepository exerciseRecordRepository,
                                  ExerciseRepository exerciseRepository,
                                  CourseRepository courseRepository,
                                  UserRepository userRepository) {
        this.certificateRepository = certificateRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.exerciseRecordRepository = exerciseRecordRepository;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CertificateVO> getMyCertificates(Long userId) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Certificate::getUserId, userId)
               .orderByDesc(Certificate::getIssuedAt);
        List<Certificate> certificates = certificateRepository.selectList(wrapper);

        return certificates.stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateVO getCertificateById(Long id, Long userId) {
        Certificate certificate = certificateRepository.selectById(id);
        if (certificate == null) {
            throw new BusinessException(ErrorCode.CERTIFICATE_NOT_FOUND);
        }
        if (!certificate.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        return convertToVO(certificate);
    }

    @Override
    @Transactional(readOnly = true)
    public String generateCertificateHtml(Long certificateId, Long userId) {
        CertificateVO cert = getCertificateById(certificateId, userId);

        String issuedDateStr = cert.getIssuedAt() != null
            ? cert.getIssuedAt().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
            : "";

        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>课程证书 - %s</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: "Microsoft YaHei", "SimHei", "Noto Serif SC", serif;
                        background: linear-gradient(135deg, #f5f7fa 0%%, #e4e8ec 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        padding: 20px;
                    }
                    .certificate {
                        width: 800px;
                        background: linear-gradient(180deg, #ffffff 0%%, #f9fafb 100%%);
                        border: 3px solid #1a1a2e;
                        border-radius: 12px;
                        padding: 60px 80px;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.15);
                        position: relative;
                    }
                    .certificate::before {
                        content: "";
                        position: absolute;
                        top: 15px; left: 15px; right: 15px; bottom: 15px;
                        border: 1px solid #c9a227;
                        border-radius: 8px;
                        pointer-events: none;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 40px;
                    }
                    .logo {
                        font-size: 14px;
                        color: #666;
                        margin-bottom: 10px;
                    }
                    .title {
                        font-size: 42px;
                        font-weight: 700;
                        color: #1a1a2e;
                        letter-spacing: 8px;
                        margin-bottom: 8px;
                    }
                    .subtitle {
                        font-size: 16px;
                        color: #888;
                        letter-spacing: 2px;
                    }
                    .content {
                        text-align: center;
                        margin: 50px 0;
                    }
                    .certify-text {
                        font-size: 18px;
                        color: #555;
                        margin-bottom: 30px;
                    }
                    .student-name {
                        font-size: 36px;
                        font-weight: 700;
                        color: #1a1a2e;
                        border-bottom: 3px solid #c9a227;
                        display: inline-block;
                        padding: 0 20px 10px;
                        margin-bottom: 30px;
                    }
                    .course-info {
                        font-size: 20px;
                        color: #333;
                        margin-bottom: 40px;
                        line-height: 1.8;
                    }
                    .course-title {
                        font-size: 26px;
                        font-weight: 600;
                        color: #1a1a2e;
                    }
                    .footer {
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-end;
                        margin-top: 60px;
                        padding-top: 30px;
                        border-top: 1px dashed #ccc;
                    }
                    .footer-left {
                        text-align: left;
                    }
                    .teacher-info {
                        font-size: 16px;
                        color: #666;
                        margin-bottom: 5px;
                    }
                    .teacher-name {
                        font-size: 18px;
                        color: #333;
                        font-weight: 600;
                    }
                    .footer-center {
                        text-align: center;
                    }
                    .date {
                        font-size: 16px;
                        color: #666;
                    }
                    .footer-right {
                        text-align: right;
                    }
                    .cert-code {
                        font-size: 14px;
                        color: #999;
                        font-family: "Courier New", monospace;
                    }
                    .seal {
                        width: 80px;
                        height: 80px;
                        border: 2px solid #c9a227;
                        border-radius: 50%%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin-top: 10px;
                        margin-left: auto;
                        color: #c9a227;
                        font-size: 12px;
                        font-weight: 600;
                    }
                    @media print {
                        body { background: white; padding: 0; }
                        .certificate { box-shadow: none; border: 2px solid #1a1a2e; }
                    }
                </style>
            </head>
            <body>
                <div class="certificate">
                    <div class="header">
                        <div class="logo">微课管理平台</div>
                        <h1 class="title">结业证书</h1>
                        <p class="subtitle">CERTIFICATE OF COMPLETION</p>
                    </div>
                    <div class="content">
                        <p class="certify-text">兹证明以下学员已完成课程学习</p>
                        <div class="student-name">学员</div>
                        <div class="course-info">
                            已完成《<span class="course-title">%s</span>》<br/>
                            课程学习并通过考核，特此颁发此证。
                        </div>
                    </div>
                    <div class="footer">
                        <div class="footer-left">
                            <div class="teacher-info">授课教师</div>
                            <div class="teacher-name">%s</div>
                        </div>
                        <div class="footer-center">
                            <div class="date">%s</div>
                        </div>
                        <div class="footer-right">
                            <div class="cert-code">%s</div>
                            <div class="seal">官方认证</div>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(
                cert.getCourseTitle(),
                cert.getCourseTitle(),
                cert.getTeacherName() != null ? cert.getTeacherName() : "教师",
                issuedDateStr,
                cert.getCertCode()
            );
    }

    @Override
    @Transactional
    public void autoIssueCertificate(Long userId, Long courseId) {
        // 检查是否已存在证书
        LambdaQueryWrapper<Certificate> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(Certificate::getUserId, userId)
                    .eq(Certificate::getCourseId, courseId);
        long count = certificateRepository.selectCount(existWrapper);
        if (count > 0) {
            return;
        }

        // 检查 enrollment.progress >= 100
        LambdaQueryWrapper<Enrollment> enrollmentWrapper = new LambdaQueryWrapper<>();
        enrollmentWrapper.eq(Enrollment::getUserId, userId)
                         .eq(Enrollment::getCourseId, courseId);
        Enrollment enrollment = enrollmentRepository.selectOne(enrollmentWrapper);
        if (enrollment == null || enrollment.getProgress() == null || enrollment.getProgress() < 100) {
            return;
        }

        // 检查 exercisePassed = true (该课程下所有练习都通过)
        boolean exercisePassed = checkExercisePassed(userId, courseId);
        if (!exercisePassed) {
            return;
        }

        // 生成证书
        String certCode = String.format("CERT-%d-%d-%d", userId, courseId, System.currentTimeMillis());
        Certificate certificate = new Certificate();
        certificate.setUserId(userId);
        certificate.setCourseId(courseId);
        certificate.setCertCode(certCode);
        certificate.setIssuedAt(LocalDateTime.now());
        certificateRepository.insert(certificate);
    }

    private boolean checkExercisePassed(Long userId, Long courseId) {
        // 获取该课程下所有练习
        LambdaQueryWrapper<Exercise> exerciseWrapper = new LambdaQueryWrapper<>();
        exerciseWrapper.eq(Exercise::getCourseId, courseId);
        List<Exercise> exercises = exerciseRepository.selectList(exerciseWrapper);

        if (exercises.isEmpty()) {
            return true;
        }

        // 每个练习都需要有 passed=true 的记录
        for (Exercise exercise : exercises) {
            LambdaQueryWrapper<ExerciseRecord> recordWrapper = new LambdaQueryWrapper<>();
            recordWrapper.eq(ExerciseRecord::getUserId, userId)
                         .eq(ExerciseRecord::getExerciseId, exercise.getId())
                         .eq(ExerciseRecord::getPassed, true);
            long passedCount = exerciseRecordRepository.selectCount(recordWrapper);
            if (passedCount == 0) {
                return false;
            }
        }
        return true;
    }

    private CertificateVO convertToVO(Certificate certificate) {
        CertificateVO vo = new CertificateVO();
        vo.setId(certificate.getId());
        vo.setUserId(certificate.getUserId());
        vo.setCourseId(certificate.getCourseId());
        vo.setCertCode(certificate.getCertCode());
        vo.setIssuedAt(certificate.getIssuedAt());

        Course course = courseRepository.selectById(certificate.getCourseId());
        if (course != null) {
            vo.setCourseTitle(course.getTitle());
            User teacher = userRepository.selectById(course.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        return vo;
    }
}