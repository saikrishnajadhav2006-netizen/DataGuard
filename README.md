# DataGuard AI

**"AI-Assisted Code Review & Software Quality Checker"**

DataGuard AI is an academic full-stack web application designed to help developers identify software quality, architecture, and security issues in their Java/Spring Boot projects before providing AI-driven explanations and recommendations.

## Features

- **Project Upload**: Upload ZIP files or link GitHub repositories (planned).
- **Deterministic Analysis**: Quickly scan code using rules to identify common pitfalls without wasting AI tokens.
- **AI Explanations**: (Requires API key) Generates contextual recommendations for identified issues.
- **Review History**: Track your code quality scores over time.

## Architecture

DataGuard AI uses a modern, monolithic architecture:

- **Frontend**: React + Vite + Tailwind CSS
- **Backend**: Java 21 + Spring Boot 3.x
- **Database**: Supabase PostgreSQL (or H2 for local dev)
- **AI Integration**: Spring AI (Compatible with OpenAI, Gemini, etc.)
- **Security**: JWT-based Authentication

## Project Structure

```
ttproject/
├── frontend/             # React application (Vite)
├── backend/              # Spring Boot application
├── .env.example          # Environment variables template
└── README.md             # This file
```

## Setup & Installation

### 1. Environment Configuration

Copy the `.env.example` file to `.env` in the root directory (or simply configure these variables in your IDE or system environment). 

```bash
cp .env.example .env
```

If you are using Supabase PostgreSQL, update the `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. For local testing, the application defaults to an in-memory **H2 database** so it runs out-of-the-box.

### 2. Running the Backend (Spring Boot)

You will need Java 21 installed.

```bash
cd backend
mvn spring-boot:run
```
*(If Maven is not installed locally, you can generate a maven wrapper `mvn wrapper:wrapper` first).*

The API runs on `http://localhost:8080`.

### 3. Running the Frontend (React)

You will need Node.js installed.

```bash
cd frontend
npm install
npm run dev
```

The frontend will run on `http://localhost:5173`.

### 4. Supabase setup

1. Create a Supabase project and open **Connect**.
2. Copy the PostgreSQL connection values into environment variables. Use the pooler connection from Supabase if the direct database hostname is not reachable from your network.
3. Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_DRIVER=org.postgresql.Driver`, and `DB_PLATFORM=org.hibernate.dialect.PostgreSQLDialect`.
4. Set a strong `JWT_SECRET` in the backend environment.
5. Start Spring Boot. Hibernate creates the current `users`, `projects`, `reviews`, `findings`, and explanation tables through `ddl-auto=update`.

For Supabase Auth, also set `SUPABASE_URL`, `SUPABASE_AUTH_ENABLED=true`, `VITE_SUPABASE_URL`, and `VITE_SUPABASE_ANON_KEY`. The browser uses only the Supabase anon key. Spring Security validates the resulting access token against the Supabase issuer, and the backend provisions a local profile row when the user uploads their first review.

Do not commit `.env` or place database passwords, JWT secrets, or AI keys in source files. Uploaded ZIPs are currently extracted temporarily; use Supabase Storage before retaining archives or generated patches in production.

### 5. AI API setup

The application uses Spring AI's OpenAI-compatible integration for optional explanations. Set `AI_API_KEY` and `AI_MODEL=gpt-4o-mini`, then clear `SPRING_AUTOCONFIGURE_EXCLUDE` in the deployment environment. Without a key, deterministic findings and fix previews still work.

### 6. Run Semgrep locally

Install Semgrep on Windows with Python:

```powershell
py -m pip install semgrep
```

Run the included rules against Java source and export JSON evidence:

```powershell
semgrep --config semgrep.yml --json --output semgrep-results.json backend/src/main/java
```

The application can consume those results in a worker after validating file paths and rule IDs. Keep Semgrep, CodeQL, Tree-sitter, and language-specific linters as separate worker steps so one unavailable tool does not prevent login or the basic review path.

## Future Enhancements

## Review Pipeline

The current review path is intentionally hybrid:

1. A user uploads a ZIP repository.
2. Spring Boot extracts it with ZIP-slip protection and size limits.
3. Irrelevant directories such as `node_modules`, `target`, `.git`, and `build` are skipped by the analyzer.
4. The deterministic analyzer scans Java source and returns severity, file, line, evidence, and recommendations.
5. Findings and the quality score are persisted with the review.
6. The dashboard displays the findings and can request a safe fix preview.
7. Spring AI/OpenAI is optional and can explain findings when an API key is configured.

The fix action currently returns a reviewable patch suggestion. It does not overwrite uploaded source automatically. This prevents an AI suggestion from silently changing a repository.

## AI and Analysis Services

Use deterministic tools for evidence and an LLM for explanation, not the other way around:

- Java: Checkstyle, PMD, SpotBugs
- JavaScript/TypeScript: ESLint and the TypeScript compiler
- Security: Semgrep or CodeQL
- Parsing: Tree-sitter for language-aware file and function chunks
- Explanation and fix previews: Spring AI with OpenAI or another compatible provider

The current project includes the first deterministic Java rules and the Spring AI integration point. Semgrep, CodeQL, Tree-sitter, and language-specific runners should be added as isolated worker processes before enabling them in production.

## Supabase Data Model

When the `DB_*` variables point to Supabase, application records can be stored in PostgreSQL. The planned tables are `users`, `projects`, `repositories`, `files`, `review_runs`, `findings`, `fixes`, and `conversations`. Uploaded archives and generated reports should use Supabase Storage rather than PostgreSQL rows. Local development defaults to in-memory H2.

## Size and Compression

The upload endpoint limits compressed ZIP requests to 25 MB and extracted content to 100 MB. Compression should reduce transfer size, not bypass review limits. The next worker iteration should calculate archive entry counts, ignore binary/media files before extraction, and split source into function/class chunks for large repositories.
