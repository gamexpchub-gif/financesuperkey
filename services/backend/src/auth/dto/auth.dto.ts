import { IsString, MinLength } from 'class-validator';

export class LoginDto {
  /** phone or email */
  @IsString()
  identifier!: string;

  @IsString()
  @MinLength(6)
  password!: string;
}

export class RefreshDto {
  @IsString()
  refresh!: string;
}
