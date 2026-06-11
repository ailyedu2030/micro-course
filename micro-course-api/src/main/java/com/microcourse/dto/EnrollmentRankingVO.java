package com.microcourse.dto;

public class EnrollmentRankingVO {

    private Integer rank;
    private Long userId;
    private String userName;
    private Double progress;
    private Boolean completed;
    private Boolean isCurrentUser;

    public EnrollmentRankingVO() {}

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }
    public Boolean getCompleted() { return completed; }
    public void setCompleted(Boolean completed) { this.completed = completed; }
    public Boolean getIsCurrentUser() { return isCurrentUser; }
    public void setIsCurrentUser(Boolean isCurrentUser) { this.isCurrentUser = isCurrentUser; }
}
