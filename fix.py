import os

def replace_in_file(path, old, new):
    with open(path, 'r') as f:
        c = f.read()
    with open(path, 'w') as f:
        f.write(c.replace(old, new))

replace_in_file('backend/src/main/java/com/dataguard/analyzer/PMDAnalyzer.java', 'f.setAnalyzer("PMD");', '')
replace_in_file('backend/src/main/java/com/dataguard/analyzer/DependencyCheckAnalyzer.java', 'f.setAnalyzer("OWASP Dependency-Check");', '')

# Fix ReviewEngine
re_path = 'backend/src/main/java/com/dataguard/service/ReviewEngine.java'
with open(re_path, 'r') as f:
    lines = f.readlines()
with open(re_path, 'w') as f:
    for line in lines:
        if 'semgrepAnalyzer' in line:
            continue
        f.write(line)
print('Fixed compilation errors')
