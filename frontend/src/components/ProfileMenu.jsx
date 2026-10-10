import { useEffect, useRef, useState } from 'react';
import { Home, LogOut, Moon, Sun, UserRound } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { clearAuth } from '../lib/authStorage';
import { logoutSession } from '../lib/api';

export default function ProfileMenu({ auth, compact = false }) {
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState(() => localStorage.getItem(`dataguard-mode:${auth.email || 'guest'}`) || localStorage.getItem('dataguard-mode') || 'light');
  const navigate = useNavigate();
  const menuRef = useRef(null);
  useEffect(() => { document.body.dataset.mode = theme; }, [theme]);
  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutside = event => { if (!menuRef.current?.contains(event.target)) setOpen(false); };
    const closeOnEscape = event => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('pointerdown', closeOnOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open]);

  function selectTheme(next) {
    setTheme(next);
    document.body.dataset.mode = next;
    localStorage.setItem(`dataguard-mode:${auth.email || 'guest'}`, next);
    localStorage.setItem('dataguard-mode', next);
  }

  async function logout() {
    try { await logoutSession(); } catch { /* Clear local state even while the server is unavailable. */ }
    clearAuth();
    setOpen(false);
    navigate('/login', { replace: true });
  }

  return <div ref={menuRef} className={`profile-menu ${open ? 'open' : ''}`}>
    <button className="usr radar-user profile-trigger" type="button" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen(value => !value)}>
      <span className="av">{(auth.fullName || auth.email || 'D').slice(0, 1).toUpperCase()}</span>
      {!compact && <span className="radar-user-copy"><b>{auth.fullName || 'Developer'}</b><small>{auth.email || 'Signed in'}</small></span>}
      <span className="profile-chevron" aria-hidden="true">⌃</span>
    </button>
    {open && <div className="profile-popover" role="menu" aria-label="Profile menu">
      <div className="profile-account"><UserRound size={17} /><span><b>{auth.fullName || 'Developer'}</b><small>{auth.email || 'Signed in'}</small></span></div>
      <Link role="menuitem" to="/" onClick={() => setOpen(false)}><Home size={16} />Home</Link>
      <div className="profile-theme" role="group" aria-label="Theme">
        <span>Theme</span>
        <button type="button" aria-pressed={theme === 'light'} className={theme === 'light' ? 'selected' : ''} onClick={() => selectTheme('light')}><Sun size={15} />Light</button>
        <button type="button" aria-pressed={theme === 'dark'} className={theme === 'dark' ? 'selected' : ''} onClick={() => selectTheme('dark')}><Moon size={15} />Dark</button>
      </div>
      <button role="menuitem" type="button" onClick={logout}><LogOut size={16} />Logout</button>
    </div>}
  </div>;
}
