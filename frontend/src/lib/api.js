const API_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');
function authData() { try { return JSON.parse(localStorage.getItem('dataguard-auth') || '{}'); } catch { return {}; } }
export function getToken() { const token = authData().token; if (!token) throw new Error('Your session has expired. Please sign in again.'); return token; }
async function request(path, options = {}) {
 const token = options.auth === false ? '' : (options.token || getToken());
 const headers = { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(options.headers || {}) };
 let response;
 try { response = await fetch(API_URL + path, { ...options, headers }); }
 catch { const site = typeof window !== 'undefined' ? window.location.origin : 'unknown site'; throw new Error('Cannot reach the DataGuard server from ' + site + '. API base URL: ' + (API_URL || '(VITE_API_URL is empty)') + '. Check the Render backend is running and CORS_ALLOWED_ORIGINS includes this exact site origin. Open /diagnostics for connection checks.'); }
 const type = response.headers.get('content-type') || '';
 if (response.status === 401 && token) { try { localStorage.removeItem('dataguard-auth'); } catch { /* ignore */ } if (typeof window !== 'undefined' && !/^\/(login|register)/.test(window.location.pathname)) window.location.assign('/login'); throw new Error('Your session has expired. Please sign in again.'); }
 const body = type.includes('application/json') ? await response.json().catch(() => ({})) : await response.text();
 if (response.ok && typeof body === 'string' && /^\s*<(!doctype|html)/i.test(body)) throw new Error('The API returned a web page instead of data. Set VITE_API_URL to your backend URL (or add an /api proxy redirect) and redeploy the frontend.');
 if (!response.ok) {
   const detail = typeof body === 'string' ? body.trim() : body?.message || body?.error || body?.detail;
   throw new Error(detail || (response.status === 403 ? 'The server refused this request (403). Sign out and sign in again; if it continues, open /diagnostics to see which link is failing.' : `Request failed (${response.status} ${response.statusText || ''}).`.trim()));
  }
 return body;
}
export function authenticate(path, payload) { if (!['/api/auth/register','/api/auth/login'].includes(path)) throw new Error('Unsupported authentication request.'); return request(path,{method:'POST',body:JSON.stringify(payload),auth:false}); }
export function getReviews() { return request('/api/projects/reviews'); }
export function uploadProject(name,file) { const form = new FormData(); form.append('name',name); form.append('file',file); return request('/api/projects/upload',{method:'POST',body:form}); }
export function generateFix(reviewId,findingId) { return request('/api/projects/reviews/'+reviewId+'/findings/'+findingId+'/fix',{method:'POST'}); }
export function applyFix(reviewId,findingId) { return request('/api/projects/reviews/'+reviewId+'/findings/'+findingId+'/apply',{method:'POST'}); }
export async function downloadReviewedProject(reviewId,name) {
 const response = await fetch(API_URL+'/api/projects/reviews/'+reviewId+'/download',{headers:{Authorization:'Bearer '+getToken()}});
 if (!response.ok) throw new Error('Could not download the reviewed project. The source may no longer be stored on the server.');
 const url = URL.createObjectURL(await response.blob()); const a = document.createElement('a'); a.href=url; a.download=(name||'dataguard-project')+'-reviewed.zip'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(()=>URL.revokeObjectURL(url),1000);
}
export function getChatSessions() { return request('/api/ai/chat/sessions'); }
export function createChatSession(title,reviewId=null) { return request('/api/ai/chat/sessions',{method:'POST',body:JSON.stringify({title,reviewId})}); }
export function getChatHistory(id) { return request('/api/ai/chat/history?sessionId='+encodeURIComponent(id)); }
export function sendChatMessage(message,sessionId) { return request('/api/ai/chat',{method:'POST',body:JSON.stringify({message,sessionId})}); }
export function sendRadarPrompt(prompt,mode,category,budget,sessionId=null) { return request('/api/ai/radar',{method:'POST',body:JSON.stringify({prompt,mode,category,budget,sessionId})}); }
export function deleteChatSession(id) { return request('/api/ai/chat/sessions/'+encodeURIComponent(id),{method:'DELETE'}); }

export function updateProfile(fullName) { return request('/api/auth/profile',{method:'PUT',body:JSON.stringify({fullName})}); }
export function changePassword(currentPassword,newPassword) { return request('/api/auth/change-password',{method:'POST',body:JSON.stringify({currentPassword,newPassword})}); }
