'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { AuditLog, Overview } from '@/lib/types';
import { PageHeader, ThemeToggle, fmtDate } from '@/components/ui';

const svg = (d: React.ReactNode) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">{d}</svg>
);

function Tile({ icon, tone, value, label, sub }: { icon: React.ReactNode; tone: string; value: number | string; label: string; sub?: string }) {
  return (
    <div className="tile">
      <div className="row"><div className={`ti ${tone}`}>{icon}</div></div>
      <b className="num">{value}</b>
      <div className="lab">{label}</div>
      {sub && <small>{sub}</small>}
    </div>
  );
}
function SkelTile() {
  return (
    <div className="tile">
      <div className="row"><div className="skel" style={{ width: 36, height: 36, borderRadius: 10 }} /></div>
      <div className="skel" style={{ width: 54, height: 26, marginBottom: 9 }} />
      <div className="skel" style={{ width: '70%', height: 12 }} />
    </div>
  );
}

function Donut({ data }: { data: Overview['devices'] }) {
  const segs = [
    { v: data.active, c: 'var(--good)', l: 'Active' },
    { v: data.review, c: 'var(--warn)', l: 'Under Review' },
    { v: data.restricted, c: 'var(--crit)', l: 'Restricted' },
  ];
  const total = data.total || 1;
  const R = 54, C = 2 * Math.PI * R;
  let offset = 0;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 22, padding: 18 }}>
      <div style={{ position: 'relative', width: 132, height: 132, flex: '0 0 auto' }}>
        <svg width="132" height="132" viewBox="0 0 132 132" style={{ transform: 'rotate(-90deg)' }}>
          <circle cx="66" cy="66" r={R} fill="none" stroke="var(--surface-3)" strokeWidth="15" />
          {segs.map((s, i) => {
            const len = (s.v / total) * C;
            const el = (
              <circle key={i} cx="66" cy="66" r={R} fill="none" stroke={s.c} strokeWidth="15"
                strokeDasharray={`${len} ${C - len}`} strokeDashoffset={-offset} strokeLinecap="butt" />
            );
            offset += len;
            return el;
          })}
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center', lineHeight: 1.1 }}>
          <div>
            <div className="num" style={{ fontSize: 28, fontWeight: 700 }}>{data.total}</div>
            <div style={{ fontSize: 11, color: 'var(--faint)' }}>devices</div>
          </div>
        </div>
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 11 }}>
        {segs.map((s, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 9, fontSize: 13 }}>
            <span style={{ width: 9, height: 9, borderRadius: 3, background: s.c, flex: '0 0 auto' }} />
            <span style={{ color: 'var(--muted)' }}>{s.l}</span>
            <b className="num" style={{ marginLeft: 'auto', fontWeight: 650 }}>{s.v}</b>
          </div>
        ))}
      </div>
    </div>
  );
}

function actionTone(a: string): string {
  if (/approve|complete|grant|active/.test(a)) return 'var(--good)';
  if (/restrict|suspend|revoke|reject/.test(a)) return 'var(--crit)';
  return 'var(--primary-ink)';
}

