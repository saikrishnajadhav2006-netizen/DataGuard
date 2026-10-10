import os, re
path = 'backend/pom.xml'
with open(path, 'r') as f:
    content = f.read()

if 'spring-boot-starter-oauth2-client' not in content:
    content = content.replace('</dependencies>', '''
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-client</artifactId>
    </dependency>
</dependencies>''')
    with open(path, 'w') as f:
        f.write(content)
    print('Added OAuth2 Client to pom.xml')
