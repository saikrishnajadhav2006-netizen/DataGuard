import { FileArchive, LoaderCircle, Upload, X } from 'lucide-react';
import { useRef, useState } from 'react';
import { uploadProject } from '../lib/api';

export default function NewReviewModal({ onClose, onComplete }) {
  const [name, setName] = useState('');
  const [file, setFile] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef(null);

  function chooseFile(candidate) {
    if (!candidate) return;
    if (!candidate.name.toLowerCase().endsWith('.zip')) {
      setError('Please choose a ZIP archive. Individual file uploads are not supported by the current backend.');
      setFile(null);
      return;
    }
    if (candidate.size > 25 * 1024 * 1024) {
      setError('ZIP files must be 25 MB or smaller.');
      setFile(null);
      return;
    }
    setError('');
    setFile(candidate);
    if (!name.trim()) setName(candidate.name.replace(/\.zip$/i, ''));
  }

  async function submit(event) {
    event.preventDefault();
    if (!name.trim()) return setError('Enter a project name.');
    if (!file?.name.toLowerCase().endsWith('.zip')) return setError('Choose a .zip project archive.');
    setLoading(true);
    setError('');
    try {
      const auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}');
      if (!auth.token) throw new Error('Your session has expired. Please sign in again.');
      const review = await uploadProject(name.trim(), file, auth.token);
      onComplete(review, name.trim());
    } catch (requestError) {
      setError(requestError.message || 'The project could not be reviewed.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !loading && onClose()}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="review-title">
        <div className="modal-heading"><div><p className="eyebrow">New analysis</p><h2 id="review-title">Review a project</h2></div><button type="button" className="icon-button" onClick={onClose} disabled={loading} title="Close"><X size={19} /></button></div>
        <p className="modal-copy">Choose a ZIP archive or drag and drop it below. The current backend accepts ZIP projects up to 25 MB.</p>
        <form onSubmit={submit} className="review-form">
          <label>Project name<input required maxLength={100} value={name} onChange={(event) => setName(event.target.value)} placeholder="Inventory API" /></label>
          <div
            className={`file-drop ${dragging ? 'dragging' : ''}`}
            role="button"
            tabIndex={0}
            onClick={() => inputRef.current?.click()}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); inputRef.current?.click(); } }}
            onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files?.[0]); }}
            aria-label="Drop a ZIP project archive or click to browse"
          >
            <FileArchive size={30} />
            <span>{file ? file.name : 'Drag and drop your project ZIP here'}</span>
            <small>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB · Ready to upload` : 'or click to browse · ZIP only · max 25 MB'}</small>
            <input ref={inputRef} type="file" accept=".zip,application/zip" hidden onChange={(event) => chooseFile(event.target.files?.[0] || null)} />
          </div>
          {file && <button type="button" className="text-button" onClick={() => { setFile(null); if (inputRef.current) inputRef.current.value = ''; }}>Remove selected file</button>}
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button" disabled={loading || !file}>{loading ? <><LoaderCircle className="spin" size={18} />Analyzing...</> : <><Upload size={18} />Start review</>}</button>
        </form>
      </section>
    </div>
  );
}
