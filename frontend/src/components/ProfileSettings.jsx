import { useState } from 'react';
import { KeyRound, LoaderCircle, UserRound, X } from 'lucide-react';
import { changePassword, updateProfile } from '../lib/api';

export default function ProfileSettings({ user, onClose, onSaved }) {
  const [name, setName] = useState(user?.fullName || '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function saveProfile(e) {
    e.preventDefault();
    setError(''); setMessage('');
    if (!name.trim()) { setError('Username is required.'); return; }
    setBusy(true);
    try {
      const profile = await updateProfile(name.trim());
      const auth = JSON.parse(localStorage.getItem('dataguard-auth') || '{}');
      const next = { ...auth, fullName: profile.fullName, email: profile.email || auth.email, role: profile.role || auth.role };
      localStorage.setItem('dataguard-auth', JSON.stringify(next));
      onSaved?.(next);
      setMessage('Username updated.');
    } catch (e) { setError(e.message || 'Could not update username.'); }
    finally { setBusy(false); }
  }

  async function savePassword(e) {
    e.preventDefault();
    setError(''); setMessage('');
    if (newPassword.length < 8) { setError('New password must contain at least 8 characters.'); return; }
    if (newPassword !== confirmPassword) { setError('The new passwords do not match.'); return; }
    setBusy(true);
    try {
      const result = await changePassword(currentPassword, newPassword);
      setCurrentPassword(''); setNewPassword(''); setConfirmPassword('');
      setMessage(result.message || 'Password updated successfully.');
    } catch (e) { setError(e.message || 'Could not change password.'); }
    finally { setBusy(false); }
  }

  return <div className="dg-profile-backdrop" onMouseDown={e=>{if(e.target===e.currentTarget&&!busy)onClose();}}>
    <section className="dg-profile-modal" role="dialog" aria-modal="true" aria-labelledby="dg-profile-title">
      <header className="dg-profile-head"><div><p className="dg-eyebrow">ACCOUNT SETTINGS</p><h2 id="dg-profile-title">Your profile</h2><p>{user?.email || ''}</p></div><button type="button" className="dg-icon-button" aria-label="Close profile settings" onClick={onClose}><X size={18}/></button></header>
      {error&&<div className="dg-profile-feedback error" role="alert">{error}</div>}
      {message&&<div className="dg-profile-feedback success" role="status">{message}</div>}
      <form className="dg-profile-form" onSubmit={saveProfile}><label><span><UserRound size={15}/> Username</span><input value={name} onChange={e=>setName(e.target.value)} maxLength={80} required autoComplete="nickname"/></label><button type="submit" disabled={busy||!name.trim()}>{busy?<LoaderCircle size={16} className="dg-spin"/>:'Save username'}</button></form>
      <div className="dg-profile-separator"/>
      <form className="dg-profile-form" onSubmit={savePassword}><h3><KeyRound size={17}/> Change password</h3><label><span>Current password</span><input type="password" value={currentPassword} onChange={e=>setCurrentPassword(e.target.value)} autoComplete="current-password" required/></label><label><span>New password</span><input type="password" value={newPassword} onChange={e=>setNewPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="new-password" required/><small>Use 8–72 characters.</small></label><label><span>Confirm new password</span><input type="password" value={confirmPassword} onChange={e=>setConfirmPassword(e.target.value)} minLength={8} maxLength={72} autoComplete="new-password" required/></label><button type="submit" disabled={busy||!currentPassword||!newPassword||!confirmPassword}>{busy?<LoaderCircle size={16} className="dg-spin"/>:'Update password'}</button></form>
    </section>
  </div>;
}
