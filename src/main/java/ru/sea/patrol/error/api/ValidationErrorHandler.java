package ru.sea.patrol.error.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
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
	public Mono<ResponseEntity<ApiErrorResponse>> handleBindException(WebExchangeBindException ex) {
		List<ApiError> errors = ex.getFieldErrors().stream()
				.map(ValidationErrorHandler::fieldErrorToEntry)
				.toList();

		if (errors.isEmpty()) {
			errors = List.of(new ApiError(CODE_VALIDATION_ERROR, "Validation failed"));
		}

		return Mono.just(ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body(new ApiErrorResponse(errors)));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public Mono<ResponseEntity<ApiErrorResponse>> handleConstraintViolation(ConstraintViolationException ex) {
		List<ApiError> errors = ex.getConstraintViolations().stream()
				.map(violation -> new ApiError(CODE_VALIDATION_ERROR,
						"%s: %s".formatted(violation.getPropertyPath(), violation.getMessage())))
				.toList();

		if (errors.isEmpty()) {
			errors = List.of(new ApiError(CODE_VALIDATION_ERROR, "Validation failed"));
		}

		return Mono.just(ResponseEntity.badRequest()
				.contentType(MediaType.APPLICATION_JSON)
				.body(new ApiErrorResponse(errors)));
	}

	private static ApiError fieldErrorToEntry(FieldError fieldError) {
		String message = "%s: %s".formatted(fieldError.getField(), safe(fieldError.getDefaultMessage()));
		return new ApiError(CODE_VALIDATION_ERROR, message);
	}

	private static String safe(String message) {
		return message == null ? "Invalid request" : message;
	}
}
