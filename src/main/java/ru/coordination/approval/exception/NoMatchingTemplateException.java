package ru.coordination.approval.exception;

public class NoMatchingTemplateException extends RuntimeException {
    public NoMatchingTemplateException(String message) {
        super(message);
    }
}
