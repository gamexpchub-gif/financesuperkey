'use client';

import { useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { AuditLog } from '@/lib/types';
import { Loading, fmtDate } from '@/components/ui';

function actionTone(action: string): string {
  if (action.includes('approve') || action.includes('complete') || action.includes('grant')) return 'good';
  if (action.includes('restrict') || action.includes('suspend') || action.includes('revoke') || action.includes('reject')) return 'crit';
  return 'info';
}

export default function AuditLogsPage() {
  const [rows, setRows] = useState<AuditLog[] | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<AuditLog[]>('/audit-logs?limit=100')
      .then(setRows)
      .catch((e) => setErr(e.message));
  }, []);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>অডিট লগ</h1>
          <p>append-only — কে কখন কী করেছে</p>
        </div>
      </div>

      {err && <div className="err">{err}</div>}

      <div className="panel">
        <div className="panel-head">
          <h3>কার্যক্রম</h3>
          {rows && <span className="pill info">{rows.length} টি</span>}
        </div>
        <div className="table-wrap">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <div className="empty">কোনো লগ নেই</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>অ্যাকশন</th>
                  <th>এনটিটি</th>
                  <th>অ্যাক্টর</th>
                  <th>সময়</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((l) => (
                  <tr key={l.id}>
                    <td>
                      <span className={`pill ${actionTone(l.action)}`}>
                        <span className="dot" /> {l.action}
                      </span>
                    </td>
                    <td className="mono">
                      {l.entityType ?? '—'}
                      {l.entityId ? ` · ${l.entityId.slice(0, 8)}…` : ''}
                    </td>
                    <td className="mono">{l.actorId ? `${l.actorId.slice(0, 8)}…` : 'system'}</td>
                    <td className="mono">{fmtDate(l.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </>
  );
}
