# Super Admin Web Panel

পুরো platform-এর control center।

- **Tech:** Next.js + TypeScript
- **Backend:** shared API (`docs/api-spec.md`)
- **Auth:** JWT + MFA (admin-এর জন্য বাধ্যতামূলক)

## মডিউল
- Dashboard — merchant, customer, device, restricted count, system health
- Merchant management — approve / suspend
- Customer & device management
- Device policy review, restriction **approval**, unlock approval
- License-key / subscription management
- Notifications, reports, analytics
- Audit logs (read-only), security center
- Admin & staff roles (RBAC)

## Admin roles (RBAC)
| Role | Access |
|---|---|
| Super Admin | full |
| Finance Admin | payment & reports |
| Support Admin | tickets |
| Device Admin | enrollment & policy review |
| Merchant Manager | shop management |
| Auditor | read-only logs |

গুরুত্বপূর্ণ কাজে MFA, approval workflow, immutable audit।

## Setup (পরে)
```bash
npx create-next-app@latest . --typescript --app
npm run dev
```
