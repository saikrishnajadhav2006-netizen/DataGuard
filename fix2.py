import os

re_path = 'backend/src/main/java/com/dataguard/service/ReviewEngine.java'
with open(re_path, 'r') as f:
    content = f.read()

broken_block = """            // Semgrep security/static analysis
            List<Finding> semgrepFindings = runAnalyzer(
                    "Semgrep",
                            extractedDir,
                            currentReview
                    )
            );

            allFindings.addAll(semgrepFindings);"""

fixed_block = """            // PMD security/static analysis
            List<Finding> pmdFindings = runAnalyzer(
                    "PMD",
                    () -> pmdAnalyzer.analyze(extractedDir, currentReview)
            );
            allFindings.addAll(pmdFindings);

            // Dependency Check analysis
            List<Finding> depFindings = runAnalyzer(
                    "Dependency Check",
                    () -> dependencyCheckAnalyzer.analyze(extractedDir, currentReview)
            );
            allFindings.addAll(depFindings);"""

content = content.replace(broken_block, fixed_block)
with open(re_path, 'w') as f:
    f.write(content)
print('Fixed ReviewEngine lambda syntax')
