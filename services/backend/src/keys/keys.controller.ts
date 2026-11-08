import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Query,
} from '@nestjs/common';
import { IsInt, Max, Min } from 'class-validator';
import { KeysService } from './keys.service';
import { CurrentUser, RequirePermissions } from '../common/decorators';
import { Permission } from '../common/permissions';
import { AuthUser } from '../common/types';
import { AuditService } from '../audit/audit.service';

class PurchaseRequestDto {
  @IsInt()
  @Min(1)
  @Max(10000)
  quantity!: number;
}

@Controller()
export class KeysController {
  constructor(
    private readonly keys: KeysService,
    private readonly audit: AuditService,
  ) {}

  /** Merchant: current quota. */
  @Get('keys/summary')
  @RequirePermissions(Permission.KeyRead)
  summary(@CurrentUser() user: AuthUser) {
    const merchantId = this.requireMerchant(user);
    return this.keys.summary(merchantId);
  }

  @Get('keys')
  @RequirePermissions(Permission.KeyRead)
  list(
    @CurrentUser() user: AuthUser,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
  ) {
    const merchantId = this.requireMerchant(user);
    return this.keys.list(
      merchantId,
      page ? Number(page) : 1,
      limit ? Number(limit) : 20,
    );
  }

  /** Merchant requests more keys — recorded, then an admin grants the batch. */
  @Post('keys/purchase-request')
  @RequirePermissions(Permission.KeyPurchase)
  async purchaseRequest(
    @CurrentUser() user: AuthUser,
    @Body() dto: PurchaseRequestDto,
  ) {
    const merchantId = this.requireMerchant(user);
    await this.audit.record({
      actorId: user.id,
      merchantId,
      action: 'key.purchase_request',
      entityType: 'license_key_batch',
      metadata: { quantity: dto.quantity },
    });
    return { status: 'pending', quantity: dto.quantity };
  }

  /** Admin grants a key batch to a merchant. */
  @Post('merchants/:merchantId/keys/grant')
  @RequirePermissions(Permission.KeyGrant)
  async grant(
    @CurrentUser() user: AuthUser,
    @Param('merchantId') merchantId: string,
    @Body() dto: PurchaseRequestDto,
  ) {
    const result = await this.keys.grantBatch(merchantId, dto.quantity);
    await this.audit.record({
      actorId: user.id,
      merchantId,
      action: 'key.grant',
      entityType: 'license_key_batch',
      entityId: result.batchId,
      metadata: { quantity: result.quantity },
    });
    return result;
  }

  private requireMerchant(user: AuthUser): string {
    if (!user.merchantId) {
      throw new BadRequestException('This endpoint requires a merchant context');
    }
    return user.merchantId;
  }
}
