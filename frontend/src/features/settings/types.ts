// Mirrors backend/src/main/java/com/shardeya/foundation/notification/dto exactly.

export interface NotificationPreferenceRow {
  typeCode: string;
  category: string;
  mandatory: boolean;
  inApp: boolean;
  whatsapp: boolean;
  sms: boolean;
  email: boolean;
}

export interface NotificationPreferenceUpdateRequest {
  typeCode: string;
  inApp: boolean;
  whatsapp: boolean;
  sms: boolean;
  email: boolean;
}

export interface WhatsAppOptInChallengeResponse {
  challengeId: string;
  maskedMobile: string;
  resendAfterSeconds: number;
}

export interface WhatsAppOptInStatusResponse {
  mobile: string;
  optedIn: boolean;
}
