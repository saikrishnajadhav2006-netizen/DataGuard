
project Structure

 Directory of C:\Users\vikram\Desktop\folder name\aimldg

19/08/2026  13:25    <DIR>          .
19/08/2026  09:45    <DIR>          ..
19/08/2026  13:25    <DIR>          backend
19/08/2026  12:21        33,752,415 Backend.tldr
19/08/2026  10:05    <DIR>          frontend
19/08/2026  12:41         9,269,196 fronyend.tldr
19/08/2026  09:45    <DIR>          oops
19/08/2026  13:25                19 readme.md
19/08/2026  12:13    <DIR>          seed
               3 File(s)     43,021,630 bytes
               6 Dir(s)  113,385,525,248 bytes free

               C:\Users\vikram\Desktop\folder name\aimldg>prompt v :

v :  tree
Folder PATH listing for volume New Volume
Volume serial number is 000000DB B21E:AC62
C:.
├───backend
│   ├───.mvn
│   │   └───wrapper
│   ├───src
│   │   ├───main
│   │   │   ├───java
│   │   │   │   └───jar
│   │   │   │       ├───controller
│   │   │   │       ├───dto
│   │   │   │       ├───model
│   │   │   │       ├───repo
│   │   │   │       └───service
│   │   │   └───resources
│   │   └───test
│   │       └───java
│   │           └───jar
│   └───target
│       ├───classes
│       │   └───jar
│       │       ├───controller
│       │       ├───dto
│       │       ├───model
│       │       ├───repo
│       │       └───service
│       └───test-classes
│           └───jar
├───frontend
│   ├───client
│   │   ├───public
│   │   └───src
│   │       ├───assets
│   │       └───components
│   └───web
│       ├───get
│       └───post
├───oops
└───seed


frontend --> {
    
    npm create vite@latest client  or  npm create vite@latest ./
    npm install                    or  npm i
    npm run dev                    or  npm run dev -- --host
    axios                          ->  npm i axios
    router                         ->  npm i react-router-dom

    }     

backend --> { 

    spring init --build=maven --dependencies=web,thymeleaf,devtools,mysql,... --package=jar --name=backend backend

    install ---> mvn clean install -DskipTests
    Run    ----> mvn spring-boot:run


}

mysql ---> { 

    local mysql :  
    C:\Users\vikram>prompt db :

    db :  mysql -u root -p
    Enter password: ****

    Cloud aiven console : 

    mysql -h -P -u dbname -p

    use postgresql neon db 


}