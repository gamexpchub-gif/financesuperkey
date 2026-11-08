'use client';

import { useEffect, useState } from 'react';
import { DeviceStatus } from '@/lib/types';

/* ---------- status pills ---------- */
const DEVICE_LABEL: Record<DeviceStatus, { t: string; c: string }> = {
  active: { t: 'Active', c: 'good' },
  review: { t: 'Under Review', c: 'warn' },
  restricted: { t: 'Restricted', c: 'crit' },
};
export function DevicePill({ status }: { status: DeviceStatus }) {
  const m = DEVICE_LABEL[status] ?? { t: status, c: 'muted' };
  return <span className={`pill ${m.c}`}><span className="dot" />{m.t}</span>;
}

const MERCHANT_LABEL: Record<string, { t: string; c: string }> = {
  active: { t: 'Active', c: 'good' },
  pending: { t: 'Pending', c: 'warn' },
  suspended: { t: 'Suspended', c: 'crit' },
};
export function MerchantPill({ status }: { status: string }) {
  const m = MERCHANT_LABEL[status] ?? { t: status, c: 'muted' };
  return <span className={`pill ${m.c}`}><span className="dot" />{m.t}</span>;
}

const REQ_LABEL: Record<string, { t: string; c: string }> = {
  pending: { t: 'Pending', c: 'warn' },
  approved: { t: 'Approved', c: 'good' },
  rejected: { t: 'Rejected', c: 'crit' },
};
export function RequestPill({ status }: { status: string }) {
  const m = REQ_LABEL[status] ?? { t: status, c: 'muted' };
  return <span className={`pill ${m.c}`}><span className="dot" />{m.t}</span>;
}

/* ---------- loading states ---------- */
export function TableSkeleton({ cols, rows = 6 }: { cols: number; rows?: number }) {
  return (
    <table>
      <tbody>
        {Array.from({ length: rows }).map((_, r) => (
          <tr key={r}>
            {Array.from({ length: cols }).map((__, c) => (
              <td key={c}>
                <div className="skel" style={{ height: 14, width: c === 0 ? '70%' : `${40 + ((r + c) % 3) * 15}%` }} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function EmptyState({ title, icon }: { title: string; icon?: React.ReactNode }) {
  return (
    <div className="empty">
      <div className="eicon">
        {icon ?? (
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" strokeLinecap="round" />
          </svg>
        )}
      </div>
      {title}
    </div>
  );
}

/* ---------- page header (inside topbar) ---------- */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="topbar">
      <div>
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="actions">{actions}</div>}
    </div>
  );
}

/* ---------- theme toggle ---------- */
export function ThemeToggle({ compact }: { compact?: boolean }) {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    const explicit = document.documentElement.getAttribute('data-theme');
    const prefers = window.matchMedia('(prefers-color-scheme: dark)').matches;
    setDark(explicit ? explicit === 'dark' : prefers);
  }, []);
  function toggle() {
    const next = dark ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try { localStorage.setItem('fsk-theme', next); } catch { /* ignore */ }
    setDark(!dark);
  }
  const icon = dark ? (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="4.5" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  ) : (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
    </svg>
  );
  if (compact) {
    return (
      <button className="s-btn" onClick={toggle} aria-label="Toggle theme">
        {icon}
      </button>
    );
  }
  return (
    <button className="icon-btn" onClick={toggle} aria-label="Toggle theme">
      {icon}
    </button>
  );
}

export function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString('en-GB', {
      day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return iso;
  }
}
