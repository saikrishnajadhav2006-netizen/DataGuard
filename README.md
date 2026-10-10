# DataGuard AI

**AI-assisted code review and software quality checker**

## Stack
- Frontend: React, Vite, React Router, Lucide
- Backend: Java 21, Spring Boot, Spring Security, JWT
- Database: Aiven PostgreSQL (required)
- Optional AI explanations: Groq Chat Completions API

## Architecture
React UI → Spring Boot REST API → Spring Security/JWT → Spring Data JPA → Aiven PostgreSQL.

Registration validates input, BCrypt-hashes the password, stores the user in PostgreSQL, and returns a signed JWT. Login verifies the password against the stored hash and returns a JWT. Protected upload, fix-preview, and download endpoints require that token. The browser never connects directly to Aiven and never receives the database password or Groq API key.

## Configure secrets
Copy the root `.env.example` to `.env`. From the Aiven Console, copy the PostgreSQL host, port, database name, username, and password into `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Keep `sslmode=require` in the JDBC URL.

Set `JWT_SECRET` to a private random value with at least 32 UTF-8 bytes. Set `GROQ_API_KEY` here only if AI explanations are enabled. Never commit `.env`, and never put database passwords, JWT secrets, or Groq keys into frontend variables or source code.

The backend intentionally fails to start if Aiven connection settings are missing or invalid; this avoids silently saving accounts into a temporary in-memory database.

## Start backend
Requires Java 21 and Maven.

```powershell
cd backend
mvn spring-boot:run
```

Spring Boot listens on `http://localhost:8080`.

## Start frontend
In a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Vite listens on `http://localhost:5173` and proxies `/api` requests to Spring Boot on port 8080. For a deployed API, configure `VITE_API_URL` in the frontend environment.

## Expected flow
1. Register with a valid name, email, and password.
2. Spring Boot validates the data, stores the BCrypt password hash in Aiven PostgreSQL, and returns a JWT.
3. The frontend stores the returned session and navigates to the dashboard only after successful registration.
4. Login validates credentials against the same Aiven-backed user table and returns a JWT.
5. Authenticated users upload ZIP archives, view findings, request fix suggestions, and download review archives through protected REST endpoints.
6. Project, review, and finding records are persisted through Spring Data JPA in Aiven PostgreSQL.

## Security and limitations
- Supabase Auth and its frontend SDK are removed. Spring Boot JWT + Aiven PostgreSQL are the only authentication/database path.
- Passwords are hashed with BCrypt; plain-text passwords are not stored.
- Database credentials, JWT secret, and Groq key remain server-side.
- The fix endpoint currently returns a reviewable suggestion; it does not silently rewrite source files.
- Uploaded archives are extracted to temporary local storage. Multi-instance production deployments should use private durable object storage.
- Keep `.env` out of Git. If a key has ever been committed, rotate it.
