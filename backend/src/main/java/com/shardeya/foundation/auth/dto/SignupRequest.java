package com.shardeya.foundation.auth.dto;

import com.shardeya.foundation.auth.Organization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Field validations mirror 02-FOUNDATION-MODULES.md M-01 §11 exactly, including its messageKey column. */
public record SignupRequest(
        @NotBlank(message = "error.name.required")
        @Size(min = 2, max = 100, message = "error.name.invalid")
        // \p{L} alone (Unicode "Letter") excludes Devanagari's combining vowel
        // signs and virama (matras like ी/ो/ा, and ्) — those are Unicode
        // category Mark, not Letter, so this rejected almost every real Hindi
        // name until \p{M} was added. Caught by driving signup through a real
        // browser with the UI already switched to Hindi (name field filled
        // with a Devanagari name) — no unit test had ever exercised a
        // non-ASCII name.
        @Pattern(regexp = "^[\\p{L}\\p{M} .'-]+$", message = "error.name.invalid")
        String fullName,

        @NotBlank(message = "error.mobile.length")
        @Pattern(regexp = "^[6-9]\\d{9}$", message = "error.mobile.length")
        String mobile,

        @NotBlank(message = "error.email.invalid")
        @Email(message = "error.email.invalid")
        @Size(max = 255, message = "error.email.invalid")
        String email,

        @NotBlank(message = "error.password.weak")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$", message = "error.password.weak")
        String password,

        @NotBlank(message = "error.password.mismatch")
        String confirmPassword,

        @NotNull(message = "error.role.required")
        Organization.Type role,

        @NotBlank(message = "error.city.required")
        @Size(min = 2, max = 100, message = "error.city.required")
        String city,

        @AssertTrue(message = "error.terms.required")
        boolean acceptTerms
) {
}
