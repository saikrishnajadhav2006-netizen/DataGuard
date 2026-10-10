import { ArrowLeft, Shield } from 'lucide-react';
import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authenticate } from '../lib/api';
import ThemeToggle from '../components/ThemeToggle';
import { saveAuth } from '../lib/authStorage';

export default function Login() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const registering = searchParams.get('mode') === 'register';
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setError('');
    try {
      const auth = await authenticate(
        registering ? '/api/auth/register' : '/api/auth/login',
        registering ? form : { email: form.email, password: form.password },
      );
      saveAuth(auth);
      navigate('/dashboard');
    } catch (requestError) {
      setError(requestError.message || (registering ? 'Registration failed.' : 'Login failed.'));
    }
  }

  function setMode(nextMode) {
    setError('');
    setSearchParams(nextMode ? { mode: nextMode } : {});
  }

  return <div className="auth-reference-page">
    <header className="auth-topbar"><Link to="/" className="landing-brand"><Shield size={26} />DataGuard AI</Link><div className="auth-top-actions"><ThemeToggle /><Link className="pill-button home-button" to="/"><ArrowLeft size={16} /> Home</Link></div></header>
    <main className={`auth-split-card ${registering ? 'registering' : ''}`}>
      <section className="auth-form-side">
        <h1>{registering ? 'Sign Up' : 'Sign In'}</h1>
        <p className="auth-divider">{registering ? 'Create your account with email' : 'Sign in with your email account'}</p>
        <form onSubmit={submit}>
          {registering && <input aria-label="Full name" required placeholder="Full name" autoComplete="name" value={form.fullName} onChange={event => setForm({ ...form, fullName: event.target.value })} />}
          <input aria-label="Email" type="email" required placeholder="Email" autoComplete="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} />
          <input aria-label="Password" type="password" required minLength={registering ? 10 : undefined} placeholder="Password" autoComplete={registering ? 'new-password' : 'current-password'} value={form.password} onChange={event => setForm({ ...form, password: event.target.value })} />
          {error && <p className="form-error" role="alert">{error}</p>}
          {!registering && <button type="button" className="forgot-link" onClick={() => setError('Password reset is not configured yet.')}>Forgot your password?</button>}
          <button className="auth-submit">{registering ? 'Sign Up' : 'Sign In'}</button>
        </form>
        <p className="auth-mobile-link">{registering ? 'Already registered?' : "Don't have an account?"} <button type="button" onClick={() => setMode(registering ? '' : 'register')}>{registering ? 'Sign In' : 'Register'}</button></p>
      </section>
      <section className="auth-welcome-side"><div>
        <h2>{registering ? 'Welcome back!' : 'Hello, Developer!'}</h2>
        <p>{registering ? 'Already have an account? Sign in and continue reviewing your projects.' : 'Create an account and start reviewing your projects today.'}</p>
        <button className="outline-button" type="button" onClick={() => setMode(registering ? '' : 'register')}>{registering ? 'Sign In' : 'Sign Up'}</button>
      </div></section>
    </main>
  </div>;
}
