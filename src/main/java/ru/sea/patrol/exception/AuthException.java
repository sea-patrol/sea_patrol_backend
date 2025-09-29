package ru.sea.patrol.exception;

public class AuthException extends ApiException{
    public AuthException(String message, String errorCode) {
        super(message, errorCode);
    }
}
