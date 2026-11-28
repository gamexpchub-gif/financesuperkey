# GX Device Tester — প্রফেশনাল মোবাইল টেস্টিং APK

একটি native Android app (Kotlin) যা একটা ফোনের **সব hardware ধাপে ধাপে (step by step)** পরীক্ষা করে, প্রতিটি
test-এর ফল সেভ করে, শেষে **score + grade সহ PDF report** বানায়। দোকানে পুরনো/নতুন ফোন কেনা-বেচা ও
সার্ভিসিং-এর জন্য বানানো।

## ✅ কী কী টেস্ট হয়

| বিভাগ | টেস্ট | কীভাবে যাচাই হয় |
|---|---|---|
| Display & Touch | Display colors / dead pixel | Red/Green/Blue/White/Black/Gray + gradient full-screen |
| | Touch screen grid | পুরো স্ক্রিনের প্রতিটা ঘর ছুঁতে হয় → **auto pass** |
| | Multi-touch | একসাথে কয়টা আঙুল ধরে — ৩+ হলে auto pass |
| Audio | Loudspeaker | random সংখ্যক beep বাজে, সঠিক সংখ্যা বলতে হয় (ফাঁকি দেওয়া যায় না) + 100Hz–8kHz sweep |
| | Earpiece (কলের স্পিকার) | শুধু উপরের earpiece দিয়ে beep (call mode) — সংখ্যা মেলাতে হয় |
| | Microphone (main + secondary) | ৩ সেকেন্ড রেকর্ড, level meter, playback, dB মাপে → auto pass |
| | Earphone jack / USB headset | লাগালেই auto detect |
| | Vibration | random সংখ্যক vibration গুনতে হয় |
| Buttons | Volume up/down, Power | চাপলে auto detect |
| Sensors | Proximity | ঢাকলে NEAR, সরালে FAR → auto pass |
| | Light, Accelerometer, Gyroscope, Compass, Barometer, Step counter | live মান + প্রতিটার নিজস্ব pass নিয়ম |
| | All sensors | ফোনের সব sensor-এর live তালিকা |
| Cameras | প্রতিটা camera আলাদা (back, front, extra) | live preview, tap-to-focus, zoom (lens switch), torch, ছবি তুলে দেখা, MP/aperture/OIS তথ্য |
| | Flashlight | random সংখ্যক blink গুনতে হয় |
| Biometrics | Fingerprint | Android BiometricPrompt — enrolled আঙুলে auto pass |
| | Face unlock | BiometricPrompt (weak class) |
| Connectivity | SIM & network | SIM state, operator, 2G/3G/4G/5G, signal, dual SIM, **carrier lock ধরা পড়ে** |
| | **IMEI check** | Luhn checksum (fake/বদলানো IMEI ধরে), পরিচিত fake IMEI, IMEI1=IMEI2 সন্দেহ, **BTRC NEIR (KYD → 16002)** ও **India CEIR (KYM → 14422)** blacklist যাচাই |
| | Wi-Fi | signal dBm, speed, 2.4/5/6 GHz, Wi-Fi standard, আসল internet test → auto pass |
| | Bluetooth | BLE scan করে আশেপাশের device খোঁজে → auto pass |
| | GPS | satellite সংখ্যা (GPS/GLONASS/Galileo/BeiDou), signal, fix time → auto pass |
| | NFC | কার্ড/ট্যাগ পড়ে → auto pass |
| Battery | Battery & charging port | health, temperature, voltage, current, design capacity, **আনুমানিক আসল capacity %**, cycle count, charger detect |
| System | Storage / RAM | **আসল RAM ও storage** (clone/fake ফোন ধরতে), write/read speed, data integrity (CRC) |
| | Security | root, Magisk, unlocked bootloader, custom ROM, emulator, USB debugging, screen lock, encryption, **device-admin / finance-lock app**, security patch বয়স |

এছাড়া: **Device info** (SoC, CPU, GPU/OpenGL, display Hz/HDR, kernel, baseband, …), **PDF report export/share**,
dashboard-এ progress ও score।

## ⚠️ সৎভাবে কিছু সীমাবদ্ধতা (Android-এর নিয়ম)
- **IMEI:** Android 10+ এ কোনো সাধারণ app IMEI পড়তে পারে না। তাই app থেকে `*#06#` ডায়াল করে IMEI দেখে টাইপ করতে হয়।
  Blacklist ডাটাবেস (GSMA/NEIR/CEIR) সরাসরি query করার কোনো public API নেই — app সরকারি SMS সার্ভিস খুলে দেয়
  (বাংলাদেশ: `KYD <IMEI>` → 16002, ভারত: `KYM <IMEI>` → 14422), উত্তর দেখে Pass/Fail দিন।
- **Face unlock:** অনেক Xiaomi/Samsung/Realme ফোন face unlock app-কে দেয় না। তখন lock screen-এ টেস্ট করে manual Pass/Fail দিন।
- **Fingerprint/Face:** আগে থেকে enroll করা থাকতে হবে (app থেকেই settings খোলা যায়)।

## 📦 APK কীভাবে পাবেন

### উপায় ১ — GitHub থেকে (সবচেয়ে সহজ)
এই repo-তে push হলেই GitHub Actions নিজে APK বানায়:
GitHub → **Actions** → *Build GX Device Tester APK* → সর্বশেষ run → **Artifacts → GX-Device-Tester-APK** ডাউনলোড।
ভিতরে `GX-Device-Tester.apk` ফোনে install করুন।

### উপায় ২ — নিজের PC-তে (Android SDK লাগবে)
```bash
cd apps/device-tester
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```
অথবা Android Studio-তে `apps/device-tester` ফোল্ডার open করে Run।

> Release APK এখন debug key দিয়ে sign করা (টেস্টের জন্য install করা যায়)। Play Store-এ দেওয়ার আগে নিজের keystore দিয়ে sign করুন।

## 🧭 ব্যবহার
1. App খুলে **“▶ Start full test (step by step)”** চাপুন — শুরুতে সব permission একবারে চাইবে।
2. প্রতিটা ধাপে নির্দেশনা পড়ুন; অনেক টেস্ট নিজে থেকেই **PASS** হয়ে পরের ধাপে যায়।
3. কোনো ধাপ বাদ দিতে **Skip**, back চাপলে Fail/Skip/Stop অপশন।
4. শেষে **Report** — score, grade, ত্রুটির তালিকা; **Export PDF** দিয়ে কাস্টমারকে শেয়ার করুন।

## 🏗 Tech
Kotlin • minSdk 24 (Android 7) • targetSdk 34 • CameraX • AndroidX Biometric • কোনো server/internet লাগে না
(শুধু Wi-Fi internet টেস্ট ছাড়া)। কোনো ডাটা কোথাও পাঠানো হয় না।
