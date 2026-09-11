import { z } from 'zod';
import type { TFunction } from 'i18next';

// Every message here is sourced from the same "errors" namespace the
// backend's {messageKey} maps to (see lib/api/errorMessage.ts) — client-side
// and server-side validation errors read identically. Regexes mirror
// backend/.../dto/*.java exactly; keep the two in sync by hand.
const e = (t: TFunction, key: string) => t(key, { ns: 'errors' });

export function buildSignupSchema(t: TFunction) {
  return z
    .object({
      fullName: z
        .string()
        .min(1, e(t, 'name.required'))
        // \p{L} alone (Unicode "Letter") excludes Devanagari's combining
        // vowel signs and virama (ी/ो/ा/् etc.) — those are category Mark,
        // not Letter, so almost every real Hindi name was being rejected.
        // \p{M} fixes it; mirrors the same fix in the backend's DTOs.
        .regex(/^[\p{L}\p{M} .'-]{2,100}$/u, e(t, 'name.invalid')),
      mobile: z.string().regex(/^[6-9]\d{9}$/, e(t, 'mobile.length')),
      email: z
        .string()
        .min(1, e(t, 'email.invalid'))
        .max(255, e(t, 'email.invalid'))
        .email(e(t, 'email.invalid')),
      password: z.string().regex(/^(?=.*[A-Za-z])(?=.*\d).{8,}$/, e(t, 'password.weak')),
      confirmPassword: z.string().min(1, e(t, 'password.mismatch')),
      city: z.string().min(2, e(t, 'city.required')).max(100, e(t, 'city.required')),
      acceptTerms: z.boolean().refine((v) => v === true, { message: e(t, 'terms.required') }),
    })
    .refine((data) => data.password === data.confirmPassword, {
      path: ['confirmPassword'],
      message: e(t, 'password.mismatch'),
    });
}
export type SignupFormValues = z.infer<ReturnType<typeof buildSignupSchema>>;

export function buildOtpSchema(t: TFunction) {
  return z.object({
    code: z.string().regex(/^\d{6}$/, e(t, 'otp.invalid')),
  });
}
export type OtpFormValues = z.infer<ReturnType<typeof buildOtpSchema>>;

export function buildLoginSchema(t: TFunction) {
  return z.object({
    identifier: z.string().min(1, e(t, 'auth.identifierRequired')),
    password: z.string().min(1, e(t, 'auth.passwordRequired')),
    rememberMe: z.boolean(),
  });
}
export type LoginFormValues = z.infer<ReturnType<typeof buildLoginSchema>>;

// Flexible mobile-or-email, same validation as buildForgotPasswordSchema's
// own identifier field -- "login with OTP" resolves via whichever matches,
// exactly like password login and forgot-password already do.
export function buildLoginOtpSchema(t: TFunction) {
  return z.object({
    identifier: z.string().min(1, e(t, 'auth.identifierRequired')),
  });
}
export type LoginOtpFormValues = z.infer<ReturnType<typeof buildLoginOtpSchema>>;

export function buildForgotPasswordSchema(t: TFunction) {
  return z.object({
    identifier: z.string().min(1, e(t, 'auth.identifierRequired')),
  });
}
export type ForgotPasswordFormValues = z.infer<ReturnType<typeof buildForgotPasswordSchema>>;

export function buildResetPasswordSchema(t: TFunction) {
  return z
    .object({
      newPassword: z.string().regex(/^(?=.*[A-Za-z])(?=.*\d).{8,}$/, e(t, 'password.weak')),
      confirmPassword: z.string().min(1, e(t, 'password.mismatch')),
    })
    .refine((data) => data.newPassword === data.confirmPassword, {
      path: ['confirmPassword'],
      message: e(t, 'password.mismatch'),
    });
}
export type ResetPasswordFormValues = z.infer<ReturnType<typeof buildResetPasswordSchema>>;
