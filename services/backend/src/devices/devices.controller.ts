import { Controller, Get, Param, Query } from '@nestjs/common';
import { DeviceStatus } from '@prisma/client';
import { DevicesService } from './devices.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';

@Controller('devices')
export class DevicesController {
  constructor(private readonly devices: DevicesService) {}

  @Get()
  @RequirePermissions(Permission.DeviceRead)
  list(
    @CurrentUser() user: AuthUser,
    @Query('status') status?: DeviceStatus,
    @Query('q') q?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
  ) {
    return this.devices.list(this.scope(user), {
      status,
      q,
      page: page ? Number(page) : undefined,
      limit: limit ? Number(limit) : undefined,
    });
  }

  @Get(':id')
  @RequirePermissions(Permission.DeviceRead)
  get(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    return this.devices.get(this.scope(user), id);
  }

  @Get(':id/timeline')
  @RequirePermissions(Permission.DeviceRead)
  timeline(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    return this.devices.timeline(this.scope(user), id);
  }

  /** Super admin sees all tenants; everyone else is scoped to their merchant. */
  private scope(user: AuthUser): string | undefined {
    return user.isSuperAdmin ? undefined : user.merchantId ?? undefined;
  }
}
