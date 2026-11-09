import {
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { RequestStatus, RestrictionType } from '@prisma/client';
import { IsEnum, IsOptional, IsString, MinLength } from 'class-validator';
import { RestrictionService } from './restriction.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';

class CreateRequestDto {
  @IsEnum(RestrictionType) type!: RestrictionType;
  @IsString() @MinLength(3) reason!: string;
}

class ReasonDto {
  @IsString() @MinLength(3) reason!: string;
}

class RejectDto {
  @IsOptional() @IsString() reason?: string;
}

@Controller()
export class RestrictionController {
  constructor(private readonly restriction: RestrictionService) {}

  /** Merchant submits a restriction/release request for one of its devices. */
  @Post('devices/:id/restriction-requests')
  @RequirePermissions(Permission.DeviceRestrictRequest)
  create(
    @CurrentUser() user: AuthUser,
    @Param('id') deviceId: string,
    @Body() dto: CreateRequestDto,
  ) {
    const merchantId = user.merchantId ?? '';
    return this.restriction.createRequest(
      merchantId,
      user.id,
      deviceId,
      dto.type,
      dto.reason,
    );
  }

  /** Admin review queue. */
  @Get('restriction-requests')
  @RequirePermissions(Permission.DeviceRestrictReview)
  list(
    @CurrentUser() user: AuthUser,
    @Query('status') status?: RequestStatus,
  ) {
    const merchantId = user.isSuperAdmin ? undefined : user.merchantId ?? undefined;
    return this.restriction.listRequests(merchantId, status);
  }

  /** Admin applies a restriction directly to a device (no request needed). */
  @Post('devices/:id/restrict')
  @RequirePermissions(Permission.DeviceRestrictApprove)
  adminRestrict(
    @CurrentUser() user: AuthUser,
    @Param('id') deviceId: string,
    @Body() dto: ReasonDto,
  ) {
    return this.restriction.adminApply(user.id, deviceId, 'restrict', dto.reason);
  }

  /** Admin releases a restriction directly. */
  @Post('devices/:id/release')
  @RequirePermissions(Permission.DeviceRestrictApprove)
  adminRelease(
    @CurrentUser() user: AuthUser,
    @Param('id') deviceId: string,
    @Body() dto: ReasonDto,
  ) {
    return this.restriction.adminApply(user.id, deviceId, 'release', dto.reason);
  }

  /** Merchant one-tap lock of its own device (no separate approval step). */
  @Post('devices/:id/lock')
  @RequirePermissions(Permission.DeviceLock)
  lock(
    @CurrentUser() user: AuthUser,
    @Param('id') deviceId: string,
    @Body() dto: ReasonDto,
  ) {
    const scope = user.isSuperAdmin ? null : user.merchantId ?? null;
    return this.restriction.adminApply(user.id, deviceId, 'restrict', dto.reason, scope);
  }

  /** Merchant one-tap unlock of its own device. */
  @Post('devices/:id/unlock')
  @RequirePermissions(Permission.DeviceLock)
  unlock(
    @CurrentUser() user: AuthUser,
    @Param('id') deviceId: string,
    @Body() dto: ReasonDto,
  ) {
    const scope = user.isSuperAdmin ? null : user.merchantId ?? null;
    return this.restriction.adminApply(user.id, deviceId, 'release', dto.reason, scope);
  }

  @Post('restriction-requests/:id/approve')
  @RequirePermissions(Permission.DeviceRestrictApprove)
  approve(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    return this.restriction.approve(user.id, id);
  }

  @Post('restriction-requests/:id/reject')
  @RequirePermissions(Permission.DeviceRestrictApprove)
  reject(
    @CurrentUser() user: AuthUser,
    @Param('id') id: string,
    @Body() dto: RejectDto,
  ) {
    return this.restriction.reject(user.id, id, dto.reason);
  }
}
