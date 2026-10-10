import { Bot, ChartNoAxesCombined, ChevronDown, FileWarning, History, LayoutDashboard, Plus, Search, Shield } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import NewReviewModal from '../components/NewReviewModal';
import FindingList from '../components/FindingList';
import { getReviews } from '../lib/api';

function readAuth() {
  try { return JSON.parse(localStorage.getItem('dataguard-auth') || '{}'); } catch { return {}; }
}

function reviewGroup(review) {
  if (!review.reviewDate) return 'Previous 7 days';
  const date = new Date(review.reviewDate);
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const days = Math.floor((today - day) / 86400000);
  if (days <= 0) return 'Today';
  if (days === 1) return 'Yesterday';
  return days <= 7 ? 'Previous 7 days' : 'Older';
}

export default function Dashboard() {
  const [modalOpen, setModalOpen] = useState(false);
  const [scanList, setScanList] = useState([]);
  const [selectedScan, setSelectedScan] = useState(null);
  const [scanQuery, setScanQuery] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const auth = readAuth();
  const activeReview = selectedScan?.review || null;
  const visibleScans = useMemo(() => scanList.filter((scan) => scan.name.toLowerCase().includes(scanQuery.trim().toLowerCase())), [scanList, scanQuery]);

  async function refreshReviews() {
    setLoading(true);
    setLoadError('');
    try {
      const reviews = await getReviews();
      const entries = reviews.map((review) => ({
        name: review.projectName || 'Untitled project',
        score: review.qualityScore ?? 0,
        group: reviewGroup(review),
        review: { ...review, projectName: review.projectName || 'Untitled project' },
      }));
      setScanList(entries);
      setSelectedScan((current) => entries.find((entry) => entry.review.id === current?.review?.id) || entries[0] || null);
    } catch (error) {
      setLoadError(error.message || 'Could not load your reviews.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refreshReviews(); }, []);

  function addReview(review, name) {
    const normalized = { ...review, projectName: review.projectName || name };
    const entry = { name: normalized.projectName, score: normalized.qualityScore ?? 0, group: 'Today', review: normalized };
    setScanList((current) => [entry, ...current.filter((scan) => scan.review?.id !== normalized.id)]);
    setSelectedScan(entry);
    setModalOpen(false);
  }

  const findings = activeReview?.findings || [];
  const countSeverity = (values) => findings.filter((finding) => values.includes(String(finding.severity || '').toUpperCase())).length;

  return (
    <div className={`scan-app${sidebarCollapsed ? ' sidebar-collapsed' : ''}`}>
      <aside className="scan-sidebar">
        <div className="scan-brand"><Shield size={22} /><span>DataGuard AI</span><button onClick={() => setSidebarCollapsed((collapsed) => !collapsed)} title={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!sidebarCollapsed}><ChevronDown size={17} /></button></div>
        <button className="new-scan-button" onClick={() => setModalOpen(true)}><Plus size={18} /><span>New scan</span></button>
        <label className="scan-search"><Search size={16} /><input className="scan-search-input" aria-label="Search scans" value={scanQuery} onChange={(event) => setScanQuery(event.target.value)} placeholder="Search reviews" /></label>
        <nav className="scan-nav" aria-label="Dashboard sections">
          <a className="selected" href="#overview"><LayoutDashboard size={17} /><span>Dashboard</span></a>
          <a href="#findings"><FileWarning size={17} /><span>All issues</span></a>
          <a href="#reports"><ChartNoAxesCombined size={17} /><span>Reports</span></a>
        </nav>
        <div className="scan-list">
          {['Today', 'Yesterday', 'Previous 7 days', 'Older'].map((group) => {
            const groupScans = visibleScans.filter((scan) => scan.group === group);
            if (!groupScans.length) return null;
            return <div key={group}><p>{group}</p>{groupScans.map((scan) => <button className={selectedScan?.review?.id === scan.review?.id ? 'scan-item active' : 'scan-item'} key={scan.review?.id ?? scan.name} onClick={() => setSelectedScan(scan)}><span>{scan.name}</span><b>{scan.score}</b></button>)}</div>;
          })}
          {!loading && visibleScans.length === 0 && <p className="scan-no-results">No saved reviews yet</p>}
        </div>
        <div className="scan-user"><span>{(auth.fullName || 'Developer').charAt(0).toUpperCase()}</span><div><b>{auth.fullName || 'Developer'}</b><small>{auth.email || ''}</small></div></div>
      </aside>
      <main className="scan-main" id="overview">
        <header className="scan-header"><div className="scan-project-title"><button className="mobile-menu" onClick={() => setSidebarCollapsed((collapsed) => !collapsed)} title="Toggle sidebar" aria-label="Toggle sidebar"><History size={20} /></button><div><h1>{selectedScan?.name || 'Your code workspace'}</h1><p>{activeReview ? `Review saved · ${activeReview.reviewDate ? new Date(activeReview.reviewDate).toLocaleString() : 'just now'}` : 'Your saved project reviews'}</p></div></div><div className="scan-actions"><button className="rescan-button" onClick={() => setModalOpen(true)}>New review</button><Bot size={25} /></div></header>
        {loadError && <p className="form-error" role="alert">{loadError} <button className="text-button" onClick={refreshReviews}>Retry</button></p>}
        <section className="quality-banner"><div><span>Review status</span><h2>{activeReview ? 'Quality check complete' : loading ? 'Loading your reviews…' : 'Ready for your first review'}</h2></div><strong className="passed-pill">{activeReview ? 'Saved' : 'Awaiting scan'}</strong><div className="grade-row"><div><b>A</b><span>Reliability</span></div><div><b className="grade-b">B</b><span>Security</span></div><div><b>A</b><span>Maintainability</span></div></div></section>
        <section className="metric-grid"><Metric label="Findings" value={activeReview ? findings.length : '—'} /><Metric label="High / critical" value={activeReview ? countSeverity(['HIGH', 'CRITICAL']) : '—'} tone="red" /><Metric label="Low / medium" value={activeReview ? countSeverity(['LOW', 'MEDIUM']) : '—'} tone="amber" /><Metric label="Quality score" value={activeReview ? `${activeReview.qualityScore ?? 0}/100` : '—'} tone="green" /></section>
        <section className="report-grid" id="reports"><div className="report-card quality-card"><h2>Code quality</h2><div className="score-ring"><strong>{activeReview?.qualityScore ?? '—'}</strong></div><span>out of 100</span></div><div className="report-card chart-card"><h2>Review history</h2><p>{scanList.length > 1 ? `${scanList.length} saved reviews. Select a review on the left to inspect its results.` : 'Your quality trend will appear here as you complete more reviews.'}</p></div></section>
        <div id="findings">{loading && !activeReview ? <section className="finding-panel"><p>Loading saved findings…</p></section> : <FindingList review={activeReview} />}</div>
        {modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={addReview} />}
      </main>
    </div>
  );
}

function Metric({ label, value, tone = '' }) {
  return <article className={`metric-card ${tone}`}><span>{label}</span><strong>{value}</strong></article>;
}
