const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
import { supabase } from './supabase';

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
    const message = typeof body === 'string'
      ? body
      : body?.message || body?.error || body?.detail || 'Request failed';
    throw new Error(message);
  }
  return body;
}

export async function authenticate(path, payload) {
  if (supabase && path === '/api/auth/register') {
    const { data, error } = await supabase.auth.signUp({
      email: payload.email,
      password: payload.password,
      options: { data: { full_name: payload.fullName } },
    });
    if (error) throw error;
    if (!data.session) throw new Error('Registration succeeded. Confirm your email before signing in.');
    return { token: data.session.access_token, email: data.user.email, fullName: payload.fullName };
  }

  if (supabase && path === '/api/auth/login') {
    const { data, error } = await supabase.auth.signInWithPassword({ email: payload.email, password: payload.password });
    if (error) throw error;
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
  const auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}');
  return request(`/api/projects/reviews/${reviewId}/findings/${findingId}/fix`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token}` },
  });
}