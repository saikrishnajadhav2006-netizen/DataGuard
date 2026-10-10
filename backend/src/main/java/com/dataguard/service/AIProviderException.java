package com.dataguard.service;

public class AIProviderException extends RuntimeException {
    private final int status;
    public AIProviderException(String message, int status) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
