'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Customer, Paged } from '@/lib/types';
import { PageHeader, TableSkeleton, EmptyState, fmtDate } from '@/components/ui';

export default function CustomersPage() {
  const [rows, setRows] = useState<Customer[] | null>(null);
  const [total, setTotal] = useState(0);
  const [q, setQ] = useState('');
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    try {
      const params = new URLSearchParams();
      if (q) params.set('q', q);
      params.set('limit', '50');
      const res = await api.get<Paged<Customer>>(`/customers?${params.toString()}`);
      setRows(res.data);
      setTotal(res.total);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to load');
      setRows([]);
    }
  }, [q]);

  useEffect(() => {
    setRows(null);
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
  }, [load, q]);

  return (
    <>
      <PageHeader title="Customers" subtitle={`${total} registered customer${total === 1 ? '' : 's'}`} />
      <div className="content">
        <div className="toolbar">
          <div className="search-box">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" strokeLinecap="round" />
            </svg>
            <input className="input" placeholder="Name or phone" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
        </div>

        {err && <div className="err">{err}</div>}

        <div className="panel">
          <div className="panel-head">
            <h3>Customers</h3>
            {rows && <span className="count-pill">{rows.length} shown</span>}
          </div>
          <div className="table-wrap">
            {!rows ? (
              <TableSkeleton cols={5} />
            ) : rows.length === 0 ? (
              <EmptyState title="No customers found" icon={
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="8" r="3.5" /><path d="M5 20a7 7 0 0 1 14 0" /></svg>
              } />
            ) : (
              <table>
                <thead>
                  <tr><th>Name</th><th>Phone</th><th>Merchant</th><th>Dealer</th><th>Registered</th></tr>
                </thead>
                <tbody>
                  {rows.map((c) => (
                    <tr key={c.id}>
                      <td className="cell-strong">{c.fullName}</td>
                      <td className="mono">{c.phone}</td>
                      <td>{c.merchant?.name ?? '—'}</td>
                      <td className="mono">{c.dealerNo ?? c.merchant?.dealerNo ?? '—'}</td>
                      <td className="mono">{fmtDate(c.createdAt)}</td>
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
