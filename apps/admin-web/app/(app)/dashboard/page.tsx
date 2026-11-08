'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { Overview } from '@/lib/types';
import { Loading } from '@/components/ui';

function Tile({
  icon,
  tone,
  value,
  label,
  sub,
}: {
  icon: React.ReactNode;
  tone: string;
  value: number | string;
  label: string;
  sub?: string;
}) {
  return (
    <div className="tile">
      <div className={`ti ${tone}`}>{icon}</div>
      <b className="num">{value}</b>
      <span>{label}</span>
      {sub && <small>{sub}</small>}
    </div>
  );
}

const svg = (d: React.ReactNode) => (
  <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    {d}
  </svg>
);

export default function DashboardPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<Overview>('/reports/overview')
      .then(setData)
      .catch((e) => setErr(e.message));
  }, []);

  if (err) return <div className="err">{err}</div>;
  if (!data) return <Loading />;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>ড্যাশবোর্ড</h1>
          <p>পুরো প্ল্যাটফর্মের সারসংক্ষেপ</p>
        </div>
        {data.restrictionRequests.pending > 0 && (
          <Link className="btn pri" href="/restriction-requests">
            {data.restrictionRequests.pending} টি review অপেক্ষমাণ →
          </Link>
        )}
      </div>

      <div className="tiles">
        <Tile tone="p" icon={svg(<><path d="M4 9h16l-1-5H5L4 9Z" /><path d="M5 9v11h14V9" /></>)} value={data.merchants.total} label="মোট মার্চেন্ট" sub={`${data.merchants.active} সক্রিয় · ${data.merchants.pending} অপেক্ষমাণ`} />
        <Tile tone="a" icon={svg(<><circle cx="12" cy="8" r="3.5" /><path d="M5 20a7 7 0 0 1 14 0" /></>)} value={data.customers.total} label="মোট কাস্টমার" />
        <Tile tone="p" icon={svg(<><rect x="6" y="2" width="12" height="20" rx="3" /><path d="M11 18h2" /></>)} value={data.devices.total} label="মোট ডিভাইস" />
        <Tile tone="g" icon={svg(<path d="M20 6 9 17l-5-5" />)} value={data.devices.active} label="সচল ডিভাইস" />
        <Tile tone="c" icon={svg(<><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>)} value={data.devices.restricted} label="সীমিত ডিভাইস" />
        <Tile tone="w" icon={svg(<><circle cx="12" cy="12" r="9" /><path d="M12 8v4M12 16h.01" /></>)} value={data.restrictionRequests.pending} label="Pending review" sub="অনুমোদনের অপেক্ষায়" />
      </div>

      <div className="panel">
        <div className="panel-head">
          <h3>লাইসেন্স কী (প্ল্যাটফর্মজুড়ে)</h3>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(130px,1fr))' }}>
          {[
            { v: data.keys.total, l: 'মোট কী', c: 'var(--primary-ink)' },
            { v: data.keys.available, l: 'অবশিষ্ট', c: 'var(--good)' },
            { v: data.keys.consumed, l: 'ব্যবহৃত', c: 'var(--accent-ink)' },
            { v: data.keys.released, l: 'রিটায়ার্ড', c: 'var(--muted)' },
          ].map((k, i) => (
            <div key={i} style={{ padding: '18px', borderRight: i < 3 ? '1px solid var(--line)' : 0 }}>
              <div className="num" style={{ fontSize: 26, fontWeight: 700, color: k.c }}>{k.v}</div>
              <div style={{ fontSize: 12, color: 'var(--muted)', fontWeight: 600, marginTop: 2 }}>{k.l}</div>
            </div>
          ))}
        </div>
      </div>

      <p className="note">
        সকল restriction বৈধ মালিকানা, কাস্টমারের সম্মতি ও সার্ভার-অনুমোদনের ভিত্তিতে পরিচালিত। EMI finance module শীঘ্রই আসছে।
      </p>
    </>
  );
}
