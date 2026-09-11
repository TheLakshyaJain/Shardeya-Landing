// Mirrors backend/src/main/java/com/shardeya/foundation/auth/dto exactly —
// keep these two in sync by hand (no shared-schema codegen in this project yet).

export type OrgType = 'BUILDER' | 'BROKER';

export interface SignupRequest {
  fullName: string;
  mobile: string;
  email: string;
  password: string;
  confirmPassword: string;
  role: OrgType;
  city: string;
  acceptTerms: boolean;
}

// maskedRecipient is a masked email for signup/login-OTP (Email OTP fix)
// and a masked mobile for forgot-password's mobile branch (unchanged, still SMS).
export interface SignupResponse {
  challengeId: string;
  maskedRecipient: string;
  resendAfterSeconds: number;
}

export interface OtpVerifyRequest {
  challengeId: string;
  code: string;
}

export interface OtpResendRequest {
  challengeId: string;
}

export interface LoginRequest {
  identifier: string;
  password: string;
  rememberMe: boolean;
}

// Flexible mobile-or-email, same shape as LoginRequest.identifier -- see
// AuthService.requestLoginOtp's own comment for why this isn't email-only.
export interface LoginOtpRequestRequest {
  identifier: string;
}

export interface ForgotPasswordRequest {
  identifier: string;
}

export interface ResetPasswordRequest {
  token?: string;
  challengeId?: string;
  code?: string;
  newPassword: string;
  confirmPassword: string;
}

export interface UserSummary {
  id: string;
  fullName: string;
  mobile: string;
  email: string | null;
  role: string;
  isOwner: boolean;
  language: string;
}

export interface OrgSummary {
  id: string;
  type: OrgType;
  name: string;
  city: string;
}

// No refreshToken field, ever -- it travels exclusively as an httpOnly
// Set-Cookie header (backend AuthController), never in a JSON body JS could
// read. See CLAUDE.md rule #15 / the httpOnly-refresh-cookie fix writeup.
export interface AuthTokensResponse {
  accessToken: string;
  expiresIn: number;
  user: UserSummary;
  org: OrgSummary;
}

export interface EntitlementsSummary {
  plan: string;
}

export interface MeResponse {
  user: UserSummary;
  org: OrgSummary;
  permissions: string[];
  entitlements: EntitlementsSummary;
  unreadCount: number;
}

export interface UpdateProfileRequest {
  fullName?: string;
  email?: string;
  city?: string;
  language?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
