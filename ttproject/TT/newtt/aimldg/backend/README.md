# Student API (local setup)

The React form sends JSON to this Spring Boot API at `http://localhost:8080/api/v1`.

## Requirements

- Java 17 or newer
- MySQL running locally
- A database named `aimldg`

Create the database once:

```sql
CREATE DATABASE aimldg;
```

Update `src/main/resources/application.properties` if your MySQL username or password differs from `root` / `password`.

## Run the API

From this `backend` directory on PowerShell:

```powershell
mvn spring-boot:run
```

The included `mvnw.cmd` wrapper is currently not starting correctly in this project, so use the installed Maven command above.

## API URLs

- `GET http://localhost:8080/api/v1/read` — list students
- `POST http://localhost:8080/api/v1/create` — create a student
- `PUT http://localhost:8080/api/v1/update/{id}` — update a student
- `DELETE http://localhost:8080/api/v1/delete/{id}` — delete a student

For create or update, send this JSON with header `Content-Type: application/json`:

```json
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "ip": "127.0.0.1"
}
```

## Run the React form

In a second PowerShell terminal:

```powershell
cd ..\frontend\client
npm run dev
```

Open the Vite URL shown in the terminal (normally `http://localhost:5173`). The form is already configured to call the local Spring Boot URL above.
