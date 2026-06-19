package com.microcourse.dto;

/**
 * 数据看板概览 VO
 */
public class DashboardOverviewVO {

    private Long totalUsers;
    private Long activeUsers7d;
    private Long totalCourses;
    private Long publishedCourses;
    private Long totalEnrollments;
    private Long totalVideos;
    private Long totalExercises;
    private Long totalDiscussions;
    private Long totalWatchTimeMinutes;
    private Long certificatesIssued;

    public DashboardOverviewVO() {}

    public Long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(Long totalUsers) { this.totalUsers = totalUsers; }
    public Long getActiveUsers7d() { return activeUsers7d; }
    public void setActiveUsers7d(Long activeUsers7d) { this.activeUsers7d = activeUsers7d; }
    public Long getTotalCourses() { return totalCourses; }
    public void setTotalCourses(Long totalCourses) { this.totalCourses = totalCourses; }
    public Long getPublishedCourses() { return publishedCourses; }
    public void setPublishedCourses(Long publishedCourses) { this.publishedCourses = publishedCourses; }
    public Long getTotalEnrollments() { return totalEnrollments; }
    public void setTotalEnrollments(Long totalEnrollments) { this.totalEnrollments = totalEnrollments; }
    public Long getTotalVideos() { return totalVideos; }
    public void setTotalVideos(Long totalVideos) { this.totalVideos = totalVideos; }
    public Long getTotalExercises() { return totalExercises; }
    public void setTotalExercises(Long totalExercises) { this.totalExercises = totalExercises; }
    public Long getTotalDiscussions() { return totalDiscussions; }
    public void setTotalDiscussions(Long totalDiscussions) { this.totalDiscussions = totalDiscussions; }
    public Long getTotalWatchTimeMinutes() { return totalWatchTimeMinutes; }
    public void setTotalWatchTimeMinutes(Long totalWatchTimeMinutes) { this.totalWatchTimeMinutes = totalWatchTimeMinutes; }
    public Long getCertificatesIssued() { return certificatesIssued; }
    public void setCertificatesIssued(Long certificatesIssued) { this.certificatesIssued = certificatesIssued; }
}