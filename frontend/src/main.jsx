import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './styles/design-system.css'
import App from './App.jsx'

const savedAuth = (() => { try { return JSON.parse(sessionStorage.getItem('dataguard-auth') || localStorage.getItem('dataguard-auth') || '{}'); } catch { return {}; } })();
const savedTheme = localStorage.getItem(`dataguard-mode:${savedAuth.email || 'guest'}`) || localStorage.getItem('dataguard-mode');
document.body.dataset.mode = savedTheme === 'dark' ? 'dark' : 'light'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
