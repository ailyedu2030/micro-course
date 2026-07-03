package com.microcourse.service;

public interface EnrollmentStatsService {

    long countByTeacherId(Long teacherId);

    long countCompletedByTeacherId(Long teacherId);

    double getAvgScoreByTeacherId(Long teacherId);
}
