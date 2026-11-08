import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import { MerchantStatus } from '@prisma/client';
import { IsEmail, IsOptional, IsString, MinLength } from 'class-validator';
import { MerchantsService } from './merchants.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';
import { AuditService } from '../audit/audit.service';

class CreateMerchantDto {
  @IsString() companyId!: string;
  @IsString() @MinLength(2) name!: string;
  @IsString() @MinLength(2) dealerNo!: string;
  @IsOptional() @IsString() phone?: string;
  @IsOptional() @IsEmail() email?: string;
  @IsOptional() @IsString() address?: string;
}

class UpdateMerchantDto {
  @IsOptional() @IsString() name?: string;
  @IsOptional() @IsString() phone?: string;
  @IsOptional() @IsEmail() email?: string;
  @IsOptional() @IsString() address?: string;
}

@Controller('merchants')
export class MerchantsController {
  constructor(
    private readonly merchants: MerchantsService,
    private readonly audit: AuditService,
  ) {}

  @Get()
  @RequirePermissions(Permission.MerchantRead)
  list(
    @Query('status') status?: MerchantStatus,
    @Query('q') q?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
  ) {
    return this.merchants.list(
      status,
      q,
      page ? Number(page) : 1,
      limit ? Number(limit) : 20,
    );
  }

  @Get(':id')
  @RequirePermissions(Permission.MerchantRead)
  get(@Param('id') id: string) {
    return this.merchants.get(id);
  }

  @Post()
  @RequirePermissions(Permission.MerchantCreate)
  async create(@CurrentUser() user: AuthUser, @Body() dto: CreateMerchantDto) {
    const merchant = await this.merchants.create(dto);
    await this.audit.record({
      actorId: user.id,
      merchantId: merchant.id,
      action: 'merchant.create',
      entityType: 'merchant',
      entityId: merchant.id,
    });
    return merchant;
  }

  @Patch(':id')
  @RequirePermissions(Permission.MerchantUpdate)
  update(@Param('id') id: string, @Body() dto: UpdateMerchantDto) {
    return this.merchants.update(id, dto);
  }

  @Post(':id/approve')
  @RequirePermissions(Permission.MerchantApprove)
  async approve(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    const merchant = await this.merchants.setStatus(id, 'active');
    await this.audit.record({
      actorId: user.id,
      merchantId: id,
      action: 'merchant.approve',
      entityType: 'merchant',
      entityId: id,
    });
    return merchant;
  }

  @Post(':id/suspend')
  @RequirePermissions(Permission.MerchantSuspend)
  async suspend(@CurrentUser() user: AuthUser, @Param('id') id: string) {
    const merchant = await this.merchants.setStatus(id, 'suspended');
    await this.audit.record({
      actorId: user.id,
      merchantId: id,
      action: 'merchant.suspend',
      entityType: 'merchant',
      entityId: id,
    });
    return merchant;
  }
}
