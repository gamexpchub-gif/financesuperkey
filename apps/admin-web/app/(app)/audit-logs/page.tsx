'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { AuditLog } from '@/lib/types';
import { PageHeader, TableSkeleton, EmptyState, fmtDate } from '@/components/ui';

function actionTone(action: string): string {
  if (/approve|complete|grant|active/.test(action)) return 'good';
  if (/restrict|suspend|revoke|reject/.test(action)) return 'crit';
  return 'info';
}

export default function AuditLogsPage() {
  const [rows, setRows] = useState<AuditLog[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(() => {
    setRows(null);
    api.get<AuditLog[]>('/audit-logs?limit=100').then(setRows).catch((e) => { setErr(e.message); setRows([]); });
  }, []);
  useEffect(() => { load(); }, [load]);

  return (
    <>
      <PageHeader
        title="Audit Log"
        subtitle="Append-only record of every action"
        actions={
          <button className="icon-btn" onClick={load} aria-label="Refresh">
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 12a9 9 0 1 1-2.6-6.4" /><path d="M21 3v6h-6" /></svg>
          </button>
        }
      />
      <div className="content">
        {err && <div className="err">{err}</div>}
        <div className="panel">
          <div className="panel-head">
            <h3>Activity</h3>
            {rows && <span className="count-pill">{rows.length}</span>}
          </div>
          <div className="table-wrap">
            {!rows ? (
              <TableSkeleton cols={4} />
            ) : rows.length === 0 ? (
              <EmptyState title="No activity yet" />
            ) : (
              <table>
                <thead>
                  <tr><th>Action</th><th>Entity</th><th>Actor</th><th>Time</th></tr>
                </thead>
                <tbody>
                  {rows.map((l) => (
                    <tr key={l.id}>
                      <td><span className={`pill ${actionTone(l.action)}`}><span className="dot" />{l.action}</span></td>
                      <td className="mono">{l.entityType ?? '—'}{l.entityId ? ` · ${l.entityId.slice(0, 8)}…` : ''}</td>
                      <td className="mono">{l.actorId ? `${l.actorId.slice(0, 8)}…` : 'system'}</td>
                      <td className="mono">{fmtDate(l.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      </div>
    </>
  );
}
