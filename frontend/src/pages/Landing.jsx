import { Bug, ChartNoAxesCombined, ChevronRight, Lightbulb, LockKeyhole, Shield, Sparkles, Zap } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';

const features = [
  { icon: Bug, title: 'Error detection with exact location', copy: 'See the file, the line and why it matters.', wide: true },
  { icon: Sparkles, title: 'Quality score', copy: 'One simple score out of 100.' },
  { icon: LockKeyhole, title: 'Security checks', copy: 'Spot risky patterns early.' },
  { icon: Lightbulb, title: 'Fix suggestions', copy: 'Plain-language steps to improve.' },
  { icon: ChartNoAxesCombined, title: 'Progress tracking', copy: 'Watch your score rise over time.' },
  { icon: Zap, title: 'Results in seconds', copy: 'Upload a folder or zip and get the full report.', wide: true },
];

const heroStops = [
  { station: '01', label: 'Upload', title: 'Guard Your Code.', accent: 'Build With Confidence.', copy: 'Start with a complete project archive and let DataGuard map the codebase before it makes a judgment.' },
  { station: '02', label: 'Analyze', title: 'Find What Matters.', accent: 'Skip the Noise.', copy: 'Deterministic checks locate real issues by file and line, so your team can move straight to evidence.' },
  { station: '03', label: 'Explain', title: 'Understand Every Finding.', accent: 'Improve With Context.', copy: 'Read plain-language recommendations and see why a pattern affects quality, security, or maintainability.' },
  { station: '04', label: 'Improve', title: 'Ship Cleaner Code.', accent: 'Keep The Score Moving.', copy: 'Review a suggested fix, apply it on your branch, and return with a healthier project.' },
];

export default function Landing() {
  const [activeStop, setActiveStop] = useState(0);
  const [tilt, setTilt] = useState({ x: 0, y: 0 });
  const stop = heroStops[activeStop];

  useEffect(() => {
    const timer = window.setInterval(() => setActiveStop((current) => (current + 1) % heroStops.length), 4200);
    return () => window.clearInterval(timer);
  }, []);

  function moveRobot(event) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - bounds.left) / bounds.width - 0.5;
    const y = (event.clientY - bounds.top) / bounds.height - 0.5;
    setTilt({ x: y * -14, y: x * 18 });
  }

  return <div className="landing-page">
    <nav className="landing-nav"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><div className="landing-links"><a href="#features">Features</a><a href="#how-it-works">How It Works</a><Link to="/login">Login</Link><Link className="pill-button" to="/register">Register</Link></div></nav>
    <section className="landing-hero"><div className="hero-copy"><p className="eyebrow">AI-assisted code review · stop {stop.station}</p><h1 className="hero-slide" key={stop.station}>{stop.title}<br /><span>{stop.accent}</span></h1><p className="hero-slide-copy" key={`${stop.station}-copy`}>{stop.copy}</p><Link className="pill-button hero-button" to="/login">{stop.label} a project <ChevronRight size={17} /></Link><small>AI-assisted analysis. Always review suggested changes before applying them.</small><div className="metro-route" aria-label="Review stages">{heroStops.map((item, index) => <button className={index === activeStop ? 'metro-stop active' : 'metro-stop'} key={item.station} onClick={() => setActiveStop(index)} aria-label={`Go to ${item.label}`}><span>{item.station}</span><b>{item.label}</b></button>)}</div></div><div className="hero-visual robot-stage" style={{ '--robot-x': `${tilt.x}deg`, '--robot-y': `${tilt.y}deg` }} onPointerMove={moveRobot} onPointerLeave={() => setTilt({ x: 0, y: 0 })}><div className="route-sign"><span>Next stop</span><strong>{stop.label}</strong></div><div className="robot-orbit orbit-one" /><div className="robot-orbit orbit-two" /><div className="robot-badge badge-left">0 errors ✓</div><div className="robot-badge badge-right">Score 92</div><div className="robot-face"><div className="robot-eyes"><i /><i /></div></div><Link className="robot-cta" to="/login">Code Here</Link></div></section>
    <section className="stack-strip"><span>Supports your stack</span><b>JavaScript</b><b>Python</b><b>Java</b><b>C++</b><b>TypeScript</b></section>
    <section className="landing-section" id="features"><div className="section-intro"><h2>Everything you need to ship clean code</h2><p>A clear report, not a wall of warnings.</p></div><div className="feature-grid">{features.map(({ icon: Icon, title, copy, wide }) => <article className={`feature-card${wide ? ' wide' : ''}`} key={title}><span className="feature-icon"><Icon size={22} /></span><h3>{title}</h3><p>{copy}</p>{title.startsWith('Error') && <div className="code-snippet"><span>12</span> const total = price * qty;<br /><em><span>13</span> console.log(totl); &nbsp; ← undefined variable</em><br /><span>14</span> return total;</div>}</article>)}</div><BeforeAfterSlider /><MotionFeatureShowcase /><div className="landing-stats"><AnimatedStat target={10000} suffix="+" label="files analysed" /><AnimatedStat target={5} suffix="+" label="languages" /><AnimatedStat target={98} suffix="%" label="issues explained" /></div></section>
    <section className="landing-section how-section" id="how-it-works"><div className="section-intro"><h2>How It Works</h2></div><div className="steps-grid"><div><b>1</b><h3>Upload</h3><p>Drop your project folder or zip.</p></div><div><b>2</b><h3>Analyze</h3><p>DataGuard AI scans every file.</p></div><div><b>3</b><h3>Improve</h3><p>Follow the report and raise your score.</p></div></div><div className="landing-cta"><h2>Ready to guard your code?</h2><Link to="/register">Create free account</Link></div></section>
    <footer className="landing-footer"><div><strong><Shield size={16} /> DataGuard AI</strong><p>Review your code with confidence.</p></div><div><b>Product</b><a href="#features">Features</a><a href="#how-it-works">How It Works</a></div><div><b>Company</b><span>About</span><span>Contact</span></div><div><b>Legal</b><span>Privacy</span><span>Terms</span></div></footer>
  </div>;
}

