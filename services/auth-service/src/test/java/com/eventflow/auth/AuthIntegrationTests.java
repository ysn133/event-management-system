package com.eventflow.auth;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.eventflow.auth.domain.AppUser;
import com.eventflow.auth.repository.AppUserRepository;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @BeforeEach void setup() {
        users.deleteAll();
        users.save(new AppUser("tester@example.com","Testeur",passwords.encode("a-long-test-password")));
    }
    @Test void loginReturnsUsableTokenAndNeverExposesPasswordHash() throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"tester@example.com\",\"password\":\"a-long-test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.nom").value("Testeur"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("tester@example.com"));
        assertThat(users.findByEmail("tester@example.com").orElseThrow().getPasswordHash()).startsWith("$2");
    }
    @Test void rejectsWrongPassword() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"tester@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void rejectsUnknownUser() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
    @Test void protectsCurrentUser() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
