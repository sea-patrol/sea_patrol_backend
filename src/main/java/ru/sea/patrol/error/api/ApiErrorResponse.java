package ru.sea.patrol.error.api;

import java.util.List;

public record ApiErrorResponse(List<ApiError> errors) {

	public ApiErrorResponse {
		errors = List.copyOf(errors);
	}

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(List.of(new ApiError(code, message)));
	}
}
