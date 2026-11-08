import { UserType } from '@prisma/client';
import { PermissionValue } from './permissions';

/** Shape attached to req.user after JWT validation. */
export interface AuthUser {
  id: string;
  userType: UserType;
  /** Present for merchant_staff — the tenant this user acts within. */
  merchantId: string | null;
  /** Resolved effective permissions for this request. */
  permissions: PermissionValue[];
  /** True for a super_admin — bypasses permission checks. */
  isSuperAdmin: boolean;
}

export interface JwtPayload {
  sub: string;
  userType: UserType;
  merchantId: string | null;
}
