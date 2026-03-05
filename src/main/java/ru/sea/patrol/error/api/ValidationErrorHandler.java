package ru.sea.patrol.error.api;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class ValidationErrorHandler {

	private static final String CODE_VALIDATION_ERROR = "SEAPATROL_VALIDATION_ERROR";

	@ExceptionHandler(WebExchangeBindException.class)
	public Mono<ResponseEntity<Map<String, Object>>> handleBindException(WebExchangeBindException ex) {
		List<Map<String, Object>> errors = ex.getFieldErrors().stream()
				.map(ValidationErrorHandler::fieldErrorToEntry)
				.collect(Collectors.toList());

		if (errors.isEmpty()) {
			errors = List.of(errorEntry(CODE_VALIDATION_ERROR, "Validation failed"));
		}

		return Mono.just(ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("errors", errors)));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public Mono<ResponseEntity<Map<String, Object>>> handleConstraintViolation(ConstraintViolationException ex) {
		List<Map<String, Object>> errors = ex.getConstraintViolations().stream()
				.map(v -> errorEntry(CODE_VALIDATION_ERROR, "%s: %s".formatted(v.getPropertyPath(), v.getMessage())))
				.collect(Collectors.toList());

		if (errors.isEmpty()) {
			errors = List.of(errorEntry(CODE_VALIDATION_ERROR, "Validation failed"));
		}

		return Mono.just(ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("errors", errors)));
	}

	private static Map<String, Object> fieldErrorToEntry(FieldError fieldError) {
		String message = "%s: %s".formatted(fieldError.getField(), safe(fieldError.getDefaultMessage()));
		return errorEntry(CODE_VALIDATION_ERROR, message);
	}

	private static Map<String, Object> errorEntry(String code, String message) {
		var entry = new LinkedHashMap<String, Object>();
		entry.put("code", code);
		entry.put("message", message);
		return entry;
	}

	private static String safe(String message) {
		return message == null ? "Invalid request" : message;
	}
}
