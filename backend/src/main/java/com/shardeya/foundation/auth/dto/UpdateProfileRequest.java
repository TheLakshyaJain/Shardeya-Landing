package com.shardeya.foundation.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All fields optional — PATCH semantics, only supplied fields are updated. */
public record UpdateProfileRequest(
        @Size(min = 2, max = 100, message = "error.name.invalid")
        // See SignupRequest.fullName's comment: \p{M} is required alongside
        // \p{L} or Devanagari names with combining vowel signs are rejected.
        @Pattern(regexp = "^[\\p{L}\\p{M} .'-]+$", message = "error.name.invalid")
        String fullName,

        @Email(message = "error.email.invalid")
        @Size(max = 255, message = "error.email.invalid")
        String email,

        @Size(min = 2, max = 100, message = "error.city.required")
        String city,

        @Pattern(regexp = "^(en|hi)$", message = "error.language.invalid")
        String language
) {
}
