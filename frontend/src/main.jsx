import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './App.css'
import './theme.css'
import './metro.css'
import './design-v4.css'
import './robot-motion.css'
import './landing-interactions.css'
import './reference-motion.css'
import App from './App.jsx'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
