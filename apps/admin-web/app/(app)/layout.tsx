'use client';

import { useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Link from 'next/link';
import { api, tokenStore, SessionUser } from '@/lib/api';
import { Overview } from '@/lib/types';

const NAV = [
  { href: '/dashboard', label: 'ড্যাশবোর্ড', icon: 'home' },
  { href: '/merchants', label: 'মার্চেন্ট', icon: 'shop' },
  { href: '/devices', label: 'ডিভাইস', icon: 'device' },
  { href: '/restriction-requests', label: 'Restriction Review', icon: 'lock', badge: true },
  { href: '/audit-logs', label: 'অডিট লগ', icon: 'list' },
];

function Icon({ name }: { name: string }) {
  const p: Record<string, React.ReactNode> = {
    home: <><path d="M3 10.5 12 3l9 7.5" /><path d="M5 9.5V20h14V9.5" /></>,
    shop: <><path d="M4 9h16l-1-5H5L4 9Z" /><path d="M5 9v11h14V9" /><path d="M9 20v-6h6v6" /></>,
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
    api
      .get<Overview>('/reports/overview')
      .then((o) => setPending(o.restrictionRequests.pending))
      .catch(() => {});
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

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="logo">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.1" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="8" cy="8" r="5" />
              <path d="M11.5 11.5 20 20M17 17l2.5-2.5" />
            </svg>
          </div>
          <div>
            <b>Super Key</b>
            <span>Admin Panel</span>
          </div>
        </div>

        {NAV.map((n) => {
          const active = pathname === n.href || pathname.startsWith(n.href + '/');
          return (
            <Link key={n.href} href={n.href} className={`nav-item${active ? ' active' : ''}`}>
              <Icon name={n.icon} />
              {n.label}
              {n.badge && pending > 0 && <span className="pip">{pending}</span>}
            </Link>
          );
        })}

        <div className="foot">
          <div className="who">
            {user?.userType === 'super_admin' ? 'Super Admin' : 'Admin'}
            <br />
            {user?.id?.slice(0, 8)}…
          </div>
          <button className="logout" onClick={logout}>
            লগ আউট
          </button>
        </div>
      </aside>

      <main className="main">{children}</main>
    </div>
  );
}
