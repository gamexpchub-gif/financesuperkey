'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Merchant, Paged } from '@/lib/types';
import { MerchantPill, PageHeader, TableSkeleton, EmptyState, fmtDate } from '@/components/ui';

const FILTERS = [
  { v: '', l: 'All' },
  { v: 'pending', l: 'Pending' },
  { v: 'active', l: 'Active' },
  { v: 'suspended', l: 'Suspended' },
];

export default function MerchantsPage() {
  const [rows, setRows] = useState<Merchant[] | null>(null);
  const [filter, setFilter] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    setRows(null);
    try {
      const q = filter ? `?status=${filter}` : '';
      const res = await api.get<Paged<Merchant>>(`/merchants${q}`);
      setRows(res.data);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to load');
      setRows([]);
    }
  }, [filter]);

  useEffect(() => { load(); }, [load]);

  async function act(id: string, action: 'approve' | 'suspend') {
    setBusy(id);
    try {
      await api.post(`/merchants/${id}/${action}`);
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Action failed');
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <PageHeader title="Merchants" subtitle="Approve, suspend and monitor shops" />
      <div className="content">
        <div className="toolbar">
          <div className="seg">
            {FILTERS.map((f) => (
              <button key={f.v} className={filter === f.v ? 'on' : ''} onClick={() => setFilter(f.v)}>{f.l}</button>
            ))}
          </div>
        </div>

        {err && <div className="err">{err}</div>}

        <div className="panel">
          <div className="panel-head">
            <h3>Merchants</h3>
            {rows && <span className="count-pill">{rows.length}</span>}
          </div>
          <div className="table-wrap">
            {!rows ? (
              <TableSkeleton cols={6} />
            ) : rows.length === 0 ? (
              <EmptyState title="No merchants found" />
            ) : (
              <table>
                <thead>
                  <tr>
                    <th>Name</th><th>Dealer</th><th>Phone</th><th>Status</th><th>Joined</th><th style={{ textAlign: 'right' }}>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((m) => (
                    <tr key={m.id}>
                      <td className="cell-strong">{m.name}</td>
                      <td className="mono">{m.dealerNo}</td>
                      <td className="mono">{m.phone ?? '—'}</td>
                      <td><MerchantPill status={m.status} /></td>
                      <td className="mono">{fmtDate(m.createdAt)}</td>
                      <td style={{ textAlign: 'right' }}>
                        {m.status !== 'active' ? (
                          <button className="btn good sm" disabled={busy === m.id} onClick={() => act(m.id, 'approve')}>Approve</button>
                        ) : (
                          <button className="btn danger sm" disabled={busy === m.id} onClick={() => act(m.id, 'suspend')}>Suspend</button>
                        )}
                      </td>
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
