// Mirrors backend/src/main/java/com/shardeya/foundation/notification/dto exactly.
export interface NotificationResponse {
  id: string;
  typeCode: string;
  titleKey: string;
  bodyKey: string | null;
  params: Record<string, unknown>;
  entityType: string | null;
  entityId: string | null;
  read: boolean;
  createdAt: string;
}
