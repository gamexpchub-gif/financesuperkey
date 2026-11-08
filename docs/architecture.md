# System Architecture

Finance Super Key — EMI Device Management Platform

## 1. উঁচু স্তরের চিত্র

```
┌─────────────────────────────────────────────────────────────┐
│                   SUPER ADMIN WEB PANEL                       │
│        Company · Merchants · Devices · Reports · RBAC        │
│                     (Next.js + TypeScript)                   │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS / JWT
┌───────────────────────────▼─────────────────────────────────┐
│                      CENTRAL BACKEND                         │
│                    (NestJS + PostgreSQL)                     │
│                                                             │
│   Auth/RBAC  │  Device Mgmt  │  Enrollment  │  Restriction  │
│   Notification │ Audit/Security │ Subscription/Keys         │
│   [ EMI Engine — Coming Soon ]                              │
└──────┬───────────────────────────────────────────┬──────────┘
       │ HTTPS / JWT                                │ HTTPS / JWT
┌──────▼─────────┐                        ┌─────────▼──────────┐
│  APK 01        │                        │  APK 02            │
│  Customer App  │                        │  Merchant App      │
│  (Kotlin)      │                        │  (Flutter)         │
│  Device policy │                        │  Finance Super Key │
└────────────────┘                        └────────────────────┘

        Shared: PostgreSQL · Redis · S3 · FCM · Secret Manager
```

সব client একই backend ও database ব্যবহার করে। কোনো client-এর নিজস্ব আলাদা database নেই।

## 2. অংশসমূহ

### 2.1 Backend (NestJS)
Single source of truth। প্রতিটি business decision (enrollment approve, restriction apply/remove, key consume) এখানে হয় — client শুধু অনুরোধ পাঠায়, সিদ্ধান্ত নয়।

মডিউল:
- **auth** — login, JWT issue/refresh, MFA, RBAC
- **merchants** — shop onboarding, staff, subscription
- **customers** — customer records
- **devices** — device records, enrollment, policy state
- **enrollment** — QR provisioning token, consent capture, signature
- **restriction** — restriction request → approval → policy push
- **keys** — license key quota (allocate, consume, release)
- **notifications** — FCM push, SMS, email
- **audit** — append-only activity log
- **emi** *(Coming Soon)* — schedule, installment, collection, ledger

### 2.2 Customer App (Kotlin + Jetpack Compose)
কাস্টমারের financed device-এ থাকে। একটি **Device Policy Controller (DPC)** — Android Device Owner হিসেবে provision হয় (দেখুন `device-enrollment.md`)। server থেকে policy decision এনে কার্যকর করে, নিজে থেকে lock করে না।

### 2.3 Merchant App (Flutter)
"Finance Super Key" — দোকানদার কাস্টমার ও device register করেন, key quota দেখেন, restriction/unlock **অনুরোধ** পাঠান। চূড়ান্ত অনুমোদন admin-এর। Prototype: `prototypes/finance-super-key.html`।

### 2.4 Super Admin Web (Next.js)
পুরো platform-এর control center — merchant approve/suspend, device policy review, restriction approval, audit logs, reports, RBAC।

## 3. Multi-tenancy

SaaS হিসেবে প্রতিটি merchant একটি **tenant**। সব প্রধান টেবিলে `merchant_id` থাকে, এবং backend প্রতিটি query-তে authenticated user-এর tenant scope প্রয়োগ করে (row-level isolation)। Super Admin cross-tenant দেখতে পারে; merchant শুধু নিজের data।

> যদি ভবিষ্যতে "একাধিক কোম্পানি + অনেক দোকান" দরকার হয়, তাহলে `merchants`-এর উপরে একটি `companies` (organization) স্তর যোগ করা হবে — schema সেই কথা মাথায় রেখে ডিজাইন করা।

## 4. অনুরোধের প্রবাহ (উদাহরণ: restriction)

```
Merchant App                Backend                 Customer Device
     │   POST restriction      │                          │
     │   request (+reason) ───▶ │  save request (pending)  │
     │                         │  notify admin            │
     │                         │                          │
 Super Admin Web               │                          │
     │   approve ────────────▶ │  verify eligibility      │
     │                         │  write audit (immutable) │
     │                         │  push policy ──────────▶ │ apply restricted
     │                         │                          │ screen (emergency
     │                         │                          │ calls stay on)
     │   payment verified ───▶ │  release policy ───────▶ │ normal
```

Client কখনো নিজে restriction সিদ্ধান্ত নেয় না — সব server-authorized।

## 5. নিরাপত্তা স্তর

| স্তর | ব্যবস্থা |
|---|---|
| Transport | HTTPS everywhere, cert pinning (mobile) |
| Auth | OAuth2 / JWT (short-lived access + refresh), MFA for admin |
| Authorization | Role-based (RBAC), tenant row-isolation |
| Device | Signed enrollment token, integrity/attestation as *review signal* |
| Payments | Server-side verification (EMI module) |
| Secrets | Cloud secret manager — কখনো repo-তে নয় |
| Audit | Append-only, tamper-evident |
| Data | Encryption at rest, data minimization |

## 6. টেক স্ট্যাক সারাংশ

| Component | Technology |
|---|---|
| Customer APK | Kotlin + Jetpack Compose |
| Merchant APK | Flutter |
| Admin Web | Next.js + TypeScript |
| Backend | NestJS |
| Database | PostgreSQL |
| Cache / Queue | Redis + BullMQ |
| Storage | S3-compatible |
| Push | Firebase Cloud Messaging |
| Auth | OAuth2 / JWT + MFA |
| Monitoring | Sentry + OpenTelemetry |
