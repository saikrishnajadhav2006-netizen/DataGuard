import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { UploadCloud, FileArchive, Shield } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Icon, SvgDefs } from '../components/Icons';
import NewReviewModal from '../components/NewReviewModal';
import { getReviews, generateFix, approveFix, approveZipFix, getFixedProjectStatus, downloadFixedProject, reReviewFixedProject, radarChat } from '../lib/api';
import ProfileMenu from '../components/ProfileMenu';
import { readAuth } from '../lib/authStorage';
import '../styles/dashboard.css';

export default function Dashboard() {
  const [modalOpen, setModalOpen] = useState(false);
  const [pendingFile, setPendingFile] = useState(null);
  const [isDraggingUpload, setIsDraggingUpload] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(window.innerWidth < 900);
  const [fixPanelOpen, setFixPanelOpen] = useState(false);
  const [reviews, setReviews] = useState([]);
  const [localReview, setLocalReview] = useState(null);
  
  const [status, setStatus] = useState('idle'); // idle, run, done
  const [statusText, setStatusText] = useState('Waiting for upload...');
  const [progress, setProgress] = useState(0);
  
  const [fixes, setFixes] = useState({}); // mapping findingId to FixResponse
  const [fixedProject, setFixedProject] = useState(null);
  const [zipWorkflow, setZipWorkflow] = useState(false);
  const [zipFixBusy, setZipFixBusy] = useState(null);
  const [zipDownloadBusy, setZipDownloadBusy] = useState(false);
  const [zipReReviewBusy, setZipReReviewBusy] = useState(false);
  const [zipFixError, setZipFixError] = useState('');
  const [reviewedFindings, setReviewedFindings] = useState(new Set());
  const [chatMessages, setChatMessages] = useState([]);
  const [chatInput, setChatInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  const [chatConversationId, setChatConversationId] = useState(null);
  const [chatMode, setChatMode] = useState('normal');
  
  const auth = readAuth();
  const navigate = useNavigate();
  const messagesEndRef = useRef(null);

  const acceptDashboardDrop = (event) => {
    event.preventDefault();
    setIsDraggingUpload(false);
    const file = event.dataTransfer.files?.[0];
    if (!file) return;
    setPendingFile(file);
    setModalOpen(true);
  };

  useEffect(() => {
    if (auth.token) {
      getReviews(auth.token).then(data => {
        setReviews(data);
        if (data.length > 0) {
          const latest = data[0];
          setLocalReview(latest);
          getFixedProjectStatus(latest.id).then(value => { setFixedProject(value); setZipWorkflow(true); }).catch(() => { setFixedProject(null); setZipWorkflow(false); });
          setStatus('done');
          setStatusText(`Quality checked · ${latest.overallScore}/100 · ${latest.findings.length} issues`);
          addMessage('bot', `I've loaded your most recent scan for ${latest.projectName}.`);
        }
      }).catch(console.error);
    } else {
      navigate('/login');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [chatMessages, status]);

  const addMessage = (role, content, attach = null) => {
    setChatMessages(prev => [...prev, { role, content, attach }]);
  };

  const handleUploadComplete = (review, name) => {
    setLocalReview(review);
    setReviews(prev => [review, ...prev.filter(r => r.id !== review.id)]);
    setModalOpen(false);
    
    setStatus('run');
    setFixPanelOpen(false);
    setReviewedFindings(new Set());
    setFixes({});
    setFixedProject(null);
    setZipWorkflow(true);
    setZipFixError('');
    setChatMessages([]);
    setChatConversationId(null);
    
    addMessage('user', 'Please check my project', `${name}.zip`);
    setProgress(100);
    setStatus('done');
    setStatusText(`Quality checked · ${review.overallScore}/100 · ${review.findings.length} issues`);
    addMessage('bot', `Quality check complete. Score ${review.overallScore}/100 · ${review.findings.length} findings. Open the fix panel to review suggestions.`);
  };

  const handleGenerateFix = async (findingId) => {
    if (fixes[findingId]) return fixes[findingId];
    try {
      const response = await generateFix(localReview.id, findingId);
      setFixes(prev => ({ ...prev, [findingId]: response }));
      return response;
    } catch (e) {
      addMessage('bot', `I couldn't prepare a suggested fix: ${e.message || 'request failed'}.`);
      return null;
    }
  };

  const handleApplyFix = async (findingId) => {
    const proposal = fixes[findingId];
    if (!proposal?.safeToApply || !proposal.patch) {
      addMessage('bot', proposal?.reason || 'This finding has no verified automatic fix.');
      return;
    }
    if (!window.confirm(`Create a separate GitHub fix branch and pull request for ${proposal.filePath}? The reviewed branch will not be modified.`)) return;
    try {
      const result = await approveFix(localReview.id, findingId, proposal);
      setFixes(prev => ({ ...prev, [findingId]: { ...prev[findingId], applyResult: result } }));
      addMessage('bot', `GitHub confirmed pull request #${result.pullRequestNumber}: ${result.pullRequestUrl}`);
    } catch (e) {
      addMessage('bot', `GitHub did not confirm the fix pull request: ${e.message || 'request failed'}. Check the repository before retrying.`);
    }
  };

  const handleApproveZipFix = async (findingId) => {
    if (!window.confirm('Approve this inspected change and create a separate fixed project ZIP? Your original upload will remain unchanged.')) return;
    setZipFixBusy(findingId); setZipFixError('');
    try {
      const result = await approveZipFix(localReview.id, findingId);
      setFixedProject(result);
      setFixes(prev => ({ ...prev, [findingId]: { ...prev[findingId], zipApplied: true } }));
    } catch (e) { setZipFixError(e.message || 'Could not prepare the fixed project ZIP.'); }
    finally { setZipFixBusy(null); }
  };

  const handleReReviewFixedProject = async () => {
    if (!fixedProject?.ready || !localReview) return;
    setZipReReviewBusy(true);
    setZipFixError('');
    try {
      const updated = await reReviewFixedProject(localReview.id);
      setLocalReview(updated);
      setReviews(prev => prev.map(r => (r.id === updated.id ? updated : r)));
      setFixes({});
      setReviewedFindings(new Set());
      setStatus('done');
      setStatusText(`Quality checked · ${updated.overallScore}/100 · ${updated.findings.length} issues`);
      addMessage('bot', `Re-analyzed the fixed project. Score ${updated.overallScore}/100 · ${updated.findings.length} remaining finding(s).`);
    } catch (e) {
      setZipFixError(e.message || 'Re-analysis failed.');
    } finally {
      setZipReReviewBusy(false);
    }
  };

  const handleDownloadFixedProject = async () => {
    if (!fixedProject?.ready) return;
    setZipDownloadBusy(true); setZipFixError('');
    try {
      const blob = await downloadFixedProject(localReview.id);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'project-fixed.zip';
      document.body.appendChild(anchor); anchor.click(); anchor.remove(); URL.revokeObjectURL(url);
    } catch (e) { setZipFixError(e.message || 'Download failed.'); }
    finally { setZipDownloadBusy(false); }
  };

  const handleSelectReview = (review) => {
    setLocalReview(review);
    setFixes({}); setFixedProject(null); setZipFixError('');
    getFixedProjectStatus(review.id).then(value => { setFixedProject(value); setZipWorkflow(true); })
      .catch(() => setZipWorkflow(false));
  };

  const handleExplain = async (findingId) => {
    const finding = localReview.findings.find(f => f.id === findingId);
    let fix = fixes[findingId];
    if (!fix) {
      fix = await handleGenerateFix(findingId);
    }
    setFixPanelOpen(true);
    addMessage('user', `Explain fix for ${finding.filePath}`);
    if (fix) {
      addMessage('bot', `${finding.filePath}:${finding.lineNumber || ''}: ${fix.description}`);
    } else {
      addMessage('bot', `Sorry, I couldn't generate an explanation for this issue.`);
    }
  };

  const markReviewed = (findingId) => {
    setReviewedFindings(prev => {
      const next = new Set(prev);
      next.add(findingId);
      return next;
    });
  };

  const markAllReviewed = () => {
    if (!localReview) return;
    const allIds = localReview.findings.map(f => f.id);
    setReviewedFindings(new Set(allIds));
  };

  const startListening = () => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      addMessage('bot', 'Voice input is not supported by this browser. Type your question instead.');
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.lang = 'en-US';
    
    recognition.onstart = () => {
      setChatInput('Listening...');
    };
    
    recognition.onresult = (event) => {
      const transcript = event.results[0][0].transcript;
      setChatInput(transcript);
      // optionally trigger send immediately
    };
    
    recognition.onerror = (event) => {
      setChatInput('');
      addMessage('bot', `Voice input stopped (${event.error}). Type your question instead.`);
    };
    
    recognition.start();
  };

  const sendChat = async (e, suggestedMessage) => {
    e?.preventDefault();
    const msg = (suggestedMessage ?? chatInput).trim();
    if (!msg || msg === 'Listening...' || chatLoading) return;
    setChatInput('');
    addMessage('user', msg);
    if (msg.toLowerCase().includes('fix')) {
      setFixPanelOpen(true);
    }
    setChatLoading(true);
    try {
      const data = await radarChat(msg, 'find', chatConversationId);
      setChatConversationId(data.conversationId);
      const assistant = [...(data.messages || [])].reverse().find(m => m.role === 'assistant');
      addMessage('bot', assistant?.content || data.reply || 'The AI response was empty.');
    } catch (error) {
      addMessage('bot', `AI assistant unavailable: ${error.message || 'the request failed.'}`);
    } finally {
      setChatLoading(false);
    }
  };

  const currentScore = localReview?.overallScore || 0;
  
  useEffect(() => {
    if (localReview && status === 'done') {
      setStatusText(`Quality checked · ${currentScore}/100 · ${localReview.findings.length} issues`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentScore, localReview, status]);

  return (
    <div className={`app ${sidebarCollapsed ? 'sbc' : ''} ${status === 'done' ? 'rdy' : ''} ${fixPanelOpen ? 'fxo' : ''}`}>
      <SvgDefs />
      
      <aside className="side">
        <div className="brand">
          <Link to="/" className="dashboard-brand-link"><Shield size={21} fill="currentColor" /> DataGuard AI</Link>
          <button title="Slide out sidebar" onClick={() => setSidebarCollapsed(true)}>
            <Icon name="left" />
          </button>
        </div>
        
        <button className="new" onClick={() => setModalOpen(true)}>
          <Icon name="plus" /> New scan
        </button>
        
        <nav className="dashboard-nav" aria-label="Workspace navigation">
          <button className="on" onClick={() => navigate('/dashboard')}><Icon name="shield" />Code check</button>
          <button onClick={() => navigate('/ai-radar')}><Icon name="spark" />AI Radar<span>NEW</span></button>
        </nav>
        
        <div className="pn">
          <div className="gl">{reviews.length ? 'Recent reviews' : 'Your reviews'}</div>
          {reviews.map(r => (
            <button type="button" key={r.id} className={`hi ${localReview?.id === r.id ? 'on' : ''}`} onClick={() => handleSelectReview(r)}>
              <s style={{ '--c': r.overallScore > 80 ? 'var(--a)' : r.overallScore > 50 ? 'var(--warn)' : 'var(--a2)' }}></s>
              <span>{r.projectName.includes('/') ? <><Icon name="github" style={{width: 14, height: 14, display: 'inline-block', verticalAlign: 'middle', marginRight: 4}}/>{r.projectName}</> : r.projectName}</span>
              <em>{r.overallScore}</em>
            </button>
          ))}
          {reviews.length === 0 && <div className="gl" style={{ textTransform: 'none' }}>No recent scans</div>}
        </div>
        
        {localReview?.findings?.length > 0 && <details className="dashboard-file-list">
          <summary>Files and findings</summary>
          {localReview.findings.map(f => (
            <div key={f.id} className="hi sub">
              <Icon name="file" />
              <span>{f.filePath.split('/').pop()}</span>
              <b className={`bg ${f.severity === 'CRITICAL' ? 'w' : ''}`}>{f.lineNumber || '1'}</b>
            </div>
          ))}
        </details>}
        
        <ProfileMenu auth={auth} />
      </aside>
      
      <section className="center">
        <header className="top">
          <div className="l">
            <button className="ib" onClick={() => setSidebarCollapsed(!sidebarCollapsed)} title="Toggle sidebar">
              <Icon name="menu" />
            </button>
            <div className="pj">
              <b>{localReview?.projectName?.includes('/') ? <><Icon name="github" style={{width: 18, height: 18, display: 'inline-block', verticalAlign: 'middle', marginRight: 6}}/>{localReview.projectName}</> : (localReview?.projectName || 'Welcome')}</b>
              <small>{localReview ? 'Analyzed project' : 'Select a project'}</small>
            </div>
          </div>
          
          <div className="st" data-s={status}>
            <span className="spn"></span>
            <Icon name="check" className="ckk" />
            <span className="tx">{statusText}</span>
            <div className="pgb"><i style={{ width: `${progress}%` }}></i></div>
            <span className="pc">{Math.round(progress)}%</span>
            <button className="fb" onClick={() => setFixPanelOpen(true)}>
              <Icon name="spark" style={{ width: 16, height: 16 }} /> Fix the code
            </button>
          </div>
          
          <div className="r">
            <button className="rs" onClick={() => setModalOpen(true)}>
              <Icon name="refresh" style={{ width: 17, height: 17 }} />
              <span>Re-scan</span>
            </button>
            <button className="ib nb" id="ft" title="Fix panel" onClick={() => setFixPanelOpen(!fixPanelOpen)}>
              <Icon name="panel" />
            </button>
          </div>
        </header>
        
        <div className="body">
          {!localReview && (
            <section className={`dashboard-upload ${isDraggingUpload ? 'drag-active' : ''}`} onDragOver={event => { event.preventDefault(); setIsDraggingUpload(true); }} onDragLeave={event => { if (!event.currentTarget.contains(event.relatedTarget)) setIsDraggingUpload(false); }} onDrop={acceptDashboardDrop}>
              <div className="dashboard-upload-icon"><UploadCloud size={27} /></div>
              <h2>Start with a project scan</h2>
              <p>Drop a ZIP project archive here, or choose one to review.</p>
              <button className="rs" onClick={() => { setPendingFile(null); setModalOpen(true); }}><FileArchive size={17} /> Upload a ZIP</button>
              <small>ZIP archives up to 25 MB</small>
            </section>
          )}
          {localReview && (
            <div className="bh">
              <div className="ring" style={{ '--p': currentScore }}><b>{currentScore}</b></div>
              <div className="tt">
                <h3>{currentScore >= 80 ? 'Quality gate passed' : 'Needs improvement'}</h3>
                <div className="rt" style={{ marginTop: 8 }}>
                  <div><i style={{ background: localReview.qualityScore >= 80 ? 'var(--a)' : 'var(--warn)' }}><Icon name="shield" style={{width: 14, height: 14, color: 'white'}} /></i>Quality</div>
                  <div><i style={{ background: localReview.securityScore >= 80 ? 'var(--a)' : localReview.securityScore >= 50 ? 'var(--warn)' : 'var(--a2)' }}><Icon name="shield" style={{width: 14, height: 14, color: 'white'}} /></i>Security</div>
                  <div><i style={{ background: localReview.architectureScore >= 80 ? 'var(--a)' : 'var(--warn)' }}><Icon name="shield" style={{width: 14, height: 14, color: 'white'}} /></i>Arch.</div>
                </div>
              </div>
              <div className="ms"><b>{localReview.findings.length}</b>Found</div>
              <div className="ms"><b style={{ color: 'var(--a2)' }}>{localReview.findings.length - reviewedFindings.size}</b>Issues</div>
              <div className="ms"><b style={{ color: 'var(--a)' }}>{reviewedFindings.size}</b>Reviewed</div>
            </div>
          )}
          
          <div className={`chat ${chatMode === 'maximized' ? 'chat-max' : chatMode === 'minimized' ? 'chat-min' : ''}`}>
            <div className="chat-tools">
              {chatMode !== 'minimized' && (
                <button type="button" className="ib" style={{ width: 28, height: 28 }} onClick={() => setChatMode('minimized')} title="Minimize">
                  <Icon name="minus" style={{ width: 14, height: 14 }} />
                </button>
              )}
              {chatMode === 'minimized' && (
                <button type="button" className="ib" style={{ width: 28, height: 28 }} onClick={() => setChatMode('normal')} title="Restore">
                  <Icon name="plus" style={{ width: 14, height: 14 }} />
                </button>
              )}
              {chatMode !== 'maximized' && (
                <button type="button" className="ib" style={{ width: 28, height: 28 }} onClick={() => setChatMode('maximized')} title="Maximize">
                  <Icon name="expand" style={{ width: 14, height: 14 }} />
                </button>
              )}
              {chatMode === 'maximized' && (
                <button type="button" className="ib" style={{ width: 28, height: 28 }} onClick={() => setChatMode('normal')} title="Restore down">
                  <Icon name="shrink" style={{ width: 14, height: 14 }} />
                </button>
              )}
            </div>
            <div className="msgs">
              {chatMessages.map((msg, idx) => (
                <div key={idx} className={`mb ${msg.role === 'user' ? 'u' : ''}`}>
                  {msg.role === 'bot' && (
                    <div className="ba"><Icon name="bot" style={{ width: 18, height: 18 }} /></div>
                  )}
                  <div className="bub">
                    <span>{msg.content}</span>
                    {msg.attach && (
                      <><br /><span className="att"><Icon name="file" style={{ width: 14, height: 14 }} />{msg.attach}</span></>
                    )}
                  </div>
                </div>
              ))}
              <div ref={messagesEndRef} />
              {chatLoading && (
                <div className="mb">
                  <div className="ba"><Icon name="bot" style={{ width: 18, height: 18 }} /></div>
                  <div className="bub"><span>Thinking…</span></div>
                </div>
              )}
            </div>
            
            <div className="cp">
              <div className="chips">
                <button type="button" onClick={() => sendChat(undefined, 'Explain top issue')}>Explain top issue</button>
                <button type="button" onClick={() => sendChat(undefined, 'Fix the code')}>Fix the code</button>
                <button type="button" onClick={() => sendChat(undefined, 'How to improve my score?')}>How to improve score?</button>
              </div>
              <form className="cpb" onSubmit={sendChat}>
                <input value={chatInput} onChange={e => setChatInput(e.target.value)} placeholder="Ask about your code..." disabled={chatLoading} />
                <button type="button" className="ib" title="Voice Assistant" style={{ border: 'none', background: 'transparent' }} onClick={startListening} disabled={chatLoading}><Icon name="mic" style={{ width: 18, height: 18, color: chatInput === 'Listening...' ? 'var(--a2)' : 'inherit' }} /></button>
                <button type="submit" className="ib sd" disabled={chatLoading}><Icon name="send" /></button>
              </form>
              <small>AI-assisted analysis. Always review suggested changes before applying them.</small>
            </div>
          </div>
        </div>
      </section>
      
      <aside className="fx">
        <div className="fxi">
          <div className="fh">
            <Icon name="spark" />
            <div>
              <b>Fix the code</b>
              <small>{status === 'run' || status === 'idle' ? 'Waiting for quality check...' : `${reviewedFindings.size} of ${localReview?.findings.length || 0} marked reviewed`}</small>
            </div>
            <button className="ib" onClick={() => setFixPanelOpen(false)}><Icon name="x" /></button>
          </div>
          
          <div className="em">
            <Icon name="spark" style={{ width: 34, height: 34, margin: 'auto', opacity: 0.4 }} />
            Fixes will appear here once<br />the quality check is finished.
          </div>
          
          <div className="fl">
            {localReview && zipWorkflow && <section className="fcd" style={{ marginBottom: 12 }}>
              <div className="ttl">Fixed project ZIP</div>
              <p>{fixedProject?.ready
                ? `${fixedProject.modifiedFiles} modified file(s) · ${fixedProject.unresolvedFindings} finding(s) still unresolved${fixedProject.omittedSensitiveFiles ? ` · ${fixedProject.omittedSensitiveFiles} sensitive/internal path(s) excluded` : ''}. Review the approved changes before using the archive.`
                : 'Approve a supported fix to build a separate, verified ZIP. Your original upload stays unchanged.'}</p>
              {zipFixError && <p role="alert" className="form-error">{zipFixError}</p>}
              <button type="button" className="ap1" onClick={handleReReviewFixedProject} disabled={!fixedProject?.ready || zipReReviewBusy || zipDownloadBusy}>
                {zipReReviewBusy ? 'Re-analyzing…' : 'Re-run analysis on fixed ZIP'}
              </button>
              <button type="button" className="ap1" onClick={handleDownloadFixedProject} disabled={!fixedProject?.ready || zipDownloadBusy || zipReReviewBusy}>
                {zipDownloadBusy ? 'Preparing download…' : 'Download Fixed Project (.zip)'}
              </button>
            </section>}
            {localReview?.findings.map(f => {
              const isReviewed = reviewedFindings.has(f.id);
              const fix = fixes[f.id];
              const sevColor = f.severity === 'CRITICAL' || f.severity === 'HIGH' ? 'var(--a2)' : f.severity === 'MEDIUM' ? 'var(--warn)' : 'var(--a)';
              
              return (
                <div key={f.id} className={`fcd ${isReviewed ? 'ap' : ''}`}>
                  <div className="fch">
                    <span className="sv" style={{ background: sevColor, display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                      <Icon name={f.severity === 'CRITICAL' || f.severity === 'HIGH' ? 'alert-triangle' : 'info'} style={{width: 12, height: 12}} />
                      {f.severity}
                    </span>
                    <code>{f.filePath.split('/').pop()}:{f.lineNumber || '?'}</code>
                    <span className="ok">✓ Reviewed</span>
                  </div>
                  <div className="ttl">{f.title}</div>
                  <div className="df">
                    {fix?.patch ? (
                      <div className="a"><small>Suggested patch · inspect before approval</small><pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', margin: '8px 0 0' }}>{fix.patch}</pre>{fix.reason && <small>{fix.reason}</small>}</div>
                    ) : fix && !fix.suggestedCode ? (
                      <div><b>No safe automatic fix</b><br />{fix.reason || fix.description || 'Review the recommendation and edit the code manually.'}</div>
                    ) : fix?.suggestedCode ? (
                      <div className="a"><span className="n">+</span>{fix.suggestedCode}<br /><small>{fix.reason || 'Suggestion only. Inspect and adapt it before applying.'}</small></div>
                    ) : (
                      <div className=""><span className="n"> </span>{f.evidence || f.description}</div>
                    )}
                    {fix?.applyResult && <small>GitHub created PR #{fix.applyResult.pullRequestNumber}: {fix.applyResult.pullRequestUrl}</small>}
                  </div>
                  <div className="fca">
                    <button className="ap1" onClick={() => markReviewed(f.id)}><Icon name="check" style={{ width: 15, height: 15 }} />Mark reviewed</button>
                    <button type="button" onClick={() => handleGenerateFix(f.id)}>
                      <Icon name="spark" style={{ width: 15, height: 15 }} />Suggest Fix
                    </button>
                    <button type="button" title="Copy proposed change" disabled={!fix?.patch && !fix?.suggestedCode} onClick={async () => {
                      const copyText = fix?.patch || fix?.suggestedCode;
                      if (!copyText) return;
                      try {
                        await navigator.clipboard.writeText(copyText);
                        addMessage('bot', `Copied the suggested change for ${f.filePath}. Review it before applying.`);
                      } catch {
                        addMessage('bot', 'Clipboard access is unavailable. Select and copy the suggestion from the fix panel.');
                      }
                    }}>
                      <Icon name="copy" style={{ width: 15, height: 15 }} />Copy suggestion
                    </button>
                    {fix?.safeToApply && <button type="button" onClick={() => handleApplyFix(f.id)} disabled={Boolean(fix.applyResult)}>
                      <Icon name="check" style={{ width: 15, height: 15 }} />{fix.applyResult ? 'PR created' : 'Approve & create PR'}
                    </button>}
                    {zipWorkflow && fix?.patch && <button type="button" onClick={() => handleApproveZipFix(f.id)} disabled={Boolean(zipFixBusy) || Boolean(fix.zipApplied)}>
                      <Icon name="check" style={{ width: 15, height: 15 }} />{zipFixBusy === f.id ? 'Building ZIP…' : fix.zipApplied ? 'Included in fixed ZIP' : 'Approve for fixed ZIP'}
                    </button>}
                    <button onClick={() => handleExplain(f.id)}>
                      <Icon name="bot" style={{ width: 15, height: 15 }} />Explain
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
          
          <div className="ff">
            <span>{reviewedFindings.size} of {localReview?.findings.length || 0} reviewed</span>
            <button className="pr" onClick={markAllReviewed}>Mark all reviewed</button>
          </div>
        </div>
      </aside>
      
      {modalOpen && <NewReviewModal initialFile={pendingFile} onClose={() => { setModalOpen(false); setPendingFile(null); }} onComplete={handleUploadComplete} />}
    </div>
  );
}
