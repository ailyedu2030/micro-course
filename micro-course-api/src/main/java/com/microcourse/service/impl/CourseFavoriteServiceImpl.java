package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.CourseFavoriteVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseFavorite;
import com.microcourse.repository.CourseFavoriteRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.service.CourseFavoriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CourseFavoriteServiceImpl implements CourseFavoriteService {

    private final CourseFavoriteRepository favoriteRepository;
    private final CourseRepository courseRepository;

    public CourseFavoriteServiceImpl(CourseFavoriteRepository favoriteRepository,
                                     CourseRepository courseRepository) {
        this.favoriteRepository = favoriteRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    @Transactional
    public void favorite(Long userId, Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            return;
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
    @Transactional
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
               .orderByDesc(CourseFavorite::getCreatedAt);
        List<CourseFavorite> favorites = favoriteRepository.selectList(wrapper);

        return favorites.stream().map(fav -> {
            CourseFavoriteVO vo = new CourseFavoriteVO();
            vo.setId(fav.getId());
            vo.setUserId(fav.getUserId());
            vo.setCourseId(fav.getCourseId());
            vo.setCreatedAt(fav.getCreatedAt());

            Course course = courseRepository.selectById(fav.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseFavoriteVO> listAll() {
        List<CourseFavorite> favorites = favoriteRepository.selectList(null);
        return favorites.stream().map(fav -> {
            CourseFavoriteVO vo = new CourseFavoriteVO();
            vo.setId(fav.getId());
            vo.setUserId(fav.getUserId());
            vo.setCourseId(fav.getCourseId());
            vo.setCreatedAt(fav.getCreatedAt());

            Course course = courseRepository.selectById(fav.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
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
