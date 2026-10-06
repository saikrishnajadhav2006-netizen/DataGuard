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
    
    private Integer qualityScore;
    private LocalDateTime reviewDate;
    
    @PrePersist
    public void prePersist() {
        this.reviewDate = LocalDateTime.now();
    }

    public Review() {}

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return this.project; }
    public void setProject(Project project) { this.project = project; }

    public Integer getQualityScore() { return this.qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }

    public LocalDateTime getReviewDate() { return this.reviewDate; }
    public void setReviewDate(LocalDateTime reviewDate) { this.reviewDate = reviewDate; }
}
