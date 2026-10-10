import { supabase } from './supabase';
import { readAuth } from './authStorage';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

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
  if (!response.ok) throw new Error(typeof body === 'string' ? body : body?.error || body?.message || 'Request failed');
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

export function logoutSession() {
  const auth = readAuth();
  if (!auth.token) return Promise.resolve();
  if (supabase) return supabase.auth.signOut();
  return request('/api/auth/logout', { method: 'POST', headers: { Authorization: `Bearer ${auth.token}` } });
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
  const auth = readAuth();
  return request(`/api/projects/reviews/${reviewId}/findings/${findingId}/fix`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token}` },
  });
}

export function approveFix(reviewId, findingId, proposal) {
  const auth = readAuth();
  return request(`/api/projects/reviews/${reviewId}/findings/${findingId}/fix/apply`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token}` },
    body: JSON.stringify({ approved: true, repository: proposal.repository, branch: proposal.branch, commitSha: proposal.commitSha }),
  });
}

export function approveZipFix(reviewId, findingId) {
  const auth = readAuth();
  return request(`/api/reviews/${reviewId}/fixed-project/findings/${findingId}/approve`, {
    method: 'POST', headers: { Authorization: `Bearer ${auth.token}` },
  });
}

export function getFixedProjectStatus(reviewId) {
  const auth = readAuth();
  return request(`/api/reviews/${reviewId}/fixed-project`, {
    headers: { Authorization: `Bearer ${auth.token}` },
  });
}

export function reReviewFixedProject(reviewId) {
  const auth = readAuth();
  return request(`/api/reviews/${reviewId}/fixed-project/re-review`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token}` },
  });
}

export async function downloadFixedProject(reviewId) {
  const auth = readAuth();
  const response = await fetch(`${API_URL}/api/reviews/${reviewId}/fixed-project/download`, {
    headers: { Authorization: `Bearer ${auth.token}` },
  });
  if (!response.ok) throw new Error('The fixed project ZIP is unavailable or failed verification.');
  return response.blob();
}

export function getReviews(token) {
  return request('/api/reviews', {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function getReview(id, token) {
  return request(`/api/reviews/${id}`, {
    method: 'GET',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export function radarChat(prompt, mode, conversationId = null) {
  const auth = readAuth();
  return request('/api/radar/chat', {
    method: 'POST',
    headers: { Authorization: `Bearer ${auth.token}` },
    body: JSON.stringify({ prompt, mode, conversationId }),
  });
}

export function getRadarConversations() {
  const auth = readAuth();
  return request('/api/radar/conversations', { headers: { Authorization: `Bearer ${auth.token}` } });
}

export function getRadarConversation(id) {
  const auth = readAuth();
  return request(`/api/radar/conversations/${encodeURIComponent(id)}`, { headers: { Authorization: `Bearer ${auth.token}` } });
}

export function deleteRadarConversation(id) {
  const auth = readAuth();
  return request(`/api/radar/conversations/${encodeURIComponent(id)}`, { method: 'DELETE', headers: { Authorization: `Bearer ${auth.token}` } });
}
