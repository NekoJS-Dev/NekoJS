package com.tkisor.nekojs.authoring;

/** A rejected authoring request with an HTTP status and a machine-readable location. */
public final class AuthoringException extends RuntimeException {
    private final int status;
    private final String code;
    private final String path;

    AuthoringException(int status, String code, String path, String message) {
        super("[NEKO-4055] " + message);
        this.status = status;
        this.code = code;
        this.path = path;
    }

    public int status() { return status; }
    public String code() { return code; }
    public String path() { return path; }
}
