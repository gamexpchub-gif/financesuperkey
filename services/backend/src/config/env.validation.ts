import { plainToInstance } from 'class-transformer';
import { IsInt, IsOptional, IsString, Min, validateSync } from 'class-validator';

/**
 * Validates environment variables at boot. If a required var is missing or
 * malformed the app refuses to start, instead of failing later at runtime.
 */
export class EnvironmentVariables {
  @IsString()
  DATABASE_URL!: string;

  @IsString()
  JWT_SECRET!: string;

  @IsString()
  JWT_REFRESH_SECRET!: string;

  @IsOptional()
  @IsString()
  JWT_ACCESS_TTL: string = '15m';

  @IsOptional()
  @IsString()
  JWT_REFRESH_TTL: string = '30d';

  @IsOptional()
  @IsInt()
  @Min(1)
  PORT: number = 3000;

  @IsOptional()
  @IsString()
  REDIS_URL?: string;
}

export function validateEnv(config: Record<string, unknown>): EnvironmentVariables {
  const validated = plainToInstance(EnvironmentVariables, config, {
    enableImplicitConversion: true,
  });
  const errors = validateSync(validated, { skipMissingProperties: false });
  if (errors.length > 0) {
    throw new Error(
      'Invalid environment configuration:\n' +
        errors.map((e) => `  - ${Object.values(e.constraints ?? {}).join(', ')}`).join('\n'),
    );
  }
  return validated;
}
