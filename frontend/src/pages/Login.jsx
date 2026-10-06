import { ArrowLeft, Shield } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authenticate } from '../lib/api';

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    try {
      const auth = await authenticate('/api/auth/login', { email, password });
      localStorage.setItem('dataguard-auth', JSON.stringify(auth));
      navigate('/dashboard');
    } catch (requestError) { setError(requestError.message || 'Login failed.'); }
  }

  return <div className="auth-reference-page"><header className="auth-topbar"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><Link className="pill-button home-button" to="/"><ArrowLeft size={16} /> Home</Link></header><main className="auth-split-card"><section className="auth-form-side"><h1>Sign In</h1><div className="social-row"><button title="Google">G</button><button title="GitHub">Gh</button><button title="LinkedIn">in</button></div><p className="auth-divider">or use your email account</p><form onSubmit={submit}><input aria-label="Email" type="email" required placeholder="Email" value={email} onChange={(event) => setEmail(event.target.value)} /><input aria-label="Password" type="password" required placeholder="Password" value={password} onChange={(event) => setPassword(event.target.value)} />{error && <p className="form-error">{error}</p>}<a className="forgot-link" href="#forgot">Forgot your password?</a><button className="auth-submit">Sign In</button></form><p className="auth-mobile-link">Don't have an account? <Link to="/register">Register</Link></p></section><section className="auth-welcome-side"><div><h2>Hello, Developer!</h2><p>Create an account and start reviewing your projects today.</p><Link className="outline-button" to="/register">Sign Up</Link></div></section></main></div>;
}
