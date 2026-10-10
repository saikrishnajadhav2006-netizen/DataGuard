import { useEffect } from 'react';

/**
 * Full-screen wrapper for the approved standalone navy/violet UI prototype.
 * The prototype is served from /public so its CSS and interactions remain intact.
 * Its sample authentication, scans, fixes, and AI Radar results are demonstration
 * behavior; production API wiring remains in the existing React application.
 */
export default function NavyVioletPrototype() {
  useEffect(() => {
    document.title = 'DataGuard AI — Navy & Violet';
    return () => {
      document.title = 'DataGuard AI';
    };
  }, []);

  return (
    <main style={{ position: 'fixed', inset: 0, background: '#050816' }}>
      <iframe
        title="DataGuard AI navy and violet website prototype"
        src="/dataguard-navy-violet.html"
        style={{ display: 'block', width: '100%', height: '100%', border: 0 }}
        allow="clipboard-read; clipboard-write"
      />
    </main>
  );
}
