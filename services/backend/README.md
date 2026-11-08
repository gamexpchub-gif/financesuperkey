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

## Setup

```bash
cd services/backend
npm install
cp .env.example .env        # DATABASE_URL ও JWT secret বসান
npx prisma migrate deploy   # বা dev: npx prisma migrate dev
npm run db:seed             # super admin, merchant, 500 key, demo customer
npm run start:dev           # http://localhost:3000/v1
```

Health check: `GET /v1/health`

### Seed লগইন (শুধু local dev)
| কে | phone | password |
|---|---|---|
| Super Admin | `01700000000` | `Admin@12345` |
| Merchant Owner | `01811111111` | `Owner@12345` |

### যাচাই করা flow (সব টেস্টেড)
auth + RBAC · license-key quota (consume/release) · QR Device Owner enrollment ·
consent capture · restriction request → **admin approval** → policy push ·
device policy poll (emergency calls সবসময় চালু) · enrollment revoke → key slot ফেরত · append-only audit log।

### মূল endpoints
`POST /v1/auth/login` · `GET /v1/keys/summary` · `POST /v1/customers` ·
`POST /v1/enrollment/start|/:id/consent|/:id/complete|/:id/revoke` ·
`GET /v1/devices` · `POST /v1/devices/:id/restriction-requests` ·
`POST /v1/restriction-requests/:id/approve` · `POST /v1/device/policy` (DPC) ·
`GET /v1/audit-logs`। পূর্ণ তালিকা: `docs/api-spec.md`।

## .env.example
```
DATABASE_URL=postgres://user:pass@localhost:5432/financekey
REDIS_URL=redis://localhost:6379
JWT_SECRET=__set_in_secret_manager__
JWT_REFRESH_SECRET=__set_in_secret_manager__
S3_BUCKET=
FCM_SERVER_KEY=
```
