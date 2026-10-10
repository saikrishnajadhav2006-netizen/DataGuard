import { useEffect, useState } from 'react';
import { History as HistoryIcon, RefreshCw } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';
import NewReviewModal from '../components/NewReviewModal';
import FindingList from '../components/FindingList';
import { getReviews } from '../lib/api';

export default function History() {
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
      setError(requestError.message || 'Could not load review history.');
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
      <div className="page-header"><div><p className="eyebrow">Traceability</p><h1>Review history</h1><p>Past reviews are loaded from your account in Aiven PostgreSQL.</p></div><button className="icon-button" onClick={refresh} title="Refresh history"><RefreshCw size={17} /></button></div>
      {error && <p className="form-error" role="alert">{error} <button className="text-button" onClick={refresh}>Retry</button></p>}
      {loading && <section className="empty-panel"><p>Loading saved review history…</p></section>}
      {!loading && reviews.length === 0 && <section className="empty-panel"><HistoryIcon size={42} /><h2>No review history yet</h2><p>Start a ZIP review. Its score and findings will be saved to your account.</p><button className="primary-button" onClick={() => setModalOpen(true)}>Start a review</button></section>}
      {reviews.length > 0 && <section className="history-page-grid"><div className="project-list-panel"><h2><HistoryIcon size={19} /> Past reviews</h2>{reviews.map((review) => <button key={review.id} className={selected?.id === review.id ? 'project-history-row selected' : 'project-history-row'} onClick={() => setSelected(review)}><span><strong>{review.projectName}</strong><small>{review.reviewDate ? new Date(review.reviewDate).toLocaleString() : 'Saved review'} · {review.findings?.length ?? 0} findings</small></span><b>{review.qualityScore ?? '—'}/100</b></button>)}</div><div className="project-findings-panel"><FindingList review={selected} /></div></section>}
      {modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={handleComplete} />}
    </DashboardLayout>
  );
}
