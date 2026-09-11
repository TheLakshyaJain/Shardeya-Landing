import { apiFetch } from '@/lib/api/client';
import type {
  NotificationPreferenceRow,
  NotificationPreferenceUpdateRequest,
  WhatsAppOptInChallengeResponse,
  WhatsAppOptInStatusResponse,
} from '../types';

export function getPreferenceMatrix(): Promise<NotificationPreferenceRow[]> {
  return apiFetch('/settings/notifications');
}

export function updatePreferenceMatrix(updates: NotificationPreferenceUpdateRequest[]): Promise<void> {
  return apiFetch('/settings/notifications', { method: 'PUT', body: updates });
}

export function startWhatsAppOptIn(): Promise<WhatsAppOptInChallengeResponse> {
  return apiFetch('/settings/whatsapp/opt-in/start', { method: 'POST' });
}

export function confirmWhatsAppOptIn(req: { challengeId: string; code: string }): Promise<void> {
  return apiFetch('/settings/whatsapp/opt-in/confirm', { method: 'POST', body: req });
}

export function optOutWhatsApp(): Promise<void> {
  return apiFetch('/settings/whatsapp/opt-out', { method: 'POST' });
}

export function getWhatsAppOptInStatus(): Promise<WhatsAppOptInStatusResponse> {
  return apiFetch('/settings/whatsapp/opt-in-status');
}
