import { Module } from '@nestjs/common';
import { DeviceGatewayService } from './device-gateway.service';
import { DeviceGatewayController } from './device-gateway.controller';
import { RestrictionModule } from '../restriction/restriction.module';

@Module({
  imports: [RestrictionModule],
  controllers: [DeviceGatewayController],
  providers: [DeviceGatewayService],
})
export class DeviceGatewayModule {}
