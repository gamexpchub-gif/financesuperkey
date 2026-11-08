# Development Roadmap

EMI finance module বাদে (Coming Soon) — device management প্রথমে।

## Phase 1 — Foundation (সপ্তাহ ১–২)
- [x] System architecture ও ডকুমেন্টেশন
- [x] Database ER ডিজাইন
- [x] Merchant app prototype (UI concept)
- [ ] Final business requirement sign-off
- [ ] Legal/compliance review শুরু (consent, privacy, lending rules)

## Phase 2 — Backend (সপ্তাহ ৩–৬)
- [ ] NestJS project + PostgreSQL migrations
- [ ] Auth (JWT + refresh), RBAC, MFA
- [ ] Merchant / customer / staff modules
- [ ] Device + enrollment + license-key modules
- [ ] Restriction request → approval flow
- [ ] Audit log (append-only)

## Phase 3 — Merchant App (সপ্তাহ ৭–১০)
- [ ] Flutter project setup
- [ ] Login, dashboard, key quota
- [ ] Customer registration
- [ ] Device enrollment wizard (QR + consent + signature)
- [ ] Device list, detail, restriction/unlock request
- [ ] Reports

## Phase 4 — Customer App (সপ্তাহ ১১–১৪)
- [ ] Kotlin DPC project
- [ ] Device Owner provisioning (QR + ADB)
- [ ] Policy poll + heartbeat + ack
- [ ] Restricted screen (emergency calls on)
- [ ] Device status, support/dispute, consent view

## Phase 5 — Super Admin Web (সপ্তাহ ১৫–১৮)
- [ ] Next.js project + auth
- [ ] Dashboard, merchant management
- [ ] Device management, policy review
- [ ] Restriction approval, unlock approval
- [ ] Audit logs, RBAC, reports

## Phase 6 — Security & Integration (সপ্তাহ ১৯–২১)
- [ ] API security testing, cert pinning
- [ ] Enrollment end-to-end on real devices (with consent)
- [ ] Role/permission testing
- [ ] Recovery/backup testing

## Phase 7 — Pilot (সপ্তাহ ২২–২৪)
- [ ] ৫–১০ merchant pilot
- [ ] Real-device testing
- [ ] Bug fixing, hardening
- [ ] Production deployment

## Future — EMI Finance Module
- [ ] EMI account + schedule generation
- [ ] Installment collection + receipt
- [ ] Payment verification + ledger (append-only)
- [ ] Late fee / refund / reversal (চুক্তি ও আইন অনুযায়ী)
- [ ] Finance reports, automated reminders
