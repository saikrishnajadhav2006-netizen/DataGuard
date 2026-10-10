package com.dataguard.service;

import com.dataguard.entity.User;
import com.dataguard.repository.RadarConversationRepository;
import com.dataguard.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AIRadarServiceTest {
    @Autowired AIRadarService service;
    @Autowired UserRepository users;
    @Autowired RadarConversationRepository conversations;
    @MockBean RadarChatProvider provider;
    @Autowired TokenRevocationService revocations;
    @Autowired com.dataguard.security.JwtService jwtService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void persistsConversationAndRestrictsReadAndDeleteToOwner() {
        User owner = user("radar-owner@example.test");
        User other = user("radar-other@example.test");
        when(provider.complete(anyList())).thenReturn("Use parameterized SQL queries for the database work.");

        var answer = service.chat(owner.getEmail(), new AIRadarService.ChatRequest("I am building a Python web app", "find", null));
        assertNotNull(answer.conversationId());
        assertEquals(2, answer.messages().size());
        assertEquals("assistant", answer.messages().getLast().role());
        assertEquals(1, service.list(owner.getEmail()).size());
        assertTrue(service.list(other.getEmail()).isEmpty());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.get(other.getEmail(), answer.conversationId()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.delete(other.getEmail(), answer.conversationId()));
        assertEquals(2, service.get(owner.getEmail(), answer.conversationId()).messages().size());
    }

    @Test
    void rejectsBlankAndOverlongPromptsBeforeCallingProvider() {
        var owner = user("radar-validation@example.test");
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.chat(owner.getEmail(), new AIRadarService.ChatRequest("  ", "find", null)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.chat(owner.getEmail(), new AIRadarService.ChatRequest("x".repeat(4001), "find", null)));
    }

    @Test
    void rejectsConversationsOwnedBySomeoneElseWhenContinuingChat() {
        User owner = user("radar-chat-owner@example.test");
        User other = user("radar-chat-other@example.test");
        when(provider.complete(anyList())).thenReturn("A clear, scoped recommendation.");
        var created = service.chat(owner.getEmail(), new AIRadarService.ChatRequest("Suggest tools", "find", null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.chat(other.getEmail(),
                new AIRadarService.ChatRequest("Continue this chat", "find", created.conversationId())));
    }

    @Test
    void logoutRevokesAnIssuedToken() {
        String token = jwtService.generateToken("logout@example.test");
        assertFalse(revocations.isRevoked(token));
        revocations.revoke(token);
        assertTrue(revocations.isRevoked(token));
    }

    @Test
    void registrationHashesPasswordAndLogoutInvalidatesProtectedApiToken() throws Exception {
        String email = "auth-" + System.nanoTime() + "@example.test";
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "fullName", "Test Developer", "email", email, "password", "Correct-Horse-42!"));
        String response = mockMvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();
        User saved = users.findByEmail(email).orElseThrow();
        assertNotEquals("Correct-Horse-42!", saved.getPassword());
        assertTrue(passwordEncoder.matches("Correct-Horse-42!", saved.getPassword()));

        mockMvc.perform(get("/api/radar/conversations"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/radar/conversations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/radar/conversations").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsInvalidCredentials() throws Exception {
        String email = "invalid-login-" + System.nanoTime() + "@example.test";
        User user = new User();
        user.setEmail(email); user.setFullName("Login Test");
        user.setPassword(passwordEncoder.encode("Correct-Horse-42!")); user.setRole(User.Role.DEVELOPER);
        users.save(user);
        String body = objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", "wrong-password"));
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    private User user(String email) {
        return users.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setFullName("Radar Test");
            user.setPassword("not-a-login-password");
            user.setRole(User.Role.DEVELOPER);
            return users.save(user);
        });
    }
}
