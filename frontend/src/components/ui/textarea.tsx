import * as React from "react"

import { cn } from "@/lib/utils"

// Not part of shadcn's generated output -- same forwardRef fix as Input.tsx
// and for the identical reason: react-hook-form's register() hands out a
// ref it uses to read the field's value; a plain (non-forwardRef) function
// component silently drops that ref, so every registered Textarea field
// reads back as undefined at validation/submit time despite visibly holding
// a value. Caught the same way Input's bug was -- driving an actual form
// (ProjectForm's Address field) through a real browser, not from a type
// error or a unit test.
const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => {
    return (
      <textarea
        ref={ref}
        data-slot="textarea"
        className={cn(
          "flex field-sizing-content min-h-16 w-full rounded-md border border-input bg-transparent px-3 py-2 text-base shadow-xs transition-[color,box-shadow] outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-destructive/20 md:text-sm dark:bg-input/30 dark:aria-invalid:ring-destructive/40",
          className
        )}
        {...props}
      />
    )
  }
)
Textarea.displayName = "Textarea"

export { Textarea }
