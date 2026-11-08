import { Controller, Get, Query } from '@nestjs/common';
import { AuditService } from './audit.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';

@Controller('audit-logs')
export class AuditController {
  constructor(private readonly audit: AuditService) {}

  @Get()
  @RequirePermissions(Permission.AuditRead)
  list(
    @CurrentUser() user: AuthUser,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
  ) {
    // A merchant-scoped auditor only sees their own tenant's logs.
    const merchantId = user.isSuperAdmin ? undefined : user.merchantId ?? undefined;
    return this.audit.list({
      merchantId,
      page: page ? Number(page) : undefined,
      limit: limit ? Number(limit) : undefined,
    });
  }
}
