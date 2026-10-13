package com.dataguard.agent;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;

import java.io.File;
import java.util.List;

/**
 * One deterministic stage in the shared review pipeline.
 * Agents return evidence-backed findings; orchestration, persistence and scoring
 * remain the responsibility of ReviewEngine.
 */
public interface ReviewAgent {
    String name();

    List<Finding> analyze(File projectDirectory, Review review);
}
