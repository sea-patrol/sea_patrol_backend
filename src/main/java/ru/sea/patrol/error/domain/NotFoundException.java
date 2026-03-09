package ru.sea.patrol.error.domain;

public class NotFoundException extends ApiException {

	public NotFoundException(String message, String errorCode) {
		super(message, errorCode);
	}
}
