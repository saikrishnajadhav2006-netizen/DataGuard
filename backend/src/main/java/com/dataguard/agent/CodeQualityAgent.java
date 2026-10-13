package com.dataguard.agent;

import com.dataguard.analyzer.CodeQualityAnalyzer;
import com.dataguard.analyzer.PmdAnalyzer;
import com.dataguard.analyzer.PotentialBugAnalyzer;
import com.dataguard.analyzer.SyntaxAnalyzer;
import com.dataguard.entity.Finding;
import com.dataguard.entity.Review;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class CodeQualityAgent implements ReviewAgent {
    private final CodeQualityAnalyzer analyzer;
    private final SyntaxAnalyzer syntaxAnalyzer;
    private final PotentialBugAnalyzer potentialBugAnalyzer;
    private final PmdAnalyzer pmdAnalyzer;

    public CodeQualityAgent(CodeQualityAnalyzer analyzer, SyntaxAnalyzer syntaxAnalyzer,
                            PotentialBugAnalyzer potentialBugAnalyzer, PmdAnalyzer pmdAnalyzer) {
        this.analyzer = analyzer;
        this.syntaxAnalyzer = syntaxAnalyzer;
        this.potentialBugAnalyzer = potentialBugAnalyzer;
        this.pmdAnalyzer = pmdAnalyzer;
    }

    @Override
    public String name() {
        return "code-quality";
    }

    @Override
    public List<Finding> analyze(File projectDirectory, Review review) {
        List<Finding> findings = new ArrayList<>(syntaxAnalyzer.analyze(projectDirectory, review));
        findings.addAll(analyzer.analyze(projectDirectory, review));
        findings.addAll(potentialBugAnalyzer.analyze(projectDirectory, review));
        findings.addAll(pmdAnalyzer.analyze(projectDirectory, review));
        return findings;
    }
}
