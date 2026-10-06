package com.dataguard.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_explanations")
public class AIExplanation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "finding_id")
    private Finding finding;
    
    @Column(columnDefinition = "TEXT")
    private String explanation;
    
    @Column(columnDefinition = "TEXT")
    private String impact;
    
    @Column(columnDefinition = "TEXT")
    private String recommendedAction;

    public AIExplanation() {}

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public Finding getFinding() { return this.finding; }
    public void setFinding(Finding finding) { this.finding = finding; }

    public String getExplanation() { return this.explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getImpact() { return this.impact; }
    public void setImpact(String impact) { this.impact = impact; }

    public String getRecommendedAction() { return this.recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
}
