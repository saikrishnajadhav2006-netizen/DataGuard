const API_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

async function request(path, options = {}) {
  const response = await fetch(\`\${API_URL}\${path}\`, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(options.headers || {}),
    },
  });
  const contentType = response.headers.get('content-type') || '';
  const body = contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) {
    const message = typeof body === 'string' ? body : body?.message || body?.error || body?.detail || 'Request failed';
    throw new Error(message);
  }
  return body;
}

// Spring Boot handles auth. Users and BCrypt password hashes live in Aiven PostgreSQL.
export function authenticate(path, payload) {
  if (path !== '/api/auth/register' && path !== '/api/auth/login') {
    throw new Error('Unsupported authentication request.');
  }
  return request(path, { method: 'POST', body: JSON.stringify(payload) });
}

export function uploadProject(name, file, token) {
  const formData = new FormData();
  formData.append('name', name);
  formData.append('file', file);
  return request('/api/projects/upload', {
    method: 'POST',
    body: formData,
    headers: { Authorization: \`Bearer \${token}\` },
  });
}

export function generateFix(reviewId, findingId) {
  let auth = {};
  try { auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}'); } catch { /* malformed session */ }
  return request(\`/api/projects/reviews/\${reviewId}/findings/\${findingId}/fix\`, {
    method: 'POST',
    headers: { Authorization: \`Bearer \${auth.token || ''}\` },
  });
}

export async function downloadReviewedProject(reviewId, projectName) {
  let auth = {};
  try { auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}'); } catch { /* malformed session */ }
  const response = await fetch(\`\${API_URL}/api/projects/reviews/\${reviewId}/download\`, {
    headers: { Authorization: \`Bearer \${auth.token || ''}\` },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'Could not download the reviewed project.');
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = \`\${(projectName || 'dataguard-project').replace(/[^a-z0-9-_]+/gi, '-').replace(/^-|-$/g, '') || 'dataguard-project'}-reviewed.zip\`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
