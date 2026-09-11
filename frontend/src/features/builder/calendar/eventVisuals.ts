import { IndianRupee, MapPin, PhoneCall, Star, Users, type LucideIcon } from 'lucide-react';
import type { CalendarEventType } from './types';

// Same accessibility requirement CLAUDE.md already established for the
// plot grid (colour + hatch pattern, never colour alone) applied to a list
// context: every event type gets a distinct ICON as well as a distinct
// colour, so the type reads even in greyscale/colour-blind conditions, not
// just from the tint. Colours themselves are deliberately muted
// tint-on-white (bg-*-50/text-*-700) rather than the loud solid
// bg-*-100/border pairing the old version used for the whole row -- a full
// list of solid-tinted rows read as noisy at 360px; a small coloured icon
// chip carries the same information with far less visual weight.
export const EVENT_TYPE_ICONS: Record<CalendarEventType, LucideIcon> = {
  FOLLOW_UP: PhoneCall,
  SITE_VISIT: MapPin,
  INSTALMENT_DUE: IndianRupee,
  MANUAL_MEETING: Users,
  IMPORTANT_DATE: Star,
};

export const EVENT_TYPE_STYLES: Record<CalendarEventType, { chip: string; icon: string; accent: string }> = {
  FOLLOW_UP: { chip: 'bg-blue-50 dark:bg-blue-950', icon: 'text-blue-600 dark:text-blue-400', accent: 'border-l-blue-500' },
  SITE_VISIT: { chip: 'bg-orange-50 dark:bg-orange-950', icon: 'text-orange-600 dark:text-orange-400', accent: 'border-l-orange-500' },
  INSTALMENT_DUE: { chip: 'bg-green-50 dark:bg-green-950', icon: 'text-green-600 dark:text-green-400', accent: 'border-l-green-500' },
  MANUAL_MEETING: { chip: 'bg-purple-50 dark:bg-purple-950', icon: 'text-purple-600 dark:text-purple-400', accent: 'border-l-purple-500' },
  IMPORTANT_DATE: { chip: 'bg-amber-50 dark:bg-amber-950', icon: 'text-amber-600 dark:text-amber-400', accent: 'border-l-amber-500' },
};

/**
 * `ScheduleService.syncInstalmentProjection` (backend) bakes the amount
 * directly into the auto-projected event's plain-string `title` (e.g.
 * `"Instalment due: 500000.00 (2nd Instalment)"`) -- a known, documented
 * gap (CLAUDE.md M4 notes: auto-projection titles are hardcoded strings,
 * not a structured key+params shape). This is a presentation-only visual
 * pass with no backend changes, so rather than touch that title-generation
 * code, the amount is parsed back out of the existing string here purely
 * for display -- falls back to showing the raw title untouched if the
 * format ever changes, never throws.
 */
export function parseInstalmentTitle(title: string): { amount: number; label: string } | null {
  const match = /^Instalment due:\s*([\d.]+)\s*\(([^)]*)\)$/.exec(title);
  if (!match) return null;
  const amount = Number(match[1]);
  if (Number.isNaN(amount)) return null;
  return { amount, label: match[2] };
}

/**
 * `CustomerService.upsertFollowUpEvent` (backend) bakes the same
 * "Follow-up: " prefix into a FOLLOW_UP auto-projection's title -- purely
 * redundant once the event-type label already renders as its own line
 * right below (see EventCard). Stripped for display only, same
 * presentation-only reasoning as {@link parseInstalmentTitle}; falls back
 * to the untouched title if the prefix isn't there.
 */
export function stripKnownPrefix(eventType: CalendarEventType, title: string): string {
  if (eventType === 'FOLLOW_UP' && title.startsWith('Follow-up: ')) {
    return title.slice('Follow-up: '.length);
  }
  return title;
}
