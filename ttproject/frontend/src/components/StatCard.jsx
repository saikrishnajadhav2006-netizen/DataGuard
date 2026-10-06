export default function StatCard({ icon: Icon, label, value, tone }) {
  return (
    <article className={`stat-card ${tone}`}>
      <div className="stat-icon"><Icon size={22} /></div>
      <div><p>{label}</p><strong>{value}</strong></div>
    </article>
  );
}