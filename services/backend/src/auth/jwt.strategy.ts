import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { PrismaService } from '../prisma/prisma.service';
import {
  ADMIN_ROLE_PERMISSIONS,
  PermissionValue,
  STAFF_ROLE_PERMISSIONS,
} from '../common/permissions';
import { AuthUser, JwtPayload } from '../common/types';

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    config: ConfigService,
    private readonly prisma: PrismaService,
  ) {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: config.getOrThrow<string>('JWT_SECRET'),
    });
  }

  /**
   * Runs for every authenticated request. Re-resolves the user's effective
   * permissions from the database so revoked access takes effect immediately
   * (the token only carries identity, never the permission set).
   */
  async validate(payload: JwtPayload): Promise<AuthUser> {
    const user = await this.prisma.user.findUnique({
      where: { id: payload.sub },
      include: { adminRoles: true, staffProfile: true },
    });
    if (!user || user.status !== 'active') {
      throw new UnauthorizedException('Account not active');
    }

    const isSuperAdmin = user.adminRoles.some((r) => r.role === 'super_admin');
    const permissions = new Set<PermissionValue>();

    for (const { role } of user.adminRoles) {
      for (const p of ADMIN_ROLE_PERMISSIONS[role] ?? []) permissions.add(p);
    }

    if (user.staffProfile && user.staffProfile.status === 'active') {
      const explicit = user.staffProfile.permissions as unknown;
      if (Array.isArray(explicit) && explicit.length > 0) {
        for (const p of explicit) permissions.add(p as PermissionValue);
      } else {
        for (const p of STAFF_ROLE_PERMISSIONS[user.staffProfile.role] ?? []) {
          permissions.add(p);
        }
      }
    }

    return {
      id: user.id,
      userType: user.userType,
      merchantId: user.staffProfile?.merchantId ?? payload.merchantId ?? null,
      permissions: [...permissions],
      isSuperAdmin,
    };
  }
}
