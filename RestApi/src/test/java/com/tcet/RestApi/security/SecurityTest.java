package com.tcet.RestApi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcet.RestApi.model.User;
import com.tcet.RestApi.repositorys.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityTest {

    private static final String PASSWORD = "Sup3rSecret!";

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private ObjectMapper json;

    private void createUser(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(PASSWORD));
        user.setRole(role);
        users.save(user);
    }

    @Test
    void anonymousReadIsRejected() throws Exception {
        mvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void anonymousWriteIsRejected() throws Exception {
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rollNo":"IT900","fullName":"Anonymous","department":"IT"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerCanRead() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerCannotWrite() throws Exception {
        mvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rollNo":"IT901","fullName":"Viewer","department":"IT"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "viewer", roles = "VIEWER")
    void viewerCannotRegisterUsers() throws Exception {
        mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"sneaky","password":"password123","role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginReturnsAUsableToken() throws Exception {
        createUser("admin2", "ADMIN");

        String body = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin2","password":"Sup3rSecret!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin2"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        String token = json.readTree(body).get("token").asText();

        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        createUser("admin3", "ADMIN");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin3","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void landingPageIsPublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
    }
}