function AnimatedStat({ target, suffix, label }) {
  const [value, setValue] = useState(0);
  const statRef = useRef(null);

  useEffect(() => {
    const element = statRef.current;
    if (!element) return undefined;
    let frame;
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      const started = performance.now();
      const animate = (now) => {
        const progress = Math.min((now - started) / 1300, 1);
        setValue(Math.round((1 - ((1 - progress) ** 3)) * target));
        if (progress < 1) frame = requestAnimationFrame(animate);
      };
      frame = requestAnimationFrame(animate);
      observer.disconnect();
    }, { threshold: 0.45 });
    observer.observe(element);
    return () => { observer.disconnect(); if (frame) cancelAnimationFrame(frame); };
  }, [target]);

  return <div ref={statRef}><b>{value.toLocaleString()}{suffix}</b><span>{label}</span></div>;
}

function BeforeAfterSlider() {
  const [position, setPosition] = useState(50);
  return <section className="before-after" aria-labelledby="before-after-title"><div className="section-intro"><h3 id="before-after-title">Before &amp; after</h3><p>Drag the handle to reveal the AI-assisted fix.</p></div><div className="code-compare"><pre className="code-pane vulnerable"><code><span>12</span> const total = price * qty;{`\n`}<em><span>13</span> console.log(totl);  x undefined variable</em>{`\n`}<span>14</span> return total;</code></pre><pre className="code-pane secured" style={{ '--reveal': `${position}%` }}><code><span>12</span> const total = price * qty;{`\n`}<strong><span>13</span> console.log(total);  ✓ fixed</strong>{`\n`}<span>14</span> return total;</code></pre><input className="code-slider" type="range" min="0" max="100" value={position} onChange={(event) => setPosition(event.target.value)} aria-label="Compare vulnerable and fixed code" /><span className="slider-handle" style={{ left: `${position}%` }} aria-hidden="true">↔</span></div></section>;
}

function MotionFeatureShowcase() {
  const cards = [
    ['🐞', 'Errors', 'Every error with its file and line number, sorted by severity.'],
    ['⭐', 'Quality', 'A score out of 100 for reliability, security and maintainability.'],
    ['🤖', 'AI Assistant', 'Ask the side bot how to fix any issue in plain language.'],
  ];
  return <section className="motion-showcase"><div className="showcase-cube" aria-label="Rotating review capabilities">{['Errors', 'Security', 'Quality', 'Speed', 'AI Fixes', 'Reports'].map((label) => <span key={label}>{label}</span>)}</div><div className="flip-grid">{cards.map(([icon, title, copy]) => <article className="flip-card" key={title}><div className="flip-inner"><div className="flip-front"><b>{icon}</b><h3>{title}</h3><p>Hover or tap to explore</p></div><div className="flip-back"><p>{copy}</p></div></div></article>)}</div><div className="faq-list">{[['Which languages are supported?', 'JavaScript, Python, Java, C++, and TypeScript to start.'], ['Is my code stored?', 'Uploads remain private to your account and are limited by the review policy.'], ['Can I trust the AI suggestions?', 'Always review suggested changes before applying them.']].map(([question, answer]) => <details key={question}><summary>{question}</summary><p>{answer}</p></details>)}</div></section>;
}
