package ru.sea.patrol.error.api;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;
import ru.sea.patrol.error.domain.ApiException;
import ru.sea.patrol.error.domain.AuthException;
import ru.sea.patrol.error.domain.ConflictException;
import ru.sea.patrol.error.domain.UnauthorizedException;

@Component
public class AppErrorAttributes extends DefaultErrorAttributes {

	private static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
	private static final String CODE_VALIDATION_ERROR = "SEAPATROL_VALIDATION_ERROR";
	private static final String CODE_BAD_REQUEST = "SEAPATROL_BAD_REQUEST";

	@Override
	public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
		var errorAttributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
		Throwable error = getError(request);

		HttpStatus status;
		var errors = new ArrayList<ApiError>();

		if (error instanceof AuthException authException) {
			status = HttpStatus.UNAUTHORIZED;
			errors.add(new ApiError(authException.getErrorCode(), authException.getMessage()));
		} else if (error instanceof UnauthorizedException unauthorizedException) {
			status = HttpStatus.UNAUTHORIZED;
			errors.add(new ApiError(unauthorizedException.getErrorCode(), unauthorizedException.getMessage()));
		} else if (error instanceof ConflictException conflictException) {
			status = HttpStatus.CONFLICT;
			errors.add(new ApiError(conflictException.getErrorCode(), conflictException.getMessage()));
		} else if (error instanceof ApiException apiException) {
			status = HttpStatus.BAD_REQUEST;
			errors.add(new ApiError(apiException.getErrorCode(), apiException.getMessage()));
		} else if (error instanceof WebExchangeBindException bindException) {
			status = HttpStatus.BAD_REQUEST;
			for (FieldError fieldError : bindException.getFieldErrors()) {
				String message = "%s: %s".formatted(fieldError.getField(), safeMessage(fieldError.getDefaultMessage()));
				errors.add(new ApiError(CODE_VALIDATION_ERROR, message));
			}
			for (ObjectError objectError : bindException.getGlobalErrors()) {
				errors.add(new ApiError(CODE_VALIDATION_ERROR, safeMessage(objectError.getDefaultMessage())));
			}
			if (errors.isEmpty()) {
				errors.add(new ApiError(CODE_VALIDATION_ERROR, "Validation failed"));
			}
		} else if (error instanceof ConstraintViolationException violationException) {
			status = HttpStatus.BAD_REQUEST;
			violationException.getConstraintViolations().forEach(violation -> {
				String message = "%s: %s".formatted(violation.getPropertyPath(), safeMessage(violation.getMessage()));
				errors.add(new ApiError(CODE_VALIDATION_ERROR, message));
			});
			if (errors.isEmpty()) {
				errors.add(new ApiError(CODE_VALIDATION_ERROR, "Validation failed"));
			}
		} else if (error instanceof ServerWebInputException) {
			status = HttpStatus.BAD_REQUEST;
			errors.add(new ApiError(CODE_BAD_REQUEST, safeMessage(error.getMessage())));
		} else {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			String message = error.getMessage();
			if (message == null) {
				message = error.getClass().getName();
			}
			errors.add(new ApiError(CODE_INTERNAL_ERROR, message));
		}

		errorAttributes.put("status", status.value());
		errorAttributes.put("errors", new ApiErrorResponse(errors));
		return errorAttributes;
	}

	private static String safeMessage(String message) {
		return message == null ? "Invalid request" : message;
	}
}
