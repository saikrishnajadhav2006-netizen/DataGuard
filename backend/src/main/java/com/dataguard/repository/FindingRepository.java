package com.dataguard.repository;

import com.dataguard.entity.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByReviewId(Long reviewId);

    void deleteByReviewId(Long reviewId);
}
