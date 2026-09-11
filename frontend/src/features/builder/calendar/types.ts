// Mirrors backend/src/main/java/com/shardeya/foundation/calendar/dto exactly.

export type CalendarEventType = 'FOLLOW_UP' | 'SITE_VISIT' | 'INSTALMENT_DUE' | 'MANUAL_MEETING' | 'IMPORTANT_DATE';
export type CalendarEventSource = 'MANUAL' | 'AUTO';
export type CalendarEventStatus = 'SCHEDULED' | 'DONE' | 'CANCELLED';

export const CALENDAR_EVENT_TYPES: CalendarEventType[] = [
  'FOLLOW_UP', 'SITE_VISIT', 'INSTALMENT_DUE', 'MANUAL_MEETING', 'IMPORTANT_DATE',
];

export interface CalendarEventResponse {
  id: string;
  title: string;
  eventDate: string;
  eventTime?: string;
  durationMinutes?: number;
  eventType: CalendarEventType;
  source: CalendarEventSource;
  customerId?: string;
  propertyId?: string;
  projectId?: string;
  plotId?: string;
  assignedTo?: string;
  notes?: string;
  reminderEnabled: boolean;
  status: CalendarEventStatus;
  createdAt: string;
}

export interface EventCreateRequest {
  title: string;
  eventDate: string;
  eventTime?: string;
  durationMinutes?: number;
  importantDate: boolean;
  customerId?: string;
  propertyId?: string;
  projectId?: string;
  plotId?: string;
  assignedTo?: string;
  notes?: string;
  reminderEnabled: boolean;
}
