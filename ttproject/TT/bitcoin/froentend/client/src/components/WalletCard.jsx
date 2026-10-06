export default function WalletCard({ username, balance }) {
  return (
    <div className="wallet-card">
      <div className="wallet-header">
        <div>
          <p className="eyebrow">Wallet</p>
          <h3>{username}&apos;s Wallet</h3>
        </div>
        <span className="wallet-badge">Verified</span>
      </div>

      <div className="wallet-balance">
        <span>Available balance</span>
        <strong>
          ${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </strong>
      </div>

      <div className="wallet-meta">
        <div>
          <span>Free credit</span>
          <strong>$1,000.00</strong>
        </div>
        <div>
          <span>Account type</span>
          <strong>Starter</strong>
        </div>
      </div>
    </div>
  );
}
