import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import AntdConfig from './theme/AntdConfig';
import MainLayout from './components/layout/MainLayout';
import DashboardHero from './components/DashboardHero';
import AIBotDrawer from './components/AIBotDrawer';
import WorkspaceBuilder from './components/pages/WorkspaceBuilder';

function App() {
  return (
    <AntdConfig>
      <Router>
        <Routes>
          <Route path="/" element={<MainLayout />}>
            <Route index element={<DashboardHero />} />
            <Route path="workspace/:id" element={<WorkspaceBuilder />} />
          </Route>
        </Routes>
        <AIBotDrawer />
      </Router>
    </AntdConfig>
  );
}

export default App;