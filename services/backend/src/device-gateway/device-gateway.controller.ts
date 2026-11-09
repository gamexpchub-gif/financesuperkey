import { Body, Controller, HttpCode, Post } from '@nestjs/common';
import { IsObject, IsOptional, IsString } from 'class-validator';
import { DeviceGatewayService } from './device-gateway.service';
import { Public } from '../common/decorators';

class DeviceTokenDto {
  @IsString() provisioningToken!: string;
}

class HeartbeatDto extends DeviceTokenDto {
  @IsOptional() @IsObject() integrity?: Record<string, unknown>;
}

class AckDto extends DeviceTokenDto {
  @IsOptional() @IsString() serverDecisionId?: string;
}

class ReportInfoDto extends DeviceTokenDto {
  @IsOptional() @IsString() imei?: string;
  @IsOptional() @IsString() model?: string;
  @IsOptional() @IsString() serial?: string;
}

// Authenticated by device token in the body, not a user JWT — hence @Public
// (the global JWT guard is skipped; the service validates the device token).
@Controller('device')
export class DeviceGatewayController {
  constructor(private readonly gateway: DeviceGatewayService) {}

  @Public()
  @Post('policy')
  @HttpCode(200)
  policy(@Body() dto: DeviceTokenDto) {
    return this.gateway.getPolicy(dto.provisioningToken);
  }

  @Public()
  @Post('heartbeat')
  @HttpCode(200)
  heartbeat(@Body() dto: HeartbeatDto) {
    return this.gateway.heartbeat(dto.provisioningToken, dto.integrity);
  }

  @Public()
  @Post('policy/ack')
  @HttpCode(200)
  ack(@Body() dto: AckDto) {
    return this.gateway.ackPolicy(dto.provisioningToken, dto.serverDecisionId);
  }

  /** DPC auto-reports the real IMEI / model / serial read from the hardware. */
  @Public()
  @Post('report-info')
  @HttpCode(200)
  reportInfo(@Body() dto: ReportInfoDto) {
    return this.gateway.reportInfo(dto.provisioningToken, {
      imei: dto.imei,
      model: dto.model,
      serial: dto.serial,
    });
  }
}
