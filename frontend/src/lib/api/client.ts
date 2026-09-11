import { useAuthStore } from '@/features/auth/store/authStore';
import type { AuthTokensResponse } from '@/features/auth/types';

// No trailing slash; every call site starts its path with "/".
const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export interface ApiFieldError {
  field: string | null;
  code: string;
  messageKey: string;
  params: Record<string, unknown>;
}

// RFC 9457 Problem Details + errors[] — 00-ARCHITECTURE.md §4.9 / CLAUDE.md
// API Conventions. Every handler in GlobalExceptionHandler returns this shape.
export class ApiError extends Error {
  readonly status: number;
  readonly errors: ApiFieldError[];

  constructor(status: number, errors: ApiFieldError[]) {
    super(errors[0]?.messageKey ?? 'error.internal');
    this.status = status;
    this.errors = errors.length > 0 ? errors : [{ field: null, code: 'UNKNOWN', messageKey: 'error.internal', params: {} }];
  }

  get messageKey(): string {
    return this.errors[0].messageKey;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  /** Skip attaching the access token / refresh-on-401 handling — for the public auth endpoints themselves. */
  anonymous?: boolean;
  idempotencyKey?: string;
}

// Refresh-on-401 is only ever meaningful for a request that carried a token
// in the first place; a 401 from e.g. a bad /auth/login attempt must never
// trigger it. Concurrent 401s share one in-flight refresh via this promise
// instead of each firing their own /auth/refresh call. The same function
// also backs the boot-time silent-refresh attempt (features/auth/bootstrap.ts)
// — there's no client-visible signal for "does a refresh_token cookie
// exist," so both callers just always attempt the call and read the result.
let refreshPromise: Promise<boolean> | null = null;

export async function refreshSession(): Promise<boolean> {
  if (refreshPromise) return refreshPromise;

  refreshPromise = (async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
        method: 'POST',
        credentials: 'include', // the refresh_token httpOnly cookie rides along automatically; no body needed
      });
      if (!res.ok) return false;

      // The refresh response now always carries real user/org (previously
      // null — nothing needed them, since a hard reload always logged
      // everyone out before this fix). setSession (not just the access
      // token) is what lets a boot-time restore satisfy RequireAuth's
      // `accessToken && org` check immediately, with no separate /me
      // round trip required just to unblock routing.
      const tokens = (await res.json()) as AuthTokensResponse;
      useAuthStore.getState().setSession(tokens);
      return true;
    } catch {
      return false;
    }
  })();

  try {
    return await refreshPromise;
  } finally {
    refreshPromise = null;
  }
}

async function parseError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as { errors?: ApiFieldError[] };
    return new ApiError(res.status, body.errors ?? []);
  } catch {
    return new ApiError(res.status, []);
  }
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}, isRetry = false): Promise<T> {
  const { method = 'GET', body, anonymous = false, idempotencyKey } = options;

  const headers = new Headers({ 'Content-Type': 'application/json' });
  const accessToken = useAuthStore.getState().accessToken;
  if (!anonymous && accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  }
  if (idempotencyKey) {
    headers.set('Idempotency-Key', idempotencyKey);
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    // Harmless on every non-auth call (the refresh_token cookie is scoped to
    // /api/v1/auth and won't be attached anyway) and required on /auth/login,
    // /auth/otp/verify, /auth/logout — anonymous:true calls still need the
    // Set-Cookie the server sends back to actually be honoured by the browser.
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && !anonymous && accessToken && !isRetry) {
    const refreshed = await refreshSession();
    if (refreshed) {
      return apiFetch<T>(path, options, true);
    }
    useAuthStore.getState().markSessionExpired();
    throw await parseError(res);
  }

  if (!res.ok) {
    throw await parseError(res);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  // A void-returning @PostMapping/@PatchMapping controller method (no
  // ResponseEntity, no explicit status) gets Spring MVC's default 200 OK
  // with a genuinely empty body -- NOT 204, which only the "204" check
  // above catches. Calling res.json() on that empty body throws (
  // "Unexpected end of JSON input"), silently rejecting the whole
  // call's promise before it ever reaches the caller's .then/onSuccess --
  // no error surfaces anywhere, the button/action just looks like it did
  // nothing. Found via NotificationController.markAllRead()/markRead(),
  // both exactly this shape (200, empty body): the backend correctly
  // persisted the change every time (confirmed directly against the
  // database and via curl), but the frontend mutation's onSuccess (which
  // invalidates the query that would refresh the UI) never ran. Reading
  // the body as text first and treating an empty string as "no content"
  // protects every current and future endpoint with this exact shape, not
  // just these two.
  const text = await res.text();
  if (text === '') {
    return undefined as T;
  }
  return JSON.parse(text) as T;
}
