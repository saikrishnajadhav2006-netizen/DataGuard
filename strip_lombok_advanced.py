import os
import re

def to_camel_case(snake_str):
    components = snake_str.split('_')
    return components[0] + ''.join(x.title() for x in components[1:])

def generate_getters_setters(fields):
    methods = ""
    for ftype, fname in fields:
        capitalized = fname[0].upper() + fname[1:]
        methods += f"\n    public {ftype} get{capitalized}() {{ return this.{fname}; }}\n"
        methods += f"    public void set{capitalized}({ftype} {fname}) {{ this.{fname} = {fname}; }}\n"
    return methods

def generate_constructor(class_name, final_fields):
    if not final_fields:
        return ""
    params = ", ".join([f"{t} {n}" for t, n in final_fields])
    assigns = "".join([f"\n        this.{n} = {n};" for t, n in final_fields])
    return f"\n    public {class_name}({params}) {{{assigns}\n    }}\n"

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    if 'lombok' not in content:
        return

    # Strip lombok imports
    content = re.sub(r'import lombok\.[^;]+;\n', '', content)
    # Strip annotations
    content = re.sub(r'@Data\s*\n', '', content)
    content = re.sub(r'@NoArgsConstructor\s*\n', '', content)
    content = re.sub(r'@RequiredArgsConstructor\s*\n', '', content)

    # Find class name
    match = re.search(r'public class (\w+)', content)
    if not match:
        return
    class_name = match.group(1)

    # Extract all fields (private ...)
    fields = re.findall(r'private (?!final)(\w+(?:<\w+>)?)\s+(\w+);', content)
    final_fields = re.findall(r'private final (\w+(?:<\w+>)?)\s+(\w+);', content)

    # Generate additions
    additions = ""
    
    # We might need an empty constructor if there are no final fields (for JPA)
    if not final_fields and class_name in ['User', 'Project', 'Review', 'Finding', 'AIExplanation', 'LoginRequest', 'RegisterRequest']:
        additions += f"\n    public {class_name}() {{}}\n"

    additions += generate_constructor(class_name, final_fields)
    additions += generate_getters_setters(fields)
    
    # Insert before the last closing brace
    last_brace_idx = content.rfind('}')
    if last_brace_idx != -1:
        content = content[:last_brace_idx] + additions + content[last_brace_idx:]

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

backend_dir = "e:/saikrishnajadhav/Workplace/Projects/ttproject/backend/src/main/java/com/dataguard"
for root, _, files in os.walk(backend_dir):
    for file in files:
        if file.endswith(".java"):
            process_file(os.path.join(root, file))

# Handle AuthDto nested classes specially
auth_dto_path = os.path.join(backend_dir, "dto/AuthDto.java")
with open(auth_dto_path, 'r', encoding='utf-8') as f:
    dto_content = f.read()
if 'import lombok' in dto_content:
    dto_content = re.sub(r'import lombok\.[^;]+;\n', '', dto_content)
    dto_content = re.sub(r'@Data\s*\n', '', dto_content)
    # Add getters and setters for LoginRequest
    dto_content = dto_content.replace('private String password;\n    }', 'private String password;\n        public String getEmail() { return email; }\n        public void setEmail(String email) { this.email = email; }\n        public String getPassword() { return password; }\n        public void setPassword(String password) { this.password = password; }\n    }')
    # Add getters and setters for RegisterRequest
    dto_content = dto_content.replace('private String password;\n    }', 'private String password;\n        public String getFullName() { return fullName; }\n        public void setFullName(String fullName) { this.fullName = fullName; }\n        public String getEmail() { return email; }\n        public void setEmail(String email) { this.email = email; }\n        public String getPassword() { return password; }\n        public void setPassword(String password) { this.password = password; }\n    }')
    
    with open(auth_dto_path, 'w', encoding='utf-8') as f:
        f.write(dto_content)

print("Lombok stripped successfully.")
