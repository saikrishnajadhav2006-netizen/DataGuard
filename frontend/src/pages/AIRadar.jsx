import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  AudioLines, BookOpen, Code2, Compass, Mic, Palette,
  Search, Shuffle, Sparkles, TrendingUp, Video, Workflow, PenLine, Megaphone,
} from 'lucide-react';
import RadarLayout from '../components/RadarLayout';
import { getRadarConversation, getRadarConversations, radarChat } from '../lib/api';

const categories = [
  ['Video', Video], ['Coding', Code2], ['Design', Palette], ['Writing', PenLine],
  ['Marketing', Megaphone], ['Audio', AudioLines], ['Research', BookOpen], ['Automation', Workflow],
];
const budgets = ['Any price', 'Free only', 'Under $20/mo'];
const teams = ['Solo', 'Small team', 'Enterprise'];

export default function AIRadar() {
  const [mode, setMode] = useState('find');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [budget, setBudget] = useState('Any price');
  const [team, setTeam] = useState('Solo');
  const [listening, setListening] = useState(false);
  const [loading, setLoading] = useState(false);
  const [messages, setMessages] = useState([]);
  const [conversationId, setConversationId] = useState(null);
  const queryRef = useRef(null);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const id = new URLSearchParams(location.search).get('conversation');
    if (!id) return;
    let active = true;
    getRadarConversation(id).then(data => {
      if (!active) return;
      setConversationId(data.conversationId);
      setMessages((data.messages || []).map(message => ({ role: message.role, text: message.content })));
    }).catch(error => { if (active) setMessages([{ role: 'error', text: error.message || 'This conversation could not be loaded.' }]); });
    return () => { active = false; };
  }, [location.search]);

  function transcribe() {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      setMessages(previous => [...previous, { role: 'error', text: 'Voice input is not supported by this browser. Type your request instead.' }]);
      return;
    }
    const recognition = new SpeechRecognition();
    recognition.lang = 'en-US';
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;
    recognition.onresult = event => setQuery(event.results[0][0].transcript);
    recognition.onerror = () => setMessages(previous => [...previous, { role: 'error', text: 'Voice input did not complete. You can type your request instead.' }]);
    recognition.onend = () => setListening(false);
    setListening(true);
    try {
      recognition.start();
    } catch { setListening(false); setMessages(previous => [...previous, { role: 'error', text: 'Voice input could not start. Type your request instead.' }]); }
  }

  async function search(event) {
    event?.preventDefault();
    const text = query.trim();
    if (!text) {
      queryRef.current?.focus();
      return;
    }
    const filters = [category, budget !== 'Any price' ? budget : '', team !== 'Solo' ? team : ''].filter(Boolean);
    const prompt = [text, filters.length ? `Preferences: ${filters.join(', ')}.` : ''].filter(Boolean).join('\n');
    setMessages(previous => [...previous, { role: 'user', text }]);
    setLoading(true);
    try {
      const data = await radarChat(prompt, mode, conversationId);
      setConversationId(data.conversationId);
      setMessages((data.messages || []).map(message => ({ role: message.role, text: message.content })));
      if (!conversationId) navigate(`/ai-radar?conversation=${data.conversationId}`, { replace: true });
      window.dispatchEvent(new Event('dataguard-radar-history-updated'));
    } catch (error) {
      setMessages(previous => [...previous, { role: 'error', text: error.message || 'Connection to server failed.' }]);
      getRadarConversations().then(() => window.dispatchEvent(new Event('dataguard-radar-history-updated'))).catch(() => {});
    } finally {
      setLoading(false);
      setQuery('');
    }
  }

  function setSuggestion(text) {
    setQuery(text);
    queryRef.current?.focus();
  }

  return (
    <RadarLayout>
      <div className="radar-page">
        <section className="radar-showcase" aria-label="AI Radar scanner">
          <div className="rdr">
            <div className="circ"><div className="sweep" /></div>
            <i className="wv" /><i className="wv" /><i className="wv" />
            <div className="radar-dots" aria-hidden="true">
              <i /><i /><i /><i /><i /><i /><i /><i />
            </div>
            <div className="lgc"><div className="lg"><Sparkles size={31} /></div></div>
            <div className="radar-title"><h1>AI Radar</h1><span>by DataGuard AI</span></div>
          </div>
        </section>

        <div className="radar-controls">
          <div className="seg" role="tablist" aria-label="Radar mode">
            <button type="button" role="tab" aria-selected={mode === 'find'} className={mode === 'find' ? 'on' : ''} onClick={() => setMode('find')}><Search size={17} />Find Tools</button>
            <button type="button" role="tab" aria-selected={mode === 'workflow'} className={mode === 'workflow' ? 'on' : ''} onClick={() => setMode('workflow')}><Shuffle size={17} />Workflow</button>
          </div>
          <p className="dsc">{mode === 'find' ? "Describe what you're working on, and AI Radar will suggest relevant tools." : 'Describe your goal to get a suggested tool workflow.'}</p>
          <button type="button" className="radar-new-chat" onClick={() => { setConversationId(null); setMessages([]); navigate('/ai-radar'); }}>New conversation</button>
          <div className="pl2">
            <button type="button" onClick={() => setSuggestion('Show me trending AI tools for software development')}><TrendingUp size={17} />Trending</button>
            <button type="button" onClick={() => setSuggestion('Explore useful AI tools for my project')}><Compass size={17} />Explore</button>
          </div>
        </div>

        <hr className="radar-divider" />

        <div className="cats" aria-label="Tool categories">
          {categories.map(([name, Icon]) => (
            <button type="button" key={name} className={`ch ${category === name ? 'on' : ''}`} onClick={() => setCategory(category === name ? '' : name)}>
              <Icon size={15} />{name}
            </button>
          ))}
        </div>

        <form className="sbx" onSubmit={search}>
          <label className="sr-only" htmlFor="radar-prompt">Describe the AI tools or workflow you need</label>
          <textarea
            ref={queryRef}
            id="radar-prompt"
            value={query}
            onChange={event => setQuery(event.target.value)}
            placeholder={mode === 'find' ? "Tell me what you need… e.g., 'I'm building a React app and need to find bugs and security issues quickly'" : "Enter your goal… e.g., 'Plan and launch a YouTube channel'"}
            disabled={loading}
          />
          <div className="radar-form-actions">
            <button className={`ib radar-mic-button ${listening ? 'listening' : ''}`} type="button" title="Voice input" onClick={transcribe} disabled={loading}><Mic size={19} /></button>
            <button className="btn radar-find-button" type="submit" disabled={loading}><Search size={18} />{loading ? 'Searching…' : mode === 'find' ? 'Find Tools' : 'Build Workflow'}</button>
          </div>
        </form>

        <div className="opts">
          <span className="filter-label">BUDGET:</span>
          {budgets.map(option => <button key={option} className={`ch ${budget === option ? 'on' : ''}`} type="button" onClick={() => setBudget(option)}>{option}</button>)}
          <span className="filter-label team-label">TEAM:</span>
          {teams.map(option => <button key={option} className={`ch ${team === option ? 'on' : ''}`} type="button" onClick={() => setTeam(option)}>{option}</button>)}
        </div>

        <section className={`radar-output ${loading ? 'scanning' : ''}`} aria-live="polite">
          {messages.length === 0 && !loading ? (
            <div className="empty"><Sparkles className="i" /><br />Your tool suggestions will appear here.<br />Pick a category or describe your project above.</div>
          ) : (
            <div className="radar-conversation">
              {messages.map((message, index) => (
                <article key={`${index}-${message.role}`} className={`radar-result-message ${message.role}`}>
                  <span className="radar-message-label">{message.role === 'user' ? 'YOU' : message.role === 'error' ? 'NOTICE' : 'AI RADAR'}</span>
                  <p>{message.text}</p>
                </article>
              ))}
              {loading && <div className="radar-loading"><span className="radar-loader" />Waiting for the AI provider…</div>}
            </div>
          )}
        </section>
      </div>
    </RadarLayout>
  );
}
