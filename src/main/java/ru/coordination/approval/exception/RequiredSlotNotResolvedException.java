package ru.coordination.approval.exception;

public class RequiredSlotNotResolvedException extends RuntimeException {
    public RequiredSlotNotResolvedException(String message) {
        super(message);
    }
}
