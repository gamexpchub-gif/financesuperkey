'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { RestrictionRequest } from '@/lib/types';
import { RequestPill, PageHeader, TableSkeleton, EmptyState, fmtDate } from '@/components/ui';
import { useToast } from '@/components/toast';

const FILTERS = [
  { v: 'pending', l: 'Pending' },
  { v: 'approved', l: 'Approved' },
  { v: 'rejected', l: 'Rejected' },
];

export default function RestrictionRequestsPage() {
  const toast = useToast();
  const [rows, setRows] = useState<RestrictionRequest[] | null>(null);
  const [status, setStatus] = useState('pending');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    setRows(null);
    try {
      const res = await api.get<RestrictionRequest[]>(`/restriction-requests?status=${status}`);
      setRows(res);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to load');
      setRows([]);
    }
  }, [status]);

  useEffect(() => { load(); }, [load]);

  async function decide(id: string, action: 'approve' | 'reject') {
    setBusy(id);
    setErr(null);
    try {
      await api.post(`/restriction-requests/${id}/${action}`);
      toast.success(action === 'approve' ? 'Request approved — policy applied' : 'Request rejected');
      await load();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Action failed');
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <PageHeader title="Restriction Review" subtitle="Review and decide on merchant requests" />
      <div className="content">
        <div className="toolbar">
          <div className="seg">
            {FILTERS.map((f) => (
              <button key={f.v} className={status === f.v ? 'on' : ''} onClick={() => setStatus(f.v)}>{f.l}</button>
            ))}
          </div>
        </div>

        {err && <div className="err">{err}</div>}

        <div className="panel">
          <div className="panel-head">
            <h3>Requests</h3>
            {rows && <span className="count-pill">{rows.length}</span>}
          </div>
          <div className="table-wrap">
            {!rows ? (
              <TableSkeleton cols={5} />
            ) : rows.length === 0 ? (
              <EmptyState title={`No ${status} requests`} icon={
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>
              } />
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Type</th><th>Reason</th><th>Device</th><th>Status</th><th>Requested</th>
                    {status === 'pending' && <th style={{ textAlign: 'right' }}>Decision</th>}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r) => (
                    <tr key={r.id}>
                      <td>
                        <span className={`pill ${r.type === 'restrict' ? 'crit' : 'good'}`}>
                          <span className="dot" />{r.type === 'restrict' ? 'Restrict' : 'Release'}
                        </span>
                      </td>
                      <td style={{ maxWidth: 300 }}>{r.reason}</td>
                      <td className="mono">{r.deviceId.slice(0, 8)}…</td>
                      <td><RequestPill status={r.status} /></td>
                      <td className="mono">{fmtDate(r.createdAt)}</td>
                      {status === 'pending' && (
                        <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <button className="btn good sm" disabled={busy === r.id} onClick={() => decide(r.id, 'approve')}>Approve</button>{' '}
                          <button className="btn danger sm" disabled={busy === r.id} onClick={() => decide(r.id, 'reject')}>Reject</button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>

        <p className="note">
          Approving pushes a device policy from the server (emergency calls stay on even when restricted).
          Every decision is written to the append-only audit log.
        </p>
      </div>
    </>
  );
}
