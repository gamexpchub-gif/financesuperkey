/**
 * Seeds a usable starting state:
 *   - one company
 *   - a super admin user
 *   - one merchant + owner staff user
 *   - a batch of license keys
 *   - a couple of demo customers
 *
 * Run with: npm run db:seed   (after prisma migrate)
 * Passwords are for local development only.
 */
import 'dotenv/config';
import { PrismaClient } from '@prisma/client';
import * as argon2 from 'argon2';
import { randomUUID } from 'crypto';

const prisma = new PrismaClient();

async function main() {
  const company = await prisma.company.upsert({
    where: { id: '00000000-0000-0000-0000-000000000001' },
    update: {},
    create: {
      id: '00000000-0000-0000-0000-000000000001',
      name: 'Finance Super Key (Default Org)',
    },
  });

  const adminPass = await argon2.hash('Admin@12345');
  const superAdmin = await prisma.user.upsert({
    where: { phone: '01700000000' },
    update: {},
    create: {
      phone: '01700000000',
      email: 'admin@financesuperkey.app',
      passwordHash: adminPass,
      fullName: 'Super Admin',
      userType: 'super_admin',
      adminRoles: { create: { role: 'super_admin' } },
    },
  });

  const merchant = await prisma.merchant.upsert({
    where: { dealerNo: 'D-1042' },
    update: {},
    create: {
      companyId: company.id,
      name: 'রহমান টেলিকম',
      dealerNo: 'D-1042',
      phone: '01800000000',
      address: 'ঢাকা',
      status: 'active',
    },
  });

  const staffPass = await argon2.hash('Owner@12345');
  await prisma.user.upsert({
    where: { phone: '01811111111' },
    update: {},
    create: {
      phone: '01811111111',
      email: 'owner@rahmantelecom.test',
      passwordHash: staffPass,
      fullName: 'দোকান মালিক',
      userType: 'merchant_staff',
      staffProfile: {
        create: { merchantId: merchant.id, role: 'owner' },
      },
    },
  });

  // Grant an initial batch of 500 license keys if none exist yet.
  const existingKeys = await prisma.licenseKey.count({ where: { merchantId: merchant.id } });
  if (existingKeys === 0) {
    const batchId = randomUUID();
    await prisma.licenseKey.createMany({
      data: Array.from({ length: 500 }, () => ({
        merchantId: merchant.id,
        batchId,
        state: 'available' as const,
      })),
    });
  }

  // Demo customers
  const existingCustomers = await prisma.customer.count({ where: { merchantId: merchant.id } });
  if (existingCustomers === 0) {
    await prisma.customer.createMany({
      data: [
        { merchantId: merchant.id, fullName: 'সাকিব আহমেদ', phone: '01712345678', dealerNo: 'D-1042' },
        { merchantId: merchant.id, fullName: 'তানিয়া আক্তার', phone: '01819220114', dealerNo: 'D-1042' },
      ],
    });
  }

  console.log('Seed complete.');
  console.log('  Super admin : 01700000000 / Admin@12345');
  console.log('  Merchant owner: 01811111111 / Owner@12345');
  console.log(`  Merchant ${merchant.name} (${merchant.dealerNo}) with 500 keys`);
  console.log(`  Super admin id: ${superAdmin.id}`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
