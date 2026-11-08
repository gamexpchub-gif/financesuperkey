# Customer App (DPC)

কাস্টমারের financed device-এ থাকা app — একটি **Device Policy Controller**, Android Device Owner হিসেবে provision হয়।

- **Tech:** Kotlin + Jetpack Compose (native — Device Policy API-র জন্য উপযুক্ত)
- **Enrollment:** QR provisioning বা ADB (দেখুন `docs/device-enrollment.md`)

## কাজ
- Device enrollment ও policy poll (`GET /device/policy`)
- Heartbeat ও integrity signal (`POST /device/heartbeat`)
- Policy প্রয়োগ নিশ্চিত (`POST /device/policy/ack`)
- EMI dashboard (status), payment status (external record থাকলে)
- Restricted screen — "Payment Required / Under Review", **জরুরি কল চালু**, finance contact
- Support / dispute, consent view, unlock request

## গুরুত্বপূর্ণ নিয়ম
- Policy decision **সবসময় server-এ** — client নিজে lock করে না।
- Restricted অবস্থাতেও emergency calls ও essential functions চালু।
- কোনো stealth/uninstall-proof/bypass নয় — শুধু Android-অনুমোদিত DPC capability।

## মূল কম্পোনেন্ট (পরিকল্পনা)
```
DeviceOwnerReceiver : DeviceAdminReceiver   // provisioning callbacks
PolicyService                               // poll + apply (lock-task/kiosk)
HeartbeatWorker (WorkManager)               // periodic check-in
EnrollmentActivity                          // post-provision register + consent
RestrictedActivity                          // allowlisted screen, emergency dialer
```
