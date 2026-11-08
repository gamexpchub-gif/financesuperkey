import {
  BadRequestException,
  Injectable,
} from '@nestjs/common';
import { Prisma, PrismaClient } from '@prisma/client';
import { randomUUID } from 'crypto';
import { PrismaService } from '../prisma/prisma.service';

type Tx = Prisma.TransactionClient | PrismaClient;

/**
 * License-key ("Super Key") quota management.
 *   available → consumed  when a device is enrolled
 *   consumed  → released  when a device is decommissioned / enrollment revoked
 * Quota = count(available) for a merchant.
 */
@Injectable()
export class KeysService {
  constructor(private readonly prisma: PrismaService) {}

  async summary(merchantId: string) {
    const grouped = await this.prisma.licenseKey.groupBy({
      by: ['state'],
      where: { merchantId },
      _count: { _all: true },
    });
    const count = (s: string) =>
      grouped.find((g) => g.state === s)?._count._all ?? 0;
    const available = count('available');
    const consumed = count('consumed');
    const released = count('released');
    return {
      total: available + consumed + released,
      available,
      consumed,
      released,
    };
  }

  list(merchantId: string, page = 1, limit = 20) {
    return this.prisma.licenseKey.findMany({
      where: { merchantId },
      orderBy: { createdAt: 'desc' },
      skip: (Math.max(1, page) - 1) * limit,
      take: Math.min(100, limit),
    });
  }

  /** Grants a batch of fresh available keys (admin-approved purchase). */
  async grantBatch(merchantId: string, quantity: number) {
    if (quantity < 1 || quantity > 10000) {
      throw new BadRequestException('Quantity must be between 1 and 10000');
    }
    const batchId = randomUUID();
    await this.prisma.licenseKey.createMany({
      data: Array.from({ length: quantity }, () => ({
        merchantId,
        batchId,
        state: 'available' as const,
      })),
    });
    return { batchId, quantity };
  }

  /**
   * Atomically consumes one available key for a device. Runs inside the
   * caller's transaction so enrollment and key consumption commit together.
   * Throws if the merchant has no available key.
   */
  async consumeOne(tx: Tx, merchantId: string, deviceId: string) {
    const key = await tx.licenseKey.findFirst({
      where: { merchantId, state: 'available' },
      orderBy: { createdAt: 'asc' },
    });
    if (!key) {
      throw new BadRequestException(
        'No available license key — purchase more keys first',
      );
    }
    await tx.licenseKey.update({
      where: { id: key.id },
      data: { state: 'consumed', deviceId, consumedAt: new Date() },
    });
    return key.id;
  }

  /**
   * Frees the key bound to a device, returning it to the available pool so the
   * merchant's quota slot is reusable (e.g. after decommission / revoke).
   * `released` remains a separate terminal state for keys an admin permanently
   * retires — it is not produced here.
   */
  async releaseForDevice(tx: Tx, deviceId: string) {
    await tx.licenseKey.updateMany({
      where: { deviceId, state: 'consumed' },
      data: {
        state: 'available',
        deviceId: null,
        consumedAt: null,
        releasedAt: new Date(),
      },
    });
  }
}
