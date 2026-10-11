import { useEffect } from 'react';

const API_BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

if (typeof window !== 'undefined') {
  window.DATAGUARD_API_URL = API_BASE;
}

export default function NavyVioletPrototype() {
  useEffect(() => {
    document.title = 'DataGuard AI — Navy & Violet';
    return () => { document.title = 'DataGuard AI'; };
  }, []);

  return (
    <main style={{ position: 'fixed', inset: 0, background: '#050816' }}>
      <iframe
        title="DataGuard AI navy and violet website"
        src="/dataguard-navy-violet.html"
        style={{ display: 'block', width: '100%', height: '100%', border: 0 }}
        allow="clipboard-read; clipboard-write"
      />
    </main>
  );
}
