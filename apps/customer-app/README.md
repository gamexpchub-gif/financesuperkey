# Customer App (Device Policy Controller)

কাস্টমারের financed device-এ থাকা app — একটি **Android Device Owner / Device Policy Controller (DPC)**। server থেকে policy এনে কার্যকর করে; নিজে থেকে কিছু lock করে না।

- **Tech:** Kotlin, Gradle (AGP 8.5), minSdk 24, WorkManager
- **Package / admin component:** `com.financesuperkey.dpc/.DeviceOwnerReceiver` (backend-এর QR payload এটাই reference করে)
- **Backend:** device-gateway endpoints — `POST /device/policy`, `/device/heartbeat`, `/device/policy/ack`

> ⚠️ container-এ Google-এর Android repo ব্লক থাকায় এটা এখানে build করা যায়নি। **আপনার Kali-তে** (যেখানে Android SDK আছে, Flutter APK যেভাবে বানিয়েছেন) build করুন।

## ⚖️ বৈধ-ব্যবহার নীতি (কোডে বাস্তবায়িত)
- শুধু **Device Owner** হিসেবে, বৈধ QR provisioning-এ, কাস্টমারের সম্মতিতে enroll হয়।
- Policy সিদ্ধান্ত **সবসময় server-এ**; client শুধু প্রয়োগ করে।
- **Restricted** = একটা pinned informational screen ("Payment Required / Under Review") — **জরুরি কল সবসময় চালু**, finance contact দেখায়।
- server "normal" বললে **সাথে সাথে release** হয়। স্থায়ী/অপরিবর্তনীয় lock নয়।
- কোনো Play Protect bypass, hidden persistence বা security bypass **নেই**।

## Build (Kali-তে)
```bash
cd apps/customer-app
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
অথবা Android Studio-তে `apps/customer-app` ফোল্ডার open করে Run।

প্রথমবার `local.properties`-এ Android SDK path লাগতে পারে:
```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## একটি device-এ enroll করা (Device Owner)

Device Owner কেবল **account-হীন / factory-reset** device-এ বসানো যায়।

### পদ্ধতি A — ADB (টেস্ট device)
```bash
# device-এ কোনো Google account না থাকা অবস্থায়:
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner com.financesuperkey.dpc/.DeviceOwnerReceiver
```
তারপর app খুলে **"Enroll device"** → server URL (default দেওয়া আছে) + **enrollment token** দিন।
token পাবেন backend থেকে: merchant app-এ enroll শুরু করলে `POST /enrollment/start`-এর response-এ
`qrPayload → …PROVISIONING_ADMIN_EXTRAS_BUNDLE → com.financesuperkey.PROVISIONING_TOKEN`।

### পদ্ধতি B — QR provisioning (আসল খুচরা flow)
factory-reset device-এ setup wizard-এ welcome screen-এ **৬ বার ট্যাপ** → QR scanner → merchant app-এর তৈরি enrollment QR স্ক্যান। QR-এর extras bundle-এ token ও server থাকে, তাই app নিজে থেকেই enroll হয়ে যায়।
> (merchant app-এ এই scannable QR দেখানোর ফিচারটা পরের ধাপে যোগ হবে — এখন `/enrollment/start`-এর JSON payload থেকে QR বানাতে হয়।)

## কীভাবে কাজ করে
```
Enroll (token পায়)
      │
      ▼
PolicyWorker (প্রতি ~15 মিনিট + app খুললে)
   ├─ POST /device/heartbeat   (integrity signal — শুধু review, auto-lock নয়)
   ├─ POST /device/policy       (server-এর current সিদ্ধান্ত)
   │     ├─ normal/reminder → স্বাভাবিক, restricted screen বন্ধ
   │     └─ restricted     → RestrictedActivity (lock-task, জরুরি কল চালু)
   └─ POST /device/policy/ack
```
admin panel বা merchant request → server policy বদলায় → পরের check-in-এ device প্রয়োগ করে।

## গঠন
```
app/src/main/
  AndroidManifest.xml
  java/com/financesuperkey/dpc/
    DeviceOwnerReceiver.kt   provisioning callback (token/server সংরক্ষণ)
    PolicyStore.kt           token, server, last policy (SharedPreferences)
    ApiClient.kt             device-gateway endpoints
    PolicyController.kt      DevicePolicyManager দিয়ে policy প্রয়োগ
    PolicyWorker.kt          periodic poll + heartbeat + ack
    MainActivity.kt          status screen + manual enroll
    RestrictedActivity.kt    "Payment Required" kiosk + emergency dial
  res/ …                     layouts, strings, theme, device-admin xml
```

## নোট
- পুরো kiosk enforcement আরও শক্ত করতে Device Owner-কে restricted অবস্থায় HOME/launcher হিসেবে সেট করা যায় (পরের iteration)।
- Production-এ APK proper keystore দিয়ে sign করতে হবে।
