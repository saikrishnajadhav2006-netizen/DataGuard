package com.dataguard.agent;

import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;

import java.io.File;
import java.util.List;

/** Shared contract for deterministic stages in the review pipeline. */
public interface ReviewAgent {
    String name();
    List<Finding> analyze(File projectDirectory, Review review);
}
