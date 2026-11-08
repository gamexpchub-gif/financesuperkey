# Super Admin Web Panel

পুরো platform-এর control center। **Next.js 14 (App Router) + TypeScript**, backend API-র সাথে যুক্ত।

## চালানো

```bash
cd apps/admin-web
npm install
cp .env.example .env        # NEXT_PUBLIC_API_BASE ঠিক আছে কিনা দেখুন
npm run dev                 # http://localhost:3000  (backend 3000-এ চললে PORT বদলান)
```

> backend ও admin দুটোই ডিফল্ট 3000-এ চায়। backend 3000-এ রেখে admin অন্য port-এ চালান:
> ```bash
> PORT=3100 npm run dev        # admin → http://localhost:3100
> ```
> আর `.env`-এ `NEXT_PUBLIC_API_BASE=http://localhost:3000/v1` রাখুন।

**লগইন (dev):** Super Admin `01700000000 / Admin@12345`

## স্ক্রিন
- **ড্যাশবোর্ড** — merchant/customer/device/key সারসংক্ষেপ (`/reports/overview`)
- **মার্চেন্ট** — list, approve, suspend
- **ডিভাইস** — list, search, status filter
- **Restriction Review** — merchant-এর অনুরোধ **approve / reject** → server policy push
- **অডিট লগ** — append-only কার্যক্রম

## যাচাই (Playwright দিয়ে টেস্টেড)
login → dashboard redirect · ৬টি stat tile · navigation · merchant/device/audit টেবিল — সব live backend-এর সাথে কাজ করে।

## গঠন
```
app/
  layout.tsx            রুট
  page.tsx              → /dashboard বা /login
  login/page.tsx        লগইন
  (app)/layout.tsx      sidebar + auth guard
  (app)/dashboard       overview
  (app)/merchants       approve/suspend
  (app)/devices         list/filter
  (app)/restriction-requests  approve/reject
  (app)/audit-logs      logs
lib/api.ts              token store + fetch client (401 → login)
lib/types.ts            API টাইপ
components/ui.tsx        pill / loading / date helper
```
RBAC: শুধু `super_admin` / `admin_staff` লগইন করতে পারে। Token localStorage-এ (dev); production-এ httpOnly cookie-তে নেওয়া উচিত।
