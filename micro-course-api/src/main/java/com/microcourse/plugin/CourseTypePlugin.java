package com.microcourse.plugin;

public interface CourseTypePlugin {

    String getType();

    String getDisplayName();

    boolean isEnabled();

    String getPlayerRoute(Long courseId);

    String getTeacherPanelRoute(Long courseId);

    String getEditorComponentName();

    String getPropertiesComponentName();
}
