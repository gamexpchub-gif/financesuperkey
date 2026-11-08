import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import {
  IsOptional,
  IsString,
  MinLength,
} from 'class-validator';
import { CustomersService } from './customers.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';
import { AuditService } from '../audit/audit.service';

class CreateCustomerDto {
  @IsString() @MinLength(2) fullName!: string;
  @IsString() @MinLength(6) phone!: string;
  @IsOptional() @IsString() nidNo?: string;
  @IsOptional() @IsString() address?: string;
  @IsOptional() @IsString() dealerNo?: string;
}

class UpdateCustomerDto {
  @IsOptional() @IsString() fullName?: string;
  @IsOptional() @IsString() phone?: string;
  @IsOptional() @IsString() nidNo?: string;
  @IsOptional() @IsString() address?: string;
  @IsOptional() @IsString() dealerNo?: string;
}

@Controller('customers')
export class CustomersController {
  constructor(
    private readonly customers: CustomersService,
    private readonly audit: AuditService,
  ) {}

  @Post()
  @RequirePermissions(Permission.CustomerCreate)
  async create(@CurrentUser() user: AuthUser, @Body() dto: CreateCustomerDto) {
    const merchantId = this.requireMerchant(user);
    const customer = await this.customers.create(merchantId, dto);
    await this.audit.record({
      actorId: user.id,
      merchantId,
      action: 'customer.create',
      entityType: 'customer',
      entityId: customer.id,
    });
    return customer;
  }

  @Get()
  @RequirePermissions(Permission.CustomerRead)
  list(
    @CurrentUser() user: AuthUser,
    @Query('q') q?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
  ) {
    // Super admin sees every tenant; merchant staff are scoped to their own.
    const merchantId = user.isSuperAdmin ? undefined : this.requireMerchant(user);
    return this.customers.list(
      merchantId,
      q,
      page ? Number(page) : 1,
      limit ? Number(limit) : 20,
    );
  }

  @Get(':id')
  @RequirePermissions(Permission.CustomerRead)
  get(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    const merchantId = user.isSuperAdmin ? undefined : this.requireMerchant(user);
    return this.customers.get(merchantId, id);
  }

  @Patch(':id')
  @RequirePermissions(Permission.CustomerUpdate)
  async update(
    @CurrentUser() user: AuthUser,
    @Param('id') id: string,
    @Body() dto: UpdateCustomerDto,
  ) {
    const merchantId = this.requireMerchant(user);
    const updated = await this.customers.update(merchantId, id, dto);
    await this.audit.record({
      actorId: user.id,
      merchantId,
      action: 'customer.update',
      entityType: 'customer',
      entityId: id,
    });
    return updated;
  }

  private requireMerchant(user: AuthUser): string {
    if (!user.merchantId) {
      throw new BadRequestException('This endpoint requires a merchant context');
    }
    return user.merchantId;
  }
}
