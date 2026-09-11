import * as React from "react"

import { cn } from "@/lib/utils"

// Not part of shadcn's generated output — added because its codegen assumes
// React 19's ref-as-prop model (see CLAUDE.md "Milestone 0" notes on the
// forwardRef warning). That gap is cosmetic for Slot-composed components
// (Button, Dialog triggers) but not here: react-hook-form's register() reads
// each field's value from the DOM node via the ref it hands out — with no
// forwardRef, that ref is silently dropped, so every registered field read
// back as undefined at submit time. Caught by driving signup through an
// actual browser (zod reported "expected string, received undefined" for
// every field despite the inputs visibly holding values).
const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        ref={ref}
        type={type}
        data-slot="input"
        className={cn(
          "h-9 w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-[color,box-shadow] outline-none selection:bg-primary selection:text-primary-foreground file:inline-flex file:h-7 file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-muted-foreground disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm dark:bg-input/30",
          "focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50",
          "aria-invalid:border-destructive aria-invalid:ring-destructive/20 dark:aria-invalid:ring-destructive/40",
          className
        )}
        {...props}
      />
    )
  }
)
Input.displayName = "Input"

export { Input }
