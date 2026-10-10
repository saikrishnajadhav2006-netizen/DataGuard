import { Moon, Sun } from 'lucide-react';
import { useEffect, useState } from 'react';

export default function ThemeToggle() {
  const [dark, setDark] = useState(() => localStorage.getItem('dataguard-mode') === 'dark');
  useEffect(() => { document.body.dataset.mode = dark ? 'dark' : 'light'; }, [dark]);
  function toggle() {
    setDark(value => {
      const next = !value;
      document.body.dataset.mode = next ? 'dark' : 'light';
      localStorage.setItem('dataguard-mode', next ? 'dark' : 'light');
      return next;
    });
  }
  return <button className="theme-toggle" type="button" onClick={toggle} aria-label={`Switch to ${dark ? 'light' : 'dark'} mode`} title={`Switch to ${dark ? 'light' : 'dark'} mode`}>
    {dark ? <Sun size={18} /> : <Moon size={18} />}
  </button>;
}
