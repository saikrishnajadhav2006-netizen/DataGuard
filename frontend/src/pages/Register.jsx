import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Shield } from 'lucide-react';
import { authenticate } from '../lib/api';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    const email = form.email.trim().toLowerCase();
    if (!form.fullName.trim()) return setError('Enter your full name.');
    if (!EMAIL_PATTERN.test(email)) return setError('Enter a valid email address.');
    if (form.password.length < 8) return setError('Use a password with at least 8 characters.');
    setLoading(true);
    try {
      const auth = await authenticate('/api/auth/register', { ...form, fullName: form.fullName.trim(), email });
      if (!auth?.token) throw new Error('The server did not return a login token. Please try again.');
      localStorage.setItem('dataguard-auth', JSON.stringify(auth));
      navigate('/dashboard', { replace: true });
    } catch (requestError) {
      setError(requestError.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  }

  return <div className="auth-reference-page"><header className="auth-topbar"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><Link className="pill-button home-button" to="/"><ArrowLeft size={16} /> Home</Link></header><main className="auth-split-card"><section className="auth-form-side"><h1>Sign Up</h1><p className="auth-divider">Create your account with email</p><form onSubmit={submit} noValidate><label className="auth-field">Full name<input autoComplete="name" aria-label="Full name" required maxLength={100} placeholder="Full name" value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} /></label><label className="auth-field">Email<input autoComplete="email" aria-label="Email" type="email" required placeholder="you@example.com" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /></label><label className="auth-field">Password<input autoComplete="new-password" aria-label="Password" type="password" required minLength={8} maxLength={72} placeholder="At least 8 characters" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="auth-submit" disabled={loading}>{loading ? 'Creating account…' : 'Create account'}</button></form><p className="auth-mobile-link">Already registered? <Link to="/login">Sign In</Link></p></section><section className="auth-welcome-side"><div><h2>Welcome to DataGuard</h2><p>Create an account to review projects and keep your code quality findings organized.</p><Link className="outline-button" to="/login">Sign In</Link></div></section></main></div>;
}
