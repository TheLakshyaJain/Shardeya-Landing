import { apiFetch } from '@/lib/api/client';
import type {
  AuthTokensResponse,
  ChangePasswordRequest,
  ForgotPasswordRequest,
  LoginOtpRequestRequest,
  LoginRequest,
  MeResponse,
  OtpResendRequest,
  OtpVerifyRequest,
  ResetPasswordRequest,
  SignupRequest,
  SignupResponse,
  UpdateProfileRequest,
  UserSummary,
} from '../types';

export function signup(req: SignupRequest): Promise<SignupResponse> {
  return apiFetch('/auth/signup', { method: 'POST', body: req, anonymous: true });
}

export function verifySignupOtp(req: OtpVerifyRequest): Promise<AuthTokensResponse> {
  return apiFetch('/auth/otp/verify', { method: 'POST', body: req, anonymous: true });
}

export function resendOtp(req: OtpResendRequest): Promise<SignupResponse> {
  return apiFetch('/auth/otp/resend', { method: 'POST', body: req, anonymous: true });
}

export function login(req: LoginRequest): Promise<AuthTokensResponse> {
  return apiFetch('/auth/login', { method: 'POST', body: req, anonymous: true });
}

export function requestLoginOtp(req: LoginOtpRequestRequest): Promise<SignupResponse> {
  return apiFetch('/auth/login/otp/request', { method: 'POST', body: req, anonymous: true });
}

export function verifyLoginOtp(req: OtpVerifyRequest): Promise<AuthTokensResponse> {
  return apiFetch('/auth/login/otp/verify', { method: 'POST', body: req, anonymous: true });
}

export function forgotPassword(req: ForgotPasswordRequest): Promise<SignupResponse> {
  return apiFetch('/auth/password/forgot', { method: 'POST', body: req, anonymous: true });
}

export function resetPassword(req: ResetPasswordRequest): Promise<void> {
  return apiFetch('/auth/password/reset', { method: 'POST', body: req, anonymous: true });
}

// No body, no refreshToken param -- the cookie rides along automatically
// (apiFetch always sends credentials: 'include') and the backend reads/
// revokes/clears it entirely server-side.
export function logout(): Promise<void> {
  return apiFetch('/auth/logout', { method: 'POST', anonymous: true });
}

export function getMe(): Promise<MeResponse> {
  return apiFetch('/me');
}

export function updateMe(req: UpdateProfileRequest): Promise<UserSummary> {
  return apiFetch('/me', { method: 'PATCH', body: req });
}

export function changePassword(req: ChangePasswordRequest): Promise<void> {
  return apiFetch('/me/password', { method: 'POST', body: req });
}
