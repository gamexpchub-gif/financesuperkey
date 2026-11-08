import { Injectable, NotFoundException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';

export interface CreateCustomerInput {
  fullName: string;
  phone: string;
  nidNo?: string;
  address?: string;
  dealerNo?: string;
}

@Injectable()
export class CustomersService {
  constructor(private readonly prisma: PrismaService) {}

  create(merchantId: string, input: CreateCustomerInput) {
    return this.prisma.customer.create({
      data: { merchantId, ...input },
    });
  }

  /** merchantId undefined = super admin listing across all tenants. */
  async list(
    merchantId: string | undefined,
    q?: string,
    page = 1,
    limit = 20,
  ) {
    const where: Prisma.CustomerWhereInput = {
      ...(merchantId ? { merchantId } : {}),
      ...(q
        ? {
            OR: [
              { fullName: { contains: q, mode: 'insensitive' } },
              { phone: { contains: q } },
            ],
          }
        : {}),
    };
    const [data, total] = await this.prisma.$transaction([
      this.prisma.customer.findMany({
        where,
        include: { merchant: { select: { name: true, dealerNo: true } } },
        orderBy: { createdAt: 'desc' },
        skip: (Math.max(1, page) - 1) * limit,
        take: Math.min(100, limit),
      }),
      this.prisma.customer.count({ where }),
    ]);
    return { data, total };
  }

  async get(merchantId: string | undefined, id: string) {
    const customer = await this.prisma.customer.findFirst({
      where: { id, ...(merchantId ? { merchantId } : {}) },
      include: { devices: true, merchant: { select: { name: true, dealerNo: true } } },
    });
    if (!customer) throw new NotFoundException('Customer not found');
    return customer;
  }

  async update(
    merchantId: string,
    id: string,
    input: Partial<CreateCustomerInput>,
  ) {
    await this.get(merchantId, id); // tenant check
    return this.prisma.customer.update({ where: { id }, data: input });
  }
}
