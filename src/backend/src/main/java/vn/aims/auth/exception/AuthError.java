package vn.aims.auth.exception;

public class AuthError extends RuntimeException {
    private final int status;
    public AuthError(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
