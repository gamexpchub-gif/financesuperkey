import {
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import * as argon2 from 'argon2';
import * as crypto from 'crypto';
import { PrismaService } from '../prisma/prisma.service';
import { JwtPayload } from '../common/types';
import { LoginDto } from './dto/auth.dto';

@Injectable()
export class AuthService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
  ) {}

  async login(dto: LoginDto) {
    const user = await this.prisma.user.findFirst({
      where: {
        OR: [{ phone: dto.identifier }, { email: dto.identifier }],
      },
      include: { staffProfile: true },
    });
    if (!user || user.status !== 'active') {
      throw new UnauthorizedException('Invalid credentials');
    }

    const ok = await argon2.verify(user.passwordHash, dto.password);
    if (!ok) throw new UnauthorizedException('Invalid credentials');

    const payload: JwtPayload = {
      sub: user.id,
      userType: user.userType,
      merchantId: user.staffProfile?.merchantId ?? null,
    };
    return this.issueTokens(payload);
  }

  async refresh(refreshToken: string) {
    let payload: JwtPayload;
    try {
      payload = await this.jwt.verifyAsync<JwtPayload>(refreshToken, {
        secret: this.config.getOrThrow<string>('JWT_REFRESH_SECRET'),
      });
    } catch {
      throw new UnauthorizedException('Invalid refresh token');
    }

    const tokenHash = this.hash(refreshToken);
    const stored = await this.prisma.refreshToken.findFirst({
      where: { userId: payload.sub, tokenHash, revokedAt: null },
    });
    if (!stored || stored.expiresAt < new Date()) {
      throw new UnauthorizedException('Refresh token expired or revoked');
    }

    // Rotate: revoke the used token, issue a fresh pair.
    await this.prisma.refreshToken.update({
      where: { id: stored.id },
      data: { revokedAt: new Date() },
    });
    return this.issueTokens({
      sub: payload.sub,
      userType: payload.userType,
      merchantId: payload.merchantId,
    });
  }

  async logout(refreshToken: string): Promise<void> {
    const tokenHash = this.hash(refreshToken);
    await this.prisma.refreshToken.updateMany({
      where: { tokenHash, revokedAt: null },
      data: { revokedAt: new Date() },
    });
  }

  private async issueTokens(payload: JwtPayload) {
    const access = await this.jwt.signAsync(payload, {
      secret: this.config.getOrThrow<string>('JWT_SECRET'),
      expiresIn: this.config.get<string>('JWT_ACCESS_TTL', '15m'),
    });
    const refreshTtl = this.config.get<string>('JWT_REFRESH_TTL', '30d');
    const refresh = await this.jwt.signAsync(payload, {
      secret: this.config.getOrThrow<string>('JWT_REFRESH_SECRET'),
      expiresIn: refreshTtl,
    });

    await this.prisma.refreshToken.create({
      data: {
        userId: payload.sub,
        tokenHash: this.hash(refresh),
        expiresAt: this.ttlToDate(refreshTtl),
      },
    });

    return {
      access,
      refresh,
      user: {
        id: payload.sub,
        userType: payload.userType,
        merchantId: payload.merchantId,
      },
    };
  }

  private hash(token: string): string {
    return crypto.createHash('sha256').update(token).digest('hex');
  }

  /** Converts a short TTL string (e.g. "30d", "15m", "12h") to a future Date. */
  private ttlToDate(ttl: string): Date {
    const match = /^(\d+)([smhd])$/.exec(ttl.trim());
    const now = Date.now();
    if (!match) return new Date(now + 30 * 24 * 60 * 60 * 1000);
    const value = Number(match[1]);
    const unit = match[2];
    const ms =
      unit === 's'
        ? value * 1000
        : unit === 'm'
          ? value * 60_000
          : unit === 'h'
            ? value * 3_600_000
            : value * 86_400_000;
    return new Date(now + ms);
  }
}
