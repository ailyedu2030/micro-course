package com.microcourse.plugin.interactive;

import com.microcourse.plugin.CourseTypePlugin;

public class InteractivePlugin implements CourseTypePlugin {

    @Override
    public String getType() { return "INTERACTIVE"; }

    @Override
    public String getDisplayName() { return "互动课程"; }

    @Override
    public boolean isEnabled() { return true; }

    @Override
    public String getPlayerRoute(Long courseId) {
        return "/student/courses/" + courseId + "/slides/player";
    }

    @Override
    public String getTeacherPanelRoute(Long courseId) {
        return "/teacher/courses/" + courseId + "/slides/manage";
    }

    @Override
    public String getEditorComponentName() { return "InteractiveLessonEditor"; }

    @Override
    public String getPropertiesComponentName() { return "InteractiveLessonProperties"; }
}
