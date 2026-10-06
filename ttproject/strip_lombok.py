import os
import re

def process_java_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # If not using lombok, skip
    if 'lombok' not in content:
        return
        
    # Remove lombok imports
    content = re.sub(r'import lombok\.[^;]+;\n', '', content)
    
    # Remove annotations
    content = re.sub(r'@Data\s*\n', '', content)
    content = re.sub(r'@NoArgsConstructor\s*\n', '', content)
    content = re.sub(r'@AllArgsConstructor\s*\n', '', content)
    content = re.sub(r'@RequiredArgsConstructor\s*\n', '', content)
    
    # We will let the IDE or a simple replacement handle constructors where needed.
    # Actually, for entities, an empty constructor is default if none is provided.
    # But wait, @RequiredArgsConstructor generates a constructor for `final` fields. 
    # Let's handle classes with `final` fields manually, or inject a constructor.
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# We will just use `sed` or simple script for specific files that need getters/setters.
