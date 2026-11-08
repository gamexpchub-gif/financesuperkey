import { Injectable, NotFoundException } from '@nestjs/common';
import { DeviceStatus, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class DevicesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(
    merchantId: string | undefined,
    opts: { status?: DeviceStatus; q?: string; page?: number; limit?: number },
  ) {
    const page = Math.max(1, opts.page ?? 1);
    const limit = Math.min(100, opts.limit ?? 20);
    const where: Prisma.DeviceWhereInput = {
      ...(merchantId ? { merchantId } : {}),
      ...(opts.status ? { status: opts.status } : {}),
      ...(opts.q
        ? {
            OR: [
              { model: { contains: opts.q, mode: 'insensitive' } },
              { imeiMasked: { contains: opts.q } },
              { customer: { fullName: { contains: opts.q, mode: 'insensitive' } } },
              { customer: { phone: { contains: opts.q } } },
            ],
          }
        : {}),
    };
    const [data, total] = await this.prisma.$transaction([
      this.prisma.device.findMany({
        where,
        include: { customer: { select: { id: true, fullName: true, phone: true } } },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      this.prisma.device.count({ where }),
    ]);
    return { data, total };
  }

  async get(merchantId: string | undefined, id: string) {
    const device = await this.prisma.device.findFirst({
      where: { id, ...(merchantId ? { merchantId } : {}) },
      include: {
        customer: true,
        enrollment: true,
        policies: { where: { active: true }, orderBy: { appliedAt: 'desc' } },
      },
    });
    if (!device) throw new NotFoundException('Device not found');
    return device;
  }

  /** Enrollment + restriction history, newest first — for the detail timeline. */
  async timeline(merchantId: string | undefined, id: string) {
    await this.get(merchantId, id); // tenant check
    const [policies, requests, enrollment] = await this.prisma.$transaction([
      this.prisma.devicePolicy.findMany({
        where: { deviceId: id },
        orderBy: { appliedAt: 'desc' },
      }),
      this.prisma.restrictionRequest.findMany({
        where: { deviceId: id },
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.deviceEnrollment.findUnique({ where: { deviceId: id } }),
    ]);
    return { enrollment, policies, requests };
  }
}
