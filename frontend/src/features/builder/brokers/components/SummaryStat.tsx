/** Shared label/value stat block used by every §26/§27/§29 commission-summary card (CommissionTreeTab, NetworkTab, NetworkTreePage). */
export function SummaryStat({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="text-lg font-semibold">{value}</p>
    </div>
  );
}
