package vn.aims.auth;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/** bcryptjs compatibility: UTF-8, cost 10, and the original 72-byte input limit. */
public class LegacyBcryptPasswordEncoder implements PasswordEncoder {
    private byte[] bytes(CharSequence raw) {
        byte[] bytes = raw.toString().getBytes(StandardCharsets.UTF_8);
        return bytes.length > 72 ? Arrays.copyOf(bytes,72) : bytes;
    }
    public String encode(CharSequence raw) { return BCrypt.hashpw(bytes(raw),BCrypt.gensalt("$2b",10)); }
    public boolean matches(CharSequence raw, String encoded) {
        if (raw == null || encoded == null) return false;
        try { return BCrypt.checkpw(bytes(raw),encoded); }
        catch (IllegalArgumentException invalidHash) { return false; }
    }
}
