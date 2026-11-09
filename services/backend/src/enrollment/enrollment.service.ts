import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { EnrollmentMethod } from '@prisma/client';
import { createHash, randomBytes } from 'crypto';
import { PrismaService } from '../prisma/prisma.service';
import { KeysService } from '../keys/keys.service';
import { AuditService } from '../audit/audit.service';

export interface StartEnrollmentInput {
  customerId: string;
  model?: string;
  imei?: string;
  serial?: string;
  method: EnrollmentMethod;
}

export interface ConsentInput {
  consentTextVersion: string;
  signatureUrl: string;
  doc1Url: string;
  doc2Url: string;
}

const TOKEN_TTL_MS = 60 * 60 * 1000; // 1 hour
// Package name of the Customer App DPC (see apps/customer-app).
const DPC_COMPONENT =
  'com.financesuperkey.dpc/.DeviceOwnerReceiver';

@Injectable()
export class EnrollmentService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly keys: KeysService,
    private readonly audit: AuditService,
    private readonly config: ConfigService,
  ) {}

  /**
   * Begins enrollment. Atomically: verifies the customer, reserves one license
   * key, creates the device + a pending enrollment with a one-time provisioning
   * token. If the merchant has no available key the whole thing rolls back.
   */
  async start(merchantId: string, actorId: string, input: StartEnrollmentInput) {
    const customer = await this.prisma.customer.findFirst({
      where: { id: input.customerId, merchantId },
    });
    if (!customer) throw new NotFoundException('Customer not found');

    const token = randomBytes(24).toString('base64url');

    const { device, enrollment } = await this.prisma.$transaction(async (tx) => {
      // IMEI/model are filled automatically by the Device Owner app after
      // provisioning (POST /device/report-info); until then use placeholders.
      const hasImei = !!(input.imei && input.imei.trim().length >= 6);
      const device = await tx.device.create({
        data: {
          merchantId,
          customerId: customer.id,
          model: input.model?.trim() || 'Pending device',
          serial: input.serial,
          imeiHash: hasImei ? this.hashImei(input.imei!) : `pending:${token}`,
          imeiMasked: hasImei ? this.maskImei(input.imei!) : 'Awaiting device…',
          status: 'active',
        },
      });
      // Reserve a key now so "start" guarantees capacity.
      await this.keys.consumeOne(tx, merchantId, device.id);
      const enrollment = await tx.deviceEnrollment.create({
        data: {
          deviceId: device.id,
          method: input.method,
          provisioningToken: token,
          tokenExpiresAt: new Date(Date.now() + TOKEN_TTL_MS),
          enrolledById: actorId,
          status: 'pending',
        },
      });
      return { device, enrollment };
    });

    await this.audit.record({
      actorId,
      merchantId,
      action: 'enrollment.start',
      entityType: 'device',
      entityId: device.id,
      metadata: { method: input.method },
    });

    return {
      enrollmentId: enrollment.id,
      deviceId: device.id,
      qrPayload: this.buildQrPayload(token),
      expiresAt: enrollment.tokenExpiresAt,
    };
  }

  /** Captures the customer's consent, signature and documents. */
  async consent(
    merchantId: string,
    actorId: string,
    enrollmentId: string,
    input: ConsentInput,
  ) {
    const enrollment = await this.findPending(merchantId, enrollmentId);
    const updated = await this.prisma.deviceEnrollment.update({
      where: { id: enrollment.id },
      data: {
        consentAt: new Date(),
        consentTextVersion: input.consentTextVersion,
        signatureUrl: input.signatureUrl,
        doc1Url: input.doc1Url,
        doc2Url: input.doc2Url,
      },
    });
    await this.audit.record({
      actorId,
      merchantId,
      action: 'enrollment.consent',
      entityType: 'device_enrollment',
      entityId: enrollment.id,
      metadata: { consentTextVersion: input.consentTextVersion },
    });
    return updated;
  }

  /**
   * Finalizes enrollment once the DPC is active and consent is captured.
   * Sets the enrollment to enrolled and writes the initial `normal` policy.
   */
  async complete(merchantId: string, actorId: string, enrollmentId: string) {
    const enrollment = await this.findPending(merchantId, enrollmentId);
    if (!enrollment.consentAt) {
      throw new BadRequestException('Consent must be captured before completion');
    }

    await this.prisma.$transaction(async (tx) => {
      await tx.deviceEnrollment.update({
        where: { id: enrollment.id },
        data: { status: 'enrolled' },
      });
      await tx.devicePolicy.create({
        data: {
          deviceId: enrollment.deviceId,
          policyType: 'normal',
          appliedById: actorId,
          reason: 'Enrollment completed',
          active: true,
        },
      });
      await tx.device.update({
        where: { id: enrollment.deviceId },
        data: { status: 'active' },
      });
    });

    await this.audit.record({
      actorId,
      merchantId,
      action: 'enrollment.complete',
      entityType: 'device',
      entityId: enrollment.deviceId,
    });
    return { deviceId: enrollment.deviceId, status: 'enrolled' };
  }

  /**
   * Cancels a pending/enrolled device and releases its license key.
   * `merchantId` null means an admin acting across tenants (no tenant filter).
   */
  async revoke(
    merchantId: string | null,
    actorId: string,
    enrollmentId: string,
  ) {
    const enrollment = await this.prisma.deviceEnrollment.findFirst({
      where: {
        id: enrollmentId,
        ...(merchantId ? { device: { merchantId } } : {}),
      },
    });
    if (!enrollment) throw new NotFoundException('Enrollment not found');

    await this.prisma.$transaction(async (tx) => {
      await tx.deviceEnrollment.update({
        where: { id: enrollment.id },
        data: { status: 'revoked' },
      });
      await tx.devicePolicy.updateMany({
        where: { deviceId: enrollment.deviceId, active: true },
        data: { active: false },
      });
      await this.keys.releaseForDevice(tx, enrollment.deviceId);
    });

    await this.audit.record({
      actorId,
      merchantId,
      action: 'enrollment.revoke',
      entityType: 'device',
      entityId: enrollment.deviceId,
    });
    return { deviceId: enrollment.deviceId, status: 'revoked' };
  }

  private async findPending(merchantId: string, enrollmentId: string) {
    const enrollment = await this.prisma.deviceEnrollment.findFirst({
      where: { id: enrollmentId, device: { merchantId } },
    });
    if (!enrollment) throw new NotFoundException('Enrollment not found');
    if (enrollment.status !== 'pending') {
      throw new BadRequestException(`Enrollment is already ${enrollment.status}`);
    }
    return enrollment;
  }

  /**
   * Android Device Owner QR provisioning payload. Scanned during device setup
   * to install the DPC with the customer's on-screen consent — no bypass.
   * See docs/device-enrollment.md.
   */
  private buildQrPayload(token: string) {
    const apkLocation = this.config.get<string>(
      'DPC_APK_URL',
      'https://admin.deencircle.online/financekey/apk/finance-key.apk',
    );
    return {
      'android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME':
        DPC_COMPONENT,
      'android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION':
        apkLocation,
      'android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE': {
        'com.financesuperkey.PROVISIONING_TOKEN': token,
        'com.financesuperkey.API_BASE':
          this.config.get<string>('PUBLIC_API_BASE', 'https://api.financesuperkey.app/v1'),
      },
      'android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED': true,
    };
  }

  private hashImei(imei: string): string {
    return createHash('sha256').update(imei.trim()).digest('hex');
  }

  private maskImei(imei: string): string {
    const clean = imei.replace(/\s+/g, '');
    if (clean.length <= 4) return clean;
    return `${clean.slice(0, 2)}••• ••••• ${clean.slice(-4)}`;
  }
}
