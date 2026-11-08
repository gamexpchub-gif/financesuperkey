# Merchant App — Finance Super Key

দোকানদারের app। **Flutter** (Android, পরে iOS), live backend API-র সাথে যুক্ত।

- **Verified:** `flutter analyze` → No issues · widget test passes
- **Backend:** `services/backend` (দেখুন `docs/api-spec.md`)

## চালানো

```bash
cd apps/merchant-app
flutter pub get
flutter run
```

> প্রথমবার platform ফোল্ডার না থাকলে: `flutter create .` চালিয়ে নিন, তারপর উপরের কমান্ড।

ডিফল্ট API: `https://admin.deencircle.online/financekey/api/v1` (আপনার live backend)।
লোকাল backend-এ চালাতে:

```bash
# Android emulator (host-এর localhost = 10.0.2.2)
flutter run --dart-define=API_BASE=http://10.0.2.2:3000/v1
```

**লগইন (dev):** `01811111111` / `Owner@12345`

## স্ক্রিন
- **Home** — license-key balance ring, device status tiles, EMI coming-soon
- **Customers** — search, add customer, per-customer **Enroll** বাটন
- **Enroll wizard** — device (model/IMEI) → consent + **signature pad** → key consume হয়ে device যোগ
- **Devices** — list, filter, search; tap → detail (timeline + **restriction/release request**)
- **Keys** — quota ring, package purchase request

## ডিজাইন নীতি (app-এ বাস্তবায়িত)
- Merchant শুধু **request** পাঠায় (restriction/release) — চূড়ান্ত সিদ্ধান্ত admin-এর, server-authorized।
- Enrollment বৈধ **Android Device Owner QR** পদ্ধতিতে, consent + signature সহ।
- কোনো Play Protect bypass বা hidden control নেই।

## গঠন
```
lib/
  main.dart            entry (session restore → login/home)
  theme.dart           brand theme (navy + gold, Material 3)
  api.dart             API client + token storage (shared_preferences)
  models.dart          JSON models
  widgets/common.dart  pills, stat tiles, ring chart, helpers
  screens/
    login_screen.dart
    home_shell.dart        bottom nav
    dashboard_screen.dart
    customers_screen.dart  + add_customer_screen.dart
    devices_screen.dart    + device_detail_screen.dart
    enroll_screen.dart     enrollment wizard + signature pad
    keys_screen.dart
```

## নোট
- Document/signature upload: backend-এ file-upload endpoint যুক্ত হলে signature ও ডকুমেন্ট object storage-এ যাবে; এখন consent record-এ placeholder reference পাঠানো হয়।
- লোকাল `http://` (https নয়) backend-এ চালালে Android-এ cleartext allow করতে network security config লাগবে — production https-এ দরকার নেই।
