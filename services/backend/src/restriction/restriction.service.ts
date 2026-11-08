import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { RequestStatus, RestrictionType } from '@prisma/client';
import { randomUUID } from 'crypto';
import { PrismaService } from '../prisma/prisma.service';
import { AuditService } from '../audit/audit.service';

/**
 * Restriction lifecycle. A merchant only *requests*; an admin approves, and the
 * server is what actually changes device state and writes the policy. The client
 * never decides restriction on its own. See docs/device-enrollment.md.
 */
@Injectable()
export class RestrictionService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly audit: AuditService,
  ) {}

  async createRequest(
    merchantId: string,
    actorId: string,
    deviceId: string,
    type: RestrictionType,
    reason: string,
  ) {
    const device = await this.prisma.device.findFirst({
      where: { id: deviceId, merchantId },
    });
    if (!device) throw new NotFoundException('Device not found');

    // Guard against contradictory requests.
    if (type === 'restrict' && device.status === 'restricted') {
      throw new BadRequestException('Device is already restricted');
    }
    if (type === 'release' && device.status !== 'restricted') {
      throw new BadRequestException('Device is not restricted');
    }

    const request = await this.prisma.restrictionRequest.create({
      data: { deviceId, merchantId, requestedBy: actorId, type, reason },
    });
    await this.audit.record({
      actorId,
      merchantId,
      action: `restriction.request.${type}`,
      entityType: 'restriction_request',
      entityId: request.id,
      metadata: { deviceId, reason },
    });
    return request;
  }

  listRequests(merchantId: string | undefined, status?: RequestStatus) {
    return this.prisma.restrictionRequest.findMany({
      where: {
        ...(merchantId ? { merchantId } : {}),
        ...(status ? { status } : {}),
      },
      orderBy: { createdAt: 'desc' },
    });
  }

  /** Admin approves: server applies the policy and flips device state. */
  async approve(adminId: string, requestId: string) {
    const request = await this.prisma.restrictionRequest.findUnique({
      where: { id: requestId },
    });
    if (!request) throw new NotFoundException('Request not found');
    if (request.status !== 'pending') {
      throw new BadRequestException(`Request already ${request.status}`);
    }

    const decisionId = randomUUID();
    const isRestrict = request.type === 'restrict';

    await this.prisma.$transaction(async (tx) => {
      await tx.restrictionRequest.update({
        where: { id: request.id },
        data: { status: 'approved', reviewedBy: adminId, reviewedAt: new Date() },
      });
      // Deactivate the current policy, write the new authoritative one.
      await tx.devicePolicy.updateMany({
        where: { deviceId: request.deviceId, active: true },
        data: { active: false },
      });
      await tx.devicePolicy.create({
        data: {
          deviceId: request.deviceId,
          policyType: isRestrict ? 'restricted' : 'normal',
          appliedById: adminId,
          reason: request.reason,
          serverDecisionId: decisionId,
          active: true,
        },
      });
      await tx.device.update({
        where: { id: request.deviceId },
        data: { status: isRestrict ? 'restricted' : 'active' },
      });
    });

    await this.audit.record({
      actorId: adminId,
      merchantId: request.merchantId,
      action: `restriction.approve.${request.type}`,
      entityType: 'device',
      entityId: request.deviceId,
      metadata: { requestId: request.id, serverDecisionId: decisionId },
    });
    return { status: 'approved', deviceId: request.deviceId, serverDecisionId: decisionId };
  }

  /**
   * Admin applies a restriction/release directly (the admin *is* the authority,
   * so no separate request→approval round-trip). Still records an approved
   * request row + policy + audit entry, so the trail is identical.
   */
  async adminApply(
    adminId: string,
    deviceId: string,
    type: RestrictionType,
    reason: string,
  ) {
    const device = await this.prisma.device.findUnique({
      where: { id: deviceId },
    });
    if (!device) throw new NotFoundException('Device not found');
    if (type === 'restrict' && device.status === 'restricted') {
      throw new BadRequestException('Device is already restricted');
    }
    if (type === 'release' && device.status !== 'restricted') {
      throw new BadRequestException('Device is not restricted');
    }

    const decisionId = randomUUID();
    const isRestrict = type === 'restrict';

    await this.prisma.$transaction(async (tx) => {
      await tx.restrictionRequest.create({
        data: {
          deviceId,
          merchantId: device.merchantId,
          requestedBy: adminId,
          type,
          reason,
          status: 'approved',
          reviewedBy: adminId,
          reviewedAt: new Date(),
        },
      });
      await tx.devicePolicy.updateMany({
        where: { deviceId, active: true },
        data: { active: false },
      });
      await tx.devicePolicy.create({
        data: {
          deviceId,
          policyType: isRestrict ? 'restricted' : 'normal',
          appliedById: adminId,
          reason,
          serverDecisionId: decisionId,
          active: true,
        },
      });
      await tx.device.update({
        where: { id: deviceId },
        data: { status: isRestrict ? 'restricted' : 'active' },
      });
    });

    await this.audit.record({
      actorId: adminId,
      merchantId: device.merchantId,
      action: `restriction.admin_${type}`,
      entityType: 'device',
      entityId: deviceId,
      metadata: { serverDecisionId: decisionId, reason },
    });
    return { status: 'applied', type, deviceId, serverDecisionId: decisionId };
  }

  async reject(adminId: string, requestId: string, reason?: string) {
    const request = await this.prisma.restrictionRequest.findUnique({
      where: { id: requestId },
    });
    if (!request) throw new NotFoundException('Request not found');
    if (request.status !== 'pending') {
      throw new BadRequestException(`Request already ${request.status}`);
    }
    const updated = await this.prisma.restrictionRequest.update({
      where: { id: request.id },
      data: { status: 'rejected', reviewedBy: adminId, reviewedAt: new Date() },
    });
    await this.audit.record({
      actorId: adminId,
      merchantId: request.merchantId,
      action: 'restriction.reject',
      entityType: 'restriction_request',
      entityId: request.id,
      metadata: { reason },
    });
    return updated;
  }

  /**
   * The policy a device's DPC should currently enforce. Restricted policy always
   * keeps emergency calling on. This is the single source of truth the client polls.
   */
  async currentPolicyForDevice(deviceId: string) {
    const policy = await this.prisma.devicePolicy.findFirst({
      where: { deviceId, active: true },
      orderBy: { appliedAt: 'desc' },
    });
    const type = policy?.policyType ?? 'normal';
    return {
      policyType: type,
      emergencyCalls: true, // always available, in every state
      reason: policy?.reason ?? null,
      serverDecisionId: policy?.serverDecisionId ?? null,
      appliedAt: policy?.appliedAt ?? null,
    };
  }
}
