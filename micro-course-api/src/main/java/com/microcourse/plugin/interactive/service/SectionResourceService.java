package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.*;
import com.microcourse.plugin.interactive.entity.*;

public interface SectionResourceService {
    QuizVO createQuiz(Long courseId, Long sectionId, CreateQuizRequest request);
    SectionTask createTask(Long courseId, Long sectionId, CreateTaskRequest request);
    SectionReflection createReflection(Long courseId, Long sectionId, CreateReflectionRequest request);
    CourseTraining createTraining(Long courseId, CreateTrainingRequest request);
    CourseFinalProject createFinalProject(Long courseId, CreateFinalProjectRequest request);
}
