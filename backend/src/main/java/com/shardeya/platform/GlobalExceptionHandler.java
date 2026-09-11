package com.shardeya.platform;

import com.shardeya.foundation.admin.AppErrorLog;
import com.shardeya.foundation.admin.AppErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every error response is RFC 9457 Problem Details + an {@code errors[]} array of
 * {@link ApiError} (00-ARCHITECTURE.md §4.9). Never a rendered sentence.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final AppErrorLogRepository errorLogRepository;
    // PROPAGATION_REQUIRES_NEW: the exception being handled may have come
    // from a transaction that's now marked rollback-only -- writing this
    // row through the ambient transaction (if any) would either fail
    // outright or silently vanish when that transaction rolls back. Same
    // "must survive regardless of the ambient transaction's fate" reasoning
    // OutboxPoller's own constructor already documents.
    private final TransactionTemplate independentTransaction;

    public GlobalExceptionHandler(AppErrorLogRepository errorLogRepository, PlatformTransactionManager transactionManager) {
        this.errorLogRepository = errorLogRepository;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toApiError)
                .toList();

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setProperty("errors", List.of(new ApiError(null, "NOT_FOUND", ex.messageKey(), Map.of())));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Unauthorized");
        problem.setProperty("errors", List.of(new ApiError(null, "UNAUTHORIZED", ex.messageKey(), Map.of())));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Forbidden");
        problem.setProperty("errors", List.of(new ApiError(null, "FORBIDDEN", ex.messageKey(), Map.of())));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Bad request");
        problem.setProperty("errors", ex.errors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflict");
        problem.setProperty("errors", List.of(new ApiError(null, "CONFLICT", ex.messageKey(), ex.params())));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ProblemDetail> handleQuotaExceeded(QuotaExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Quota exceeded");
        Map<String, Object> params = Map.of("limitKey", ex.limitKey(), "used", ex.used(), "limit", ex.limit());
        problem.setProperty("errors", List.of(new ApiError(null, "QUOTA_EXCEEDED", ex.messageKey(), params)));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(FeatureNotEnabledException.class)
    public ResponseEntity<ProblemDetail> handleFeatureNotEnabled(FeatureNotEnabledException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Feature not enabled");
        Map<String, Object> params = Map.of("featureKey", ex.featureKey());
        problem.setProperty("errors", List.of(new ApiError(null, "FEATURE_NOT_ENABLED", ex.messageKey(), params)));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
        problem.setTitle("Too many requests");
        problem.setProperty("errors", List.of(new ApiError(null, "TOO_MANY_REQUESTS", ex.messageKey(), ex.params())));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Every OTHER handler in this class maps a well-known, expected
        // exception type to a specific status/messageKey. This one is the
        // catch-all for everything else — by definition a bug or an
        // unhandled edge case, so it must never fail silently. Losing this
        // log line once already cost real debugging time: several
        // AuthFlowIntegrationTest failures showed only "500" with no way to
        // tell why until this was added.
        log.error("Unhandled exception", ex);
        recordError(ex, request);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Unexpected error");
        problem.setProperty("errors", List.of(new ApiError(null, "INTERNAL_ERROR", "error.internal", Map.of())));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // M-14 stand-in (CLAUDE.md "Post-M7 -- Minimal Ops Visibility"): only
    // the catch-all above ever calls this -- every OTHER handler in this
    // class maps an EXPECTED condition (validation, not-found, quota, etc.)
    // to a real status, and none of those are "an error we'd want to be
    // paged for." Wrapped in its own try/catch: a failure to LOG the error
    // must never itself become a second, masking exception.
    private void recordError(Exception ex, HttpServletRequest request) {
        try {
            TenantContext.Tenant tenant = TenantContext.currentOrNull();
            UUID orgId = tenant != null ? tenant.orgId() : null;
            UUID userId = tenant != null ? tenant.userId() : null;
            String message = ex.getMessage();
            String truncated = message == null ? null : message.substring(0, Math.min(message.length(), MAX_MESSAGE_LENGTH));
            independentTransaction.executeWithoutResult(status -> errorLogRepository.save(
                    new AppErrorLog(UUID.randomUUID(), orgId, userId, request.getMethod(), request.getRequestURI(),
                            ex.getClass().getName(), truncated)));
        } catch (Exception loggingFailure) {
            log.warn("Failed to persist app_error_log entry", loggingFailure);
        }
    }

    private ApiError toApiError(FieldError fieldError) {
        // Bean Validation annotations set message="error.xxx.yyy" directly
        // (01-DATA-MODEL.md / M-01 §11's validation table names the exact
        // keys) — getDefaultMessage() returns that literal string, not a
        // resolved bundle lookup, which is exactly the {messageKey, params}
        // contract CLAUDE.md rule #14 requires (never a rendered sentence).
        return new ApiError(fieldError.getField(), fieldError.getCode(), fieldError.getDefaultMessage(), Map.of());
    }
}
