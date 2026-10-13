package com.dataguard.agent;

import com.dataguard.analyzer.CodeQualityAnalyzer;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
public class CodeQualityAgent implements ReviewAgent {
    private final CodeQualityAnalyzer analyzer;

    public CodeQualityAgent(CodeQualityAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public String name() {
        return "code-quality";
    }

    @Override
    public List<Finding> analyze(File projectDirectory, Review review) {
        return analyzer.analyze(projectDirectory, review);
    }
}
