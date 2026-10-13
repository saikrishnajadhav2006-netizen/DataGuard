# DataGuard GitHub App deployment setup

The GitHub App webhook is served by the Spring Boot backend at:

`POST /api/github/webhook`

## Render backend

Configure the Render backend service to build the Dockerfile from the `backend` directory (Docker build context must contain `pom.xml` and `src/`).

Set these environment variables in Render. Do not commit their values to GitHub:

- `GITHUB_APP_ID`: numeric GitHub App ID.
- `GITHUB_APP_PRIVATE_KEY`: the complete PEM private key downloaded from the GitHub App settings. Preserve newlines, or represent line breaks as `\n`.
- `GITHUB_WEBHOOK_SECRET`: the exact webhook secret configured in GitHub App settings.

The Docker image installs the syntax parser, Semgrep, and PMD and enables those tools for the review pipeline. PMD is used inside the Code Quality agent; Semgrep is used inside the Security agent.

## GitHub App settings

1. Set **Webhook URL** to `https://<your-render-service-host>/api/github/webhook`. Replace the host with the actual public Render backend URL. Do not use the frontend's Vercel URL or the temporary Cloudflare tunnel URL for the deployed service.
2. Set the webhook secret to the same value as `GITHUB_WEBHOOK_SECRET` in Render.
3. Subscribe to **Push** and **Pull request** events.
4. Grant repository permissions **Contents: Read-only** and **Checks: Read and write**. Metadata read access is required by GitHub.
5. Install the App on the DataGuard repository after changing permissions.

The webhook verifies GitHub's `X-Hub-Signature-256` HMAC before queueing a review. Push and supported pull-request events create a Check Run; the backend downloads the revision archive, extracts it with path and expansion limits, runs the same ReviewEngine agent pipeline as ZIP reviews, and publishes count-based metrics and up to 50 inline annotations. GitHub reviews do not run the website's Fix It workflow.

## Frontend

No frontend files are required for the backend pipeline or webhook. Vercel should continue using the existing frontend project and its existing API URL configuration. A push to `main` triggers Vercel only if the Vercel project is connected to this repository with automatic production deployments enabled.

## Verify after deployment

- Check the Render deploy log for a successful Docker build and backend startup.
- Send a GitHub webhook ping from App settings and confirm the endpoint returns a successful response when the webhook secret and App credentials are configured.
- Push a small commit and verify a **DataGuard AI Review** Check Run appears on that commit.
- Confirm the Check Run summary contains counts (not percentages) for syntax errors, potential bugs, code quality, security, architecture, and open issues. Semgrep does not appear as a separate category.
