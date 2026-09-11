// CLAUDE.md rule #2: Indian grouping (₹1,23,45,678), lakh/crore. Digit
// grouping stays Latin/Arabic numerals even in the Hindi UI (standard
// Indian UX convention) — only labels are translated, not numerals.
const indianNumberFormatter = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 });
const indianCurrencyFormatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  maximumFractionDigits: 0,
});

export function formatIndianNumber(value: number): string {
  return indianNumberFormatter.format(value);
}

export function formatIndianCurrency(value: number): string {
  return indianCurrencyFormatter.format(value);
}

// Lakh/crore abbreviation for compact display (dashboard cards, stats bars)
// where the full grouped number would be too wide.
export function formatCompactIndianCurrency(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1_00_00_000) return `₹${(value / 1_00_00_000).toFixed(2)} Cr`;
  if (abs >= 1_00_000) return `₹${(value / 1_00_000).toFixed(2)} L`;
  return formatIndianCurrency(value);
}
