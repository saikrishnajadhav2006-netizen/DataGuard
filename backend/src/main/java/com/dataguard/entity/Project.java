package com.dataguard.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String technology;
    
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    private LocalDateTime createdAt;
    private Integer lastQualityScore;

    public Project() {}

    public Long getId() { return this.id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }

    public String getTechnology() { return this.technology; }
    public void setTechnology(String technology) { this.technology = technology; }

    public User getUser() { return this.user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getCreatedAt() { return this.createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getLastQualityScore() { return this.lastQualityScore; }
    public void setLastQualityScore(Integer lastQualityScore) { this.lastQualityScore = lastQualityScore; }
}
