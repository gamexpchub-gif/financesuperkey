# Merchant App — Finance Super Key

দোকানদারের app। কাস্টমার ও device register, license-key quota, restriction/unlock অনুরোধ।

- **Tech:** Flutter (Android + ভবিষ্যতে iOS)
- **Backend:** `services/backend` (shared API — দেখুন `docs/api-spec.md`)
- **UI concept:** `prototypes/finance-super-key.html` (browser-এ খুলে দেখুন)

## Screens
- হোম — key balance (total/used/available), device status, recent activity
- ডিভাইস — list, search, filter, detail sheet, restriction/unlock **request**
- এনরোল — ৪-ধাপ wizard: customer → device+QR → ২ docs → consent+signature
- কী — quota ring, usage, package purchase request
- আরও — profile, staff, customer, security/consent, support

## সীমা (ডিজাইনগত)
Merchant শুধু **অনুরোধ** পাঠায়; restriction/unlock-এর চূড়ান্ত সিদ্ধান্ত Super Admin-এর ও server-authorized।

## Setup (পরে)
```bash
flutter create . --org com.financesuperkey --project-name merchant_app
flutter pub get
flutter run
```
API base URL `.env`-এ (কখনো key commit নয়; `.env.example` দেখুন)।