export default function DashboardPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [activity, setActivity] = useState<AuditLog[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(() => {
    api.get<Overview>('/reports/overview').then(setData).catch((e) => setErr(e.message));
    api.get<AuditLog[]>('/audit-logs?limit=7').then(setActivity).catch(() => setActivity([]));
  }, []);
  useEffect(() => { load(); }, [load]);

  const pct = data && data.keys.total ? Math.round((data.keys.consumed / data.keys.total) * 100) : 0;

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle="Platform overview"
        actions={
          <>
            <ThemeToggle />
            <button className="icon-btn" onClick={load} aria-label="Refresh">{svg(<><path d="M21 12a9 9 0 1 1-2.6-6.4" /><path d="M21 3v6h-6" /></>)}</button>
          </>
        }
      />
      <div className="content">
        {err && <div className="err">{err}</div>}

        <div className="tiles">
          {!data ? Array.from({ length: 6 }).map((_, i) => <SkelTile key={i} />) : (
            <>
              <Tile tone="p" icon={svg(<><path d="M4 9h16l-1-5H5L4 9Z" /><path d="M5 9v11h14V9" /></>)} value={data.merchants.total} label="Total Merchants" sub={`${data.merchants.active} active · ${data.merchants.pending} pending`} />
              <Tile tone="a" icon={svg(<><circle cx="12" cy="8" r="3.5" /><path d="M5 20a7 7 0 0 1 14 0" /></>)} value={data.customers.total} label="Total Customers" />
              <Tile tone="p" icon={svg(<><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M11 18h2" /></>)} value={data.devices.total} label="Total Devices" />
              <Tile tone="g" icon={svg(<path d="M20 6 9 17l-5-5" />)} value={data.devices.active} label="Active Devices" />
              <Tile tone="c" icon={svg(<><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>)} value={data.devices.restricted} label="Restricted Devices" />
              <Tile tone="w" icon={svg(<><circle cx="12" cy="12" r="9" /><path d="M12 8v4M12 16h.01" /></>)} value={data.restrictionRequests.pending} label="Pending Review" sub="Awaiting approval" />
            </>
          )}
        </div>

        {data && data.restrictionRequests.pending > 0 && (
          <div className="panel" style={{ marginBottom: 18, borderColor: 'var(--warn)', background: 'var(--warn-soft)' }}>
            <div className="panel-head" style={{ borderBottom: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ color: 'var(--warn)' }}>{svg(<><circle cx="12" cy="12" r="9" /><path d="M12 8v4M12 16h.01" /></>)}</span>
                <h3 style={{ color: 'var(--warn)' }}>{data.restrictionRequests.pending} restriction request(s) awaiting review</h3>
              </div>
              <Link className="btn pri sm" href="/restriction-requests">Review now →</Link>
            </div>
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1.1fr)', gap: 16, marginBottom: 18 }} className="dash-grid">
          <div className="panel">
            <div className="panel-head"><h3>Device Status</h3></div>
            {data ? <Donut data={data.devices} /> : <div className="empty"><span className="spinner" /></div>}
          </div>
          <div className="panel">
            <div className="panel-head"><h3>Recent Activity</h3><Link href="/audit-logs" style={{ fontSize: 12.5, color: 'var(--primary-ink)', fontWeight: 600 }}>View all</Link></div>
            <div>
              {!activity ? (
                <div className="empty"><span className="spinner" /></div>
              ) : activity.length === 0 ? (
                <div className="empty">No activity yet</div>
              ) : (
                activity.map((l) => (
                  <div key={l.id} style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '11px 18px', borderBottom: '1px solid var(--line)' }}>
                    <span style={{ width: 8, height: 8, borderRadius: '50%', background: actionTone(l.action), flex: '0 0 auto' }} />
                    <span style={{ fontSize: 13, fontWeight: 550 }}>{l.action}</span>
                    <span className="mono" style={{ marginLeft: 'auto', fontSize: 11 }}>{fmtDate(l.createdAt)}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="panel-head">
            <div><h3>License Keys</h3><div className="sub">Platform-wide quota · {pct}% consumed</div></div>
          </div>
          <div className="keystrip">
            {data ? (
              <>
                <div><div className="kv" style={{ color: 'var(--primary-ink)' }}>{data.keys.total}</div><div className="kl">Total Keys</div></div>
                <div><div className="kv" style={{ color: 'var(--good)' }}>{data.keys.available}</div><div className="kl">Available</div></div>
                <div><div className="kv" style={{ color: 'var(--accent-ink)' }}>{data.keys.consumed}</div><div className="kl">In Use</div></div>
                <div><div className="kv" style={{ color: 'var(--muted)' }}>{data.keys.released}</div><div className="kl">Retired</div></div>
              </>
            ) : Array.from({ length: 4 }).map((_, i) => (
              <div key={i}><div className="skel" style={{ width: 50, height: 26, marginBottom: 6 }} /><div className="skel" style={{ width: 60, height: 12 }} /></div>
            ))}
          </div>
          {data && (
            <div style={{ padding: '0 18px 18px' }}>
              <div className="keybar">
                <i style={{ width: `${(data.keys.consumed / (data.keys.total || 1)) * 100}%`, background: 'var(--accent)' }} />
                <i style={{ width: `${(data.keys.released / (data.keys.total || 1)) * 100}%`, background: 'var(--muted)' }} />
              </div>
            </div>
          )}
        </div>

        <p className="note">
          Every restriction is governed by lawful ownership, explicit customer consent and server-side authorization.
          Emergency calls stay available at all times. The EMI finance module is coming soon.
        </p>
      </div>
    </>
  );
}
