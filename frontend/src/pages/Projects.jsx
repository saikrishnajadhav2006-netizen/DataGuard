import { useState } from 'react';
import { FolderKanban, Plus } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';
import NewReviewModal from '../components/NewReviewModal';
import FindingList from '../components/FindingList';

export default function Projects() {
  const [modalOpen, setModalOpen] = useState(false);
  const [review, setReview] = useState(null);

  function handleComplete(result, name) {
    setReview({ ...result, projectName: result.projectName || name });
    setModalOpen(false);
  }

  return (
    <DashboardLayout onNewReview={() => setModalOpen(true)}>
      <div className="page-header">
        <div>
          <p className="eyebrow">Workspace</p>
          <h1>Projects</h1>
          <p>Upload a project archive to run a review and inspect its findings.</p>
        </div>
        <button className="primary-button" onClick={() => setModalOpen(true)}><Plus size={18} />New review</button>
      </div>
      {review ? (
        <FindingList review={review} />
      ) : (
        <section className="empty-panel">
          <FolderKanban size={42} />
          <h2>Your project shelf is ready</h2>
          <p>Start a review to create your first project report.</p>
          <button className="primary-button" onClick={() => setModalOpen(true)}><Plus size={18} />Start first review</button>
        </section>
      )}
      {modalOpen && <NewReviewModal onClose={() => setModalOpen(false)} onComplete={handleComplete} />}
    </DashboardLayout>
  );
}
