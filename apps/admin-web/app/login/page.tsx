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
      const res = await api.post<LoginResponse>('/auth/login', { identifier, password });
      if (res.user.userType !== 'super_admin' && res.user.userType !== 'admin_staff') {
        setError('This panel is for administrators only.');
        setLoading(false);
        return;
      }
      tokenStore.set(res.access, res.refresh, res.user);
      router.replace('/dashboard');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Sign in failed — please try again');
      setLoading(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <div className="lb">
          <div className="logo">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="8" cy="8" r="5" /><path d="M11.5 11.5 20 20" /><path d="M17 17l2.5-2.5" />
            </svg>
          </div>
          <div>
            <h2>Finance Super Key</h2>
            <div className="lsub">Super Admin Console</div>
          </div>
        </div>

        <div className="login-form">
          {error && (
            <div className="err">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="9" /><path d="M12 8v4M12 16h.01" strokeLinecap="round" /></svg>
              {error}
            </div>
          )}

          <div className="field">
            <label htmlFor="id">Phone or email</label>
            <input id="id" className="input" value={identifier} onChange={(e) => setIdentifier(e.target.value)} placeholder="01700000000" autoComplete="username" />
          </div>
          <div className="field">
            <label htmlFor="pw">Password</label>
            <input id="pw" className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" autoComplete="current-password" />
          </div>

          <button className="btn pri" style={{ width: '100%', marginTop: 4 }} disabled={loading}>
            {loading ? <span className="spinner" style={{ borderTopColor: '#fff' }} /> : 'Sign in'}
          </button>

          <div className="cred-hint">
            <b>Dev login:</b> <code>01700000000</code> / <code>Admin@12345</code>
          </div>
        </div>
      </form>
    </div>
  );
}
