import { useState } from 'react';
import { History as HistoryIcon } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';
import NewReviewModal from '../components/NewReviewModal';

export default function History() {
  const [modalOpen, setModalOpen] = useState(false);
  const [latestReview, setLatestReview] = useState(null);

  function handleComplete(review, name) {
    setLatestReview({ ...review, projectName: review.projectName || name });
    setModalOpen(false);
  }

  return (
    <DashboardLayout onNewReview={() => setModalOpen(true)}>
      <div className="page-header">
        <div><p className="eyebrow">Traceability</p><h1>Review history</h1><p>Review summaries from this session appear here after you scan a project.</p></div>
      </div>
      {latestReview ? (
        <section className="empty-panel history-result">
          <HistoryIcon size={36} />
          <h2>{latestReview.projectName}</h2>
          <p>Quality score: <strong>{latestReview.qualityScore ?? latestReview.score ?? '—'}/100</strong></p>
          <p>{latestReview.findings?.length ?? 0} findings returned by the latest review.</p>
          <button className="primary-button" onClick={() => setModalOpen(true)}>Review another project</button>
        </section>
      ) : (
        <section className="empty-panel">
          <HistoryIcon size={42} />
          <h2>No review in this session yet</h2>
          <p>Start a ZIP review to see its summary here.</p>
          <button className="primary-button" onClick={() => setModalOpen(true)}>Start a review</button>
        </section>
      )}
      {modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={handleComplete} />}
    </DashboardLayout>
  );
}
