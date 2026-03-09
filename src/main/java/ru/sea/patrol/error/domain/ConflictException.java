package ru.sea.patrol.error.domain;

public class ConflictException extends ApiException {

	public ConflictException(String message, String errorCode) {
		super(message, errorCode);
	}
}
