import { Module } from '@nestjs/common';
import { RestrictionService } from './restriction.service';
import { RestrictionController } from './restriction.controller';

@Module({
  controllers: [RestrictionController],
  providers: [RestrictionService],
  exports: [RestrictionService],
})
export class RestrictionModule {}
