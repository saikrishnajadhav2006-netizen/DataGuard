package com.dataguard.agent;

import com.dataguard.analyzer.SecurityAnalyzer;
import com.dataguard.analyzer.SemgrepAnalyzer;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Security checks, including Semgrep, are grouped under one agent. */
@Component
public class SecurityAgent implements ReviewAgent {
    private final SecurityAnalyzer securityAnalyzer;
    private final SemgrepAnalyzer semgrepAnalyzer;

    public SecurityAgent(SecurityAnalyzer securityAnalyzer, SemgrepAnalyzer semgrepAnalyzer) {
        this.securityAnalyzer = securityAnalyzer;
        this.semgrepAnalyzer = semgrepAnalyzer;
    }

    @Override
    public String name() {
        return "security";
    }

    @Override
    public List<Finding> analyze(File projectDirectory, Review review) {
        List<Finding> findings = new ArrayList<>(securityAnalyzer.analyze(projectDirectory, review));
        findings.addAll(semgrepAnalyzer.analyze(projectDirectory, review));
        return findings;
    }
}
