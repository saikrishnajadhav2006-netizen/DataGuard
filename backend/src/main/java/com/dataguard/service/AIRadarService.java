package com.dataguard.service;

import com.dataguard.entity.RadarConversation;
import com.dataguard.entity.RadarMessage;
import com.dataguard.entity.User;
import com.dataguard.repository.RadarConversationRepository;
import com.dataguard.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
public class AIRadarService {
    private final RadarConversationRepository conversations;
    private final UserRepository users;
    private final RadarChatProvider provider;

    public AIRadarService(RadarConversationRepository conversations, UserRepository users, RadarChatProvider provider) {
        this.conversations = conversations;
        this.users = users;
        this.provider = provider;
    }

    @Transactional
    public ChatResult chat(String email, ChatRequest request) {
        String prompt = request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isBlank() || prompt.length() > 4000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a message up to 4,000 characters.");
        String mode = request.mode() == null ? "find" : request.mode();
        if (!mode.equals("find") && !mode.equals("workflow")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose Find Tools or Workflow mode.");

        User owner = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        RadarConversation conversation;
        if (request.conversationId() == null) {
            conversation = new RadarConversation(owner, titleFor(prompt));
        } else {
            conversation = conversations.findByIdAndOwner_Email(request.conversationId(), email)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
        }

        conversation.addMessage(new RadarMessage("user", prompt));
        conversations.saveAndFlush(conversation);
        List<RadarChatProvider.Turn> turns = conversation.getMessages().stream()
                .skip(Math.max(0, conversation.getMessages().size() - 20))
                .map(message -> new RadarChatProvider.Turn(message.getRole(), message.getContent())).toList();
        String instructions = mode.equals("workflow")
                ? "You are DataGuard AI Radar. Help developers design a practical, safe, step-by-step tool workflow. State uncertainty and do not invent live pricing or tool capabilities."
                : "You are DataGuard AI Radar, an assistant that recommends developer tools. Explain fit and tradeoffs, and do not invent live pricing or tool capabilities.";
        java.util.ArrayList<RadarChatProvider.Turn> promptTurns = new java.util.ArrayList<>();
        promptTurns.add(new RadarChatProvider.Turn("system", instructions));
        promptTurns.addAll(turns);
        String reply = provider.complete(promptTurns);
        conversation.addMessage(new RadarMessage("assistant", reply));
        conversations.save(conversation);
        return new ChatResult(conversation.getId(), reply, toMessages(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationSummary> list(String email) {
        return conversations.findAllByOwner_EmailOrderByUpdatedAtDesc(email).stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitle(), c.getUpdatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public ChatResult get(String email, Long id) {
        RadarConversation conversation = conversations.findByIdAndOwner_Email(id, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
        return new ChatResult(id, null, toMessages(conversation));
    }

    @Transactional
    public void delete(String email, Long id) {
        RadarConversation conversation = conversations.findByIdAndOwner_Email(id, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
        conversations.delete(conversation);
    }

    private List<MessageView> toMessages(RadarConversation c) {
        return c.getMessages().stream().map(m -> new MessageView(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt())).toList();
    }

    private String titleFor(String prompt) {
        String compact = prompt.replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 117) + "...";
    }

    public record ChatRequest(String prompt, String mode, Long conversationId) {}
    public record MessageView(Long id, String role, String content, java.time.Instant createdAt) {}
    public record ConversationSummary(Long id, String title, java.time.Instant updatedAt) {}
    public record ChatResult(Long conversationId, String reply, List<MessageView> messages) {}
}
