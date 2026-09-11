import type { OrgType } from './types';

export function dashboardPathFor(orgType: OrgType): string {
  return orgType === 'BUILDER' ? '/builder/dashboard' : '/broker/dashboard';
}
