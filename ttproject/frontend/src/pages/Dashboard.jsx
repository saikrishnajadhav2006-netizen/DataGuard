import { Bot, ChartNoAxesCombined, ChevronDown, FileWarning, History, LayoutDashboard, Plus, Search, Settings, Shield } from 'lucide-react';
import { useState } from 'react';
import NewReviewModal from '../components/NewReviewModal';
import FindingList from '../components/FindingList';

const scans = [
  { name: 'my-shop-app', score: 78, group: 'Today' },
  { name: 'portfolio-site', score: 91, group: 'Today' },
  { name: 'chat-backend', score: 64, group: 'Yesterday' },
  { name: 'todo-react', score: 86, group: 'Yesterday' },
  { name: 'college-erp', score: 52, group: 'Previous 7 days' },
];

export default function Dashboard() {
  const [modalOpen, setModalOpen] = useState(false);
  const [selectedScan, setSelectedScan] = useState(scans[0]);
  const [localReview, setLocalReview] = useState(null);
  const auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}');

  function addReview(review, name) {
    setLocalReview({ ...review, name, score: review.qualityScore });
    setSelectedScan({ name, score: review.qualityScore, group: 'Just now' });
    setModalOpen(false);
  }

  return <div className="scan-app"><aside className="scan-sidebar"><div className="scan-brand"><Shield size={22} />DataGuard AI<button title="Collapse sidebar"><ChevronDown size={17} /></button></div><button className="new-scan-button" onClick={() => setModalOpen(true)}><Plus size={18} /> New scan</button><div className="scan-search"><Search size={16} />Search scans</div><nav className="scan-nav"><a className="selected"><LayoutDashboard size={17} />Dashboard</a><a><FileWarning size={17} />All issues</a><a><ChartNoAxesCombined size={17} />Reports</a></nav><div className="scan-list">{['Today', 'Yesterday', 'Previous 7 days'].map((group) => <div key={group}><p>{group}</p>{scans.filter((scan) => scan.group === group).map((scan) => <button className={selectedScan.name === scan.name ? 'scan-item active' : 'scan-item'} key={scan.name} onClick={() => setSelectedScan(scan)}><span>{scan.name}</span><b>{scan.score}</b></button>)}</div>)}</div><div className="scan-user"><span>{(auth.fullName || 'Developer').charAt(0).toUpperCase()}</span><div><b>{auth.fullName || 'Developer'}</b><small>Free plan</small></div><Settings size={17} /></div></aside><main className="scan-main"><header className="scan-header"><div className="scan-project-title"><button className="mobile-menu" title="Menu"><History size={20} /></button><div><h1>{selectedScan.name}</h1><p>Scanned today · 128 files</p></div></div><div className="scan-actions"><button className="rescan-button" onClick={() => setModalOpen(true)}>Re-scan</button><Bot size={25} /></div></header><section className="quality-banner"><div><span>Status</span><h2>Quality Gate</h2></div><strong className="passed-pill">Passed</strong><div className="grade-row"><div><b>A</b><span>Reliability</span></div><div><b className="grade-b">B</b><span>Security</span></div><div><b>A</b><span>Maintainability</span></div></div></section><section className="metric-grid"><Metric label="Files scanned" value={localReview ? localReview.findings.length : '128'} /><Metric label="Total errors" value={localReview ? localReview.findings.filter((finding) => ['HIGH', 'CRITICAL'].includes(finding.severity)).length : '42'} tone="red" /><Metric label="Warnings" value={localReview ? localReview.findings.filter((finding) => finding.severity === 'LOW' || finding.severity === 'MEDIUM').length : '67'} tone="amber" /><Metric label="Fixed" value="0" tone="green" /></section><section className="report-grid"><div className="report-card quality-card"><h2>Code quality</h2><div className="score-ring"><strong>{localReview?.score || selectedScan.score}</strong></div><span>out of 100</span></div><div className="report-card chart-card"><h2>Issues over time</h2><div className="fake-chart"><svg viewBox="0 0 600 180" preserveAspectRatio="none"><path d="M0 20 L100 55 L200 42 L300 100 L400 126 L500 150 L600 170 L600 180 L0 180Z" /><polyline points="0,20 100,55 200,42 300,100 400,126 500,150 600,170" /></svg></div><p>Errors dropping across the last 7 scans</p></div></section><FindingList review={localReview} />{modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={addReview} />}</main></div>;
}

function Metric({ label, value, tone = '' }) { return <article className={`metric-card ${tone}`}><span>{label}</span><strong>{value}</strong></article>; }
