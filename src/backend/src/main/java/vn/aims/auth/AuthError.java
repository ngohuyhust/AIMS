package vn.aims.auth;

public class AuthError extends RuntimeException {
    final int status;
    public AuthError(int status, String message) { super(message); this.status = status; }
}
