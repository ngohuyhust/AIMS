package vn.aims.auth.service;

import vn.aims.auth.exception.AuthError;

import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.auth.security.JwtTokens;
import vn.aims.auth.security.AimsUserPrincipal;
import vn.aims.user.entity.*;
import vn.aims.user.repository.*;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserAuditLogRepository logs;
    private final PasswordEncoder passwords;
    private final JwtTokens tokens;
    private final AuthenticationManager authentication;
    public AuthService(UserRepository users, UserAuditLogRepository logs, PasswordEncoder passwords,
            JwtTokens tokens, AuthenticationManager authentication) {
        this.users=users; this.logs=logs; this.passwords=passwords; this.tokens=tokens; this.authentication=authentication;
    }
    public Map<String,Object> login(String email, String password) {
        AimsUserPrincipal principal;
        try {
            var result = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email == null ? "" : email, password));
            principal = (AimsUserPrincipal) result.getPrincipal();
        } catch (InternalAuthenticationServiceException error) {
            if (error.getCause() instanceof org.springframework.dao.DataAccessException databaseFailure)
                throw databaseFailure;
            throw error;
        } catch (DisabledException error) {
            throw new AuthError(401,"Tài khoản đã bị vô hiệu hóa hoặc khóa");
        } catch (AuthenticationException error) {
            throw new AuthError(401,"Tài khoản hoặc mật khẩu không chính xác");
        }
        var roles = principal.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();
        return Map.of("token",tokens.issue(principal.userID(),principal.email(),principal.fullName(),roles),
                "user",Map.of("userID",principal.userID(),"email",principal.email(),"fullName",principal.fullName(),"roles",roles));
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
