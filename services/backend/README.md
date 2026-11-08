# Backend API

Central backend — সব client (customer app, merchant app, admin web) এটি ব্যবহার করে।

- **Tech:** NestJS + PostgreSQL + Redis (BullMQ) + S3 + FCM
- **Spec:** `docs/api-spec.md` · **Schema:** `docs/database-schema.md`

## মডিউল
```
auth          — login, JWT, refresh, MFA, RBAC
merchants     — onboarding, staff, subscription
customers     — customer records
devices       — device records, policy state
enrollment    — provisioning token, consent, signature
restriction   — request → approval → policy push
keys          — license-key quota (allocate/consume/release)
notifications — FCM / SMS / email
audit         — append-only activity log
emi           — (Coming Soon) schedule, installment, collection, ledger
```

## নিরাপত্তা নিয়ম
- সব business decision server-এ — client শুধু অনুরোধ করে।
- Restriction প্রয়োগ হয় admin approval + server decision-এর পর, audit সহ।
- Secrets cloud secret manager-এ; `.env` কখনো commit নয় (`.env.example` দেখুন)।
- অর্থ integer (সর্বনিম্ন একক); ledger append-only।

## Setup (পরে)
```bash
npm i -g @nestjs/cli
nest new . --package-manager npm
# PostgreSQL + Redis চালু করে migration চালান
npm run start:dev
```

## .env.example
```
DATABASE_URL=postgres://user:pass@localhost:5432/financekey
REDIS_URL=redis://localhost:6379
JWT_SECRET=__set_in_secret_manager__
JWT_REFRESH_SECRET=__set_in_secret_manager__
S3_BUCKET=
FCM_SERVER_KEY=
```
