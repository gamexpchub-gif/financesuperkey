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
