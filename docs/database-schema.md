# Database Schema

PostgreSQL. এখন device management-এর টেবিল সম্পূর্ণ; EMI টেবিলগুলো *reserved* — structure রাখা আছে যাতে module যোগ করলে ক্লায়েন্ট পরিবর্তন করতে না হয়।

নীতি:
- সব প্রধান টেবিলে `merchant_id` (tenant isolation)।
- সব টেবিলে `id uuid primary key`, `created_at`, `updated_at`।
- `payment_ledger` ও `audit_logs` **append-only** — edit/delete নয়, শুধু reversal/adjustment entry।
- অর্থ সবসময় `integer` (পয়সা/সর্বনিম্ন একক), float নয়।

## ER সারসংক্ষেপ

```
companies ──< merchants ──< merchant_staff
                  │
                  ├──< customers ──< devices ──< device_enrollments
                  │                     │
                  │                     └──< restriction_requests
                  │
                  ├──< subscriptions ──< license_keys
                  └──< audit_logs

devices ──< device_policies
[ emi_accounts ──< emi_installments ──< payments ──< payment_ledger ]  (Coming Soon)
roles ──< role_permissions ;  notifications ;  support_tickets
```

---

## মূল টেবিল (এখন তৈরি হবে)

### companies
ভবিষ্যতের multi-company স্তর। এখন এক row (default org)।
| column | type | note |
|---|---|---|
| id | uuid PK | |
| name | text | |
| status | enum(active,suspended) | |
| created_at / updated_at | timestamptz | |

### merchants
| column | type | note |
|---|---|---|
| id | uuid PK | |
| company_id | uuid FK→companies | |
| name | text | দোকানের নাম |
| dealer_no | text unique | D-1042 |
| phone / email | text | |
| address | text | |
| status | enum(pending,active,suspended) | admin approve করে |
| created_at / updated_at | timestamptz | |

### merchant_staff
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id | uuid FK | |
| user_id | uuid FK→users | |
| role | enum(owner,manager,collector,staff) | |
| permissions | jsonb | fine-grained |
| status | enum(active,disabled) | |

### users
সব ধরনের লগইন অ্যাকাউন্ট (admin, staff, customer)।
| column | type | note |
|---|---|---|
| id | uuid PK | |
| phone | text unique | |
| email | text | |
| password_hash | text | |
| full_name | text | |
| user_type | enum(super_admin,admin_staff,merchant_staff,customer) | |
| mfa_enabled | bool | |
| status | enum(active,disabled) | |

### customers
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id | uuid FK | |
| user_id | uuid FK→users nullable | customer app লগইন থাকলে |
| full_name | text | |
| phone | text | |
| nid_no | text | |
| address | text | |
| dealer_no | text | |
| created_at / updated_at | timestamptz | |

### devices
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id | uuid FK | |
| customer_id | uuid FK | |
| imei | text | encrypted/hashed |
| serial | text | |
| model | text | |
| android_id | text | |
| status | enum(active,review,restricted) | server-authoritative |
| enrollment_id | uuid FK→device_enrollments | |
| created_at / updated_at | timestamptz | |

### device_enrollments
বৈধ enrollment-এর প্রমাণ।
| column | type | note |
|---|---|---|
| id | uuid PK | |
| device_id | uuid FK | |
| method | enum(qr_device_owner,adb_device_owner) | |
| provisioning_token | text | signed, one-time |
| consent_at | timestamptz | কাস্টমার সম্মতির সময় |
| consent_text_version | text | কোন consent শর্তে সম্মতি |
| signature_url | text | S3 |
| doc1_url / doc2_url | text | NID, photo |
| enrolled_by | uuid FK→users | staff |
| status | enum(pending,enrolled,revoked) | |

### device_policies
কোন policy এখন device-এ প্রযোজ্য।
| column | type | note |
|---|---|---|
| id | uuid PK | |
| device_id | uuid FK | |
| policy_type | enum(normal,reminder,restricted) | |
| applied_at | timestamptz | |
| applied_by | uuid FK→users | admin |
| reason | text | |
| server_decision_id | uuid | অনুমোদনের রেফারেন্স |
| active | bool | |

### restriction_requests
| column | type | note |
|---|---|---|
| id | uuid PK | |
| device_id | uuid FK | |
| merchant_id | uuid FK | |
| requested_by | uuid FK→users | |
| type | enum(restrict,release) | |
| reason | text | |
| status | enum(pending,approved,rejected) | |
| reviewed_by | uuid FK→users | admin |
| reviewed_at | timestamptz | |

### subscriptions
Merchant package ও billing।
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id | uuid FK | |
| plan | enum(starter,business,enterprise) | |
| status | enum(active,past_due,canceled) | |
| renews_at | timestamptz | |

### license_keys
আপনার "Super Key" quota ব্যবস্থা।
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id | uuid FK | |
| batch_id | uuid | কোন ক্রয়ে এসেছে |
| state | enum(available,consumed,released) | |
| device_id | uuid FK nullable | consumed হলে |
| consumed_at | timestamptz | |
| released_at | timestamptz | |

> **Key quota** = count(available) per merchant। Enrollment শুরু হলে একটি key `available→consumed`; enrollment revoke / device decommission হলে key আবার `available`-এ ফিরে আসে (slot পুনঃব্যবহারযোগ্য)। `released` state শুধু admin-এর স্থায়ীভাবে retire করা key-এর জন্য reserved।

### notifications
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id / user_id | uuid FK | |
| channel | enum(push,sms,email) | |
| title / body | text | |
| status | enum(queued,sent,failed) | |
| sent_at | timestamptz | |

### support_tickets
| column | type | note |
|---|---|---|
| id | uuid PK | |
| merchant_id / customer_id | uuid FK | |
| subject / body | text | |
| status | enum(open,in_progress,resolved) | |
| priority | enum(low,normal,high) | |

### audit_logs (append-only)
| column | type | note |
|---|---|---|
| id | uuid PK | |
| actor_id | uuid FK→users | |
| merchant_id | uuid FK nullable | |
| action | text | e.g. restriction.approve |
| entity_type / entity_id | text / uuid | |
| metadata | jsonb | before/after |
| ip / user_agent | text | |
| created_at | timestamptz | কখনো update নয় |

### roles / role_permissions
| column | type | note |
|---|---|---|
| roles.id | uuid PK | |
| roles.name | enum(super_admin,finance_admin,support_admin,device_admin,merchant_manager,auditor) | |
| role_permissions.role_id | uuid FK | |
| role_permissions.permission | text | e.g. device.restrict.approve |

---

## EMI টেবিল (Coming Soon — reserved)

এখন তৈরি হবে না, কিন্তু নাম ও সম্পর্ক ঠিক করা আছে যাতে পরে যোগ করা সহজ হয়:

- **emi_accounts** — device_id, finance_amount, down_payment, tenure, monthly_emi, status
- **emi_installments** — emi_account_id, seq, due_date, amount, status(due,paid,overdue)
- **payments** — installment_id, amount, method, verified_by
- **payment_ledger** (append-only) — account_id, entry_type(charge,payment,reversal,adjustment), amount, balance_after

> EMI module যুক্ত হলে `devices.status` আর restriction flow একই থাকবে — শুধু status পরিবর্তনের *কারণ* (due payment) যোগ হবে।
