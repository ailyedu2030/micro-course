package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.ClassCreateRequest;
import com.microcourse.dto.ClassStudentVO;
import com.microcourse.dto.ClassUpdateRequest;
import com.microcourse.dto.ClassVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.ClassService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassServiceImpl implements ClassService {

    private final ClassesRepository classesRepository;
    private final MajorRepository majorRepository;
    private final UserRepository userRepository;

    public ClassServiceImpl(ClassesRepository classesRepository,
                           MajorRepository majorRepository,
                           UserRepository userRepository) {
        this.classesRepository = classesRepository;
        this.majorRepository = majorRepository;
        this.userRepository = userRepository;
    }

    @Override
    public PageResult<ClassVO> page(int page, int size) {
        IPage<Classes> ipage = classesRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<Classes>()
                        .orderByAsc(Classes::getSortOrder)
        );
        PageResult<ClassVO> result = new PageResult<>();
        result.setItems(ipage.getRecords().stream().map(this::convertToVO).toList());
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    public ClassVO getById(Long id) {
        Classes classes = classesRepository.selectById(id);
        if (classes == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        return convertToVO(classes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassVO create(ClassCreateRequest request) {
        Major major = majorRepository.selectById(request.getMajorId());
        if (major == null) {
            throw new BusinessException(ErrorCode.MAJOR_NOT_FOUND);
        }
        if (request.getCounselorId() != null) {
            User counselor = userRepository.selectById(request.getCounselorId());
            if (counselor == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            if (counselor.getRole() != UserRole.TEACHER) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }
        // 检查班级名称唯一性
        if (request.getName() != null && !request.getName().isBlank()) {
            long nameCount = classesRepository.selectCount(
                    new LambdaQueryWrapper<Classes>().eq(Classes::getName, request.getName()));
            if (nameCount > 0) {
                throw new BusinessException(ErrorCode.CLASS_NAME_EXISTS);
            }
        }
        Classes classes = new Classes();
        classes.setName(request.getName());
        classes.setMajorId(request.getMajorId());
        classes.setGrade(request.getGrade());
        classes.setCounselorId(request.getCounselorId());
        classes.setSortOrder(request.getSortOrder());
        classes.setCreatedAt(LocalDateTime.now());
        classes.setUpdatedAt(LocalDateTime.now());
        classesRepository.insert(classes);
        return convertToVO(classes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassVO update(Long id, ClassUpdateRequest request) {
        Classes classes = classesRepository.selectById(id);
        if (classes == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        if (request.getName() != null) {
            classes.setName(request.getName());
        }
        if (request.getMajorId() != null) {
            classes.setMajorId(request.getMajorId());
        }
        if (request.getGrade() != null) {
            classes.setGrade(request.getGrade());
        }
        if (request.getCounselorId() != null) {
            classes.setCounselorId(request.getCounselorId());
        }
        if (request.getSortOrder() != null) {
            classes.setSortOrder(request.getSortOrder());
        }
        classes.setUpdatedAt(LocalDateTime.now());
        classesRepository.updateById(classes);
        return convertToVO(classes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Classes classes = classesRepository.selectById(id);
        if (classes == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        long count = userRepository.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getClassId, id)
                        .ne(User::getStatus, 3)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.CLASS_HAS_STUDENTS);
        }
        classesRepository.deleteById(id);
    }

    /**
     * Round 5-3 (P1-10): 获取班级学生名单。
     *
     * <p>students = {@code users} 表中 {@code class_id = classId} 且未被删除（status != 3）的用户。
     * 班级不存在抛 {@link ErrorCode#CLASS_NOT_FOUND}（404）。复用既有 userRepository，零新表/列。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<ClassStudentVO> getStudents(Long classId) {
        Classes classes = classesRepository.selectById(classId);
        if (classes == null) {
            throw new BusinessException(ErrorCode.CLASS_NOT_FOUND);
        }
        List<User> users = userRepository.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getClassId, classId)
                        .ne(User::getStatus, 3)
                        .orderByAsc(User::getStudentNo));
        return users.stream().map(u -> {
            ClassStudentVO vo = new ClassStudentVO();
            vo.setUserId(u.getId());
            vo.setClassId(classId);
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setStudentNo(u.getStudentNo());
            vo.setStatus(u.getStatus());
            return vo;
        }).collect(Collectors.toList());
    }

    private ClassVO convertToVO(Classes classes) {
        ClassVO vo = new ClassVO();
        vo.setId(classes.getId());
        vo.setName(classes.getName());
        vo.setMajorId(classes.getMajorId());
        vo.setGrade(classes.getGrade());
        vo.setCounselorId(classes.getCounselorId());
        vo.setSortOrder(classes.getSortOrder());
        vo.setCreatedAt(classes.getCreatedAt());

        Major major = majorRepository.selectById(classes.getMajorId());
        if (major != null) {
            vo.setMajorName(major.getName());
        }

        if (classes.getCounselorId() != null) {
            User counselor = userRepository.selectById(classes.getCounselorId());
            if (counselor != null) {
                vo.setCounselorName(counselor.getRealName());
            }
        }
        return vo;
    }
}
