package com.shardeya.foundation.auth.dto;

// No refreshToken field -- it never touches JS/JSON at all. It travels only
// as an httpOnly Set-Cookie header (see AuthController's cookie helpers),
// which is precisely what keeps it unreadable to any XSS payload per
// CLAUDE.md rule #15. AuthService.TokenIssueResult is the internal-only
// carrier that still knows the raw value, strictly for the controller layer
// to write into the cookie -- it never becomes part of this response body.
public record AuthTokensResponse(
        String accessToken, long expiresIn, UserSummary user, OrgSummary org) {
}
