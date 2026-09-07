package vn.aims.auth;

import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.user.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserAuditLogRepository logs;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    public AuthService(UserRepository users, UserAuditLogRepository logs, PasswordEncoder passwords, JwtTokens tokens) {
        this.users=users; this.logs=logs; this.passwords=passwords; this.tokens=tokens;
    }
    @Transactional(readOnly = true)
    public Map<String,Object> login(String email, String password) {
        var user = users.findByEmail(email == null ? "" : email)
                .orElseThrow(() -> new AuthError(401,"Tài khoản hoặc mật khẩu không chính xác"));
        if (!"ACTIVE".equals(user.getStatus())) throw new AuthError(401,"Tài khoản đã bị vô hiệu hóa hoặc khóa");
        if (password == null || !passwords.matches(password,user.getPasswordHash()))
            throw new AuthError(401,"Tài khoản hoặc mật khẩu không chính xác");
        var roles = user.getRoles().stream().map(Role::getName).toList();
        return Map.of("token",tokens.issue(user.getUserID(),user.getEmail(),user.getFullName(),roles),
                "user",Map.of("userID",user.getUserID(),"email",user.getEmail(),"fullName",user.getFullName(),"roles",roles));
    }
    @Transactional
    public Map<String,Object> changePassword(int userID, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.replaceAll("^[\\s\\p{Z}\\uFEFF]+|[\\s\\p{Z}\\uFEFF]+$", "").length() < 6)
            throw new AuthError(400,"Mật khẩu mới phải có ít nhất 6 ký tự");
        var user = users.findById(userID).orElseThrow(() -> new AuthError(404,"Không tìm thấy người dùng"));
        if (oldPassword == null || !passwords.matches(oldPassword,user.getPasswordHash()))
            throw new AuthError(400,"Mật khẩu cũ không chính xác");
        user.replacePasswordHash(passwords.encode(newPassword));
        users.save(user);
        logs.save(new UserAuditLog("CHANGE_PASSWORD","Người dùng tự đổi mật khẩu cá nhân",user.getEmail(),user));
        return Map.of("success",true,"message","Đổi mật khẩu thành công");
    }
}
