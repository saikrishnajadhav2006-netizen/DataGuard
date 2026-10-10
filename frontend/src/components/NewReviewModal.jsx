import { FileArchive, LoaderCircle, Upload, X } from 'lucide-react';
import { useState } from 'react';
import { uploadProject } from '../lib/api';
import { readAuth } from '../lib/authStorage';

export default function NewReviewModal({ onClose, onComplete, initialFile = null }) {
  const [name, setName] = useState('');
  const [file, setFile] = useState(initialFile);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const [isDragging, setIsDragging] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (!file?.name.toLowerCase().endsWith('.zip')) return setError('Choose a .zip project archive.');
    setLoading(true);
    setError('');
    try {
      const auth = readAuth();
      const review = await uploadProject(name, file, auth.token);
      onComplete(review, name);
    } catch (requestError) {
      setError(requestError.message || 'The project could not be reviewed.');
    } finally {
      setLoading(false);
    }
  }

  function handleDrop(e) {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setFile(e.dataTransfer.files[0]);
      e.dataTransfer.clearData();
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="review-title">
        <div className="modal-heading"><div><p className="eyebrow">New analysis</p><h2 id="review-title">Review a project</h2></div><button className="icon-button" onClick={onClose} title="Close"><X size={19} /></button></div>
        <p className="modal-copy">Upload the complete project structure as a ZIP archive. DataGuard scans relevant source files and returns a quality score. ZIP uploads are limited to 25 MB.</p>
        <form onSubmit={submit} className="review-form">
          <label>Project name<input required value={name} onChange={(event) => setName(event.target.value)} placeholder="Inventory API" /></label>
          <label 
            className={`file-drop ${isDragging ? 'drag-active' : ''}`}
            onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
            onDragLeave={e => { e.preventDefault(); setIsDragging(false); }}
            onDrop={handleDrop}
          >
            <FileArchive size={28} />
            <span>{file ? file.name : 'Choose a ZIP archive or drag here'}</span>
            <small>Source folders and build files are accepted</small>
            <input required type="file" accept=".zip,application/zip" onChange={(event) => setFile(event.target.files?.[0] || null)} />
          </label>
          {error && <p className="form-error">{error}</p>}
          <button className="primary-button" disabled={loading}>{loading ? <><LoaderCircle className="spin" size={18} />Analyzing...</> : <><Upload size={18} />Start review</>}</button>
        </form>
      </section>
    </div>
  );
}
