/**
 * Fine-grained permissions. Admin roles map to sets of these; a merchant-staff
 * member carries an explicit list. Guards check the permission, not the role,
 * so the mapping can change without touching endpoints.
 */
export const Permission = {
  MerchantRead: 'merchant.read',
  MerchantCreate: 'merchant.create',
  MerchantUpdate: 'merchant.update',
  MerchantApprove: 'merchant.approve',
  MerchantSuspend: 'merchant.suspend',

  CustomerRead: 'customer.read',
  CustomerCreate: 'customer.create',
  CustomerUpdate: 'customer.update',

  DeviceRead: 'device.read',
  DeviceUpdate: 'device.update',
  DeviceRestrictRequest: 'device.restrict.request',
  DeviceRestrictReview: 'device.restrict.review',
  DeviceRestrictApprove: 'device.restrict.approve',

  EnrollmentCreate: 'enrollment.create',
  EnrollmentComplete: 'enrollment.complete',
  EnrollmentRevoke: 'enrollment.revoke',

  KeyRead: 'key.read',
  KeyPurchase: 'key.purchase',
  KeyGrant: 'key.grant',

  AuditRead: 'audit.read',
  ReportRead: 'report.read',
} as const;

export type PermissionValue = (typeof Permission)[keyof typeof Permission];

/** Admin role → permissions. Super admin is handled as a wildcard in the guard. */
export const ADMIN_ROLE_PERMISSIONS: Record<string, PermissionValue[]> = {
  super_admin: Object.values(Permission), // full access
  finance_admin: [
    Permission.MerchantRead,
    Permission.KeyRead,
    Permission.KeyGrant,
    Permission.ReportRead,
  ],
  support_admin: [
    Permission.MerchantRead,
    Permission.CustomerRead,
    Permission.DeviceRead,
  ],
  device_admin: [
    Permission.DeviceRead,
    Permission.DeviceUpdate,
    Permission.DeviceRestrictReview,
    Permission.DeviceRestrictApprove,
    Permission.EnrollmentRevoke,
  ],
  merchant_manager: [
    Permission.MerchantRead,
    Permission.MerchantCreate,
    Permission.MerchantUpdate,
    Permission.MerchantApprove,
    Permission.MerchantSuspend,
  ],
  auditor: [Permission.AuditRead, Permission.ReportRead],
};

/** Default permissions for a merchant-staff role when none are set explicitly. */
export const STAFF_ROLE_PERMISSIONS: Record<string, PermissionValue[]> = {
  owner: [
    Permission.CustomerRead,
    Permission.CustomerCreate,
    Permission.CustomerUpdate,
    Permission.DeviceRead,
    Permission.DeviceRestrictRequest,
    Permission.EnrollmentCreate,
    Permission.EnrollmentComplete,
    Permission.EnrollmentRevoke, // owner may decommission its own devices
    Permission.KeyRead,
    Permission.KeyPurchase,
  ],
  manager: [
    Permission.CustomerRead,
    Permission.CustomerCreate,
    Permission.CustomerUpdate,
    Permission.DeviceRead,
    Permission.DeviceRestrictRequest,
    Permission.EnrollmentCreate,
    Permission.EnrollmentComplete,
    Permission.EnrollmentRevoke,
    Permission.KeyRead,
  ],
  collector: [
    Permission.CustomerRead,
    Permission.DeviceRead,
    Permission.DeviceRestrictRequest,
  ],
  staff: [Permission.CustomerRead, Permission.DeviceRead],
};
