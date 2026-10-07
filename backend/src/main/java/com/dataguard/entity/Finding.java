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

    /** e.g. CODE_QUALITY, SECURITY, ARCHITECTURE */
    private String category;

    /** CRITICAL | HIGH | MEDIUM | LOW | INFO */
    private String severity;

    /** Which analyzer produced this finding: CODE_QUALITY, SEMGREP, ARCHITECTURE, AI */
    private String source;

    /** Rule or check ID, e.g. dataguard-java-system-out */
    private String ruleId;

    private String title;

    @Column(length = 2000)
    private String description;

    private String filePath;
    private Integer lineNumber;

    @Column(length = 2000)
    private String evidence;

    @Column(length = 2000)
    private String recommendation;

    /** OPEN | RESOLVED */
    private String status;

    public Finding() {}

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public Review getReview() { return this.review; }
    public void setReview(Review review) { this.review = review; }

    public String getCategory() { return this.category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return this.severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSource() { return this.source; }
    public void setSource(String source) { this.source = source; }

    public String getRuleId() { return this.ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getTitle() { return this.title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return this.description; }
    public void setDescription(String description) { this.description = description; }

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

