package com.microcourse.plugin.interactive.service;

import com.microcourse.plugin.interactive.dto.*;
import com.microcourse.plugin.interactive.entity.*;

public interface SectionResourceService {
    SectionQuiz createQuiz(Long courseId, Long sectionId, CreateQuizRequest request);
    SectionTask createTask(Long courseId, Long sectionId, CreateTaskRequest request);
    SectionReflection createReflection(Long courseId, Long sectionId, CreateReflectionRequest request);
}
