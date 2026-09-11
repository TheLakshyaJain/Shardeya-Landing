import i18n from '@/i18n';
import { ApiError, type ApiFieldError } from './client';

// Every backend messageKey is "error.<dot.path>" (CLAUDE.md rule #14: the
// backend only ever sends {messageKey, params}, never a rendered sentence).
// The "errors" i18n namespace mirrors that dot-path with the "error." prefix
// stripped, so any current or future backend key resolves here with no
// manual mapping table to keep in sync.
export function resolveMessageKey(messageKey: string, params: Record<string, unknown> = {}): string {
  const key = messageKey.startsWith('error.') ? messageKey.slice('error.'.length) : messageKey;
  if (i18n.exists(key, { ns: 'errors' })) {
    return i18n.t(key, { ns: 'errors', ...params });
  }
  return i18n.t('generic', { ns: 'errors' });
}

export function resolveErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return resolveMessageKey(error.messageKey, error.errors[0].params);
  }
  return i18n.t('generic', { ns: 'errors' });
}

export function resolveRowError(rowError: ApiFieldError): string {
  return resolveMessageKey(rowError.messageKey, rowError.params);
}

export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  const result: Record<string, string> = {};
  for (const e of error.errors) {
    if (!e.field) continue;
    result[e.field] = resolveMessageKey(e.messageKey, e.params);
  }
  return result;
}
