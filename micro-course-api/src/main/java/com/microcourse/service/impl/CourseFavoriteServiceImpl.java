package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.dto.PageResult;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseFavorite;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseFavoriteRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.CourseFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CourseFavoriteServiceImpl implements CourseFavoriteService {

    private final CourseFavoriteRepository favoriteRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseFavoriteServiceImpl(CourseFavoriteRepository favoriteRepository,
                                     CourseRepository courseRepository,
                                     UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favorite(Long userId, Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }

        LambdaQueryWrapper<CourseFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseFavorite::getUserId, userId)
               .eq(CourseFavorite::getCourseId, courseId);
        long count = favoriteRepository.selectCount(wrapper);
        if (count > 0) {
            return;
        }

        CourseFavorite favorite = new CourseFavorite();
        favorite.setUserId(userId);
        favorite.setCourseId(courseId);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteRepository.insert(favorite);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavorite(Long userId, Long courseId) {
        LambdaQueryWrapper<CourseFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseFavorite::getUserId, userId)
               .eq(CourseFavorite::getCourseId, courseId);
        favoriteRepository.delete(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseFavoriteVO> getMyFavorites(Long userId) {
        LambdaQueryWrapper<CourseFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseFavorite::getUserId, userId)
               .orderByDesc(CourseFavorite::getCreatedAt)
               .last("LIMIT 200"); // 防止无界增长
        List<CourseFavorite> favorites = favoriteRepository.selectList(wrapper);

        return buildVOs(favorites);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CourseFavoriteVO> listAll(int page, int size, String studentName, String courseName) {
        LambdaQueryWrapper<CourseFavorite> wrapper = new LambdaQueryWrapper<>();

        java.util.Set<Long> filterCourseIds = new java.util.HashSet<>();
        java.util.Set<Long> filterUserIds = new java.util.HashSet<>();

        if (courseName != null && !courseName.isEmpty()) {
            LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
            courseWrapper.like(Course::getTitle,
                    courseName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
                    .isNull(Course::getDeletedAt);
            courseRepository.selectList(courseWrapper).forEach(c -> filterCourseIds.add(c.getId()));
            if (filterCourseIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, page, size);
            }
            wrapper.in(CourseFavorite::getCourseId, filterCourseIds);
        }
        if (studentName != null && !studentName.isEmpty()) {
            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.like(User::getRealName,
                    studentName.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"));
            userRepository.selectList(userWrapper).forEach(u -> filterUserIds.add(u.getId()));
            if (filterUserIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), 0L, page, size);
            }
            wrapper.in(CourseFavorite::getUserId, filterUserIds);
        }

        wrapper.orderByDesc(CourseFavorite::getCreatedAt);

        IPage<CourseFavorite> favoritePage = favoriteRepository.selectPage(new Page<>(page + 1, size), wrapper);

        List<CourseFavoriteVO> vos = buildVOs(favoritePage.getRecords());
        return PageResult.of(vos, favoritePage.getTotal(), page, size);
    }

    private List<CourseFavoriteVO> buildVOs(List<CourseFavorite> favorites) {
        if (favorites.isEmpty()) return new ArrayList<>();

        java.util.Set<Long> courseIds = favorites.stream()
                .map(CourseFavorite::getCourseId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds).forEach(c -> courseMap.put(c.getId(), c));
        }

        java.util.Set<Long> userIds = favorites.stream()
                .map(CourseFavorite::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        java.util.Map<Long, User> userMap = new java.util.HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        return favorites.stream().map(fav -> {
            CourseFavoriteVO vo = new CourseFavoriteVO();
            vo.setId(fav.getId());
            vo.setUserId(fav.getUserId());
            vo.setCourseId(fav.getCourseId());
            vo.setCreatedAt(fav.getCreatedAt());

            Course course = courseMap.get(fav.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
                vo.setCourseName(course.getTitle());
                vo.setCoverUrl(course.getCoverUrl());
            }
            User teacher = course != null ? userMap.get(course.getTeacherId()) : null;
            if (teacher != null) {
                vo.setTeacherName(teacher.getRealName());
            }
            User student = userMap.get(fav.getUserId());
            if (student != null) {
                vo.setStudentName(student.getRealName() != null ? student.getRealName() : student.getUsername());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorited(Long userId, Long courseId) {
        LambdaQueryWrapper<CourseFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseFavorite::getUserId, userId)
               .eq(CourseFavorite::getCourseId, courseId);
        long count = favoriteRepository.selectCount(wrapper);
        return count > 0;
    }
}
