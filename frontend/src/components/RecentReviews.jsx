import { ArrowUpRight, FileCheck2 } from 'lucide-react';

export default function RecentReviews({ reviews = [] }) {
  return (
    <section className="content-section">
      <div className="section-heading"><div><p className="eyebrow">Your workspace</p><h2>Recent reviews</h2></div><FileCheck2 size={22} /></div>
      <div className="review-list">
        {reviews.length === 0 && <p className="empty-state">No reviews yet. Upload a project to see its quality report here.</p>}
        {reviews.map((review) => (
          <div className="review-row" key={review.id}>
            <div className="review-title"><span className="review-dot" /><div><strong>{review.name}</strong><p>{review.issues} findings · {review.date}</p></div></div>
            <div className="review-score"><span>Quality score</span><strong>{review.score}</strong></div>
            <button className="icon-button" title="Open report"><ArrowUpRight size={18} /></button>
          </div>
        ))}
      </div>
    </section>
  );
}