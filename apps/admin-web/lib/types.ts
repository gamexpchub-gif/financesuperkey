export interface Overview {
  merchants: { total: number; active: number; pending: number };
  customers: { total: number };
  devices: { total: number; active: number; review: number; restricted: number };
  keys: { total: number; available: number; consumed: number; released: number };
  restrictionRequests: { pending: number };
}

export type DeviceStatus = 'active' | 'review' | 'restricted';

export interface Merchant {
  id: string;
  name: string;
  dealerNo: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  status: 'pending' | 'active' | 'suspended';
  createdAt: string;
}

export interface Device {
  id: string;
  model: string;
  imeiMasked: string;
  status: DeviceStatus;
  createdAt: string;
  customer?: { id: string; fullName: string; phone: string };
}

export interface RestrictionRequest {
  id: string;
  deviceId: string;
  merchantId: string;
  type: 'restrict' | 'release';
  reason: string;
  status: 'pending' | 'approved' | 'rejected';
  reviewedAt: string | null;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  actorId: string | null;
  merchantId: string | null;
  action: string;
  entityType: string | null;
  entityId: string | null;
  metadata: unknown;
  createdAt: string;
}

export interface Paged<T> {
  data: T[];
  total: number;
}
