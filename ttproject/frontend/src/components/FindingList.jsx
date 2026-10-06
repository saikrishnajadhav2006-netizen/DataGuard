import { AlertTriangle, CheckCircle2, LoaderCircle, Wrench } from 'lucide-react';
import { useState } from 'react';
import { generateFix } from '../lib/api';

export default function FindingList({ review }) {
  const [selectedFix, setSelectedFix] = useState(null);
  const [loadingId, setLoadingId] = useState(null);
  const findings = review?.findings || [];

  async function suggestFix(finding) {
    setLoadingId(finding.id);
    try {
      setSelectedFix(await generateFix(review.id, finding.id));
    } catch (error) {
      setSelectedFix({ error: error.message || 'Could not generate a fix preview.' });
    } finally {
      setLoadingId(null);
    }
  }

  if (!review) return <section className="finding-panel empty-findings"><CheckCircle2 size={22} /><p>Run a scan to see exact mistakes and suggested fixes.</p></section>;

  return <section className="finding-panel"><div className="finding-heading"><div><p className="eyebrow">Review evidence</p><h2>{findings.length} findings in {review.projectName}</h2></div><AlertTriangle size={22} /></div>{findings.length === 0 && <p className="empty-state">No deterministic issues found in this scan.</p>}<div className="finding-list">{findings.map((finding) => <article className="finding-row" key={finding.id}><div className={`severity-dot ${finding.severity.toLowerCase()}`} /><div className="finding-main"><div className="finding-title"><strong>{finding.title}</strong><span>{finding.severity}</span></div><p>{finding.filePath}:{finding.lineNumber}</p><code>{finding.evidence}</code><small>{finding.recommendation}</small></div><button className="fix-button" onClick={() => suggestFix(finding)} disabled={loadingId === finding.id}>{loadingId === finding.id ? <LoaderCircle className="spin" size={16} /> : <Wrench size={16} />}Suggest fix</button></article>)}</div>{selectedFix && <div className="fix-preview"><div><p className="eyebrow">Safe patch preview</p><h3>{selectedFix.error ? 'Fix unavailable' : 'Review before applying'}</h3></div>{selectedFix.error ? <p className="form-error">{selectedFix.error}</p> : <><p>{selectedFix.description}</p><pre>{selectedFix.suggestedCode}</pre></>}<button className="icon-button" onClick={() => setSelectedFix(null)} title="Close preview">Close</button></div>}</section>;
}