import { useState } from 'react';
import axios from 'axios';
import { useNavigate, Link } from 'react-router-dom';

export default function Register() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const navigate = useNavigate();

  const handleRegister = async (e) => {
    e.preventDefault();

    try {
      const res = await axios.post('http://localhost:8080/api/auth/signup', {
        username,
        password,
      });

      if (res.data.status === 201) {
        alert('Account created successfully! Please login.');
        navigate('/');
      } else {
        alert(res.data.msg || 'Registration failed');
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
          <div className="brand-badge">Create Account</div>
          <h1>Start your wallet journey.</h1>
          <p>
            Create your profile to unlock a clean wallet experience, manage your
            funds, and get started with a free starter balance.
          </p>
          <ul className="feature-list">
            <li>Simple registration</li>
            <li>Secure wallet setup</li>
            <li>Starter balance included</li>
          </ul>
        </div>

        <div className="form-panel">
          <div className="auth-card">
            <p className="eyebrow">Get started</p>
            <h2>Register</h2>

            <form onSubmit={handleRegister} className="auth-form">
              <label>
                <span>Username</span>
                <input
                  type="text"
                  placeholder="Choose a username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                />
              </label>

              <label>
                <span>Password</span>
                <input
                  type="password"
                  placeholder="Choose a password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </label>

              <button type="submit" className="primary-button success-button">
                Create Account
              </button>
            </form>

            <p className="switch-text">
              Already have an account? <Link to="/">Login here</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}