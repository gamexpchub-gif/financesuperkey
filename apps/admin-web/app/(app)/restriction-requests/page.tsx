'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { RestrictionRequest } from '@/lib/types';
import { RequestPill, Loading, fmtDate } from '@/components/ui';

export default function RestrictionRequestsPage() {
  const [rows, setRows] = useState<RestrictionRequest[] | null>(null);
  const [status, setStatus] = useState('pending');
  const [busy, setBusy] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const load = useCallback(async () => {
    setErr(null);
    try {
      const res = await api.get<RestrictionRequest[]>(
        `/restriction-requests?status=${status}`,
      );
      setRows(res);
    } catch (e) {
      setErr(e instanceof Error ? e.message : 'লোড ব্যর্থ');
    }
  }, [status]);

  useEffect(() => {
    load();
  }, [load]);

  async function decide(id: string, action: 'approve' | 'reject') {
    setBusy(id);
    setErr(null);
    try {
      await api.post(`/restriction-requests/${id}/${action}`);
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
          <h1>Restriction Review</h1>
          <p>মার্চেন্টের অনুরোধ যাচাই করে অনুমোদন বা প্রত্যাখ্যান করুন</p>
        </div>
      </div>

      <div className="toolbar" style={{ marginBottom: 16 }}>
        {[
          { v: 'pending', l: 'অপেক্ষমাণ' },
          { v: 'approved', l: 'অনুমোদিত' },
          { v: 'rejected', l: 'প্রত্যাখ্যাত' },
        ].map((f) => (
          <button key={f.v} className={`chip${status === f.v ? ' on' : ''}`} onClick={() => setStatus(f.v)}>
            {f.l}
          </button>
        ))}
      </div>

      {err && <div className="err">{err}</div>}

      <div className="panel">
        <div className="panel-head">
          <h3>অনুরোধ</h3>
          {rows && <span className="pill info">{rows.length} টি</span>}
        </div>
        <div className="table-wrap">
          {!rows ? (
            <Loading />
          ) : rows.length === 0 ? (
            <div className="empty">এই অবস্থায় কোনো অনুরোধ নেই</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>ধরন</th>
                  <th>কারণ</th>
                  <th>ডিভাইস</th>
                  <th>স্ট্যাটাস</th>
                  <th>সময়</th>
                  {status === 'pending' && <th style={{ textAlign: 'right' }}>সিদ্ধান্ত</th>}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <span className={`pill ${r.type === 'restrict' ? 'crit' : 'good'}`}>
                        <span className="dot" /> {r.type === 'restrict' ? 'সীমিত করুন' : 'প্রত্যাহার'}
                      </span>
                    </td>
                    <td style={{ maxWidth: 280 }}>{r.reason}</td>
                    <td className="mono">{r.deviceId.slice(0, 8)}…</td>
                    <td><RequestPill status={r.status} /></td>
                    <td className="mono">{fmtDate(r.createdAt)}</td>
                    {status === 'pending' && (
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button className="btn good sm" disabled={busy === r.id} onClick={() => decide(r.id, 'approve')}>
                          অনুমোদন
                        </button>{' '}
                        <button className="btn danger sm" disabled={busy === r.id} onClick={() => decide(r.id, 'reject')}>
                          প্রত্যাখ্যান
                        </button>
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
        অনুমোদন করলে সার্ভার device policy push করে (restricted অবস্থায়ও জরুরি কল চালু থাকে)। প্রতিটি সিদ্ধান্ত immutable audit log-এ সংরক্ষিত হয়।
      </p>
    </>
  );
}
