import {
  BadRequestException,
  Body,
  Controller,
  Param,
  Post,
} from '@nestjs/common';
import { EnrollmentMethod } from '@prisma/client';
import { IsEnum, IsOptional, IsString, MinLength } from 'class-validator';
import { EnrollmentService } from './enrollment.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';

class StartEnrollmentDto {
  @IsString() customerId!: string;
  @IsString() @MinLength(2) model!: string;
  @IsString() @MinLength(6) imei!: string;
  @IsOptional() @IsString() serial?: string;
  @IsEnum(EnrollmentMethod) method!: EnrollmentMethod;
}

class ConsentDto {
  @IsString() consentTextVersion!: string;
  @IsString() signatureUrl!: string;
  @IsString() doc1Url!: string;
  @IsString() doc2Url!: string;
}

@Controller('enrollment')
export class EnrollmentController {
  constructor(private readonly enrollment: EnrollmentService) {}

  @Post('start')
  @RequirePermissions(Permission.EnrollmentCreate)
  start(@CurrentUser() user: AuthUser, @Body() dto: StartEnrollmentDto) {
    return this.enrollment.start(this.requireMerchant(user), user.id, dto);
  }

  @Post(':id/consent')
  @RequirePermissions(Permission.EnrollmentCreate)
  consent(
    @CurrentUser() user: AuthUser,
    @Param('id') id: string,
    @Body() dto: ConsentDto,
  ) {
    return this.enrollment.consent(this.requireMerchant(user), user.id, id, dto);
  }

  @Post(':id/complete')
  @RequirePermissions(Permission.EnrollmentComplete)
  complete(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    return this.enrollment.complete(this.requireMerchant(user), user.id, id);
  }

  @Post(':id/revoke')
  @RequirePermissions(Permission.EnrollmentRevoke)
  revoke(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    // Admins with device_admin may revoke across tenants (null = no filter);
    // a merchant staff member is scoped to their own tenant.
    const merchantId = user.merchantId ?? null;
    return this.enrollment.revoke(merchantId, user.id, id);
  }

  private requireMerchant(user: AuthUser): string {
    if (!user.merchantId) {
      throw new BadRequestException('This endpoint requires a merchant context');
    }
    return user.merchantId;
  }
}
