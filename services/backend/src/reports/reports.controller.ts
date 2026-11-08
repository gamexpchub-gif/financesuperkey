import { Controller, Get } from '@nestjs/common';
import { ReportsService } from './reports.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';

@Controller('reports')
export class ReportsController {
  constructor(private readonly reports: ReportsService) {}

  @Get('overview')
  @RequirePermissions(Permission.ReportRead)
  overview(@CurrentUser() user: AuthUser) {
    const merchantId = user.isSuperAdmin ? null : user.merchantId ?? null;
    return this.reports.overview(merchantId);
  }
}
