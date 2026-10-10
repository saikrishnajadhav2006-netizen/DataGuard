package com.dataguard.repository;

import com.dataguard.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProjectId(Long projectId);

    @Query("select r from Review r where r.project.user.id = :userId order by r.reviewDate desc")
    List<Review> findAllForUser(@Param("userId") Long userId);
}
