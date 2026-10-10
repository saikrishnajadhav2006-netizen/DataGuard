import { Bug, ChartNoAxesCombined, ChevronRight, Lightbulb, LockKeyhole, Shield, Sparkles, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';

const features = [
  { icon: Bug, tone: 'rose', title: 'Error detection with exact location', copy: 'See the file, the line and why it matters.', wide: true },
  { icon: Sparkles, tone: 'amber', title: 'Quality score', copy: 'One simple score out of 100.' },
  { icon: LockKeyhole, tone: 'cyan', title: 'Security checks', copy: 'Spot risky patterns early.' },
  { icon: Lightbulb, tone: 'violet', title: 'Fix suggestions', copy: 'Plain-language steps to improve.' },
  { icon: ChartNoAxesCombined, tone: 'green', title: 'Progress tracking', copy: 'Watch your score rise over time.' },
  { icon: Zap, tone: 'blue', title: 'Results in seconds', copy: 'Upload a folder or zip and get the full report.', wide: true },
];

const heroStops = [
  { station: '01', label: 'Upload', title: 'Guard Your Code.', accent: 'Build With Confidence.', copy: 'Start with a complete project archive and let DataGuard map the codebase before it makes a judgment.' },
  { station: '02', label: 'Analyze', title: 'Find What Matters.', accent: 'Skip the Noise.', copy: 'Deterministic checks locate real issues by file and line, so your team can move straight to evidence.' },
  { station: '03', label: 'Explain', title: 'Understand Every Finding.', accent: 'Improve With Context.', copy: 'Read plain-language recommendations and see why a pattern affects quality, security, or maintainability.' },
  { station: '04', label: 'Improve', title: 'Ship Cleaner Code.', accent: 'Keep The Score Moving.', copy: 'Review a suggested fix, apply it on your branch, and return with a healthier project.' },
];

export default function Landing() {
  const [activeStop, setActiveStop] = useState(0);
  const stop = heroStops[activeStop];

  useEffect(() => {
    const timer = window.setInterval(() => setActiveStop((current) => (current + 1) % heroStops.length), 4200);
    return () => window.clearInterval(timer);
  }, []);

  return <div className="landing-page">
    <nav className="landing-nav"><Link to="/" className="landing-brand"><Shield className="brand-shield" size={26} fill="currentColor" strokeWidth={1.6} />DataGuard AI</Link><div className="landing-links"><a href="#features">Features</a><a href="#how-it-works">How It Works</a><Link to="/login">Login</Link><Link className="pill-button" to="/register">Register</Link></div></nav>
    <section className="landing-hero"><div className="hero-copy"><p className="eyebrow">AI-assisted code review · stop {stop.station}</p><h1 key={stop.station}>{stop.title}<br /><span>{stop.accent}</span></h1><p key={`${stop.station}-copy`}>{stop.copy}</p><Link className="pill-button hero-button" to="/login">{stop.label} a project <ChevronRight size={17} /></Link><small>AI-assisted analysis. Always review suggested changes before applying them.</small><div className="metro-route" aria-label="Review stages">{heroStops.map((item, index) => <button className={index === activeStop ? 'metro-stop active' : 'metro-stop'} key={item.station} onClick={() => setActiveStop(index)} aria-label={`Go to ${item.label}`}><span>{item.station}</span><b>{item.label}</b></button>)}</div></div><div className="hero-visual"><div className="route-sign"><span>Next stop</span><strong>{stop.label}</strong></div><div className="robot-face"><div className="robot-eyes"><i /><i /></div></div><Link className="robot-cta" to="/login">Code Here</Link></div></section>
    <section className="stack-strip"><span>Supports your stack</span><b>JavaScript</b><b>Python</b><b>Java</b><b>C++</b><b>TypeScript</b></section>
    <section className="landing-section" id="features"><div className="section-intro"><h2>Everything you need to ship clean code</h2><p>A clear report, not a wall of warnings.</p></div><div className="feature-grid">{features.map(({ icon: Icon, tone, title, copy, wide }) => <article className={`feature-card${wide ? ' wide' : ''}`} key={title}><span className={`feature-icon feature-icon-${tone}`}><Icon size={22} strokeWidth={2.2} /></span><h3>{title}</h3><p>{copy}</p>{title.startsWith('Error') && <div className="code-snippet"><span>12</span> const total = price * qty;<br /><em><span>13</span> console.log(totl); &nbsp; ← undefined variable</em><br /><span>14</span> return total;</div>}</article>)}</div><div className="landing-stats"><div><b>10k+</b><span>files analysed</span></div><div><b>5</b><span>languages</span></div><div><b>98%</b><span>issues explained</span></div></div></section>
    <section className="landing-section how-section" id="how-it-works"><div className="section-intro"><h2>How It Works</h2></div><div className="steps-grid"><div><b>1</b><h3>Upload</h3><p>Drop your project folder or zip.</p></div><div><b>2</b><h3>Analyze</h3><p>DataGuard AI scans every file.</p></div><div><b>3</b><h3>Improve</h3><p>Follow the report and raise your score.</p></div></div><div className="landing-cta"><h2>Ready to guard your code?</h2><Link to="/register">Create free account</Link></div></section>
    <footer className="landing-footer"><div><strong><Shield className="brand-shield" size={16} fill="currentColor" strokeWidth={1.6} /> DataGuard AI</strong><p>Review your code with confidence.</p></div><div><b>Product</b><a href="#features">Features</a><a href="#how-it-works">How It Works</a></div><div><b>Company</b><span>About</span><span>Contact</span></div><div><b>Legal</b><span>Privacy</span><span>Terms</span></div></footer>
  </div>;
}
