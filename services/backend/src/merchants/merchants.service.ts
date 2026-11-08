import { Injectable, NotFoundException } from '@nestjs/common';
import { MerchantStatus, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

export interface CreateMerchantInput {
  companyId: string;
  name: string;
  dealerNo: string;
  phone?: string;
  email?: string;
  address?: string;
}

@Injectable()
export class MerchantsService {
  constructor(private readonly prisma: PrismaService) {}

  create(input: CreateMerchantInput) {
    return this.prisma.merchant.create({ data: input });
  }

  async list(status?: MerchantStatus, q?: string, page = 1, limit = 20) {
    const where: Prisma.MerchantWhereInput = {
      ...(status ? { status } : {}),
      ...(q
        ? {
            OR: [
              { name: { contains: q, mode: 'insensitive' } },
              { dealerNo: { contains: q, mode: 'insensitive' } },
            ],
          }
        : {}),
    };
    const [data, total] = await this.prisma.$transaction([
      this.prisma.merchant.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip: (Math.max(1, page) - 1) * limit,
        take: Math.min(100, limit),
      }),
      this.prisma.merchant.count({ where }),
    ]);
    return { data, total };
  }

  async get(id: string) {
    const merchant = await this.prisma.merchant.findUnique({ where: { id } });
    if (!merchant) throw new NotFoundException('Merchant not found');
    return merchant;
  }

  async update(id: string, input: Partial<CreateMerchantInput>) {
    await this.get(id);
    return this.prisma.merchant.update({ where: { id }, data: input });
  }

  async setStatus(id: string, status: MerchantStatus) {
    await this.get(id);
    return this.prisma.merchant.update({ where: { id }, data: { status } });
  }
}
