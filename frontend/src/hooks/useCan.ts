import { useAuthStore } from '@/features/auth/store/authStore';

// UI hiding is cosmetic (CLAUDE.md rule #5) — every endpoint this gates
// still enforces the same permission server-side via @RequiresPermission.
// This hook only avoids showing controls the user's own /me permissions
// list says they can't use anyway.
export function useCan(permission: string): boolean {
  return useAuthStore((s) => s.permissions.includes(permission));
}
