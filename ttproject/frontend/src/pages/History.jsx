import { History as HistoryIcon } from 'lucide-react';
import DashboardLayout from '../components/DashboardLayout';

export default function History() {
  return <DashboardLayout onNewReview={() => {}}><div className="page-header"><div><p className="eyebrow">Traceability</p><h1>Review history</h1><p>Compare how your code quality changes over time.</p></div></div><section className="empty-panel"><HistoryIcon size={42} /><h2>No review history yet</h2><p>Completed project reviews will be collected here.</p></section></DashboardLayout>;
}