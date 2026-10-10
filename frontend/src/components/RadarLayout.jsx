import { useEffect, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Menu, Plus, Shield, Sparkles, MessageCircle } from 'lucide-react';
import ProfileMenu from './ProfileMenu';
import { getRadarConversations, getReviews } from '../lib/api';
import { readAuth } from '../lib/authStorage';

export default function RadarLayout({ children }) {
  const [collapsed, setCollapsed] = useState(false);
  const [reviews, setReviews] = useState([]);
  const [conversations, setConversations] = useState([]);
  const auth = readAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (!auth.token) {
      navigate('/login');
      return;
    }
    getReviews(auth.token).then(setReviews).catch(() => setReviews([]));
    getRadarConversations().then(setConversations).catch(() => setConversations([]));
    const refreshHistory = () => getRadarConversations().then(setConversations).catch(() => {});
    window.addEventListener('dataguard-radar-history-updated', refreshHistory);
    return () => window.removeEventListener('dataguard-radar-history-updated', refreshHistory);
  }, [auth.token, navigate]);

  return (
    <div className={`app radar-app ${collapsed ? 'sbc' : ''}`}>
      <aside className="side">
        <Link className="brand brand-link" to="/"><Shield className="i" fill="currentColor" />DataGuard AI</Link>
        <button className="new" onClick={() => navigate('/dashboard')}><Plus className="i" />New scan</button>
        <nav className="radar-nav" aria-label="Workspace navigation">
          <NavLink to="/dashboard"><Shield className="i" />Code check</NavLink>
          <NavLink to="/ai-radar" className="on"><Sparkles className="i" />AI Radar<span>NEW</span></NavLink>
        </nav>
        <div className="pn radar-history">
          <div className="gl">Code review history</div>
          {reviews.length ? reviews.slice(0, 12).map(review => (
            <button key={review.id} className="hi" onClick={() => navigate('/dashboard')}>
              <s style={{ '--c': review.overallScore >= 80 ? '#34d399' : review.overallScore >= 60 ? '#fbbf24' : '#fb7185' }} />
              <span>{review.projectName}</span><em>{review.overallScore}</em>
            </button>
          )) : <div className="radar-no-history">Your project reviews will appear here.</div>}
        </div>
        <div className="pn conversation-history">
          <div className="gl">AI Radar conversations</div>
          {conversations.length ? conversations.slice(0, 10).map(item => {
            const active = new URLSearchParams(location.search).get('conversation') === String(item.id);
            return <button type="button" aria-current={active ? 'page' : undefined} className={`conversation-link ${active ? 'selected' : ''}`} key={item.id} onClick={() => navigate(`/ai-radar?conversation=${item.id}`)}><MessageCircle size={14} /><span>{item.title || 'New conversation'}</span></button>;
          }) : <div className="radar-no-history">Your AI conversations will appear here.</div>}
        </div>
        <ProfileMenu auth={auth} />
      </aside>

      <section className="center radar-center">
        <header className="top radar-topbar">
          <div className="l"><button className="ib" onClick={() => setCollapsed(value => !value)} title="Toggle sidebar"><Menu className="i" /></button></div>
          <div className="r" />
        </header>
        <main className="body rv radar-scroll">{children}</main>
      </section>
    </div>
  );
}
