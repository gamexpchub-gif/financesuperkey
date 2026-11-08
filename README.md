# Finance Super Key

**EMI Device Management & Lock Platform** — একটি SaaS-based device enrollment ও management system, যেখানে পুরো ব্যবসা একটি centralized Super Admin panel থেকে পরিচালিত হয়।

> ⚠️ **EMI finance module (কিস্তি হিসাব, collection, ledger) — Coming Soon।** এই রিলিজে device enrollment ও management চালু। Database ও architecture এমনভাবে ডিজাইন করা যাতে পরে EMI module যোগ করলে কোনো অংশ নতুন করে বানাতে না হয়।

---

## এই প্ল্যাটফর্মে যা আছে

| অংশ | কী | টেক |
|---|---|---|
| **Customer App** | কিস্তিতে কেনা device-এর status ও authorized policy | Kotlin + Jetpack Compose |
| **Merchant App** (Finance Super Key) | কাস্টমার, device, key quota, lock/unlock অনুরোধ | Flutter |
| **Super Admin Web** | পুরো কোম্পানি, merchant, device, restriction approval | Next.js + TypeScript |
| **Backend API** | সব ক্লায়েন্টের common backend | NestJS + PostgreSQL |

তিনটি client **একই backend ও একই database** ব্যবহার করে। আলাদা database নয়।

---

## ⚖️ মূল ডিজাইন নীতি (গুরুত্বপূর্ণ)

এই সিস্টেম device locking করে **শুধুমাত্র** বৈধ, সম্মতিভিত্তিক উপায়ে:

- ✅ Android-এর অনুমোদিত **Device Owner / Device Policy** ব্যবস্থা ব্যবহার করে
- ✅ Enrollment-এর সময় কাস্টমারের **স্পষ্ট informed consent** ও signature নেওয়া হয়
- ✅ সব restriction **server-side authorization** ও admin approval-এর পর কার্যকর হয়
- ✅ **জরুরি কল** ও essential functions সবসময় চালু থাকে
- ✅ ভুল status বা বিরোধে **dispute resolution** ও emergency contact-এর পথ থাকে

এই সিস্টেম **করে না**:

- ❌ Google Play Protect বা Android security bypass
- ❌ গোপন / stealth control বা uninstall-proof persistence
- ❌ কাস্টমারের অজান্তে device control নেওয়া

ভারত/বাংলাদেশে চালুর আগে financing agreement, consent, privacy ও প্রযোজ্য digital-lending নিয়ম আইনজীবী ও compliance specialist দিয়ে যাচাই করতে হবে।

---

## রিপো কাঠামো

```
financekey/
├── docs/                      # পুরো প্ল্যাটফর্মের ডকুমেন্টেশন
│   ├── architecture.md        # সিস্টেম আর্কিটেকচার
│   ├── database-schema.md     # সব টেবিল ও সম্পর্ক
│   ├── api-spec.md            # REST API endpoints
│   ├── device-enrollment.md   # বৈধ Device Owner enrollment flow
│   └── roadmap.md             # ২৪-সপ্তাহের development plan
├── prototypes/
│   └── finance-super-key.html # Merchant app-এর interactive prototype
├── apps/
│   ├── merchant-app/          # Flutter — দোকানদারের app
│   ├── customer-app/          # Kotlin — কাস্টমারের device app
│   └── admin-web/             # Next.js — Super Admin panel
└── services/
    └── backend/               # NestJS — central API + EMI engine
```

## শুরু করা

প্রতিটি অংশের নিজস্ব `README.md`-তে setup নির্দেশ আছে। প্রথমে `docs/architecture.md` ও `docs/database-schema.md` পড়ুন।

একটা দ্রুত ধারণা পেতে browser-এ খুলুন:

```
prototypes/finance-super-key.html
```
