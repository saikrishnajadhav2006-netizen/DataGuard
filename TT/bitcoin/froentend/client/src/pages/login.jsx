import { useState } from 'react';
import axios from 'axios';
import { useNavigate, Link } from 'react-router-dom';

export default function Login() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();

  const handleLogin = async (e) => {
    e.preventDefault();

    try {
      const res = await axios.post('http://localhost:8080/api/auth/login', {
        username,
        password,
      });

      if (res.data.status === 200) {
        localStorage.setItem('user', JSON.stringify(res.data.data));
        navigate('/dashboard');
      } else {
        alert(res.data.msg || 'Invalid credentials');
      }
    } catch (err) {
      console.error(err);
      alert('Server connection error. Please make sure the backend is running.');
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-panel">
        <div className="brand-panel">
          <div className="brand-badge">Secure Login</div>
          <h1>Welcome back to your wallet.</h1>
          <p>
            Sign in to view your balance, manage your wallet, and continue from
            where you left off with safe, fast access.
          </p>
          <ul className="feature-list">
            <li>Quick wallet access</li>
            <li>Protected sign in</li>
            <li>Free starter balance</li>
          </ul>
        </div>

        <div className="form-panel">
          <div className="auth-card">
            <p className="eyebrow">Welcome back</p>
            <h2>Login</h2>

            <form onSubmit={handleLogin} className="auth-form">
              <label>
                <span>Username</span>
                <input
                  type="text"
                  placeholder="Enter your username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </label>

              <label>
                <span>Password</span>
                <input
                  type="password"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </label>

              <button type="submit" className="primary-button">
                Log In
              </button>
            </form>

            <p className="switch-text">
              Don&apos;t have an account?{' '}
              <Link to="/register">Register here</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}