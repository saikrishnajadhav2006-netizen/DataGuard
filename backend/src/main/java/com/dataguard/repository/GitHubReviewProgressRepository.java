package com.dataguard.repository;

import com.dataguard.entity.GitHubReviewProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GitHubReviewProgressRepository extends JpaRepository<GitHubReviewProgress, String> {
    Optional<GitHubReviewProgress> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GitHubReviewProgress p where p.activeCheckRunId = :checkRunId")
    Optional<GitHubReviewProgress> findByActiveCheckRunIdForUpdate(@Param("checkRunId") Long checkRunId);
}
