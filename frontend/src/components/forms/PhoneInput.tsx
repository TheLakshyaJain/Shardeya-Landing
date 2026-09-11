import * as React from 'react';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

// Indian mobile numbers only, per backend's `^[6-9]\d{9}$` validation — the
// +91 prefix is fixed chrome, not part of the submitted value.
export const PhoneInput = React.forwardRef<HTMLInputElement, React.ComponentProps<'input'>>(
  ({ className, ...props }, ref) => (
    <div
      className={cn(
        'flex h-9 items-center gap-2 rounded-md border border-input bg-transparent pl-3 shadow-xs transition-[color,box-shadow] focus-within:border-ring focus-within:ring-[3px] focus-within:ring-ring/50 has-[input[aria-invalid=true]]:border-destructive has-[input[aria-invalid=true]]:ring-destructive/20 dark:bg-input/30',
        className,
      )}
    >
      <span className="select-none text-sm text-muted-foreground">+91</span>
      <Input
        ref={ref}
        type="tel"
        inputMode="numeric"
        maxLength={10}
        autoComplete="tel-national"
        className="h-full border-0 bg-transparent px-0 shadow-none focus-visible:ring-0"
        {...props}
      />
    </div>
  ),
);
PhoneInput.displayName = 'PhoneInput';
