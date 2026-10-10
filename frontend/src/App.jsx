import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Register from './pages/Register';
import Projects from './pages/Projects';
import History from './pages/History';
import NavyVioletPrototype from './pages/NavyVioletPrototype';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<NavyVioletPrototype />} />
        <Route path="/prototype" element={<NavyVioletPrototype />} />
        <Route path="/app" element={<Landing />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/projects" element={<Projects />} />
        <Route path="/history" element={<History />} />
      </Routes>
    </Router>
  );
}

export default App;
