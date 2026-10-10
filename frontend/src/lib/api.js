const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8081').replace(/\/$/, '');
import { supabase } from './supabase';

// Aiven is the PostgreSQL database; Spring Boot JWT is the default auth provider.
const useSupabaseAuth = import.meta.env.VITE_AUTH_PROVIDER === 'supabase' && Boolean(supabase);

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
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

export async function authenticate(path, payload) {
  if (useSupabaseAuth && path === '/api/auth/register') {
    const { data, error } = await supabase.auth.signUp({
      email: payload.email,
      password: payload.password,
      options: { data: { full_name: payload.fullName } },
    });
    if (error) throw error;
    if (!data.session) throw new Error('Registration succeeded. Confirm your email before signing in.');
    return { token: data.session.access_token, email: data.user.email, fullName: payload.fullName };
  }
  if (useSupabaseAuth && path === '/api/auth/login') {
    const { data, error } = await supabase.auth.signInWithPassword({ email: payload.email, password: payload.password });
    if (error) throw error;
    if (!data.session) throw new Error('No active session returned by authentication provider.');
    return { token: data.session.access_token, email: data.user.email, fullName: data.user.user_metadata?.full_name || data.user.email };
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
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function generateFix(reviewId, findingId) {
  let auth = {};
  try { auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}'); } catch { /* expired or malformed session */ }
  return request(`/api/projects/reviews/${reviewId}/findings/${findingId}/fix`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token || ''}` },
  });
}

export async function downloadReviewedProject(projectId, projectName) {
  let auth = {};
  try { auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}'); } catch { /* expired or malformed session */ }
  const response = await fetch(`${API_URL}/api/projects/reviews/${projectId}/download`, {
    headers: { Authorization: `Bearer ${auth.token || ''}` },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || 'Could not download the reviewed project.');
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${(projectName || 'dataguard-project').replace(/[^a-z0-9-_]+/gi, '-').replace(/^-|-$/g, '') || 'dataguard-project'}-reviewed.zip`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}
