package ru.sea.patrol.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;

public interface MessageService {

    Flux<WebSocketMessage> initialize(String username, WebSocketSession session);

    default WebSocketMessage createWebSocketMessage(String type, Object payload, WebSocketSession session, ObjectMapper objectMapper) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            node.set("payload", objectMapper.valueToTree(payload));
            return session.textMessage(node.toString());
        } catch (Exception e) {
            throw new RuntimeException("Serialization error", e);
        }
    }
}
