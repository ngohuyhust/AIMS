package vn.aims.auth.security;

public final class AccessTokenErrors {
    public static final String MISSING = "Không có quyền truy cập: Token thiếu hoặc không hợp lệ";
    public static final String INVALID = "Không có quyền truy cập: Phiên đăng nhập đã hết hạn hoặc không hợp lệ";

    private AccessTokenErrors() { }
}
