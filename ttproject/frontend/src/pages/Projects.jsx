import { FolderKanban, Plus } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';

export default function Projects() {
  return <DashboardLayout onNewReview={() => {}}><div className="page-header"><div><p className="eyebrow">Workspace</p><h1>Projects</h1><p>Every uploaded codebase will appear here after its first review.</p></div><button className="primary-button"><Plus size={18} />New review</button></div><section className="empty-panel"><FolderKanban size={42} /><h2>Your project shelf is ready</h2><p>Start from the dashboard to upload a ZIP archive and create your first project.</p></section></DashboardLayout>;
}