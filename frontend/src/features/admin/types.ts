// Mirrors backend/src/main/java/com/shardeya/foundation/admin/dto exactly.
// M-14 stand-in -- see CLAUDE.md "Post-M7 -- Minimal Ops Visibility".

export type MessageChannel = 'WHATSAPP' | 'SMS' | 'EMAIL';
export type MessageStatus = 'QUEUED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';

export interface MessageDeliveryLogRow {
  id: string;
  channel: MessageChannel;
  recipientMasked: string;
  templateCode: string | null;
  provider: string;
  status: MessageStatus;
  errorCode: string | null;
  sentAt: string | null;
  createdAt: string;
}

export interface AppErrorLogRow {
  id: string;
  occurredAt: string;
  httpMethod: string;
  path: string;
  exceptionClass: string;
  message: string | null;
  platformLevel: boolean;
}
