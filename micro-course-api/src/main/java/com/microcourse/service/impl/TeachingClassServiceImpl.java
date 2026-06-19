package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.*;
import com.microcourse.entity.ClassSchedule;
import com.microcourse.entity.Course;
import com.microcourse.entity.TeachingClass;
import com.microcourse.entity.TeachingClassStudent;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassScheduleRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.TeachingClassRepository;
import com.microcourse.repository.TeachingClassStudentRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.TeachingClassService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeachingClassServiceImpl implements TeachingClassService {

    private final TeachingClassRepository teachingClassRepository;
    private final TeachingClassStudentRepository teachingClassStudentRepository;
    private final ClassScheduleRepository classScheduleRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public TeachingClassServiceImpl(TeachingClassRepository teachingClassRepository,
                                   TeachingClassStudentRepository teachingClassStudentRepository,
                                   ClassScheduleRepository classScheduleRepository,
                                   CourseRepository courseRepository,
                                   UserRepository userRepository) {
        this.teachingClassRepository = teachingClassRepository;
        this.teachingClassStudentRepository = teachingClassStudentRepository;
        this.classScheduleRepository = classScheduleRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PageResult<TeachingClassVO> page(int page, int size, Long teacherId, Long courseId,
                                           String semester, Integer status) {
        Page<TeachingClass> ipage = new Page<>(page, size);
        // MyBatis-Plus Page 是 1-based, Controller 传 0-based, 已在 Controller 层转换
        LambdaQueryWrapper<TeachingClass> wrapper = new LambdaQueryWrapper<>();

        if (teacherId != null) {
            wrapper.eq(TeachingClass::getTeacherId, teacherId);
        }
        if (courseId != null) {
            wrapper.eq(TeachingClass::getCourseId, courseId);
        }
        if (semester != null && !semester.isBlank()) {
            wrapper.eq(TeachingClass::getSemester, semester);
        }
        if (status != null) {
            wrapper.eq(TeachingClass::getStatus, status);
        }
        wrapper.orderByDesc(TeachingClass::getCreatedAt);

        IPage<TeachingClass> result = teachingClassRepository.selectPage(ipage, wrapper);
        List<TeachingClassVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        PageResult<TeachingClassVO> pageResult = new PageResult<>();
        pageResult.setItems(voList);
        pageResult.setPage((int) result.getCurrent());
        pageResult.setSize((int) result.getSize());
        pageResult.setTotalElements(result.getTotal());
        pageResult.setTotalPages(result.getPages());
        return pageResult;
    }

    @Override
    public TeachingClassVO getById(Long id) {
        TeachingClass tc = teachingClassRepository.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        return convertToVO(tc);
    }

    @Override
    @Transactional
    public TeachingClassVO create(TeachingClassCreateRequest req) {
        Course course = courseRepository.selectById(req.getCourseId());
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        if (req.getTeacherId() != null) {
            User teacher = userRepository.selectById(req.getTeacherId());
            if (teacher == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            if (teacher.getRole() != UserRole.TEACHER) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }

        TeachingClass tc = new TeachingClass();
        tc.setCourseId(req.getCourseId());
        tc.setTeacherId(req.getTeacherId());
        tc.setName(req.getName());
        tc.setMaxStudents(req.getMaxStudents());
        tc.setStudentCount(0);
        tc.setSchedule(req.getSchedule());
        tc.setLocation(req.getLocation());
        tc.setSemester(req.getSemester());
        tc.setStatus(0);
        tc.setCreatedAt(LocalDateTime.now());
        tc.setUpdatedAt(LocalDateTime.now());
        tc.setVersion(0);
        teachingClassRepository.insert(tc);

        if (req.getClassSchedules() != null && !req.getClassSchedules().isEmpty()) {
            for (ClassScheduleDTO dto : req.getClassSchedules()) {
                ClassSchedule cs = new ClassSchedule();
                cs.setClassId(tc.getId());
                cs.setDayOfWeek(dto.getDayOfWeek());
                cs.setStartPeriod(dto.getStartPeriod());
                cs.setEndPeriod(dto.getEndPeriod());
                cs.setStartTime(dto.getStartTime());
                cs.setEndTime(dto.getEndTime());
                cs.setLocation(dto.getLocation());
                cs.setWeekPattern(dto.getWeekPattern());
                cs.setCustomWeeks(dto.getCustomWeeks());
                classScheduleRepository.insert(cs);
            }
        }

        return convertToVO(tc);
    }

    @Override
    @Transactional
    public TeachingClassVO update(Long id, TeachingClassUpdateRequest req) {
        TeachingClass tc = teachingClassRepository.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }

        if (req.getCourseId() != null) {
            tc.setCourseId(req.getCourseId());
        }
        if (req.getTeacherId() != null) {
            tc.setTeacherId(req.getTeacherId());
        }
        if (req.getName() != null) {
            tc.setName(req.getName());
        }
        if (req.getMaxStudents() != null) {
            tc.setMaxStudents(req.getMaxStudents());
        }
        if (req.getSchedule() != null) {
            tc.setSchedule(req.getSchedule());
        }
        if (req.getLocation() != null) {
            tc.setLocation(req.getLocation());
        }
        if (req.getSemester() != null) {
            tc.setSemester(req.getSemester());
        }
        if (req.getStatus() != null) {
            tc.setStatus(req.getStatus());
        }
        tc.setUpdatedAt(LocalDateTime.now());
        teachingClassRepository.updateById(tc);

        if (req.getClassSchedules() != null) {
            classScheduleRepository.deleteByClassId(id);
            for (ClassScheduleDTO dto : req.getClassSchedules()) {
                ClassSchedule cs = new ClassSchedule();
                cs.setClassId(id);
                cs.setDayOfWeek(dto.getDayOfWeek());
                cs.setStartPeriod(dto.getStartPeriod());
                cs.setEndPeriod(dto.getEndPeriod());
                cs.setStartTime(dto.getStartTime());
                cs.setEndTime(dto.getEndTime());
                cs.setLocation(dto.getLocation());
                cs.setWeekPattern(dto.getWeekPattern());
                cs.setCustomWeeks(dto.getCustomWeeks());
                classScheduleRepository.insert(cs);
            }
        }

        return convertToVO(tc);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TeachingClass tc = teachingClassRepository.selectById(id);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        classScheduleRepository.deleteByClassId(id);
        teachingClassRepository.deleteById(id);
    }

    @Override
    public List<TeachingClassStudentVO> getClassStudents(Long classId) {
        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }

        List<TeachingClassStudent> students = teachingClassStudentRepository.selectActiveByClassId(classId);
        if (students.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> userIds = students.stream()
                .map(TeachingClassStudent::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return students.stream().map(s -> {
            TeachingClassStudentVO vo = new TeachingClassStudentVO();
            vo.setId(s.getId());
            vo.setClassId(s.getClassId());
            vo.setUserId(s.getUserId());
            vo.setEnrolledAt(s.getEnrolledAt());
            vo.setStatus(s.getStatus());
            vo.setClassName(tc.getName());
            User user = userMap.get(s.getUserId());
            if (user != null) {
                vo.setRealName(user.getRealName());
                vo.setStudentNo(user.getStudentNo());
                vo.setAvatar(user.getAvatar());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addStudent(Long classId, Long userId) {
        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }

        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (teachingClassStudentRepository.existsByClassIdAndUserId(classId, userId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
        }

        if (tc.getMaxStudents() != null && tc.getMaxStudents() > 0
                && tc.getStudentCount() != null && tc.getStudentCount() >= tc.getMaxStudents()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "教学班已达最大容量");
        }

        TeachingClassStudent record = new TeachingClassStudent();
        record.setClassId(classId);
        record.setUserId(userId);
        record.setEnrolledAt(LocalDateTime.now());
        record.setStatus("ENROLLED");
        teachingClassStudentRepository.insert(record);

        tc.setStudentCount(tc.getStudentCount() == null ? 1 : tc.getStudentCount() + 1);
        tc.setUpdatedAt(LocalDateTime.now());
        teachingClassRepository.updateById(tc);
    }

    @Override
    @Transactional
    public void removeStudent(Long classId, Long userId) {
        TeachingClassStudent record = teachingClassStudentRepository.selectList(
                new LambdaQueryWrapper<TeachingClassStudent>()
                        .eq(TeachingClassStudent::getClassId, classId)
                        .eq(TeachingClassStudent::getUserId, userId)
        ).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        record.setStatus("DROPPED");
        teachingClassStudentRepository.updateById(record);

        TeachingClass tc = teachingClassRepository.selectById(classId);
        if (tc != null && tc.getStudentCount() != null && tc.getStudentCount() > 0) {
            tc.setStudentCount(tc.getStudentCount() - 1);
            tc.setUpdatedAt(LocalDateTime.now());
            teachingClassRepository.updateById(tc);
        }
    }

    @Override
    @Transactional
    public void updateStudentStatus(Long classId, Long userId, String status) {
        TeachingClassStudent record = teachingClassStudentRepository.selectList(
                new LambdaQueryWrapper<TeachingClassStudent>()
                        .eq(TeachingClassStudent::getClassId, classId)
                        .eq(TeachingClassStudent::getUserId, userId)
        ).stream().findFirst().orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        record.setStatus(status);
        teachingClassStudentRepository.updateById(record);
    }

    private TeachingClassVO convertToVO(TeachingClass tc) {
        TeachingClassVO vo = new TeachingClassVO();
        vo.setId(tc.getId());
        vo.setCourseId(tc.getCourseId());
        vo.setTeacherId(tc.getTeacherId());
        vo.setName(tc.getName());
        vo.setMaxStudents(tc.getMaxStudents());
        vo.setStudentCount(tc.getStudentCount());
        vo.setSchedule(tc.getSchedule());
        vo.setLocation(tc.getLocation());
        vo.setSemester(tc.getSemester());
        vo.setStatus(tc.getStatus());
        vo.setStatusLabel(resolveStatusLabel(tc.getStatus()));
        vo.setCreatedAt(tc.getCreatedAt());
        vo.setUpdatedAt(tc.getUpdatedAt());
        vo.setVersion(tc.getVersion());

        if (tc.getCourseId() != null) {
            Course course = courseRepository.selectById(tc.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
                vo.setCourseCoverUrl(course.getCoverUrl());
            }
        }

        if (tc.getTeacherId() != null) {
            User teacher = userRepository.selectById(tc.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
        }

        return vo;
    }

    private String resolveStatusLabel(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待开课";
            case 1 -> "进行中";
            case 2 -> "已结课";
            default -> "未知";
        };
    }
}