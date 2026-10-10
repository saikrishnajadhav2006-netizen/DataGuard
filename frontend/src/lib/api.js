import { supabase } from './supabase';

const API_URL = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');
const AUTH_PROVIDER = (import.meta.env.VITE_AUTH_PROVIDER || 'spring').toLowerCase();

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...options,
      headers: {
        ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
        ...(options.headers || {}),
      },
    });
  } catch {
    throw new Error(`Cannot reach the DataGuard backend at ${API_URL}. Make sure Spring Boot is running and VITE_API_URL is correct.`);
  }

  const contentType = response.headers.get('content-type') || '';
  const rawBody = await response.text();
  let body = rawBody;
  if (rawBody && contentType.includes('application/json')) {
    try {
      body = JSON.parse(rawBody);
    } catch {
      body = rawBody;
    }
  }

  if (!response.ok) {
    const message = typeof body === 'string'
      ? body.trim()
      : body?.message || body?.error || body?.detail || '';
    const fallback = response.status === 400 ? 'The server rejected the submitted data.'
      : response.status === 401 ? 'Invalid email or password.'
      : response.status === 403 ? 'The server blocked this request. Restart the updated backend and check Spring Security logs.'
      : response.status === 409 ? 'An account with this email already exists. Please sign in.'
      : `Request failed with HTTP ${response.status}.`;
    throw new Error(message || fallback);
  }
  return body;
}

function requireToken(auth, provider) {
  if (!auth?.token) {
    throw new Error(provider === 'supabase'
      ? 'Authentication did not return a session. Confirm your email if email confirmation is enabled, then sign in.'
      : 'The backend returned success without a JWT token. Check the AuthController response and JWT_SECRET configuration.');
  }
  return auth;
}

export async function authenticate(path, payload) {
  const normalizedPayload = {
    ...payload,
    ...(typeof payload.email === 'string' ? { email: payload.email.trim().toLowerCase() } : {}),
    ...(typeof payload.fullName === 'string' ? { fullName: payload.fullName.trim() } : {}),
  };

  if (AUTH_PROVIDER === 'supabase') {
    if (!supabase) {
      throw new Error('VITE_AUTH_PROVIDER is set to supabase, but VITE_SUPABASE_URL or VITE_SUPABASE_ANON_KEY is missing.');
    }
    if (path === '/api/auth/register') {
      const { data, error } = await supabase.auth.signUp({
        email: normalizedPayload.email,
        password: normalizedPayload.password,
        options: { data: { full_name: normalizedPayload.fullName } },
      });
      if (error) throw error;
      if (!data.session) return { pendingConfirmation: true, email: data.user?.email || normalizedPayload.email };
      return requireToken({
        token: data.session.access_token,
        email: data.user.email,
        fullName: normalizedPayload.fullName,
        role: data.user.user_metadata?.role || 'DEVELOPER',
      }, 'supabase');
    }

    if (path === '/api/auth/login') {
      const { data, error } = await supabase.auth.signInWithPassword({
        email: normalizedPayload.email,
        password: normalizedPayload.password,
      });
      if (error) throw error;
      return requireToken({
        token: data.session?.access_token,
        email: data.user?.email,
        fullName: data.user?.user_metadata?.full_name || data.user?.email,
        role: data.user?.user_metadata?.role || 'DEVELOPER',
      }, 'supabase');
    }
    throw new Error('Unsupported authentication endpoint for Supabase.');
  }

  if (AUTH_PROVIDER !== 'spring') {
    throw new Error('Invalid VITE_AUTH_PROVIDER. Use spring or supabase.');
  }

  return requireToken(await request(path, {
    method: 'POST',
    body: JSON.stringify(normalizedPayload),
  }), 'spring');
}

export function uploadProject(name, file, token) {
  if (!token) throw new Error('Your session has expired. Please sign in again.');
  const formData = new FormData();
  formData.append('name', name.trim());
  formData.append('file', file);
  return request('/api/projects/upload', {
    method: 'POST',
    body: formData,
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function generateFix(reviewId, findingId) {
  const auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}');
  if (!auth.token) throw new Error('Your session has expired. Please sign in again.');
  return request(`/api/projects/reviews/${reviewId}/findings/${findingId}/fix`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token}` },
  });
}
