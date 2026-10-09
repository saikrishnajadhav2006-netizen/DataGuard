package com.dataguard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id")
    private Project project;

    /** 0–100 composite score */
    private Integer overallScore;

    /** 0–100 security sub-score */
    private Integer securityScore;

    /** 0–100 code-quality sub-score */
    private Integer qualityScore;

    /** 0–100 architecture sub-score */
    private Integer architectureScore;

    /** PENDING | RUNNING | COMPLETED | FAILED */
    private String status;

    private LocalDateTime reviewDate;
    private LocalDateTime completedAt;

    @Transient
    private java.util.List<String> analysisWarnings = new java.util.ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.reviewDate = LocalDateTime.now();
        if (this.status == null) {
            this.status = "PENDING";
        }
    }

    public Review() {}

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return this.project; }
    public void setProject(Project project) { this.project = project; }

    public Integer getOverallScore() { return this.overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }

    public Integer getSecurityScore() { return this.securityScore; }
    public void setSecurityScore(Integer securityScore) { this.securityScore = securityScore; }

    public Integer getQualityScore() { return this.qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public Integer getArchitectureScore() { return this.architectureScore; }
    public void setArchitectureScore(Integer architectureScore) { this.architectureScore = architectureScore; }

    public String getStatus() { return this.status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getReviewDate() { return this.reviewDate; }
    public void setReviewDate(LocalDateTime reviewDate) { this.reviewDate = reviewDate; }

    public LocalDateTime getCompletedAt() { return this.completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public java.util.List<String> getAnalysisWarnings() { return analysisWarnings; }
    public void setAnalysisWarnings(java.util.List<String> analysisWarnings) {
        this.analysisWarnings = analysisWarnings == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(analysisWarnings);
    }
}

