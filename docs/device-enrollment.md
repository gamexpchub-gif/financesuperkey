# Device Enrollment — বৈধ Device Owner পদ্ধতি

এই ডকুমেন্ট বর্ণনা করে কীভাবে customer device বৈধভাবে, সম্মতিসহ enroll ও manage হয় — **Android-এর অনুমোদিত Device Policy ব্যবস্থার ভেতরে থেকে**।

## নীতি (আগে পড়ুন)

- Enrollment হয় **বিক্রয়ের সময়, কাস্টমারের সামনে, তাঁর সম্মতিতে** — গোপনে নয়।
- অ্যাপ একটি **Device Policy Controller (DPC)**, Android Device Owner হিসেবে provision হয়।
- Policy decision **সবসময় server-এ**; device নিজে lock করে না।
- Restricted অবস্থায়ও **জরুরি কল ও essential functions** চালু থাকে।
- আমরা **করি না**: Play Protect bypass, stealth persistence, security bypass, uninstall-proofing কৌশল। এগুলো policy-বিরুদ্ধ ও আইনি ঝুঁকিপূর্ণ।

---

## কোন enrollment পদ্ধতি

Android Device Owner কেবল নির্দিষ্ট supported উপায়ে বসানো যায়:

### A. QR provisioning (প্রস্তাবিত, খুচরা দোকানের জন্য)
factory-reset / নতুন ফোনে setup wizard-এ welcome screen-এ **৬ বার ট্যাপ** → QR scanner খোলে → Finance Super Key-এর enrollment QR স্ক্যান → DPC download ও Device Owner হিসেবে set হয়।

- QR payload-এ থাকে: DPC package, download URL, checksum, আর server থেকে নেওয়া **signed, one-time provisioning token**।
- কোনো account যোগ করার আগেই করতে হয় (Android-এর শর্ত)।

### B. ADB provisioning (dealer workstation, account-less device)
device-এ কোনো account না থাকলে:
```
adb shell dpm set-device-owner com.financesuperkey.dpc/.DeviceOwnerReceiver
```
এটিও কাস্টমারের উপস্থিতিতে ও সম্মতিতে, বিক্রয়ের সময়।

> দুটোই Android-এর documented capability — কোনো bypass নয়। কোনটা ব্যবহার হবে তা device ও দোকানের workflow-এর উপর নির্ভর করে।

---

## End-to-end flow

```
1. Merchant App: কাস্টমার + device তথ্য দেয়
        │  POST /enrollment/start   (key available থাকলেই token পায়)
        ▼
2. Backend: signed one-time provisioning token + QR payload দেয়
        │
        ▼
3. কাস্টমারের ফোন: QR স্ক্যান → DPC ইনস্টল → Device Owner set
        │  (কাস্টমার on-screen consent দেখেন ও সম্মতি দেন)
        ▼
4. Merchant App: consent screen — শর্ত পড়ানো, signature, ২ ডকুমেন্ট
        │  POST /enrollment/:id/consent
        ▼
5. DPC: server-এ register → GET /device/policy (normal)
        │  POST /enrollment/:id/complete
        ▼
6. Backend: device.status=active, ১ license key consume, audit log
```

## DPC কী করে (Device Owner হিসেবে)

অনুমোদিত DevicePolicyManager capability, সম্মতির সীমায়:

| অবস্থা | DPC আচরণ |
|---|---|
| **normal** | কিছু restrict নয়; শুধু heartbeat ও policy poll |
| **reminder** | পেমেন্ট reminder notification; device পুরো ব্যবহারযোগ্য |
| **restricted** | server-approved lock-task/kiosk screen — "Payment Required / Under Review", **জরুরি কল চালু**, finance provider-এর contact দেখায় |

Policy সবসময় `GET /device/policy` থেকে আসে। Payment/dispute সমাধান হলে server `normal` push করে, DPC দ্রুত release করে।

## Integrity signals (review, যুদ্ধ নয়)

Root/tamper/attestation signal **review flag** হিসেবে ব্যবহার করুন (admin দেখবেন) — restriction bypass করার অস্ত্র হিসেবে নয়। Factory reset বা enrollment হারালে device-কে আবার বৈধভাবে enroll করতে হবে; জোর করে persistence বসানোর চেষ্টা নয়।

## Consent ও dispute

- Consent text-এর version সংরক্ষিত (`consent_text_version`) — কাস্টমার কী শর্তে রাজি হয়েছেন তা প্রমাণযোগ্য।
- Signature ও ডকুমেন্ট immutable storage-এ।
- ভুল status/বিরোধে কাস্টমার app থেকে **dispute/support ticket** খুলতে পারেন; emergency contact সবসময় দৃশ্যমান।
- Admin override ও appeal পথ থাকে।

## যা কখনো করা হবে না

- ❌ Play Protect বন্ধ/bypass করার নির্দেশ বা কৌশল
- ❌ কাস্টমারের অজান্তে Device Owner নেওয়া
- ❌ Stealth/hidden app, uninstall-proof hack, অন্য security এড়ানো
- ❌ জরুরি কল বন্ধ করা
