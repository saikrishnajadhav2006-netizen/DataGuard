package com.dataguard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/** Durable state for a resumable GitHub changed-file review. */
@Entity
@Table(name = "github_review_progress")
public class GitHubReviewProgress {
    @Id private String id;
    @Column(nullable = false, unique = true, length = 500) private String idempotencyKey;
    @Column(nullable = false) private String eventType;
    @Column(nullable = false) private String owner;
    @Column(nullable = false) private String repository;
    private String branch;
    private String baseSha;
    @Column(nullable = false) private String commitSha;
    private Integer pullRequestNumber;
    @Column(nullable = false, columnDefinition = "TEXT") private String filesJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String skippedJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String batchResultsJson;
    @Column(nullable = false) private int nextBatchIndex;
    @Column(nullable = false, length = 40) private String status;
    private Long activeCheckRunId;
    private Long parentReviewId;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version private long version;

    public GitHubReviewProgress() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getRepository() { return repository; }
    public void setRepository(String repository) { this.repository = repository; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getBaseSha() { return baseSha; }
    public void setBaseSha(String baseSha) { this.baseSha = baseSha; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public Integer getPullRequestNumber() { return pullRequestNumber; }
    public void setPullRequestNumber(Integer pullRequestNumber) { this.pullRequestNumber = pullRequestNumber; }
    public String getFilesJson() { return filesJson; }
    public void setFilesJson(String filesJson) { this.filesJson = filesJson; }
    public String getSkippedJson() { return skippedJson; }
    public void setSkippedJson(String skippedJson) { this.skippedJson = skippedJson; }
    public String getBatchResultsJson() { return batchResultsJson; }
    public void setBatchResultsJson(String batchResultsJson) { this.batchResultsJson = batchResultsJson; }
    public int getNextBatchIndex() { return nextBatchIndex; }
    public void setNextBatchIndex(int nextBatchIndex) { this.nextBatchIndex = nextBatchIndex; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getActiveCheckRunId() { return activeCheckRunId; }
    public void setActiveCheckRunId(Long activeCheckRunId) { this.activeCheckRunId = activeCheckRunId; }
    public Long getParentReviewId() { return parentReviewId; }
    public void setParentReviewId(Long parentReviewId) { this.parentReviewId = parentReviewId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public long getVersion() { return version; }
}
