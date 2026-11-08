'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api';
import { Device, DeviceStatus, DeviceTimeline } from '@/lib/types';
import { DevicePill, fmtDate } from '@/components/ui';
import { useToast } from '@/components/toast';

export function DeviceDrawer({
  deviceId,
  onClose,
  onChanged,
}: {
  deviceId: string;
  onClose: () => void;
  onChanged: () => void;
}) {
  const toast = useToast();
  const [device, setDevice] = useState<Device | null>(null);
  const [timeline, setTimeline] = useState<DeviceTimeline | null>(null);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    const [d, t] = await Promise.all([
      api.get<Device>(`/devices/${deviceId}`),
      api.get<DeviceTimeline>(`/devices/${deviceId}/timeline`),
    ]);
    setDevice(d);
    setTimeline(t);
  }, [deviceId]);

  useEffect(() => {
    load().catch((e) => toast.error(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deviceId]);

  async function act(kind: 'restrict' | 'release') {
    if (reason.trim().length < 3) {
      toast.error('Please enter a reason (min 3 characters)');
      return;
    }
    setBusy(true);
    try {
      await api.post(`/devices/${deviceId}/${kind}`, { reason: reason.trim() });
      toast.success(kind === 'restrict' ? 'Device restricted' : 'Restriction released');
      setReason('');
      await load();
      onChanged();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Action failed');
    } finally {
      setBusy(false);
    }
  }

  const restricted = device?.status === 'restricted';

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="drawer" role="dialog" aria-label="Device detail">
        <div className="drawer-head">
          <div className="dicon">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M11 18h2" /></svg>
          </div>
          <div style={{ minWidth: 0 }}>
            <h3>{device?.customer?.fullName ?? 'Device'}</h3>
            <div className="sub">{device?.model ?? '…'}</div>
          </div>
          <div className="x">
            {device && <DevicePill status={device.status as DeviceStatus} />}
          </div>
        </div>

        <div className="drawer-body">
          {!device ? (
            <div className="empty"><span className="spinner" /></div>
          ) : (
            <>
              <div className="kv-grid">
                <div><span>IMEI</span><b className="mono">{device.imeiMasked}</b></div>
                <div><span>Phone</span><b className="mono">{device.customer?.phone ?? '—'}</b></div>
                <div><span>Enrolled</span><b>{fmtDate(device.createdAt)}</b></div>
                <div><span>Enrollment</span><b>{timeline?.enrollment?.method === 'qr_device_owner' ? 'QR Device Owner' : timeline?.enrollment?.method ?? '—'}</b></div>
              </div>

              <div className="panel" style={{ marginBottom: 20, background: 'var(--accent-soft)', borderColor: 'transparent' }}>
                <div style={{ padding: '12px 14px', display: 'flex', gap: 10 }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--accent-ink)" strokeWidth="2" style={{ flex: '0 0 auto', marginTop: 1 }}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" strokeLinecap="round" /></svg>
                  <div>
                    <b style={{ fontSize: 12.5, color: 'var(--accent-ink)', display: 'block' }}>EMI info — coming soon</b>
                    <span style={{ fontSize: 11.5, color: 'var(--muted)' }}>Installment & payment data arrives with the finance module.</span>
                  </div>
                </div>
              </div>

              <div className="sec-label">Activity timeline</div>
              <div className="timeline">
                {timelineItems(timeline).map((it, i) => (
                  <div className="tl" key={i}>
                    <div className="td" style={{ boxShadow: `0 0 0 2px ${it.color}` }}><i style={{ background: it.color }} /></div>
                    <div className="tc">
                      <b>{it.title}</b>
                      <span>{it.sub}</span> · <em>{it.time}</em>
                    </div>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {device && (
          <div className="drawer-foot">
            <input
              className="input"
              placeholder={restricted ? 'Reason for releasing…' : 'Reason for restriction…'}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <div className="action-row">
              {restricted ? (
                <button className="btn good" disabled={busy} onClick={() => act('release')} style={{ gridColumn: '1 / -1' }}>
                  Release restriction
                </button>
              ) : (
                <>
                  <button className="btn danger" disabled={busy} onClick={() => act('restrict')}>Restrict device</button>
                  <button className="btn ghost" onClick={onClose}>Close</button>
                </>
              )}
            </div>
            <p style={{ fontSize: 11, color: 'var(--faint)', margin: '2px 0 0', lineHeight: 1.5 }}>
              Applied by the server with an audit entry. Emergency calls stay available when restricted.
            </p>
          </div>
        )}
      </aside>
    </>
  );
}

function timelineItems(t: DeviceTimeline | null) {
  if (!t) return [];
  const items: { title: string; sub: string; time: string; color: string }[] = [];
  const C = { good: 'var(--good)', warn: 'var(--warn)', crit: 'var(--crit)', info: 'var(--primary-ink)' };

  if (t.enrollment) {
    items.push({
      title: 'Enrolled',
      sub: t.enrollment.method === 'qr_device_owner' ? 'QR Device Owner provisioning' : t.enrollment.method,
      time: fmtDate(t.enrollment.createdAt),
      color: C.good,
    });
    if (t.enrollment.consentAt) {
      items.push({ title: 'Consent & signature captured', sub: `Version ${t.enrollment.consentTextVersion ?? '—'}`, time: fmtDate(t.enrollment.consentAt), color: C.good });
    }
  }
  for (const r of [...t.requests].reverse()) {
    const col = r.type === 'restrict' ? C.crit : C.good;
    const statusWord = r.status === 'approved' ? 'approved' : r.status;
    items.push({
      title: `${r.type === 'restrict' ? 'Restriction' : 'Release'} ${statusWord}`,
      sub: r.reason,
      time: fmtDate(r.reviewedAt ?? r.createdAt),
      color: r.status === 'rejected' ? C.warn : col,
    });
  }
  return items.reverse();
}
