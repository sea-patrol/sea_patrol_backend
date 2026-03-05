package ru.sea.patrol.error.api;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import ru.sea.patrol.error.domain.UnauthorizedException;

@Component
public class AppErrorAttributes extends DefaultErrorAttributes {

	private static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";
	private static final String CODE_VALIDATION_ERROR = "SEAPATROL_VALIDATION_ERROR";
	private static final String CODE_BAD_REQUEST = "SEAPATROL_BAD_REQUEST";

	public AppErrorAttributes() {
		super();
	}

	@Override
	public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
		var errorAttributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
		Throwable error = getError(request);

		HttpStatus status;
		var errorList = new ArrayList<Map<String, Object>>();

		if (error instanceof AuthException || error instanceof UnauthorizedException) {
			status = HttpStatus.UNAUTHORIZED;
			ApiException api = (ApiException) error;
			errorList.add(errorMap(api.getErrorCode(), api.getMessage()));
		} else if (error instanceof ApiException api) {
			status = HttpStatus.BAD_REQUEST;
			errorList.add(errorMap(api.getErrorCode(), api.getMessage()));
		} else if (error instanceof WebExchangeBindException bindException) {
			status = HttpStatus.BAD_REQUEST;

			for (FieldError fieldError : bindException.getFieldErrors()) {
				String msg = "%s: %s".formatted(fieldError.getField(), safeMessage(fieldError.getDefaultMessage()));
				errorList.add(errorMap(CODE_VALIDATION_ERROR, msg));
			}
			for (ObjectError objectError : bindException.getGlobalErrors()) {
				String msg = safeMessage(objectError.getDefaultMessage());
				errorList.add(errorMap(CODE_VALIDATION_ERROR, msg));
			}
			if (errorList.isEmpty()) {
				errorList.add(errorMap(CODE_VALIDATION_ERROR, "Validation failed"));
			}
		} else if (error instanceof ConstraintViolationException violationException) {
			status = HttpStatus.BAD_REQUEST;
			violationException.getConstraintViolations().forEach(v -> {
				String msg = "%s: %s".formatted(v.getPropertyPath(), safeMessage(v.getMessage()));
				errorList.add(errorMap(CODE_VALIDATION_ERROR, msg));
			});
			if (errorList.isEmpty()) {
				errorList.add(errorMap(CODE_VALIDATION_ERROR, "Validation failed"));
			}
		} else if (error instanceof ServerWebInputException) {
			status = HttpStatus.BAD_REQUEST;
			errorList.add(errorMap(CODE_BAD_REQUEST, safeMessage(error.getMessage())));
		} else {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
			String message = error.getMessage();
			if (message == null) {
				message = error.getClass().getName();
			}
			errorList.add(errorMap(CODE_INTERNAL_ERROR, message));
		}

		var errors = new HashMap<String, Object>();
		errors.put("errors", errorList);
		errorAttributes.put("status", status.value());
		errorAttributes.put("errors", errors);

		return errorAttributes;
	}

	private static Map<String, Object> errorMap(String code, String message) {
		var errorMap = new LinkedHashMap<String, Object>();
		errorMap.put("code", code);
		errorMap.put("message", message);
		return errorMap;
	}

	private static String safeMessage(String message) {
		return message == null ? "Invalid request" : message;
	}
}
