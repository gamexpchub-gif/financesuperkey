'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, tokenStore, ApiError, SessionUser } from '@/lib/api';

interface LoginResponse {
  access: string;
  refresh: string;
  user: SessionUser;
}

export default function LoginPage() {
  const router = useRouter();
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await api.post<LoginResponse>('/auth/login', {
        identifier,
        password,
      });
      if (res.user.userType !== 'super_admin' && res.user.userType !== 'admin_staff') {
        setError('এই প্যানেল শুধু অ্যাডমিনদের জন্য।');
        setLoading(false);
        return;
      }
      tokenStore.set(res.access, res.refresh, res.user);
      router.replace('/dashboard');
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'লগইন ব্যর্থ — আবার চেষ্টা করুন',
      );
      setLoading(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <div className="lb">
          <div className="logo">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="8" cy="8" r="5" />
              <path d="M11.5 11.5 20 20" />
              <path d="M17 17l2.5-2.5" />
            </svg>
          </div>
          <div>
            <b style={{ fontSize: 16, fontWeight: 700 }}>Finance Super Key</b>
            <div style={{ fontSize: 12, color: 'var(--muted)' }}>Super Admin Panel</div>
          </div>
        </div>

        {error && <div className="err">{error}</div>}

        <div className="field">
          <label htmlFor="id">ফোন বা ইমেইল</label>
          <input
            id="id"
            className="input"
            value={identifier}
            onChange={(e) => setIdentifier(e.target.value)}
            placeholder="01700000000"
            autoComplete="username"
          />
        </div>
        <div className="field">
          <label htmlFor="pw">পাসওয়ার্ড</label>
          <input
            id="pw"
            className="input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            autoComplete="current-password"
          />
        </div>

        <button className="btn pri" style={{ width: '100%', marginTop: 4 }} disabled={loading}>
          {loading ? <span className="spinner" style={{ borderTopColor: '#fff' }} /> : 'লগইন'}
        </button>

        <div className="cred-hint">
          <b>Dev লগইন:</b> 01700000000 / Admin@12345
        </div>
      </form>
    </div>
  );
}
