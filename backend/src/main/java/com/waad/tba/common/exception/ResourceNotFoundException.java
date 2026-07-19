package com.waad.tba.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private String messageAr;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, String messageAr) {
        super(message);
        this.messageAr = messageAr;
    }

    public ResourceNotFoundException(String resource, String field, Object value) {
        super("%s not found with %s: '%s'".formatted(resource, field, value));
    }

    public String getMessageAr() {
        return messageAr;
    }
}
