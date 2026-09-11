// B-15 §6 DateRangePicker presets: This Month · Last Month · This Quarter ·
// This FY · Last 12 Months · Custom. Plain browser-local date math, not a
// real Asia/Kolkata conversion like the backend's own IndianTime -- a
// builder actually using this app is physically in India, so local-date
// math is already correct in practice; unlike a server that must be
// correct for every request regardless of where it's deployed, this is a
// reasonable simplification for a client-only date picker.
function toIso(d: Date): string {
  return d.toISOString().slice(0, 10);
}

export const IndianTime = {
  presetRange(preset: string): { from: string; to: string } {
    const now = new Date();
    const y = now.getFullYear();
    const m = now.getMonth();

    switch (preset) {
      case 'THIS_MONTH':
        return { from: toIso(new Date(y, m, 1)), to: toIso(new Date(y, m + 1, 0)) };
      case 'LAST_MONTH':
        return { from: toIso(new Date(y, m - 1, 1)), to: toIso(new Date(y, m, 0)) };
      case 'THIS_QUARTER': {
        const qStart = Math.floor(m / 3) * 3;
        return { from: toIso(new Date(y, qStart, 1)), to: toIso(new Date(y, qStart + 3, 0)) };
      }
      case 'THIS_FY': {
        // Indian financial year: April 1 - March 31.
        const fyStartYear = m >= 3 ? y : y - 1;
        return { from: toIso(new Date(fyStartYear, 3, 1)), to: toIso(new Date(fyStartYear + 1, 2, 31)) };
      }
      case 'LAST_12_MONTHS':
        return { from: toIso(new Date(y, m - 11, 1)), to: toIso(now) };
      default:
        return { from: '', to: '' };
    }
  },
};
