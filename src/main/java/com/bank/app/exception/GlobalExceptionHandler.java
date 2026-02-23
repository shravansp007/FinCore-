package com.bank.app.exception;

import com.bank.app.dto.IdempotentTransferResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Validation Failed",
                "One or more request fields failed validation",
                request
        );
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "parameter";
            errors.put(path, violation.getMessage());
        }

        ProblemDetail problem = buildProblem(
                HttpStatus.BAD_REQUEST,
                "validation-failed",
                "Validation Failed",
                "Request parameters are invalid",
                request
        );
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildProblem(
                HttpStatus.BAD_REQUEST,
                "malformed-request",
                "Malformed Request",
                "Request body is malformed or has invalid JSON format",
                request
        ));
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleUsernameNotFoundException(UsernameNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildProblem(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                ex.getMessage(),
                request
        ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildProblem(
                HttpStatus.UNAUTHORIZED,
                "invalid-credentials",
                "Invalid Credentials",
                "Invalid email or password",
                request
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildProblem(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                ex.getMessage(),
                request
        ));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildProblem(
                HttpStatus.FORBIDDEN,
                "unauthorized",
                "Unauthorized",
                ex.getMessage(),
                request
        ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        String errorType = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("insufficient balance")
                ? "insufficient-balance"
                : "bad-request";
        String errorTitle = "insufficient-balance".equals(errorType) ? "Insufficient Balance" : "Bad Request";
        return ResponseEntity.badRequest().body(buildProblem(
                HttpStatus.BAD_REQUEST,
                errorType,
                errorTitle,
                ex.getMessage(),
                request
        ));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildProblem(
                HttpStatus.CONFLICT,
                "conflict",
                "Conflict",
                ex.getMessage(),
                request
        ));
    }

    @ExceptionHandler(DuplicateTransferRequestException.class)
    public ResponseEntity<IdempotentTransferResponse> handleDuplicateTransfer(DuplicateTransferRequestException ex) {
        return ResponseEntity.ok(ex.getCachedResponse());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildProblem(
                HttpStatus.FORBIDDEN,
                "access-denied",
                "Access Denied",
                "You do not have permission to access this resource",
                request
        ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(buildProblem(
                HttpStatus.UNAUTHORIZED,
                "authentication-failed",
                "Authentication Failed",
                "Authentication is required to access this resource",
                request
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildProblem(
                HttpStatus.BAD_REQUEST,
                "invalid-parameter",
                "Invalid Parameter",
                "Invalid parameter: " + ex.getName(),
                request
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildProblem(
                HttpStatus.NOT_FOUND,
                "resource-not-found",
                "Resource Not Found",
                "Resource not found",
                request
        ));
    }

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<ProblemDetail> handleJpaSystemException(JpaSystemException ex, HttpServletRequest request) {
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        if (message.contains("No enum constant") && message.contains("Transaction$TransactionType")) {
            log.error("action=error.enum_mapping path={} message={}", request.getRequestURI(), message, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildProblem(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "invalid-transaction-type",
                    "Invalid Transaction Type",
                    "Transaction data contains unsupported type values in database",
                    request
            ));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "database-error",
                "Database Error",
                "An unexpected database error occurred",
                request
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        String raw = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        String message = raw != null ? raw.toLowerCase() : "";

        if (message.contains("email")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildProblem(
                    HttpStatus.CONFLICT,
                    "duplicate-email",
                    "Duplicate Email",
                    "Email already registered",
                    request
            ));
        }
        if (message.contains("phone")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(buildProblem(
                    HttpStatus.CONFLICT,
                    "duplicate-phone",
                    "Duplicate Phone",
                    "Phone number already registered",
                    request
            ));
        }

        log.error("action=error.data_integrity path={} message={}", request.getRequestURI(), raw, ex);
        return ResponseEntity.badRequest().body(buildProblem(
                HttpStatus.BAD_REQUEST,
                "data-integrity",
                "Data Integrity Violation",
                "Invalid or duplicate data",
                request
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        log.error("action=error.runtime path={} message={}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal Server Error",
                "An unexpected error occurred",
                request
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("action=error.unhandled path={} message={}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal Server Error",
                "An unexpected error occurred",
                request
        ));
    }

    private ProblemDetail buildProblem(HttpStatus status, String errorKey, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://fincore.com/errors/" + errorKey));
        problem.setInstance(URI.create(request.getRequestURI()));

        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            Object requestId = request.getAttribute("requestId");
            traceId = requestId != null ? String.valueOf(requestId) : null;
        }
        if (traceId != null && !traceId.isBlank()) {
            problem.setProperty("traceId", traceId);
        }

        return problem;
    }
}
