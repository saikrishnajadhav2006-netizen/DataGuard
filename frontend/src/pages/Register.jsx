import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Shield } from 'lucide-react';
import { authenticate } from '../lib/api';

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError('');
    setNotice('');
    setLoading(true);
    try {
      const auth = await authenticate('/api/auth/register', form);
      if (auth.pendingConfirmation) {
        setNotice(`Account created for ${auth.email}. Check your email to confirm the account, then sign in.`);
        return;
      }
      localStorage.setItem('dataguard-auth', JSON.stringify(auth));
      navigate('/dashboard');
    } catch (requestError) {
      setError(requestError.message || 'Registration failed.');
    } finally {
      setLoading(false);
    }
  }

  return <div className="auth-reference-page"><header className="auth-topbar"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><Link className="pill-button home-button" to="/"><ArrowLeft size={16} /> Home</Link></header><main className="auth-split-card"><section className="auth-form-side"><h1>Sign Up</h1><div className="social-row"><button type="button" title="Google">G</button><button type="button" title="GitHub">Gh</button><button type="button" title="LinkedIn">in</button></div><p className="auth-divider">or create your account</p><form onSubmit={submit}><input aria-label="Full name" autoComplete="name" required placeholder="Full name" value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} /><input aria-label="Email" autoComplete="email" type="email" required placeholder="Email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /><input aria-label="Password" autoComplete="new-password" type="password" required minLength={6} placeholder="Password (at least 6 characters)" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />{error && <p className="form-error" role="alert">{error}</p>}{notice && <p className="form-success" role="status">{notice}</p>}<button className="auth-submit" disabled={loading}>{loading ? 'Creating account…' : 'Sign Up'}</button></form><p className="auth-mobile-link">Already registered? <Link to="/login">Sign In</Link></p></section><section className="auth-welcome-side"><div><h2>Welcome back!</h2><p>Already have an account? Sign in and continue reviewing your projects.</p><Link className="outline-button" to="/login">Sign In</Link></div></section></main></div>;
}
