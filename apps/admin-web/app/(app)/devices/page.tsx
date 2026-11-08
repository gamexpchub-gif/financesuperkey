'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Device, DeviceStatus, Paged } from '@/lib/types';
import { DevicePill, Loading, fmtDate } from '@/components/ui';

export default function DevicesPage() {
  const [rows, setRows] = useState<Device[] | null>(null);
  const [total, setTotal] = useState(0);
  const [status, setStatus] = useState<string>('');
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
      setErr(e instanceof Error ? e.message : 'লোড ব্যর্থ');
    }
  }, [status, q]);

  useEffect(() => {
    const t = setTimeout(load, q ? 300 : 0);
    return () => clearTimeout(t);
  }, [load, q]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>ডিভাইস ম্যানেজমেন্ট</h1>
          <p>সব enrolled device — {total} টি</p>
        </div>
      </div>

      <div className="toolbar" style={{ marginBottom: 16 }}>
        <input
          className="input"
          style={{ maxWidth: 280 }}
          placeholder="মডেল, IMEI বা কাস্টমার"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        {[
          { v: '', l: 'সব' },
          { v: 'active', l: 'সচল' },
          { v: 'review', l: 'পর্যালোচনা' },
          { v: 'restricted', l: 'সীমিত' },
        ].map((f) => (
          <button key={f.v} className={`chip${status === f.v ? ' on' : ''}`} onClick={() => setStatus(f.v)}>
            {f.l}
          </button>
        ))}
      </div>

      {err && <div className="err">{err}</div>}

      <div className="panel">
        <div className="panel-head">
          <h3>ডিভাইস</h3>
          {rows && <span className="pill info">{rows.length} দেখানো হচ্ছে</span>}
        </div>
        <div className="table-wrap">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <div className="empty">কোনো ডিভাইস পাওয়া যায়নি</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>কাস্টমার</th>
                  <th>মডেল</th>
                  <th>IMEI</th>
                  <th>স্ট্যাটাস</th>
                  <th>এনরোল</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => (
                  <tr key={d.id}>
                    <td style={{ fontWeight: 600 }}>
                      {d.customer?.fullName ?? '—'}
                      <div className="mono" style={{ fontWeight: 400 }}>{d.customer?.phone ?? ''}</div>
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
    </>
  );
}
