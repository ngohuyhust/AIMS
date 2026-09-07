package vn.aims.user;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.aims.auth.AuthError;

@Service
@Transactional
public class UserAdminService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserAuditLogRepository logs;
    private final PasswordEncoder passwords;
    private final SecureRandom random = new SecureRandom();
    public UserAdminService(UserRepository users, RoleRepository roles, UserAuditLogRepository logs, PasswordEncoder passwords) {
        this.users=users; this.roles=roles; this.logs=logs; this.passwords=passwords;
    }
    private User require(int id) { return users.findById(id).orElseThrow(() -> new AuthError(404,"Không tìm thấy người dùng")); }
    private List<Role> resolve(List<String> names) {
        var found=roles.findByNameIn(names);
        var valid=found.stream().map(Role::getName).toList();
        var invalid=names.stream().filter(name -> !valid.contains(name)).toList();
        if (!invalid.isEmpty()) throw new AuthError(400,"Vai trò không hợp lệ: ["+String.join(", ",invalid)+"]");
        return found;
    }
    private void audit(User user,String action,String description,String actor) {
        logs.saveAndFlush(new UserAuditLog(action,description,actor,user));
    }
    public Map<String,Object> create(UserCreateInput input,String actor) {
        if (users.findByEmail(input.email()).isPresent()) throw new AuthError(400,"User with email ["+input.email()+"] already exists");
        var user=users.saveAndFlush(new User(input.email(),passwords.encode(input.password()),input.fullName(),input.phoneNumber(),resolve(input.roles())));
        audit(user,"CREATE_USER","Tạo tài khoản mới cho ["+input.email()+"] với vai trò ["+String.join(", ",input.roles())+"]",actor);
        return view(user,true);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list() {
        return users.findAll(Sort.by("userID")).stream().map(user -> {
            var result=view(user,true);
            result.put("auditLogs",logs.findByUser_UserIDOrderByCreatedAtDesc(user.getUserID(),PageRequest.of(0,10)).stream().map(log -> logView(log,false)).toList());
            return result;
        }).toList();
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> allLogs() { return logs.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(log -> logView(log,true)).toList(); }
    public Map<String,Object> update(int id,JsonNode input,String actor) {
        var user=require(id);
        if(input.has("email")) user.email=text(input.get("email"));
        if(input.has("fullName")) user.fullName=text(input.get("fullName"));
        if(input.has("phoneNumber")) user.phoneNumber=text(input.get("phoneNumber"));
        users.saveAndFlush(user);
        audit(user,"UPDATE_USER","Cập nhật thông tin tài khoản user ID ["+id+"]",actor);
        return view(user,true);
    }
    public Map<String,Object> status(int id,String input,String actor) {
        String status=switch(input==null?"":input) {
            case "ACTIVE","UNBLOCKED" -> "ACTIVE"; case "DEACTIVATED","BLOCKED" -> "DEACTIVATED";
            default -> throw new AuthError(400,"Trạng thái không hợp lệ");
        };
        var user=require(id); String old=user.status; user.status=status;
        users.saveAndFlush(user);
        audit(user,"TOGGLE_STATUS","Thay đổi trạng thái tài khoản từ ["+old+"] thành ["+status+"] (input: "+input+")",actor);
        return view(user,true);
    }
    public Map<String,Object> roles(int id,List<String> names,String actor) {
        if(names.isEmpty()) throw new AuthError(400,"Danh sách vai trò không được trống");
        var user=require(id);
        String old=String.join(", ",user.getRoles().stream().map(Role::getName).toList());
        user.replaceRoles(resolve(names)); users.saveAndFlush(user);
        audit(user,"UPDATE_ROLES","Cập nhật vai trò từ ["+old+"] thành ["+String.join(", ",names)+"]",actor);
        return view(user,true);
    }
    public Map<String,Object> reset(int id,String actor,boolean authRoute) {
        var user=users.findById(id).orElseThrow(() -> new AuthError(404,authRoute?"Không tìm thấy người dùng cần reset mật khẩu":"Không tìm thấy người dùng"));
        String password;
        if(authRoute) { byte[] bytes=new byte[4]; random.nextBytes(bytes); password=HexFormat.of().withUpperCase().formatHex(bytes); }
        else {
            String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            var value=new StringBuilder(); for(int i=0;i<12;i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            password=value.toString();
        }
        user.replacePasswordHash(passwords.encode(password)); users.saveAndFlush(user);
        audit(user,"RESET_PASSWORD",authRoute?"Admin reset mật khẩu. Mật khẩu mới ngẫu nhiên được cấp phát.":"Đặt lại mật khẩu tạm thời cho user ID ["+id+"]",actor);
        return authRoute?Map.of("success",true,"message","Reset mật khẩu thành công","newPassword",password,"email",user.getEmail(),"fullName",user.getFullName()):Map.of("temporaryPassword",password);
    }
    static String text(JsonNode value) { return value==null || value.isNull()?null:value.asText(); }
    static Map<String,Object> view(User user,boolean withRoles) {
        var map=new LinkedHashMap<String,Object>();
        map.put("userID",user.getUserID()); map.put("email",user.getEmail()); map.put("fullName",user.getFullName());
        map.put("phoneNumber",user.getPhoneNumber()); map.put("status",user.getStatus());
        var date=new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
        map.put("createdAt",date.format(user.getCreatedAt())); map.put("updatedAt",date.format(user.getUpdatedAt()));
        if(withRoles) map.put("roles",user.getRoles().stream().map(role -> Map.of("roleID",role.getRoleID(),"name",role.getName())).toList());
        return map;
    }
    private Map<String,Object> logView(UserAuditLog log,boolean withUser) {
        var map=new LinkedHashMap<String,Object>(); map.put("logID",log.getLogID()); map.put("action",log.getAction());
        map.put("description",log.getDescription()); map.put("performedBy",log.getPerformedBy());
        map.put("createdAt",new DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(log.getCreatedAt()));
        if(withUser) map.put("user",log.getUser()==null?null:view(log.getUser(),false));
        return map;
    }
}
