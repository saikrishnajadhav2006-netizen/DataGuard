import { useEffect, useState } from 'react';

const THEME_KEY = 'dataguard-theme';

function readTheme() {
  try { return localStorage.getItem(THEME_KEY) === 'dark'; }
  catch { return false; }
}

export default function useAppTheme() {
  const [dark, setDark] = useState(readTheme);

  useEffect(() => {
    const applyTheme = (isDark) => {
      document.documentElement.classList.toggle('dg-dark', isDark);
      document.body?.classList.toggle('dg-dark', isDark);
      document.documentElement.style.colorScheme = isDark ? 'dark' : 'light';
    };
    applyTheme(dark);
    try { localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light'); } catch { /* storage may be unavailable */ }
    const syncTheme = (event) => setDark(event.key === THEME_KEY ? event.newValue === 'dark' : readTheme());
    window.addEventListener('storage', syncTheme);
    return () => window.removeEventListener('storage', syncTheme);
  }, [dark]);

  return [dark, setDark];
}
