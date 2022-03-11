package com.davidepetilli.fdl.api;

public class FDLException extends RuntimeException {
    public FDLException() {
    }

    public FDLException(String message) {
        super(message);
    }

    public FDLException(String message, Throwable cause) {
        super(message, cause);
    }
}
