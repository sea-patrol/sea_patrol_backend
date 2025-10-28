package ru.sea.patrol.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.MessageType;
import ru.sea.patrol.dto.websocket.MessageInput;
import ru.sea.patrol.service.chat.ChatService;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getName())
                .flatMap(username -> {
                    // 1. Поток чата
                    Flux<WebSocketMessage> chatFlux = chatService.initialize(username, session);

                    // 2. Поток позиций (все игроки!)

                    // 3. Объединяем потоки
                    Flux<WebSocketMessage> outbound = Flux.merge(chatFlux);

                    // Входящие сообщения
                    Flux<MessageInput> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .map(this::parseMessage);

                    Mono<Void> input = inbound
                            .flatMap(msg -> switch (msg.getType()) {
                                case MessageType.CHAT_MESSAGE, MessageType.CHAT_JOIN, MessageType.CHAT_LEAVE ->
                                        chatService.handle(username, msg);
                                default -> Mono.empty();
                            })
                            .onErrorContinue((ex, obj) -> {
                                // Логируем ошибку, но не рвём соединение
                                log.error("Error processing: " + obj + " | " + ex.getMessage());
                            })
                            .then();

                    // При завершении сессии (отключение клиента)
                    Mono<Void> cleanup = Mono.fromRunnable(() -> {
                        log.info("Player {} disconnected", username);
                    });

                    return session.send(outbound)
                                    .and(input)
                                    .doFinally(sig -> cleanup.subscribe());
                }).then();
    }

    private MessageInput parseMessage(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty message");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray() || node.size() < 2) {
                throw new IllegalArgumentException("Expected [type, payload]");
            }
            MessageInput msg = new MessageInput();
            msg.setType(MessageType.valueOf(node.get(0).asText()));
            msg.setPayload(node.get(1));
            return msg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}

