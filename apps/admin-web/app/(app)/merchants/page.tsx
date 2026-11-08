'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Merchant, Paged } from '@/lib/types';
import { MerchantPill, Loading, fmtDate } from '@/components/ui';

export default function MerchantsPage() {
  const [rows, setRows] = useState<Merchant[] | null>(null);
  const [filter, setFilter] = useState<string>('');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    try {
      const q = filter ? `?status=${filter}` : '';
      const res = await api.get<Paged<Merchant>>(`/merchants${q}`);
      setRows(res.data);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'লোড ব্যর্থ');
    }
  }, [filter]);

  useEffect(() => {
    load();
  }, [load]);

  async function act(id: string, action: 'approve' | 'suspend') {
    setBusy(id);
    try {
      await api.post(`/merchants/${id}/${action}`);
      await load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'ব্যর্থ');
    } finally {
      setBusy(null);
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>মার্চেন্ট ম্যানেজমেন্ট</h1>
          <p>দোকান approve, suspend ও মনিটর</p>
        </div>
      </div>

      <div className="toolbar" style={{ marginBottom: 16 }}>
        {[
          { v: '', l: 'সব' },
          { v: 'pending', l: 'অপেক্ষমাণ' },
          { v: 'active', l: 'সক্রিয়' },
          { v: 'suspended', l: 'স্থগিত' },
        ].map((f) => (
          <button key={f.v} className={`chip${filter === f.v ? ' on' : ''}`} onClick={() => setFilter(f.v)}>
            {f.l}
          </button>
        ))}
      </div>

      {err && <div className="err">{err}</div>}

      <div className="panel">
        <div className="panel-head">
          <h3>মার্চেন্ট</h3>
          {rows && <span className="pill info">{rows.length} টি</span>}
        </div>
        <div className="table-wrap">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <div className="empty">কোনো মার্চেন্ট নেই</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>নাম</th>
                  <th>ডিলার</th>
                  <th>ফোন</th>
                  <th>স্ট্যাটাস</th>
                  <th>যুক্ত হয়েছে</th>
                  <th style={{ textAlign: 'right' }}>অ্যাকশন</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((m) => (
                  <tr key={m.id}>
                    <td style={{ fontWeight: 600 }}>{m.name}</td>
                    <td className="mono">{m.dealerNo}</td>
                    <td className="mono">{m.phone ?? '—'}</td>
                    <td><MerchantPill status={m.status} /></td>
                    <td className="mono">{fmtDate(m.createdAt)}</td>
                    <td style={{ textAlign: 'right' }}>
                      {m.status !== 'active' && (
                        <button className="btn good sm" disabled={busy === m.id} onClick={() => act(m.id, 'approve')}>
                          Approve
                        </button>
                      )}
                      {m.status === 'active' && (
                        <button className="btn danger sm" disabled={busy === m.id} onClick={() => act(m.id, 'suspend')}>
                          Suspend
                        </button>
                      )}
                    </td>
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
