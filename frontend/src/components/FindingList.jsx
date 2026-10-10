import { AlertTriangle, CheckCircle2, Download, LoaderCircle, Wrench } from 'lucide-react';
import { useState } from 'react';
import { downloadReviewedProject, generateFix } from '../lib/api';

export default function FindingList({ review }) {
  const [selectedFix, setSelectedFix] = useState(null);
  const [loadingId, setLoadingId] = useState(null);
  const [downloadError, setDownloadError] = useState('');
  const [downloading, setDownloading] = useState(false);
  const findings = review?.findings || [];

  async function suggestFix(finding) {
    setLoadingId(finding.id);
    setSelectedFix(null);
    try { setSelectedFix(await generateFix(review.id, finding.id)); }
    catch (error) { setSelectedFix({ error: error.message || 'Could not generate a fix preview.' }); }
    finally { setLoadingId(null); }
  }

  async function downloadProject() {
    setDownloading(true);
    setDownloadError('');
    try { await downloadReviewedProject(review.id, review.projectName); }
    catch (error) { setDownloadError(error.message || 'Download failed.'); }
    finally { setDownloading(false); }
  }

  if (!review) return <section className="finding-panel empty-findings"><CheckCircle2 size={22} /><p>Run a scan to see exact mistakes and suggested fixes.</p></section>;

  function downloadPatch() {
    if (!selectedFix || selectedFix.error) return;
    const patch = [
      '# DataGuard AI suggested fix',
      `# Finding ID: ${selectedFix.findingId ?? 'not supplied'}`,
      `# Evidence: ${selectedFix.evidence || ''}`,
      '# Review this suggestion and apply it manually to the matching source file.',
      '',
      selectedFix.suggestedCode || '',
      '',
    ].join('\n');
    const blob = new Blob([patch], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `dataguard-fix-${selectedFix.findingId ?? 'suggestion'}.patch.txt`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return <section className="finding-panel">
    <div className="finding-heading"><div><p className="eyebrow">Review evidence</p><h2>{findings.length} findings in {review.projectName}</h2><p>Download the reviewed source archive or export an individual fix suggestion.</p></div><button className="primary-button" onClick={downloadProject} disabled={downloading}>{downloading ? <LoaderCircle className="spin" size={16} /> : <Download size={16} />}{downloading ? 'Preparing ZIP…' : 'Download project ZIP'}</button></div>
    {downloadError && <p className="form-error" role="alert">{downloadError}</p>}
    {findings.length === 0 && <p className="empty-state">No deterministic issues found in this scan.</p>}
    <div className="finding-list">{findings.map((finding) => <article className="finding-row" key={finding.id}><div className={`severity-dot ${String(finding.severity || 'low').toLowerCase()}`} /><div className="finding-main"><div className="finding-title"><strong>{finding.title}</strong><span>{finding.severity}</span></div><p>{finding.filePath}:{finding.lineNumber}</p><code>{finding.evidence}</code><small>{finding.recommendation}</small></div><button className="fix-button" onClick={() => suggestFix(finding)} disabled={loadingId === finding.id}>{loadingId === finding.id ? <LoaderCircle className="spin" size={16} /> : <Wrench size={16} />}Suggest fix</button></article>)}</div>
    {selectedFix && <div className="fix-preview"><div><p className="eyebrow">Safe patch preview</p><h3>{selectedFix.error ? 'Fix unavailable' : 'Review before applying'}</h3></div>{selectedFix.error ? <p className="form-error">{selectedFix.error}</p> : <><p>{selectedFix.description}</p><pre>{selectedFix.suggestedCode}</pre><button className="primary-button" onClick={downloadPatch}><Download size={16} />Download fix suggestion</button></>}<button className="icon-button" onClick={() => setSelectedFix(null)} title="Close preview">Close</button></div>}
  </section>;
}
