package com.hms.user.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}
