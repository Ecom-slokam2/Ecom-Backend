package com.metaarch.fulfillment.exception;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class) ResponseEntity<Map<String,Object>> denied(Exception ex, WebRequest request) { return error(HttpStatus.FORBIDDEN, "Access denied", request); }
  @ExceptionHandler(ResourceNotFoundException.class) ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException ex, WebRequest request) { return error(HttpStatus.NOT_FOUND, ex.getMessage(), request); }
  @ExceptionHandler({ConflictException.class, InvalidFulfillmentStateException.class, DataIntegrityViolationException.class}) ResponseEntity<Map<String, Object>> conflict(Exception ex, WebRequest request) { return error(HttpStatus.CONFLICT, ex instanceof DataIntegrityViolationException ? "Request conflicts with existing fulfillment data" : ex.getMessage(), request); }
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex, WebRequest request) { Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream().collect(Collectors.toMap(e -> e.getField(), e -> Optional.ofNullable(e.getDefaultMessage()).orElse("invalid"), (a, b) -> a, LinkedHashMap::new)); Map<String, Object> body = body(HttpStatus.BAD_REQUEST, "Validation failed", request); body.put("fields", fields); return ResponseEntity.badRequest().body(body); }
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class, org.springframework.beans.TypeMismatchException.class}) ResponseEntity<Map<String, Object>> badRequest(Exception ex, WebRequest request) { return error(HttpStatus.BAD_REQUEST, ex instanceof HttpMessageNotReadableException ? "Malformed request body" : ex.getMessage(), request); }
  @ExceptionHandler(Exception.class) ResponseEntity<Map<String, Object>> unexpected(Exception ex, WebRequest request) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request); }
  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, WebRequest request) { return ResponseEntity.status(status).body(body(status, message, request)); }
  private Map<String, Object> body(HttpStatus status, String message, WebRequest request) { Map<String, Object> body = new LinkedHashMap<>(); body.put("timestamp", Instant.now()); body.put("status", status.value()); body.put("error", status.getReasonPhrase()); body.put("message", message); body.put("path", request.getDescription(false).replace("uri=", "")); return body; }
}
