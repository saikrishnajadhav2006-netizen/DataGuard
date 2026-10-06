const apiBaseUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');

async function request(path, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`API request failed with status ${response.status}`);
  }

  return response.json();
}

export function getLogs(userId) {
  const query = userId ? `?userId=${encodeURIComponent(userId)}` : '';
  return request(`/api/logs${query}`);
}

export function createLog(logEntry) {
  return request('/api/logs', {
    method: 'POST',
    body: JSON.stringify(logEntry),
  });
}