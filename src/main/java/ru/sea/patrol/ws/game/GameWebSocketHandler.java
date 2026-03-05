package ru.sea.patrol.ws.game;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.sea.patrol.ws.protocol.MessageType;
import ru.sea.patrol.ws.protocol.dto.MessageInput;
import ru.sea.patrol.ws.protocol.dto.MessageOutput;
import ru.sea.patrol.service.chat.ChatService;
import ru.sea.patrol.service.game.GameService;

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
                  // 1. Поток чата
                  Flux<WebSocketMessage> chatFlux = chatService.initialize(username)
                          .map(message -> createWebSocketMessage(message, session, objectMapper));

                  // 2. Поток игры
                  Flux<WebSocketMessage> gameFlux = gameService.initialize(username)
                          .map(message -> createWebSocketMessage(message, session, objectMapper));
                  gameService.joinRoom(username, "main");
                  gameService.startRoom("main");

                  // 3. Объединяем потоки
                  Flux<WebSocketMessage> outbound = Flux.merge(chatFlux, gameFlux);

                  // Входящие сообщения
                  Flux<MessageInput> inbound = session.receive()
                          .map(WebSocketMessage::getPayloadAsText)
                          .map(this::parseMessage);

                  Mono<Void> input = inbound
                          .flatMap(msg -> switch (msg.getType()) {
                              case MessageType.CHAT_MESSAGE, MessageType.CHAT_JOIN, MessageType.CHAT_LEAVE ->
                                      chatService.handle(username, msg);
                              case MessageType.PLAYER_INPUT -> gameService.handle(username, msg);
                              default -> Mono.empty();
                          })
                          .onErrorContinue((ex, obj) -> {
                              // Логируем ошибку, но не рвём соединение
                              log.error("Error processing: " + obj + " | " + ex.getMessage());
                          })
                          .then();

                   // При завершении сессии (отключение клиента)
                   Mono<Void> cleanup = Mono.fromRunnable(() -> {
                     chatService.cleanupUser(username);
                     gameService.cleanupPlayer(username);
                     log.info("Player {} disconnected", username);
                   });

                   Mono<Void> sessionFlow = session.send(outbound).and(input);
                   return Mono.usingWhen(
                           Mono.just(username),
                           __ -> sessionFlow,
                           __ -> cleanup,
                           (__, err) -> cleanup,
                           __ -> cleanup);
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

  private WebSocketMessage createWebSocketMessage(
          MessageOutput messageOutput, WebSocketSession session, ObjectMapper objectMapper) {
    try {
      ObjectNode node = objectMapper.createObjectNode();
      node.put("type", messageOutput.getType().toString());
      node.set("payload", objectMapper.valueToTree(messageOutput.getPayload()));
      return session.textMessage(node.toString());
    } catch (Exception e) {
      throw new RuntimeException("Serialization error", e);
    }
  }
}

