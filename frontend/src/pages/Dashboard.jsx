import { Bot, ChartNoAxesCombined, ChevronDown, FileWarning, History, LayoutDashboard, Plus, Search, Shield } from 'lucide-react';
import { useState } from 'react';
import NewReviewModal from '../components/NewReviewModal';
import FindingList from '../components/FindingList';

const initialScans = [
  { name: 'my-shop-app', score: 78, group: 'Today' },
  { name: 'portfolio-site', score: 91, group: 'Today' },
  { name: 'chat-backend', score: 64, group: 'Yesterday' },
  { name: 'todo-react', score: 86, group: 'Yesterday' },
  { name: 'college-erp', score: 52, group: 'Previous 7 days' },
];

function readAuth() {
  try {
    return JSON.parse(localStorage.getItem('dataguard-auth') || '{}');
  } catch {
    return {};
  }
}

export default function Dashboard() {
  const [modalOpen, setModalOpen] = useState(false);
  const [scanList, setScanList] = useState(initialScans);
  const [selectedScan, setSelectedScan] = useState(initialScans[0]);
  const [scanQuery, setScanQuery] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const auth = readAuth();
  const activeReview = selectedScan.review || null;
  const visibleScans = scanList.filter((scan) => scan.name.toLowerCase().includes(scanQuery.trim().toLowerCase()));

  function addReview(review, name) {
    const entry = {
      name,
      score: review.qualityScore ?? review.score ?? 0,
      group: 'Today',
      review: { ...review, projectName: review.projectName || name },
    };
    setScanList((current) => [entry, ...current.filter((scan) => scan.name !== name)]);
    setSelectedScan(entry);
    setModalOpen(false);
  }

  return (
    <div className={`scan-app${sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <aside className="scan-sidebar">
        <div className="scan-brand"><Shield size={22} /><span>DataGuard AI</span><button onClick={() => setSidebarCollapsed((collapsed) => !collapsed)} title={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!sidebarCollapsed}><ChevronDown size={17} /></button></div>
        <button className="new-scan-button" onClick={() => setModalOpen(true)}><Plus size={18} /><span>New scan</span></button>
        <label className="scan-search"><Search size={16} /><input className="scan-search-input" aria-label="Search scans" value={scanQuery} onChange={(event) => setScanQuery(event.target.value)} placeholder="Search scans" /></label>
        <nav className="scan-nav" aria-label="Dashboard sections">
          <a className="selected" href="#overview"><LayoutDashboard size={17} /><span>Dashboard</span></a>
          <a href="#findings"><FileWarning size={17} /><span>All issues</span></a>
          <a href="#reports"><ChartNoAxesCombined size={17} /><span>Reports</span></a>
        </nav>
        <div className="scan-list">
          {['Today', 'Yesterday', 'Previous 7 days'].map((group) => {
            const groupScans = visibleScans.filter((scan) => scan.group === group);
            if (!groupScans.length) return null;
            return <div key={group}><p>{group}</p>{groupScans.map((scan) => <button className={selectedScan.name === scan.name ? 'scan-item active' : 'scan-item'} key={scan.name} onClick={() => setSelectedScan(scan)}><span>{scan.name}</span><b>{scan.score}</b></button>)}</div>;
          })}
          {visibleScans.length === 0 && <p className="scan-no-results">No matching scans</p>}
        </div>
        <div className="scan-user"><span>{(auth.fullName || 'Developer').charAt(0).toUpperCase()}</span><div><b>{auth.fullName || 'Developer'}</b><small>Free plan</small></div></div>
      </aside>
      <main className="scan-main" id="overview">
        <header className="scan-header"><div className="scan-project-title"><button className="mobile-menu" onClick={() => setSidebarCollapsed((collapsed) => !collapsed)} title="Toggle sidebar" aria-label="Toggle sidebar"><History size={20} /></button><div><h1>{selectedScan.name}</h1><p>{activeReview ? 'Review completed just now' : 'Example project overview'}</p></div></div><div className="scan-actions"><button className="rescan-button" onClick={() => setModalOpen(true)}>New review</button><Bot size={25} /></div></header>
        <section className="quality-banner"><div><span>Status</span><h2>Quality Gate</h2></div><strong className="passed-pill">{activeReview ? 'Reviewed' : 'Sample'}</strong><div className="grade-row"><div><b>A</b><span>Reliability</span></div><div><b className="grade-b">B</b><span>Security</span></div><div><b>A</b><span>Maintainability</span></div></div></section>
        <section className="metric-grid"><Metric label="Files scanned" value={activeReview ? (activeReview.filesScanned ?? activeReview.totalFiles ?? '—') : '128'} /><Metric label="High / critical findings" value={activeReview ? (activeReview.findings || []).filter((finding) => ['HIGH', 'CRITICAL'].includes(String(finding.severity).toUpperCase())).length : '42'} tone="red" /><Metric label="Low / medium findings" value={activeReview ? (activeReview.findings || []).filter((finding) => ['LOW', 'MEDIUM'].includes(String(finding.severity).toUpperCase())).length : '67'} tone="amber" /><Metric label="Fixed" value="—" tone="green" /></section>
        <section className="report-grid" id="reports"><div className="report-card quality-card"><h2>Code quality</h2><div className="score-ring"><strong>{activeReview?.qualityScore ?? selectedScan.score}</strong></div><span>out of 100</span></div><div className="report-card chart-card"><h2>Issues over time</h2><div className="fake-chart"><svg viewBox="0 0 600 180" preserveAspectRatio="none"><path d="M0 20 L100 55 L200 42 L300 100 L400 126 L500 150 L600 170 L600 180 L0 180Z" /><polyline points="0,20 100,55 200,42 300,100 400,126 500,150 600,170" /></svg></div><p>{activeReview ? 'Trend history will appear after more reviews.' : 'Example trend for a sample project'}</p></div></section>
        <div id="findings"><FindingList review={activeReview} /></div>
        {modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={addReview} />}
      </main>
    </div>
  );
}

function Metric({ label, value, tone = '' }) {
  return <article className={`metric-card ${tone}`}><span>{label}</span><strong>{value}</strong></article>;
}
