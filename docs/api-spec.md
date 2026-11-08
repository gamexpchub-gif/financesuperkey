# API Specification

Base: `https://api.financesuperkey.app/v1`
Auth: `Authorization: Bearer <jwt>` (login ছাড়া সব endpoint-এ)।
Format: JSON। সব list endpoint-এ `?page=&limit=&q=`।

RBAC: প্রতিটি endpoint-এ প্রয়োজনীয় permission দেওয়া আছে। Merchant token শুধু নিজের tenant data পায়।

---

## Auth

| Method | Path | কাজ |
|---|---|---|
| POST | `/auth/login` | phone/email + password → access + refresh token |
| POST | `/auth/refresh` | refresh token → নতুন access |
| POST | `/auth/mfa/verify` | admin MFA OTP যাচাই |
| POST | `/auth/logout` | refresh token বাতিল |

```
POST /auth/login
{ "identifier": "01712345678", "password": "••••" }
→ 200 { "access": "...", "refresh": "...", "user": { "id","user_type","merchant_id" } }
```

## Merchants  *(admin)*

| Method | Path | permission |
|---|---|---|
| GET | `/merchants` | merchant.read |
| POST | `/merchants` | merchant.create |
| PATCH | `/merchants/:id` | merchant.update |
| POST | `/merchants/:id/approve` | merchant.approve |
| POST | `/merchants/:id/suspend` | merchant.suspend |

## Staff  *(merchant owner / admin)*

| Method | Path |
|---|---|
| GET | `/merchants/:id/staff` |
| POST | `/merchants/:id/staff` |
| PATCH | `/staff/:id` |

## Customers

| Method | Path | permission |
|---|---|---|
| GET | `/customers` | customer.read |
| POST | `/customers` | customer.create |
| GET | `/customers/:id` | customer.read |
| PATCH | `/customers/:id` | customer.update |

## Devices

| Method | Path | permission |
|---|---|---|
| GET | `/devices` | device.read |
| GET | `/devices/:id` | device.read |
| GET | `/devices/:id/timeline` | device.read |
| PATCH | `/devices/:id` | device.update |

```
GET /devices?status=restricted&page=1&limit=20
→ { "data":[{ "id","model","imei_masked","status","customer":{...} }], "total":37 }
```

## Enrollment  (বৈধ Device Owner flow — দেখুন device-enrollment.md)

| Method | Path | কাজ |
|---|---|---|
| POST | `/enrollment/start` | customer+device তথ্য → signed provisioning token + QR payload |
| POST | `/enrollment/:id/consent` | consent + signature + docs সংরক্ষণ |
| POST | `/enrollment/:id/complete` | device DPC সক্রিয় নিশ্চিত → device.status=active, ১ key consume |
| POST | `/enrollment/:id/revoke` | enrollment বাতিল, key release |

```
POST /enrollment/start
{ "customer_id":"…", "model":"Redmi 13C", "imei":"…", "method":"qr_device_owner" }
→ { "enrollment_id":"…", "qr_payload":{…}, "expires_at":"…" }   // key available থাকলেই
```

## Restriction  (request → approval → policy)

| Method | Path | permission |
|---|---|---|
| POST | `/devices/:id/restriction-requests` | device.restrict.request (merchant) |
| GET | `/restriction-requests?status=pending` | device.restrict.review (admin) |
| POST | `/restriction-requests/:id/approve` | device.restrict.approve (admin) |
| POST | `/restriction-requests/:id/reject` | device.restrict.approve |

```
POST /devices/:id/restriction-requests
{ "type":"restrict", "reason":"…" }        // merchant শুধু অনুরোধ করে
→ 201 { "id","status":"pending" }

POST /restriction-requests/:id/approve     // admin সিদ্ধান্ত নেয়
→ server policy push করে; audit log immutable
```

## Device Policy  (DPC ↔ server; client decision নেয় না)

| Method | Path | কে |
|---|---|---|
| GET | `/device/policy` | DPC (device token) — current policy টানে |
| POST | `/device/heartbeat` | DPC — online/integrity signal |
| POST | `/device/policy/ack` | DPC — policy প্রয়োগ নিশ্চিত |

> Restricted policy-তেও `emergency_calls: true` সবসময় থাকে।

## License Keys

| Method | Path | permission |
|---|---|---|
| GET | `/keys/summary` | key.read → { total, available, consumed, released } |
| GET | `/keys` | key.read |
| POST | `/keys/purchase-request` | key.purchase (merchant) → admin approval |
| POST | `/keys/batches/:id/approve` | key.grant (admin) |

```
GET /keys/summary
→ { "total":500, "available":158, "consumed":342, "released":0 }
```

## Notifications / Support / Audit / Reports

| Method | Path |
|---|---|
| GET/POST | `/notifications` |
| GET/POST | `/support-tickets` |
| GET | `/audit-logs` *(auditor, read-only)* |
| GET | `/reports/overview` *(admin)* |

---

## Error shape

```
400/401/403/404/409/422
{ "error": { "code":"restriction_not_eligible", "message":"…", "details":{…} } }
```

## EMI endpoints — Coming Soon (reserved)

`/emi/accounts`, `/emi/accounts/:id/installments`, `/payments`, `/payments/:id/verify`, `/emi/ledger` — module রিলিজে যোগ হবে। বর্তমান client contract ভাঙবে না।
