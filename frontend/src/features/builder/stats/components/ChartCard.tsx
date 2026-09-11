import type { ReactNode } from 'react';

interface ChartCardProps {
  title: string;
  subtitle?: string;
  children: ReactNode;
}

// B-15 §6 ChartCard: "title, subtitle, export-as-image, expand" -- export/
// expand are trimmed this round (a real, documented gap; see CLAUDE.md),
// the title/subtitle wrapper every chart shares is what's built.
export function ChartCard({ title, subtitle, children }: ChartCardProps) {
  return (
    <div className="rounded-lg border border-border bg-card p-4 shadow-sm">
      <h3 className="text-sm font-medium">{title}</h3>
      {subtitle && <p className="mb-2 text-xs text-muted-foreground">{subtitle}</p>}
      <div className="mt-2">{children}</div>
    </div>
  );
}
