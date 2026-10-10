import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Shield } from 'lucide-react';
import { authenticate } from '../lib/api';

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    try {
      const auth = await authenticate('/api/auth/register', form);
      localStorage.setItem('dataguard-auth', JSON.stringify(auth));
      navigate('/dashboard');
    } catch (requestError) { setError(requestError.message || 'Registration failed.'); }
  }

  return <div className="auth-reference-page"><header className="auth-topbar"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><Link className="pill-button home-button" to="/"><ArrowLeft size={16} /> Home</Link></header><main className="auth-split-card"><section className="auth-form-side"><h1>Sign Up</h1><p className="auth-divider">Create your account with email</p><form onSubmit={submit}><input aria-label="Full name" required placeholder="Full name" value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} /><input aria-label="Email" type="email" required placeholder="Email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} /><input aria-label="Password" type="password" required minLength="6" placeholder="Password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />{error && <p className="form-error">{error}</p>}<button className="auth-submit">Sign Up</button></form><p className="auth-mobile-link">Already registered? <Link to="/login">Sign In</Link></p></section><section className="auth-welcome-side"><div><h2>Welcome back!</h2><p>Already have an account? Sign in and continue reviewing your projects.</p><Link className="outline-button" to="/login">Sign In</Link></div></section></main></div>;
}