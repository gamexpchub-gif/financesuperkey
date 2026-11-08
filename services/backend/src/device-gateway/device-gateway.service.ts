import { Injectable, UnauthorizedException } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { RestrictionService } from '../restriction/restriction.service';
import { AuditService } from '../audit/audit.service';

/**
 * Device-facing endpoints used by the Customer App DPC. The device authenticates
 * with its one-time provisioning token (a real build would exchange it for a
 * rotating device token after first contact). Policy decisions are read-only
 * here — the device applies what the server already decided.
 */
@Injectable()
export class DeviceGatewayService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly restriction: RestrictionService,
    private readonly audit: AuditService,
  ) {}

  private async resolveDeviceId(provisioningToken: string): Promise<string> {
    const enrollment = await this.prisma.deviceEnrollment.findFirst({
      where: { provisioningToken, status: { in: ['pending', 'enrolled'] } },
    });
    if (!enrollment) throw new UnauthorizedException('Unknown device token');
    return enrollment.deviceId;
  }

  async getPolicy(provisioningToken: string) {
    const deviceId = await this.resolveDeviceId(provisioningToken);
    return this.restriction.currentPolicyForDevice(deviceId);
  }

  async heartbeat(
    provisioningToken: string,
    integrity?: Record<string, unknown>,
  ) {
    const deviceId = await this.resolveDeviceId(provisioningToken);
    // Integrity/root signals are recorded as review signals, never used to
    // escalate restriction automatically.
    await this.audit.record({
      action: 'device.heartbeat',
      entityType: 'device',
      entityId: deviceId,
      metadata: { integrity: (integrity ?? {}) as Prisma.InputJsonValue },
    });
    return { ok: true };
  }

  async ackPolicy(provisioningToken: string, serverDecisionId?: string) {
    const deviceId = await this.resolveDeviceId(provisioningToken);
    await this.audit.record({
      action: 'device.policy.ack',
      entityType: 'device',
      entityId: deviceId,
      metadata: { serverDecisionId: serverDecisionId ?? null },
    });
    return { ok: true };
  }
}
