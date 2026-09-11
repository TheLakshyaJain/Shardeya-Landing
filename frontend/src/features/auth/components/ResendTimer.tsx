import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';

interface ResendTimerProps {
  initialSeconds: number;
  onResend: () => void;
  resendLabel: string;
  countdownLabel: (seconds: number) => string;
  disabled?: boolean;
}

// Re-keys off `initialSeconds` — the parent re-supplies the fresh
// resendAfterSeconds it gets back from the resend API call, which is what
// restarts the countdown after a successful resend.
export function ResendTimer({ initialSeconds, onResend, resendLabel, countdownLabel, disabled }: ResendTimerProps) {
  const [seconds, setSeconds] = useState(initialSeconds);

  useEffect(() => {
    setSeconds(initialSeconds);
  }, [initialSeconds]);

  useEffect(() => {
    if (seconds <= 0) return;
    const id = setInterval(() => setSeconds((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(id);
  }, [seconds]);

  if (seconds > 0) {
    return <p className="text-sm text-muted-foreground">{countdownLabel(seconds)}</p>;
  }

  return (
    <Button type="button" variant="link" className="h-auto p-0 text-sm" onClick={onResend} disabled={disabled}>
      {resendLabel}
    </Button>
  );
}
