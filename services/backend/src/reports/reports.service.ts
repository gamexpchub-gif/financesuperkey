import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

/**
 * Aggregate counts for the admin dashboard. `merchantId` null = platform-wide
 * (super admin); otherwise scoped to one tenant.
 */
@Injectable()
export class ReportsService {
  constructor(private readonly prisma: PrismaService) {}

  async overview(merchantId: string | null) {
    const deviceWhere: Prisma.DeviceWhereInput = merchantId ? { merchantId } : {};
    const keyWhere: Prisma.LicenseKeyWhereInput = merchantId ? { merchantId } : {};

    const merchantScope: Prisma.MerchantWhereInput = merchantId
      ? { id: merchantId }
      : {};

    const [
      merchantsTotal,
      merchantsActive,
      merchantsPending,
      customersTotal,
      devicesByStatus,
      keysByState,
      pendingRequests,
    ] = await Promise.all([
      this.prisma.merchant.count({ where: merchantScope }),
      this.prisma.merchant.count({ where: { ...merchantScope, status: 'active' } }),
      this.prisma.merchant.count({ where: { ...merchantScope, status: 'pending' } }),
      this.prisma.customer.count({ where: merchantId ? { merchantId } : {} }),
      this.prisma.device.groupBy({
        by: ['status'],
        where: deviceWhere,
        _count: true,
        orderBy: { status: 'asc' },
      }),
      this.prisma.licenseKey.groupBy({
        by: ['state'],
        where: keyWhere,
        _count: true,
        orderBy: { state: 'asc' },
      }),
      this.prisma.restrictionRequest.count({
        where: { status: 'pending', ...(merchantId ? { merchantId } : {}) },
      }),
    ]);

    const devCount = (s: string) =>
      devicesByStatus.find((d) => d.status === s)?._count ?? 0;
    const keyCount = (s: string) =>
      keysByState.find((k) => k.state === s)?._count ?? 0;

    const available = keyCount('available');
    const consumed = keyCount('consumed');
    const released = keyCount('released');

    return {
      merchants: {
        total: merchantsTotal,
        active: merchantsActive,
        pending: merchantsPending,
      },
      customers: { total: customersTotal },
      devices: {
        total: devCount('active') + devCount('review') + devCount('restricted'),
        active: devCount('active'),
        review: devCount('review'),
        restricted: devCount('restricted'),
      },
      keys: {
        total: available + consumed + released,
        available,
        consumed,
        released,
      },
      restrictionRequests: { pending: pendingRequests },
    };
  }
}
