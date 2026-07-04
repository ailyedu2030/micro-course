package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Department;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.User;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.MicroSpecialtyEnrollmentQueryService;
import com.microcourse.service.MicroSpecialtyService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MicroSpecialtyEnrollmentQueryServiceImpl implements MicroSpecialtyEnrollmentQueryService {

    private final MicroSpecialtyEnrollmentRepository enrollmentRepository;
    private final MicroSpecialtyRepository msRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final ClassesRepository classRepository;
    private final MicroSpecialtyService msService;

    public MicroSpecialtyEnrollmentQueryServiceImpl(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                                    MicroSpecialtyRepository msRepository,
                                                    UserRepository userRepository,
                                                    DepartmentRepository departmentRepository,
                                                    ClassesRepository classRepository,
                                                    MicroSpecialtyService msService) {
        this.enrollmentRepository = enrollmentRepository;
        this.msRepository = msRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.classRepository = classRepository;
        this.msService = msService;
    }

    @Override
    public List<MicroSpecialtyEnrollmentVO> getMyEnrollments() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<MicroSpecialtyEnrollment> list = enrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getUserId, userId)
                        .orderByDesc(MicroSpecialtyEnrollment::getCreatedAt));
        return list.stream().map(en -> {
            MicroSpecialty ms = msRepository.selectById(en.getMicroSpecialtyId());
            return toVO(en, ms);
        }).collect(Collectors.toList());
    }

    @Override
    public PageResult<MicroSpecialtyEnrollmentVO> listEnrollments(Long msId, int page, int size, String status) {
        LambdaQueryWrapper<MicroSpecialtyEnrollment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(MicroSpecialtyEnrollment::getStatus, status);
        }
        wrapper.orderByDesc(MicroSpecialtyEnrollment::getCreatedAt);

        // Fix 4: 权限校验——仅负责人或管理员/教务处可查看报名列表
        if (!SecurityUtil.isAdminOrAcademic()) {
            msService.requireLeadOf(msId);
        }

        // page 参数为 0-based（前端约定），MyBatis-Plus 使用 1-based，需 +1 转换
        IPage<MicroSpecialtyEnrollment> ipage = enrollmentRepository.selectPage(new Page<>(page + 1, size), wrapper);
        MicroSpecialty ms = msRepository.selectById(msId);

        List<MicroSpecialtyEnrollmentVO> vos = ipage.getRecords().stream()
                .map(en -> toVO(en, ms)).collect(Collectors.toList());
        return PageResult.of(vos, ipage.getTotal(), page, size);
    }

    @Override
    public MicroSpecialtyEnrollmentVO toVO(MicroSpecialtyEnrollment en, MicroSpecialty ms) {
        MicroSpecialtyEnrollmentVO vo = new MicroSpecialtyEnrollmentVO();
        vo.setId(en.getId());
        vo.setMicroSpecialtyId(en.getMicroSpecialtyId());
        vo.setUserId(en.getUserId());
        vo.setSource(en.getSource());
        vo.setClassId(en.getClassId());
        vo.setStatus(en.getStatus());

        // Set class name
        if (en.getClassId() != null) {
            Classes clazz = classRepository.selectById(en.getClassId());
            if (clazz != null) {
                vo.setClassName(clazz.getName());
            }
        }
        vo.setProgress(en.getProgress());
        vo.setCreditsEarned(en.getCreditsEarned());
        vo.setCoursesCompleted(en.getCoursesCompleted());
        vo.setCoursesRequired(en.getCoursesRequired());
        vo.setFinalScore(en.getFinalScore());
        vo.setFinalGrade(en.getFinalGrade());
        vo.setCertificateId(en.getCertificateId());
        vo.setCanDownloadCert(en.getCertificateId() != null);
        vo.setPendingCourses(en.getPendingCourses());   // G2
        vo.setAppliedAt(en.getAppliedAt());
        vo.setApprovedAt(en.getApprovedAt());
        vo.setCompletedAt(en.getCompletedAt());
        vo.setDroppedAt(en.getDroppedAt());
        vo.setDropReason(en.getDropReason());

        if ("FAILED".equals(en.getStatus())) {
            vo.setFailReason(en.getDropReason());
        }

        if (ms != null) {
            vo.setMicroSpecialtyTitle(ms.getTitle());
            vo.setCoverUrl(ms.getCoverUrl());
            // Look up department name for the offering department
            if (ms.getOfferDepartmentId() != null) {
                Department dept = departmentRepository.selectById(ms.getOfferDepartmentId());
                if (dept != null) {
                    vo.setDepartmentName(dept.getName());
                }
            }
        }
        if (en.getUserId() != null) {
            User user = userRepository.selectById(en.getUserId());
            if (user != null) vo.setUserName(user.getRealName());
        }
        return vo;
    }
}
