package com.dataguard.agent;

import com.dataguard.analyzer.ArchitectureAnalyzer;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class ArchitectureAgent implements ReviewAgent {
    private final ArchitectureAnalyzer analyzer;

    public ArchitectureAgent(ArchitectureAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public String name() {
        return "architecture";
    }

    @Override
    public List<Finding> analyze(File projectDirectory, Review review) {
        return analyzer.analyze(projectDirectory, review);
    }
}
