package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.CourseSection;
import com.microcourse.entity.Exercise;
import com.microcourse.entity.ExerciseQuestion;
import com.microcourse.entity.Video;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.ExerciseQuestionRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.CourseCopyContentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 课程复制 · 章节内容复制实现。
 *
 * <p>2026-08-04 二轮审查修复：</p>
 * <ul>
 *   <li>P0：复制视频未赋值 videos.original_name（NOT NULL）→ INSERT 约束违反
 *       → 整个复制课程事务 409 回滚，含视频的课程复制 100% 失败。</li>
 *   <li>P1-C：原复制只处理章节+视频，课时（sections）/练习（含题目关联）全部丢失，
 *       新课程无法学习。</li>
 * </ul>
 */
@Service
public class CourseCopyContentServiceImpl implements CourseCopyContentService {

    private final VideoRepository videoRepository;
    private final CourseSectionRepository sectionRepository;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseQuestionRepository exerciseQuestionRepository;

    public CourseCopyContentServiceImpl(VideoRepository videoRepository,
                                        CourseSectionRepository sectionRepository,
                                        ExerciseRepository exerciseRepository,
                                        ExerciseQuestionRepository exerciseQuestionRepository) {
        this.videoRepository = videoRepository;
        this.sectionRepository = sectionRepository;
        this.exerciseRepository = exerciseRepository;
        this.exerciseQuestionRepository = exerciseQuestionRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyChapterContent(Long originalChapterId, Long newChapterId, Long newCourseId) {
        // 视频（仅元数据，不复制实际视频文件；original_name 必填）
        List<Video> videos = videoRepository.selectList(
                new LambdaQueryWrapper<Video>()
                        .eq(Video::getChapterId, originalChapterId)
                        .isNull(Video::getDeletedAt));
        for (Video v : videos) {
            Video copyV = new Video();
            copyV.setChapterId(newChapterId);
            copyV.setCourseId(newCourseId);
            copyV.setTitle(v.getTitle());
            copyV.setSortOrder(v.getSortOrder());
            copyV.setDuration(v.getDuration());
            copyV.setFileName(v.getFileName() != null ? v.getFileName()
                    : (v.getTitle() != null ? v.getTitle() : "copied") + ".mp4");
            videoRepository.insert(copyV);
        }

        // 课时（sections）—— 课程核心结构，缺失则新课程无法学习
        List<CourseSection> sections = sectionRepository.selectList(
                new LambdaQueryWrapper<CourseSection>()
                        .eq(CourseSection::getChapterId, originalChapterId)
                        .isNull(CourseSection::getDeletedAt));
        for (CourseSection s : sections) {
            CourseSection copyS = new CourseSection();
            copyS.setChapterId(newChapterId);
            copyS.setCourseId(newCourseId);
            copyS.setTitle(s.getTitle());
            copyS.setSectionType(s.getSectionType());
            copyS.setSortOrder(s.getSortOrder());
            copyS.setDuration(s.getDuration());
            copyS.setVisible(s.getVisible());
            copyS.setDescription(s.getDescription());
            copyS.setScriptContent(s.getScriptContent());
            copyS.setContentUrl(s.getContentUrl());
            copyS.setNo(s.getNo());
            copyS.setLearningObjectives(s.getLearningObjectives());
            copyS.setAnchorScenarioStep(s.getAnchorScenarioStep());
            copyS.setCoreCompetency(s.getCoreCompetency());
            copyS.setCoursewareType(s.getCoursewareType());
            copyS.setAudioStrategy(s.getAudioStrategy());
            sectionRepository.insert(copyS);
        }

        // 练习/考试（含题目关联；题库题目全局共享，questionId 直接复用）
        List<Exercise> exercises = exerciseRepository.selectList(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getChapterId, originalChapterId)
                        .isNull(Exercise::getDeletedAt));
        for (Exercise ex : exercises) {
            Exercise copyEx = new Exercise();
            copyEx.setChapterId(newChapterId);
            copyEx.setCourseId(newCourseId);
            copyEx.setTitle(ex.getTitle());
            copyEx.setDescription(ex.getDescription());
            copyEx.setPassScore(ex.getPassScore());
            copyEx.setTimeLimit(ex.getTimeLimit());
            copyEx.setMaxAttempts(ex.getMaxAttempts());
            copyEx.setShowAnswerWhen(ex.getShowAnswerWhen());
            copyEx.setShuffleQuestions(ex.getShuffleQuestions());
            copyEx.setShuffleOptions(ex.getShuffleOptions());
            copyEx.setTotalScore(ex.getTotalScore());
            copyEx.setQuestionCount(ex.getQuestionCount());
            copyEx.setIsExam(ex.getIsExam());
            exerciseRepository.insert(copyEx);

            List<ExerciseQuestion> eqList = exerciseQuestionRepository.selectList(
                    new LambdaQueryWrapper<ExerciseQuestion>()
                            .eq(ExerciseQuestion::getExerciseId, ex.getId()));
            for (ExerciseQuestion eq : eqList) {
                ExerciseQuestion copyEq = new ExerciseQuestion();
                copyEq.setExerciseId(copyEx.getId());
                copyEq.setQuestionId(eq.getQuestionId());
                copyEq.setScore(eq.getScore());
                copyEq.setSortOrder(eq.getSortOrder());
                exerciseQuestionRepository.insert(copyEq);
            }
        }
    }
}
