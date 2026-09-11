// Mirrors backend/src/main/java/com/shardeya/builder/team/dto exactly.

export type StaffRoleCode = 'MANAGER' | 'SALES_EXECUTIVE' | 'ACCOUNTS_STAFF' | 'VIEW_ONLY';
export const STAFF_ROLE_CODES: StaffRoleCode[] = ['MANAGER', 'SALES_EXECUTIVE', 'ACCOUNTS_STAFF', 'VIEW_ONLY'];

export interface TeamMemberResponse {
  id: string;
  fullName: string;
  mobile: string;
  email?: string;
  roleCode: string;
  status: 'INVITED' | 'ACTIVE' | 'INACTIVE' | 'REMOVED';
  owner: boolean;
  allProjects: boolean;
  projectAccess: string[];
  lastLoginAt?: string;
  createdAt: string;
  inviteStatus?: 'PENDING' | 'ACCEPTED' | 'EXPIRED';
  inviteSentAt?: string;
}

export interface TeamMemberCreateRequest {
  fullName: string;
  mobile: string;
  email?: string;
  roleCode: string;
  projectAccess?: string[];
  sendInvite: boolean;
}

export interface TeamMemberUpdateRequest {
  fullName?: string;
  email?: string;
  roleCode?: string;
  projectAccess?: string[];
  allProjects?: boolean;
}

export interface DeactivateRequest {
  reason?: string;
  reassignToUserId?: string;
  leaveUnassigned: boolean;
}

export interface StaffActivityResponse {
  leadsAssigned: number;
  interactionsConducted: number;
  lastLoginAt?: string;
}

export interface RoleSummary {
  code: string;
  nameEn: string;
  nameHi: string;
}

export interface PermissionsMeResponse {
  permissions: string[];
  projectScope: string[];
  scopeMode: 'ALL' | 'SCOPED';
}
