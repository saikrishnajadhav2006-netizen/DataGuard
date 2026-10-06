import { ChartNoAxesCombined, FolderKanban, History, Shield, Sparkles } from 'lucide-react';
import { NavLink, useNavigate } from 'react-router-dom';

const links = [
  { to: '/dashboard', label: 'Dashboard', icon: ChartNoAxesCombined },
  { to: '/projects', label: 'Projects', icon: FolderKanban },
  { to: '/history', label: 'History', icon: History },
];

export default function DashboardLayout({ children, onNewReview }) {
  const navigate = useNavigate();

  function logout() {
    localStorage.removeItem('dataguard-auth');
    navigate('/login');
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <button className="brand" onClick={() => navigate('/dashboard')}>
          <Shield size={22} />
          <span>DataGuard AI</span>
        </button>
        <nav className="side-nav" aria-label="Main navigation">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} className={({ isActive }) => `side-link${isActive ? ' active' : ''}`}>
              <Icon size={19} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <div className="sidebar-tip"><Sparkles size={17} /><span>Review smarter, ship safer.</span></div>
          <button className="text-button" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main-panel">{children}</main>
      <button className="floating-new-review" onClick={onNewReview} title="Start a new review"><Sparkles size={18} /></button>
    </div>
  );
}