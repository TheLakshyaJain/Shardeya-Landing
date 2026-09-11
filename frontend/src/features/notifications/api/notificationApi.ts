import { apiFetch } from '@/lib/api/client';
import type { NotificationResponse } from '../types';

export function listNotifications(limit = 20): Promise<NotificationResponse[]> {
  return apiFetch(`/notifications?limit=${limit}`);
}

export function getUnreadCount(): Promise<{ count: number }> {
  return apiFetch('/notifications/unread-count');
}

export function markNotificationRead(id: string): Promise<void> {
  return apiFetch(`/notifications/${id}/read`, { method: 'POST' });
}

export function markAllNotificationsRead(): Promise<void> {
  return apiFetch('/notifications/read-all', { method: 'POST' });
}
