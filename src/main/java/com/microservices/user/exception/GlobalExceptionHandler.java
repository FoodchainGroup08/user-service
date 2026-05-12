package com.microservices.user.exception;

import com.microservices.user.dto.BaseResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<BaseResponse<Void>> handleNotFound(ResourceNotFoundException e, HttpServletRequest req) {
        return errorResponse(HttpStatus.NOT_FOUND, "Not Found", e.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<Void>> handleBadRequest(IllegalArgumentException e, HttpServletRequest req) {
        return errorResponse(HttpStatus.BAD_REQUEST, "Bad Request", e.getMessage(), req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<BaseResponse<Void>> handleBadCredentials(BadCredentialsException e, HttpServletRequest req) {
        return errorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid email or password", req);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<BaseResponse<Void>> handleJwtException(JwtException e, HttpServletRequest req) {
        return errorResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage(), req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BaseResponse<Void>> handleResponseStatus(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String reason = e.getReason() != null ? e.getReason() : status.getReasonPhrase();
        return errorResponse(status, status.getReasonPhrase(), reason, req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        return errorResponse(HttpStatus.FORBIDDEN, "Forbidden", "You do not have permission to access this resource", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        return ResponseEntity.badRequest().body(baseBody(HttpStatus.BAD_REQUEST, "Validation Failed", "One or more fields are invalid", req)
                .fields(fieldErrors)
                .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleGeneral(Exception e, HttpServletRequest req) {
        log.error("Unhandled exception", e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", req);
    }

    private ResponseEntity<BaseResponse<Void>> errorResponse(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(baseBody(status, error, message, req).build());
    }

    private BaseResponse.BaseResponseBuilder<Void> baseBody(HttpStatus status, String error, String message, HttpServletRequest req) {
        return BaseResponse.<Void>builder()
                .success(false)
                .status(status.value())
                .error(error)
                .message(message)
                .path(req.getRequestURI())
                .timestamp(Instant.now().toString());
    }
}
