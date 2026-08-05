package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.ResetPasswordRequest;
import com.microcourse.dto.StudentSearchVO;
import com.microcourse.dto.UserCreateRequest;
import com.microcourse.dto.UserPageQuery;
import com.microcourse.dto.TeacherStatusRequest;
import com.microcourse.dto.UserStatusRequest;
import com.microcourse.dto.UserUpdateRequest;
import com.microcourse.dto.UserVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.enums.UserStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.UserBatchImportService;
import com.microcourse.service.UserService;
import com.microcourse.service.UserStatusService;
import com.microcourse.service.UserQueryService;
import com.microcourse.util.SecurityUtil;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    /**
     * 空字符串规范化：DB 检查约束（chk_users_gender 等）不接受空串，
     * 但接受 NULL。前端表单字段未填写时常以 "" 提交，需归一化为 null。
     */
    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final ClassesRepository classesRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserQueryService queryService;
    private final UserStatusService statusService;
    private final UserBatchImportService batchImportService;
    private final UserServiceImpl self;

    public UserServiceImpl(UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder,
                           DepartmentRepository departmentRepository,
                           MajorRepository majorRepository,
                           ClassesRepository classesRepository,
                           CourseRepository courseRepository,
                           EnrollmentRepository enrollmentRepository,
                           UserQueryService queryService,
                           UserStatusService statusService,
                           UserBatchImportService batchImportService,
                           @Lazy UserServiceImpl self) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.classesRepository = classesRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.queryService = queryService;
        this.statusService = statusService;
        this.batchImportService = batchImportService;
        this.self = self;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UserVO> pageUsers(UserPageQuery query) {
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            Long teacherId = SecurityUtil.getCurrentUserId();
            List<Long> courseIds = courseRepository.selectList(
                new LambdaQueryWrapper<Course>()
                    .eq(Course::getTeacherId, teacherId)
                    .isNull(Course::getDeletedAt)
                    .select(Course::getId)
            ).stream().map(Course::getId).collect(Collectors.toList());
            if (!courseIds.isEmpty()) {
                List<Long> studentIds = enrollmentRepository.selectList(
                    new LambdaQueryWrapper<Enrollment>()
                        .in(Enrollment::getCourseId, courseIds)
                        .isNull(Enrollment::getDeletedAt)
                        .select(Enrollment::getUserId)
                ).stream().map(Enrollment::getUserId).distinct().collect(Collectors.toList());
                query.setInUserIds(studentIds.isEmpty() ? Collections.singletonList(-1L) : studentIds);
            } else {
                query.setInUserIds(Collections.singletonList(-1L));
            }
        }
        return queryService.pageUsers(query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSearchVO> searchStudents(String keyword, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRole, "STUDENT")
                .eq(User::getStatus, 1)
                .select(User::getId, User::getRealName, User::getUsername, User::getStudentNo, User::getAvatar, User::getStatus)
                .last("LIMIT " + Math.min(Math.max(size, 1), 100));
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getRealName, kw)
                    .or().like(User::getStudentNo, kw)
                    .or().like(User::getUsername, kw));
        }
        return userRepository.selectList(wrapper).stream().map(u -> {
            StudentSearchVO vo = new StudentSearchVO();
            vo.setId(u.getId());
            vo.setRealName(u.getRealName());
            vo.setUsername(u.getUsername());
            vo.setStudentNo(u.getStudentNo());
            vo.setAvatar(u.getAvatar());
            vo.setStatus(u.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserVO getUserById(Long id) {
        return queryService.getUserById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO createUser(UserCreateRequest request) {
        if (!PASSWORD_PATTERN.matcher(request.getPassword()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "密码需至少 8 位且包含字母和数字");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }

        // P1C-12: 邮箱唯一性预检查（email 可为空/BLANK，空值绕过 UNIQUE 约束）
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Long emailCount = userRepository.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, request.getEmail().trim()));
            if (emailCount > 0) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // P1C-12: 学号唯一性预检查（studentNo 可为空/BLANK，空值绕过 UNIQUE 约束）
        if (request.getStudentNo() != null && !request.getStudentNo().isBlank()) {
            Long studentNoCount = userRepository.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getStudentNo, request.getStudentNo().trim()));
            if (studentNoCount > 0) {
                throw new BusinessException(ErrorCode.STUDENT_NO_EXISTS);
            }
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());  // NOT NULL, 不归一
        // 文本字段统一 nullIfBlank：避开 DB chk_users_gender / uk_users_student_no
        // / uk_users_teacher_no 等约束（Postgres UNIQUE 把空串视为相等，与 NULL 不同）
        user.setEmail(nullIfBlank(request.getEmail()));
        user.setPhone(nullIfBlank(request.getPhone()));
        user.setRole(request.getRole());
        user.setDepartmentId(request.getDepartmentId());
        user.setMajorId(request.getMajorId());
        user.setClassId(request.getClassId());
        user.setGender(nullIfBlank(request.getGender()));
        user.setStudentNo(nullIfBlank(request.getStudentNo()));
        user.setTeacherNo(nullIfBlank(request.getTeacherNo()));
        user.setEnrollmentYear(request.getEnrollmentYear());
        user.setGraduationYear(request.getGraduationYear());
        user.setGrade(request.getGrade());
        user.setPoliticalStatus(nullIfBlank(request.getPoliticalStatus()));
        // 【user-domain-drift-fix】状态字段改用枚举引用
        user.setStatus(request.getStatus() != null
                ? UserStatus.fromCode(request.getStatus()).getCode()
                : UserStatus.ACTIVE.getCode());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.insert(user);
        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // P1C-12: 邮箱修改时检查唯一性（排除自身）
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Long emailCount = userRepository.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getEmail, request.getEmail().trim())
                            .ne(User::getId, id));
            if (emailCount > 0) {
                throw new BusinessException(ErrorCode.EMAIL_EXISTS);
            }
        }

        // P1C-12: 学号修改时检查唯一性（排除自身）
        if (request.getStudentNo() != null && !request.getStudentNo().isBlank()) {
            Long studentNoCount = userRepository.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getStudentNo, request.getStudentNo().trim())
                            .ne(User::getId, id));
            if (studentNoCount > 0) {
                throw new BusinessException(ErrorCode.STUDENT_NO_EXISTS);
            }
        }

        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getEmail() != null) {
            user.setEmail(nullIfBlank(request.getEmail()));
        }
        if (request.getPhone() != null) {
            user.setPhone(nullIfBlank(request.getPhone()));
        }
        if (request.getGender() != null) {
            user.setGender(nullIfBlank(request.getGender()));
        }
        if (request.getAvatar() != null) {
            user.setAvatar(nullIfBlank(request.getAvatar()));
        }
        if (request.getDepartmentId() != null) {
            user.setDepartmentId(request.getDepartmentId());
        }
        if (request.getMajorId() != null) {
            user.setMajorId(request.getMajorId());
        }
        if (request.getClassId() != null) {
            user.setClassId(request.getClassId());
        }
        if (request.getGrade() != null) {
            user.setGrade(request.getGrade());
        }
        if (request.getEnrollmentYear() != null) {
            user.setEnrollmentYear(request.getEnrollmentYear());
        }
        if (request.getGraduationYear() != null) {
            user.setGraduationYear(request.getGraduationYear());
        }
        if (request.getStudentNo() != null) {
            user.setStudentNo(nullIfBlank(request.getStudentNo()));
        }
        if (request.getTeacherNo() != null) {
            user.setTeacherNo(nullIfBlank(request.getTeacherNo()));
        }
        if (request.getPoliticalStatus() != null) {
            user.setPoliticalStatus(nullIfBlank(request.getPoliticalStatus()));
        }
        // P2-017: 合并状态变更与普通字段更新为单次 DB 写操作
        if (request.getStatus() != null) {
            if (!Objects.equals(user.getStatus(), request.getStatus())) {
                // 【user-domain-drift-fix】状态字段改用枚举引用
                user.setStatus(UserStatus.fromCode(request.getStatus()).getCode());
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        return convertToVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, ResetPasswordRequest request) {
        if (!PASSWORD_PATTERN.matcher(request.getNewPassword()).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "密码需至少 8 位且包含字母和数字");
        }
        User user = userRepository.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        log.info("管理员重置用户密码: userId={}, operator={}", id, SecurityUtil.getCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, UserStatusRequest request) {
        statusService.updateStatus(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        statusService.updateStatus(id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTeacherStatus(Long id, TeacherStatusRequest request) {
        statusService.updateTeacherStatus(id, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchImportResultVO batchImportUsers(MultipartFile file) {
        return batchImportService.batchImportUsers(file);
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        validateImageMagic(file);

        String uploadDir = System.getProperty("user.dir") + "/uploads/avatars/";
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) dir.mkdirs();

        String contentType = file.getContentType();
        String ext = ".jpg";
        if ("image/png".equals(contentType)) ext = ".png";
        else if ("image/gif".equals(contentType)) ext = ".gif";
        else if ("image/webp".equals(contentType)) ext = ".webp";
        String filename = userId + "_" + System.currentTimeMillis() + ext;
        java.io.File dest = new java.io.File(uploadDir + filename);

        try {
            file.transferTo(dest);
        } catch (Exception e) {
            log.error("[User] 头像文件保存失败 userId={}", userId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像文件保存失败");
        }

        String oldAvatarToDelete = null;
        if (user.getAvatar() != null && user.getAvatar().startsWith("/api/files/avatars/")) {
            oldAvatarToDelete = user.getAvatar().substring("/api/files/avatars/".length());
        }
        final String oldFileToClean = oldAvatarToDelete;

        String avatarUrl = "/api/files/avatars/" + filename;
        try {
            self.updateAvatarInDb(userId, avatarUrl);
        } catch (Exception e) {
            if (dest.exists()) {
                dest.delete();
            }
            log.error("[User] 头像 DB 更新失败，已清理文件 userId={}", userId, e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像上传失败");
        }

        if (oldFileToClean != null) {
            try {
                java.io.File oldFile = new java.io.File(uploadDir + oldFileToClean);
                if (oldFile.exists()) oldFile.delete();
            } catch (Exception e) {
                log.warn("[User] 清理旧头像文件失败 userId={}, oldFile={}", userId, oldFileToClean, e);
            }
        }

        return avatarUrl;
    }

    @Transactional(rollbackFor = Exception.class)
    protected void updateAvatarInDb(Long userId, String avatarUrl) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        user.setAvatar(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
    }

    private void validateImageMagic(MultipartFile file) {
        try (java.io.InputStream is = file.getInputStream()) {
            byte[] magic = new byte[8];
            int read = is.read(magic);
            if (read < 4) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件过小，无法验证图片格式");
            boolean isJpeg = (magic[0] & 0xFF) == 0xFF && (magic[1] & 0xFF) == 0xD8 && (magic[2] & 0xFF) == 0xFF;
            boolean isPng = (magic[0] & 0xFF) == 0x89 && magic[1] == 'P' && magic[2] == 'N' && magic[3] == 'G';
            if (!isJpeg && !isPng) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "头像必须为 JPEG 或 PNG 格式（魔数校验失败）");
        } catch (java.io.IOException e) { throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "无法读取头像文件"); }
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        // P1I-005: convertToVO 用于 create/update 端点，不应脱敏
        // list 端点的脱敏由 UserQueryServiceImpl.pageUsers() 分角色控制
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setGender(user.getGender());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setMajorId(user.getMajorId());
        vo.setClassId(user.getClassId());
        vo.setGrade(user.getGrade());
        vo.setEnrollmentYear(user.getEnrollmentYear());
        vo.setGraduationYear(user.getGraduationYear());
        vo.setCasBound(user.getCasBound());
        vo.setStudentNo(user.getStudentNo());
        vo.setTeacherNo(user.getTeacherNo());
        vo.setPoliticalStatus(user.getPoliticalStatus());
        vo.setStatus(user.getStatus());
        vo.setTeacherStatus(user.getTeacherStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());

        if (user.getDepartmentId() != null) {
            Department dept = departmentRepository.selectById(user.getDepartmentId());
            if (dept != null) {
                vo.setDepartmentName(dept.getName());
            }
        }
        if (user.getMajorId() != null) {
            Major major = majorRepository.selectById(user.getMajorId());
            if (major != null) {
                vo.setMajorName(major.getName());
            }
        }
        if (user.getClassId() != null) {
            Classes cls = classesRepository.selectById(user.getClassId());
            if (cls != null) {
                vo.setClassName(cls.getName());
            }
        }

        if (user.getStatus() != null) {
            UserStatus us = UserStatus.fromCode(user.getStatus());
            if (us != null) {
                switch (us) {
                    case INACTIVE: vo.setStatusText("未激活"); break;
                    case ACTIVE:   vo.setStatusText("正常"); break;
                    case DISABLED: vo.setStatusText("禁用"); break;
                    case DELETED:  vo.setStatusText("已删除"); break;
                    default:       vo.setStatusText("未知");
                }
            } else {
                vo.setStatusText("未知");
            }
        }

        return vo;
    }

}
