'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Device, DeviceStatus, Paged } from '@/lib/types';
import { DevicePill, PageHeader, TableSkeleton, EmptyState, fmtDate } from '@/components/ui';

const FILTERS = [
  { v: '', l: 'All' },
  { v: 'active', l: 'Active' },
  { v: 'review', l: 'Review' },
  { v: 'restricted', l: 'Restricted' },
];

export default function DevicesPage() {
  const [rows, setRows] = useState<Device[] | null>(null);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    try {
      const params = new URLSearchParams();
      if (status) params.set('status', status);
      if (q) params.set('q', q);
      params.set('limit', '50');
      const res = await api.get<Paged<Device>>(`/devices?${params.toString()}`);
      setRows(res.data);
      setTotal(res.total);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'Failed to load');
      setRows([]);
    }
  }, [status, q]);

  useEffect(() => {
    setRows(null);
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
  }, [load, q]);

  return (
    <>
      <PageHeader title="Devices" subtitle={`${total} enrolled device${total === 1 ? '' : 's'}`} />
      <div className="content">
        <div className="toolbar">
          <div className="search-box">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" strokeLinecap="round" />
            </svg>
            <input className="input" placeholder="Model, IMEI or customer" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <div className="seg">
            {FILTERS.map((f) => (
              <button key={f.v} className={status === f.v ? 'on' : ''} onClick={() => setStatus(f.v)}>{f.l}</button>
            ))}
          </div>
        </div>

        {err && <div className="err">{err}</div>}

        <div className="panel">
          <div className="panel-head">
            <h3>Devices</h3>
            {rows && <span className="count-pill">{rows.length} shown</span>}
          </div>
          <div className="table-wrap">
            {!rows ? (
              <TableSkeleton cols={5} />
            ) : rows.length === 0 ? (
              <EmptyState title="No devices found" />
            ) : (
              <table>
                <thead>
                  <tr><th>Customer</th><th>Model</th><th>IMEI</th><th>Status</th><th>Enrolled</th></tr>
                </thead>
                <tbody>
                  {rows.map((d) => (
                    <tr key={d.id}>
                      <td>
                        <div className="cell-strong">{d.customer?.fullName ?? '—'}</div>
                        <div className="mono">{d.customer?.phone ?? ''}</div>
                      </td>
                      <td>{d.model}</td>
                      <td className="mono">{d.imeiMasked}</td>
                      <td><DevicePill status={d.status as DeviceStatus} /></td>
                      <td className="mono">{fmtDate(d.createdAt)}</td>
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
