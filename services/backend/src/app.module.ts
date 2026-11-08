import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';

import { validateEnv } from './config/env.validation';
import { PrismaModule } from './prisma/prisma.module';
import { AuditModule } from './audit/audit.module';
import { AuthModule } from './auth/auth.module';
import { MerchantsModule } from './merchants/merchants.module';
import { CustomersModule } from './customers/customers.module';
import { DevicesModule } from './devices/devices.module';
import { KeysModule } from './keys/keys.module';
import { EnrollmentModule } from './enrollment/enrollment.module';
import { RestrictionModule } from './restriction/restriction.module';
import { DeviceGatewayModule } from './device-gateway/device-gateway.module';

import { JwtAuthGuard } from './common/guards/jwt-auth.guard';
import { PermissionsGuard } from './common/guards/permissions.guard';
import { AppController } from './app.controller';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validate: validateEnv,
    }),
    PrismaModule,
    AuditModule,
    AuthModule,
    MerchantsModule,
    CustomersModule,
    DevicesModule,
    KeysModule,
    EnrollmentModule,
    RestrictionModule,
    DeviceGatewayModule,
  ],
  controllers: [AppController],
  providers: [
    // Order matters: authenticate first, then check permissions.
    { provide: APP_GUARD, useClass: JwtAuthGuard },
    { provide: APP_GUARD, useClass: PermissionsGuard },
  ],
})
export class AppModule {}
