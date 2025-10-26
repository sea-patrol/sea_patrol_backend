package ru.sea.patrol.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import ru.sea.patrol.dto.chat.ChatMessage;
import ru.sea.patrol.dto.chat.GameMessageInput;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.GameService;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameWebSocketHandler implements WebSocketHandler {

    private final ChatService chatService;
    private final GameService gameService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication().getName())
                .flatMap(username -> {
                    // 1. Отправляем начальный snapshot
                    var allPlayers = gameService.initAndReturnAllPlayers(username);
                    Mono<Void> initialSnapshot = session.send(
                            Mono.just(createWebSocketMessage("game/players", allPlayers, session))
                    );

                    // 1. Поток чата
                    Flux<WebSocketMessage> chatFlux = chatService.initialize(username, session);

                    // 2. Поток позиций (все игроки!)
                    Flux<WebSocketMessage> stateFlux = gameService.getStateUpdates()
                            .map(update -> createWebSocketMessage("game/position", update, session));

                    Flux<WebSocketMessage> playerLeftFlux = gameService.getPlayerLeftEvents()
                            .map(left -> createWebSocketMessage("game/playerLeft", left, session));

                    // 3. Объединяем потоки
                    Flux<WebSocketMessage> outbound = Flux.merge(chatFlux, stateFlux, playerLeftFlux);

                    // Входящие сообщения
                    Flux<GameMessageInput> inbound = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .map(this::parseMessage);

                    Mono<Void> input = inbound
                            .flatMap(msg -> switch (msg.getType()) {
                                case MessageType.CHAT_MESSAGE, MessageType.CHAT_JOIN, MessageType.CHAT_LEAVE ->
                                        chatService.handle(username, msg);
                                case MessageType.GAME_INPUT ->
                                        gameService.handlePlayerInput(username, msg.getPayload());
                            })
                            .onErrorContinue((ex, obj) -> {
                                // Логируем ошибку, но не рвём соединение
                                log.error("Error processing: " + obj + " | " + ex.getMessage());
                            })
                            .then();

                    // При завершении сессии (отключение клиента)
                    Mono<Void> cleanup = Mono.fromRunnable(() -> {
                        log.info("Player {} disconnected", username);
                        gameService.removePlayer(username);
                    });

                    return initialSnapshot.then(
                            session.send(outbound)
                                    .and(input)
                                    .doFinally(sig -> cleanup.subscribe()));
                }).then();
    }

    private GameMessageInput parseMessage(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty message");
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray() || node.size() < 2) {
                throw new IllegalArgumentException("Expected [type, payload]");
            }
            GameMessageInput msg = new GameMessageInput();
            msg.setType(MessageType.valueOf(node.get(0).asText()));
            msg.setPayload(node.get(1));
            return msg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }

    private String toJson(ChatMessage msg) {
        try {
            return objectMapper.writeValueAsString(new Object[]{"chat", msg});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WebSocketMessage createWebSocketMessage(String type, Object payload, WebSocketSession session) {
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

