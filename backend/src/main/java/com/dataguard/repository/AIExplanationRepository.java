package com.dataguard.repository;

import com.dataguard.entity.AIExplanation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIExplanationRepository extends JpaRepository<AIExplanation, Long> {
    void deleteByFinding_Review_Id(Long reviewId);
}
