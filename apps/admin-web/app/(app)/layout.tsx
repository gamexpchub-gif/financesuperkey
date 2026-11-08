'use client';

import { useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Link from 'next/link';
import { api, tokenStore, SessionUser } from '@/lib/api';
import { Overview } from '@/lib/types';
import { ThemeToggle } from '@/components/ui';
import { ToastProvider } from '@/components/toast';

const NAV = [
  { href: '/dashboard', label: 'Dashboard', icon: 'home' },
  { href: '/merchants', label: 'Merchants', icon: 'shop' },
  { href: '/customers', label: 'Customers', icon: 'users' },
  { href: '/devices', label: 'Devices', icon: 'device' },
  { href: '/restriction-requests', label: 'Restriction Review', icon: 'lock', badge: true },
  { href: '/audit-logs', label: 'Audit Log', icon: 'list' },
];

function Icon({ name }: { name: string }) {
  const p: Record<string, React.ReactNode> = {
    home: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20h14V9.5" /></>,
    shop: <><path d="M4 9h16l-1-5H5L4 9Z" /><path d="M5 9v11h14V9" /><path d="M9 20v-6h6v6" /></>,
    users: <><circle cx="9" cy="8" r="3.2" /><path d="M3 20a6 6 0 0 1 12 0M16 3.5a3.2 3.2 0 0 1 0 6M15 20a6 6 0 0 0-1-3.3" /></>,
    device: <><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M11 18h2" /></>,
    lock: <><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>,
    list: <><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" /></>,
  };
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {p[name]}
    </svg>
  );
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [user, setUser] = useState<SessionUser | null>(null);
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState(0);

  useEffect(() => {
    if (!tokenStore.get()) {
      router.replace('/login');
      return;
    }
    setUser(tokenStore.user());
    setReady(true);
    api.get<Overview>('/reports/overview').then((o) => setPending(o.restrictionRequests.pending)).catch(() => {});
  }, [router]);

  function logout() {
    tokenStore.clear();
    router.replace('/login');
  }

  if (!ready) {
    return (
      <div className="login-wrap">
        <span className="spinner" />
      </div>
    );
  }

  const roleLabel = user?.userType === 'super_admin' ? 'Super Admin' : 'Admin';

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#231704" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="8" cy="8" r="5" /><path d="M11.5 11.5 20 20M17 17l2.5-2.5" />
            </svg>
          </div>
          <div>
            <b>Finance Super Key</b>
            <span>Admin Console</span>
          </div>
        </div>

        <div className="nav-label">Overview</div>
        {NAV.slice(0, 1).map((n) => (
          <NavLink key={n.href} n={n} pathname={pathname} pending={pending} />
        ))}
        <div className="nav-label">Manage</div>
        {NAV.slice(1).map((n) => (
          <NavLink key={n.href} n={n} pathname={pathname} pending={pending} />
        ))}

        <div className="foot">
          <div className="user-chip">
            <div className="av">{roleLabel.slice(0, 1)}</div>
            <div style={{ minWidth: 0 }}>
              <b>{roleLabel}</b>
              <span>ID {user?.id?.slice(0, 8)}…</span>
            </div>
          </div>
          <div className="sidebar-actions">
            <ThemeToggle compact />
            <button className="s-btn" onClick={logout}>
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
              </svg>
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <main className="main">
        <ToastProvider>{children}</ToastProvider>
      </main>
    </div>
  );
}

function NavLink({
  n,
  pathname,
  pending,
}: {
  n: { href: string; label: string; icon: string; badge?: boolean };
  pathname: string;
  pending: number;
}) {
  const active = pathname === n.href || pathname.startsWith(n.href + '/');
  return (
    <Link href={n.href} className={`nav-item${active ? ' active' : ''}`}>
      <Icon name={n.icon} />
      {n.label}
      {n.badge && pending > 0 && <span className="pip">{pending}</span>}
    </Link>
  );
}
