package com.microcourse.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.BatchImportResultVO;
import com.microcourse.dto.UserBatchImportDTO;
import com.microcourse.entity.Classes;
import com.microcourse.entity.Department;
import com.microcourse.entity.Major;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.listener.UserBatchImportListener;
import com.microcourse.repository.ClassesRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MajorRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.UserBatchImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserBatchImportServiceImpl implements UserBatchImportService {

    private static final Logger log = LoggerFactory.getLogger(UserBatchImportServiceImpl.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final MajorRepository majorRepository;
    private final ClassesRepository classesRepository;
    private final UserBatchImportServiceImpl self;

    public UserBatchImportServiceImpl(UserRepository userRepository,
                                       BCryptPasswordEncoder passwordEncoder,
                                       DepartmentRepository departmentRepository,
                                       MajorRepository majorRepository,
                                       ClassesRepository classesRepository,
                                       @Lazy UserBatchImportServiceImpl self) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.majorRepository = majorRepository;
        this.classesRepository = classesRepository;
        this.self = self;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchImportResultVO batchImportUsers(MultipartFile file) {
        List<BatchImportResultVO.ImportErrorItem> errors = new ArrayList<>();

        UserBatchImportListener listener = new UserBatchImportListener();
        try {
            EasyExcel.read(file.getInputStream(), UserBatchImportDTO.class, listener).sheet().doRead();
        } catch (Exception e) {
            log.warn("[UserImport] Excel 解析失败", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "Excel 文件解析失败，请检查文件格式和内容是否正确", e);
        }

        errors.addAll(listener.getErrors());
        List<UserBatchImportDTO> rows = listener.getRows();

        if (rows.isEmpty() && errors.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件中无有效数据行");
        }

        if (rows.size() > 10000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM,
                    "批量导入最多支持 10000 条数据，当前文件含 " + rows.size() + " 行");
        }

        Map<String, Long> deptNameMap = buildDepartmentNameMap();
        Map<String, Long> majorNameMap = buildMajorNameMap();
        Map<String, Long> classNameMap = buildClassNameMap();

        Set<String> seenUsernames = new HashSet<>();
        Set<String> allUsernames = rows.stream()
                .map(UserBatchImportDTO::getUsername)
                .collect(Collectors.toSet());
        Set<String> existingUsernames = findExistingUsernames(allUsernames);

        List<User> usersToInsert = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            UserBatchImportDTO row = rows.get(i);
            int rowNum = i + 2;
            String username = row.getUsername();

            if (!seenUsernames.add(username)) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username, "用户名在批次中重复"));
                continue;
            }

            if (existingUsernames.contains(username)) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username, "用户名已存在"));
                continue;
            }

            User user = buildUserFromRow(row, rowNum, deptNameMap, majorNameMap, classNameMap, errors);
            if (user != null) {
                usersToInsert.add(user);
            }
        }

        if (!errors.isEmpty()) {
            for (User u : usersToInsert) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        0, u.getUsername(), "批次存在错误行，整体回滚"));
            }
            BatchImportResultVO result = new BatchImportResultVO();
            result.setSuccessCount(0);
            result.setFailCount(errors.size());
            result.setErrors(errors);
            return result;
        }

        int total = usersToInsert.size();
        int successCount = 0;
        int failedCount = errors.size();

        final int batchSize = 100;
        for (int i = 0; i < total; i += batchSize) {
            int end = Math.min(i + batchSize, total);
            List<User> chunk = usersToInsert.subList(i, end);
            try {
                if (self != null) {
                    self.batchInsertUsersTransactional(chunk);
                } else {
                    batchInsertUsersTransactional(chunk);
                }
                successCount += chunk.size();
            } catch (Exception e) {
                failedCount += chunk.size();
                log.error("[UserImport] 批量入库失败 range=[{}-{}), 原因={}", i, end, e.getMessage(), e);
                for (User u : chunk) {
                    errors.add(new BatchImportResultVO.ImportErrorItem(
                            0, u.getUsername(), "入库失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())));
                }
            }
        }

        BatchImportResultVO result = new BatchImportResultVO();
        result.setSuccessCount(successCount);
        result.setFailCount(failedCount);
        result.setErrors(errors);
        return result;
    }

    private Map<String, Long> buildDepartmentNameMap() {
        List<Department> all = departmentRepository.selectList(
                new LambdaQueryWrapper<Department>().last("LIMIT 10000"));
        Map<String, Long> map = new HashMap<>();
        for (Department d : all) {
            map.put(d.getName(), d.getId());
        }
        return map;
    }

    private Map<String, Long> buildMajorNameMap() {
        List<Major> all = majorRepository.selectList(
                new LambdaQueryWrapper<Major>().last("LIMIT 10000"));
        Map<String, Long> map = new HashMap<>();
        for (Major m : all) {
            map.put(m.getName(), m.getId());
        }
        return map;
    }

    private Map<String, Long> buildClassNameMap() {
        List<Classes> all = classesRepository.selectList(
                new LambdaQueryWrapper<Classes>().last("LIMIT 10000"));
        Map<String, Long> map = new HashMap<>();
        for (Classes c : all) {
            map.put(c.getName(), c.getId());
        }
        return map;
    }

    private Set<String> findExistingUsernames(Set<String> usernames) {
        if (usernames.isEmpty()) {
            return Set.of();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getUsername, usernames)
               .isNull(User::getDeletedAt)
               .select(User::getUsername);
        return userRepository.selectList(wrapper).stream()
                .map(User::getUsername)
                .collect(Collectors.toSet());
    }

    private User buildUserFromRow(UserBatchImportDTO row, int rowNum,
                                   Map<String, Long> deptNameMap,
                                   Map<String, Long> majorNameMap,
                                   Map<String, Long> classNameMap,
                                   List<BatchImportResultVO.ImportErrorItem> errors) {
        String username = row.getUsername();
        User user = new User();
        user.setUsername(username);
        user.setRealName(row.getRealName());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(row.getPassword()));

        if (row.getRole() != null && !row.getRole().trim().isEmpty()) {
            try {
                user.setRole(UserRole.valueOf(row.getRole().trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "角色 '" + row.getRole() + "' 不合法，应为 STUDENT/TEACHER/ADMIN/ACADEMIC"));
                return null;
            }
        } else {
            user.setRole(UserRole.STUDENT);
        }

        if (row.getDepartmentName() != null && !row.getDepartmentName().trim().isEmpty()) {
            Long deptId = deptNameMap.get(row.getDepartmentName().trim());
            if (deptId == null) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "院系 '" + row.getDepartmentName().trim() + "' 不存在"));
                return null;
            }
            user.setDepartmentId(deptId);
        }

        if (row.getMajorName() != null && !row.getMajorName().trim().isEmpty()) {
            Long majorId = majorNameMap.get(row.getMajorName().trim());
            if (majorId == null) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "专业 '" + row.getMajorName().trim() + "' 不存在"));
                return null;
            }
            user.setMajorId(majorId);
        }

        if (row.getClassName() != null && !row.getClassName().trim().isEmpty()) {
            Long classId = classNameMap.get(row.getClassName().trim());
            if (classId == null) {
                errors.add(new BatchImportResultVO.ImportErrorItem(
                        rowNum, username,
                        "班级 '" + row.getClassName().trim() + "' 不存在"));
                return null;
            }
            user.setClassId(classId);
        }

        return user;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    protected void batchInsertUsersTransactional(List<User> users) {
        if (users.isEmpty()) {
            return;
        }
        for (User user : users) {
            userRepository.insert(user);
        }
    }
}
