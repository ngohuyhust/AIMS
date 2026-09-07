package vn.aims.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.aims.auth.JwtTokens;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers @SpringBootTest @AutoConfigureMockMvc
class UserAdminIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokens jwt;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    String admin;
    @BeforeEach void setup() {
        jdbc.update("DELETE FROM user_audit_logs"); jdbc.update("DELETE FROM users");
        admin="Bearer "+jwt.issue(999,"admin@example.test","Admin",List.of("ADMIN"));
    }
    String payload="{\"email\":\"new@example.test\",\"fullName\":\"Tên mới\",\"phoneNumber\":\"0123456789\",\"password\":\"test-password\",\"roles\":[\"STAFF\"]}";
    int create() throws Exception {
        var result=mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.roles[0].name").value("STAFF")).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("userID").asInt();
    }
    @Test void guardsRequireJwtAndExactAdminRole() throws Exception {
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        for(String role:List.of("STAFF","PRODUCT_MANAGER","admin"))
            mvc.perform(get("/api/users").header("Authorization","Bearer "+jwt.issue(1,"a@example.test","A",List.of(role))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Bạn không có quyền truy cập chức năng này"));
    }
    @Test void createListAndAuditShapesHideCredentials() throws Exception {
        int id=create();
        mvc.perform(get("/api/users").header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userID").value(id)).andExpect(jsonPath("$[0].auditLogs[0].action").value("CREATE_USER"))
                .andExpect(jsonPath("$[0].auditLogs[0].user").doesNotExist()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/users/logs").header("Authorization",admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].performedBy").value("admin@example.test"))
                .andExpect(jsonPath("$[0].user.email").value("new@example.test"))
                .andExpect(jsonPath("$[0].user.passwordHash").doesNotExist());
    }
    @Test void invalidCreateDuplicateAndUnknownRolesAre400() throws Exception {
        mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isArray());
        create();
        mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("User with email [new@example.test] already exists"));
        mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("new@example.test","other@example.test").replace("STAFF","UNKNOWN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Vai trò không hợp lệ: [UNKNOWN]"));
    }
    @Test void updateStatusAliasesRolesAndIgnoredFields() throws Exception {
        int id=create();
        mvc.perform(patch("/api/users/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Changed\",\"phoneNumber\":null,\"passwordHash\":\"ignored\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Changed")).andExpect(jsonPath("$.phoneNumber").isEmpty());
        for(String state:List.of("BLOCKED","UNBLOCKED"))
            mvc.perform(patch("/api/users/"+id+"/status").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\""+state+"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(state.equals("BLOCKED")?"DEACTIVATED":"ACTIVE"));
        mvc.perform(patch("/api/users/"+id+"/roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\",\"STAFF\",\"STAFF\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(2));
    }
    @Test void bothResetRoutesPreserveDistinctContractsAndHashNewPasswords() throws Exception {
        int id=create();
        for(String route:List.of("/api/users/"+id+"/reset-password","/api/auth/reset-password/"+id)) {
            var response=mvc.perform(post(route).header("Authorization",admin)).andExpect(status().isCreated()).andReturn();
            var node=json.readTree(response.getResponse().getContentAsString());
            String password=node.get(route.startsWith("/api/users")?"temporaryPassword":"newPassword").asText();
            assertThat(password).matches(route.startsWith("/api/users")?"[a-zA-Z0-9]{12}":"[A-F0-9]{8}");
            assertThat(passwords.matches(password,jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id=?",String.class,id))).isTrue();
        }
    }
    @Test void invalidIdsEmptyRolesAndInvalidStatus() throws Exception {
        int id=create();
        mvc.perform(patch("/api/users/nope").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/users/999999").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/users/"+id+"/roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Danh sách vai trò không được trống"));
        mvc.perform(patch("/api/users/"+id+"/status").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BAD\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void auditFailureRollsBackEntireCreate() throws Exception {
        String longActor="Bearer "+jwt.issue(1,"a".repeat(51)+"@test.vn","A",List.of("ADMIN"));
        mvc.perform(post("/api/users").header("Authorization",longActor).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_audit_logs",Integer.class)).isZero();
    }
    @Test void listCapsAuditAtTenAndDeletedUsersKeepNullAuditUser() throws Exception {
        int id=create();
        for(int i=0;i<12;i++) jdbc.update("INSERT INTO user_audit_logs(action,user_id,created_at) VALUES (?,?,now() + ? * interval '1 second')","EVENT_"+i,id,i+1);
        mvc.perform(get("/api/users").header("Authorization",admin)).andExpect(jsonPath("$[0].auditLogs.length()").value(10))
                .andExpect(jsonPath("$[0].auditLogs[0].action").value("EVENT_11"));
        jdbc.update("DELETE FROM users WHERE user_id=?",id);
        mvc.perform(get("/api/users/logs").header("Authorization",admin)).andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].user").isEmpty());
    }
    @Test void everyMutationRequiresAdminAndIgnoresForgedAttribution() throws Exception {
        int id=create(); String staff="Bearer "+jwt.issue(1,"staff@example.test","Staff",List.of("STAFF"));
        mvc.perform(post("/api/users").header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        for(String suffix:List.of("","/roles","/status"))
            mvc.perform(patch("/api/users/"+id+suffix).header("Authorization",staff).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        for(String route:List.of("/api/users/"+id+"/reset-password","/api/auth/reset-password/"+id))
            mvc.perform(post(route).header("Authorization",staff)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/users/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Renamed\",\"performedBy\":\"forged\",\"status\":\"DEACTIVATED\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.roles[0].name").value("STAFF"));
        assertThat(jdbc.queryForObject("SELECT performed_by FROM user_audit_logs WHERE action='UPDATE_USER'",String.class)).isEqualTo("admin@example.test");
    }
    @Test void emptyCreateMatchesOriginalValidationMessagesAndOrdering() throws Exception {
        // Captured directly using source class-validator/CreateUserDto, without Nest bootstrap.
        var response=mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).get("message"))
                .isEqualTo(json.readTree("""
                        ["email không hợp lệ","fullName không được để trống","fullName must be a string",
                        "phoneNumber không được để trống","phoneNumber must be a string","password tối thiểu 6 ký tự",
                        "password must be a string","each value in roles must be a string","phải chọn ít nhất một vai trò","roles must be an array"]
                        """));
    }
    @Test void emailValidationRequiresDomainTldAndPhoneRemainsRequired() throws Exception {
        for(String email:List.of("a.b@localhost","a@domain.c","a@domain.123","not-an-email"))
            mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                    .content(payload.replace("new@example.test",email)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message[0]").value("email không hợp lệ"));
        mvc.perform(post("/api/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("\"phoneNumber\":\"0123456789\",","")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message[0]").value("phoneNumber không được để trống"));
    }
}
