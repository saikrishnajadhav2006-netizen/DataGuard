import { useEffect, useState } from 'react';
import { FolderKanban, Plus, RefreshCw } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';
import NewReviewModal from '../components/NewReviewModal';
import FindingList from '../components/FindingList';
import { getReviews } from '../lib/api';

export default function Projects() {
  const [modalOpen, setModalOpen] = useState(false);
  const [reviews, setReviews] = useState([]);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  async function refresh() {
    setLoading(true);
    setError('');
    try {
      const data = await getReviews();
      setReviews(data);
      setSelected((current) => data.find((item) => item.id === current?.id) || data[0] || null);
    } catch (requestError) {
      setError(requestError.message || 'Could not load saved projects.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refresh(); }, []);

  function handleComplete(result, name) {
    const review = { ...result, projectName: result.projectName || name };
    setReviews((current) => [review, ...current.filter((item) => item.id !== review.id)]);
    setSelected(review);
    setModalOpen(false);
  }

  return (
    <DashboardLayout onNewReview={() => setModalOpen(true)}>
      <div className="page-header"><div><p className="eyebrow">Workspace</p><h1>Projects</h1><p>Your saved project reviews, loaded from the DataGuard API.</p></div><button className="primary-button" onClick={() => setModalOpen(true)}><Plus size={18} />New review</button></div>
      {error && <p className="form-error" role="alert">{error} <button className="text-button" onClick={refresh}><RefreshCw size={14} /> Retry</button></p>}
      <section className="project-history-grid">
        <div className="project-list-panel">
          <h2><FolderKanban size={19} /> Saved reviews</h2>
          {loading && <p>Loading saved projects…</p>}
          {!loading && reviews.length === 0 && <div className="empty-panel"><FolderKanban size={36} /><h3>No saved projects yet</h3><p>Upload a ZIP archive to create your first review.</p><button className="primary-button" onClick={() => setModalOpen(true)}><Plus size={16} />Start review</button></div>}
          {reviews.map((review) => <button key={review.id} className={selected?.id === review.id ? 'project-history-row selected' : 'project-history-row'} onClick={() => setSelected(review)}><span><strong>{review.projectName}</strong><small>{review.reviewDate ? new Date(review.reviewDate).toLocaleString() : 'Saved review'}</small></span><b>{review.qualityScore ?? '—'}/100</b></button>)}
        </div>
        <div className="project-findings-panel"><FindingList review={selected} /></div>
      </section>
      {modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={handleComplete} />}
    </DashboardLayout>
  );
}
