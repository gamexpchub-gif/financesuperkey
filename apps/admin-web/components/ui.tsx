'use client';

import { DeviceStatus } from '@/lib/types';

const DEVICE_LABEL: Record<DeviceStatus, { t: string; c: string }> = {
  active: { t: 'সচল', c: 'good' },
  review: { t: 'পর্যালোচনা', c: 'warn' },
  restricted: { t: 'সীমিত', c: 'crit' },
};

export function DevicePill({ status }: { status: DeviceStatus }) {
  const m = DEVICE_LABEL[status] ?? { t: status, c: 'muted' };
  return (
    <span className={`pill ${m.c}`}>
      <span className="dot" /> {m.t}
    </span>
  );
}

const MERCHANT_LABEL: Record<string, { t: string; c: string }> = {
  active: { t: 'সক্রিয়', c: 'good' },
  pending: { t: 'অপেক্ষমাণ', c: 'warn' },
  suspended: { t: 'স্থগিত', c: 'crit' },
};

export function MerchantPill({ status }: { status: string }) {
  const m = MERCHANT_LABEL[status] ?? { t: status, c: 'muted' };
  return (
    <span className={`pill ${m.c}`}>
      <span className="dot" /> {m.t}
    </span>
  );
}

const REQ_LABEL: Record<string, { t: string; c: string }> = {
  pending: { t: 'অপেক্ষমাণ', c: 'warn' },
  approved: { t: 'অনুমোদিত', c: 'good' },
  rejected: { t: 'প্রত্যাখ্যাত', c: 'crit' },
};

export function RequestPill({ status }: { status: string }) {
  const m = REQ_LABEL[status] ?? { t: status, c: 'muted' };
  return (
    <span className={`pill ${m.c}`}>
      <span className="dot" /> {m.t}
    </span>
  );
}

export function Loading() {
  return (
    <div className="empty">
      <span className="spinner" />
    </div>
  );
}

export function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleString('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}
