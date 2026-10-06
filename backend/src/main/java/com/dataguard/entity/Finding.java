package com.dataguard.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "findings")
public class Finding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "review_id")
    private Review review;
    
    private String category; // e.g. "CODE_QUALITY", "SECURITY", "ARCHITECTURE"
    private String severity; // e.g. "CRITICAL", "HIGH", "MEDIUM", "LOW"
    
    private String title;
    private String filePath;
    private Integer lineNumber;
    
    @Column(length = 2000)
    private String evidence;
    
    @Column(length = 2000)
    private String recommendation;
    
    private String status; // e.g. "OPEN", "RESOLVED"

    public Finding() {}

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public Review getReview() { return this.review; }
    public void setReview(Review review) { this.review = review; }

    public String getCategory() { return this.category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return this.severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getTitle() { return this.title; }
    public void setTitle(String title) { this.title = title; }

    public String getFilePath() { return this.filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Integer getLineNumber() { return this.lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public String getEvidence() { return this.evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getRecommendation() { return this.recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    public String getStatus() { return this.status; }
    public void setStatus(String status) { this.status = status; }
}
