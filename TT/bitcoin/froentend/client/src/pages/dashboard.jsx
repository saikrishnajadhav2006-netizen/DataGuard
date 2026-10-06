import { useNavigate } from 'react-router-dom';
import WalletCard from '../components/WalletCard';

export default function Dashboard() {
  const navigate = useNavigate();
  const storedUser = localStorage.getItem('user');
  const user = storedUser ? JSON.parse(storedUser) : { username: 'User' };
  const balance = 1000;

  const handleLogout = () => {
    localStorage.removeItem('user');
    navigate('/');
  };

  return (
    <div className="dashboard-shell">
      <aside className="dashboard-sidebar">
        <div className="sidebar-brand">
          <div className="brand-badge small">CW</div>
          <div>
            <span className="sidebar-label">Portfolio</span>
            <h3>Crypto Wallet</h3>
          </div>
        </div>

        <nav className="sidebar-nav">
          <button className="nav-item active">Overview</button>
          <button className="nav-item">Transactions</button>
          <button className="nav-item">Cards</button>
          <button className="nav-item">Settings</button>
        </nav>
      </aside>

      <main className="dashboard-main">
        <header className="dashboard-header">
          <div>
            <p className="eyebrow">Welcome back</p>
            <h1>{user.username}</h1>
          </div>

          <button className="logout-button" onClick={handleLogout}>
            Log Out
          </button>
        </header>

        <section className="stats-grid">
          <div className="stat-card accent">
            <span>Wallet balance</span>
            <strong>${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong>
          </div>
          <div className="stat-card">
            <span>Free credits</span>
            <strong>$1,000.00</strong>
          </div>
          <div className="stat-card">
            <span>Status</span>
            <strong>Active</strong>
          </div>
        </section>

        <section className="content-grid">
          <WalletCard username={user.username} balance={balance} />

          <div className="info-card">
            <p className="eyebrow">Quick actions</p>
            <h3>Wallet tools</h3>

            <div className="action-list">
              <button className="action-button primary">Deposit</button>
              <button className="action-button secondary">Send</button>
              <button className="action-button tertiary">View activity</button>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}