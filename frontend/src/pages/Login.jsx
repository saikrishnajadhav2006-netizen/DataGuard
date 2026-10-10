import { ArrowLeft, Shield } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authenticate } from '../lib/api';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    const normalizedEmail = email.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(normalizedEmail)) return setError('Enter a valid email address.');
    if (!password) return setError('Enter your password.');
    setLoading(true);
    try {
      const auth = await authenticate('/api/auth/login', { email: normalizedEmail, password });
      if (!auth?.token) throw new Error('The server did not return a login token. Please check your credentials and try again.');
      localStorage.setItem('dataguard-auth', JSON.stringify(auth));
      navigate('/dashboard', { replace: true });
    } catch (requestError) {
      setError(requestError.message || 'Login failed.');
    } finally {
      setLoading(false);
    }
  }

  return <div className="auth-reference-page"><header className="auth-topbar"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><Link className="pill-button home-button" to="/"><ArrowLeft size={16} /> Home</Link></header><main className="auth-split-card"><section className="auth-form-side"><h1>Sign In</h1><p className="auth-divider">Sign in with your email and password</p><form onSubmit={submit} noValidate><label className="auth-field">Email<input autoComplete="email" aria-label="Email" type="email" required placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} /></label><label className="auth-field">Password<input autoComplete="current-password" aria-label="Password" type="password" required placeholder="Password" value={password} onChange={(event) => setPassword(event.target.value)} /></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="auth-submit" disabled={loading}>{loading ? 'Signing in…' : 'Sign In'}</button></form><p className="auth-mobile-link">Don't have an account? <Link to="/register">Register</Link></p></section><section className="auth-welcome-side"><div><h2>Hello, Developer!</h2><p>Sign in to access your saved projects and code reviews.</p><Link className="outline-button" to="/register">Create account</Link></div></section></main></div>;
}
